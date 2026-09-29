package dev.cluvex.zedsecure.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Ikev2VpnProfile
import android.net.VpnProfileState
import android.net.ipsec.ike.IkeFqdnIdentification
import android.net.ipsec.ike.IkeIpv4AddrIdentification
import android.net.ipsec.ike.IkeIpv6AddrIdentification
import android.net.ipsec.ike.IkeRfc822AddrIdentification
import android.net.ipsec.ike.IkeSessionParams
import android.net.ipsec.ike.IkeTunnelConnectionParams
import android.net.ipsec.ike.TunnelModeChildSessionParams
import android.os.Build
import android.security.KeyChain
import dev.cluvex.zedsecure.core.AppLog as Log
import androidx.annotation.RequiresApi
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.config.Ikev2Auth
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object Ikev2Controller {
    private const val TAG = "Ikev2Controller"

    private const val CONNECT_TIMEOUT_MS = 45_000L

    private const val POLL_INTERVAL_MS = 750L

    @Volatile
    private var activeRemark: String? = null

    val isActive: Boolean get() = activeRemark != null

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNELS)

    val isSupportedSdk: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @RequiresApi(Build.VERSION_CODES.R)
    @Throws(Ikev2Exception::class)
    fun provision(context: Context, profile: Ikev2Profile): Intent? {
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !isMainThread()) {
            "Ikev2Controller.provision must run off the main thread (KeyChain is @WorkerThread)"
        }
        val vpnManager = context.getSystemService(android.net.VpnManager::class.java)
            ?: throw Ikev2Exception(R.string.ikev2_vpn_manager_unavailable)
        val materials = materials(context, profile)

        buildAdvanced(profile, materials)?.let { advanced ->
            try {
                return provisionOne(vpnManager, advanced, "advanced")
            } catch (e: SecurityException) {
                throw Ikev2Exception(R.string.ikev2_blocked_by_always_on, e)
            } catch (e: Exception) {
                note("platform refused the advanced profile; falling back to its own proposals", e)
            }
        }

        val simple = buildSimple(profile, materials)
        return try {
            provisionOne(vpnManager, simple, "simple")
        } catch (e: SecurityException) {
            throw Ikev2Exception(R.string.ikev2_blocked_by_always_on, e)
        }
    }

    private fun provisionOne(
        vpnManager: android.net.VpnManager,
        built: Ikev2VpnProfile,
        label: String,
    ): Intent? {
        val consent = vpnManager.provisionVpnProfile(built)
        note("provisioned ($label); consent " + if (consent != null) "required" else "already granted")
        return consent
    }

    internal fun note(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.i(TAG, message)
        LogBus.append((if (error != null) "E/IKEv2 " else "I/IKEv2 ") + message +
            (error?.let { ": ${it::class.java.simpleName}: ${it.message}" } ?: ""))
    }

    private fun isMainThread(): Boolean =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    @RequiresApi(Build.VERSION_CODES.R)
    fun startProvisioned(context: Context, remark: String) {
        val vpnManager = context.getSystemService(android.net.VpnManager::class.java) ?: run {
            VpnManager.onError(context.getString(R.string.ikev2_vpn_manager_unavailable)); return
        }
        activeRemark = remark
        note("starting provisioned session for $remark")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vpnManager.startProvisionedVpnProfileSession()
            } else {
                @Suppress("DEPRECATION")
                vpnManager.startProvisionedVpnProfile()
            }
        } catch (e: SecurityException) {
            fail(context, context.getString(R.string.ikev2_blocked_by_always_on), e)
            return
        } catch (e: Exception) {
            fail(context, e.message ?: context.getString(R.string.ikev2_failed_generic), e)
            return
        }

        VpnManager.setActiveKind(VpnManager.KIND_IKEV2)
        awaitConnected(context, remark)
    }

    private fun awaitConnected(context: Context, remark: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            VpnManager.onConnected(remark).also { startMetrics() }
            return
        }
        val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (activeRemark == null) return
            when (currentState(context)) {
                VpnProfileState.STATE_CONNECTED -> {
                    note("platform reports CONNECTED")
                    VpnManager.onConnected(remark).also { startMetrics() }
                    return
                }
                VpnProfileState.STATE_FAILED -> {
                    fail(context, context.getString(R.string.ikev2_ike_error), null); return
                }
                VpnProfileState.STATE_DISCONNECTED -> {
                    if (System.currentTimeMillis() > deadline - CONNECT_TIMEOUT_MS + 5_000L) Unit
                }
                else -> Unit
            }
            runCatching { Thread.sleep(POLL_INTERVAL_MS) }.onFailure { return }
        }
        fail(context, context.getString(R.string.ikev2_connect_timeout), null)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun currentState(context: Context): Int? = runCatching {
        context.getSystemService(android.net.VpnManager::class.java)
            ?.getProvisionedVpnProfileState()?.state
    }.getOrNull()

    private fun fail(context: Context, reason: String, cause: Throwable?) {
        note("failed: $reason", cause)
        activeRemark = null
        runCatching {
            context.getSystemService(android.net.VpnManager::class.java)?.stopProvisionedVpnProfile()
        }
        forgetProvisionedProfile(context)
        VpnManager.onError(reason)
    }

    private fun startMetrics() {
        if (metricsJob?.isActive == true) return
        val startedAt = System.currentTimeMillis()
        var lastRx = android.net.TrafficStats.getTotalRxBytes()
        var lastTx = android.net.TrafficStats.getTotalTxBytes()
        var totalDown = 0L
        var totalUp = 0L
        metricsJob = metricsScope.launch {
            while (isActive) {
                delay(1_000)
                if (activeRemark == null) break
                val rx = android.net.TrafficStats.getTotalRxBytes()
                val tx = android.net.TrafficStats.getTotalTxBytes()

                val down = if (rx >= 0 && lastRx >= 0 && rx >= lastRx) rx - lastRx else 0L
                val up = if (tx >= 0 && lastTx >= 0 && tx >= lastTx) tx - lastTx else 0L
                lastRx = rx
                lastTx = tx
                totalDown += down
                totalUp += up
                VpnManager.onMetrics(
                    ((System.currentTimeMillis() - startedAt) / 1000).toInt(),
                    down, up, totalDown, totalUp,
                )
            }
        }
    }

    private fun stopMetrics() {
        metricsJob?.cancel()
        metricsJob = null
    }

    private val metricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var metricsJob: Job? = null

    fun stop(context: Context) {
        stopMetrics()
        activeRemark = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.getSystemService(android.net.VpnManager::class.java)?.stopProvisionedVpnProfile()
            }
            forgetProvisionedProfile(context)
        }
        VpnManager.onDisconnected()
    }

    fun forgetProvisionedProfile(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            context.getSystemService(android.net.VpnManager::class.java)?.deleteProvisionedVpnProfile()
        }.onFailure { Log.w(TAG, "deleteProvisionedVpnProfile failed", it) }
    }

    fun onPlatformEvent(context: Context, state: Int?, reason: String?) {
        val remark = activeRemark ?: return
        when {
            reason != null -> fail(context, reason, null)
            state == VpnProfileState.STATE_CONNECTED -> VpnManager.onConnected(remark).also { startMetrics() }
            state == VpnProfileState.STATE_DISCONNECTED -> { activeRemark = null; VpnManager.onDisconnected() }
        }
    }

    fun resync(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return isActive
        val state = currentState(context) ?: return isActive
        val up = state == VpnProfileState.STATE_CONNECTED || state == VpnProfileState.STATE_CONNECTING
        if (up && activeRemark == null) {
            activeRemark = context.getString(R.string.ikev2_adopted_session)
            if (state == VpnProfileState.STATE_CONNECTED) VpnManager.onConnected(activeRemark).also { startMetrics() }
            else VpnManager.onStarting(activeRemark!!)
        } else if (!up && activeRemark != null) {
            activeRemark = null
            VpnManager.onDisconnected()
        }
        return up
    }

    private class Materials(
        val auth: Ikev2Auth,
        val serverCa: X509Certificate?,
        val cert: Pair<X509Certificate, PrivateKey>?,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private fun materials(context: Context, profile: Ikev2Profile): Materials {
        val auth = profile.effectiveAuth
        return Materials(
            auth = auth,
            serverCa = parseCa(profile.caCertPem),

            cert = if (auth == Ikev2Auth.CERTIFICATE || auth == Ikev2Auth.EAP_TLS) {
                loadClientCert(context, profile)
            } else null,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildAdvanced(profile: Ikev2Profile, m: Materials): Ikev2VpnProfile? {
        val wanted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            m.auth != Ikev2Auth.EAP_MSCHAPV2 &&
            (profile.remoteId.isNotBlank() || profile.ikeProposal.isNotBlank() ||
                profile.espProposal.isNotBlank())
        if (!wanted) return null
        return buildViaIkeParams(profile, m.auth, m.cert, m.serverCa)
            ?: null.also { Log.w(TAG, "advanced IKE parameters were not usable") }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildSimple(profile: Ikev2Profile, m: Materials): Ikev2VpnProfile {
        val auth = m.auth
        val cert = m.cert
        val serverCa = m.serverCa
        val identity = localIdentity(profile, cert?.first)

        Log.i(TAG, "IKEv2 simple params: identity=$identity mtu=${profile.mtu} (platform adds mobike)")
        val builder = Ikev2VpnProfile.Builder(profile.server, identity)
        tune(builder, profile)
        applyAuth(builder, profile, auth, cert, serverCa)
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildViaIkeParams(
        profile: Ikev2Profile,
        auth: Ikev2Auth,
        cert: Pair<X509Certificate, PrivateKey>?,
        serverCa: X509Certificate?,
    ): Ikev2VpnProfile? = runCatching {
        val ike = IkeSessionParams.Builder()
            .setServerHostname(profile.server)
            .setLocalIdentification(ikeIdentification(localIdentity(profile, cert?.first)))
            .setRemoteIdentification(ikeIdentification(profile.effectiveRemoteId))

            .addIkeOption(IkeSessionParams.IKE_OPTION_MOBIKE)
        when (auth) {
            Ikev2Auth.PSK -> ike.setAuthPsk(profile.plainPsk().toByteArray())
            Ikev2Auth.CERTIFICATE, Ikev2Auth.EAP_TLS -> {
                val (chainCert, key) = cert ?: return@runCatching null
                ike.setAuthDigitalSignature(serverCa, chainCert, key)
            }

            Ikev2Auth.EAP_MSCHAPV2 -> return@runCatching null
        }

        ike.addIkeSaProposal(Ikev2Proposals.parseIke(profile.ikeProposal) ?: Ikev2Proposals.defaultIke())

        val child = TunnelModeChildSessionParams.Builder()
        child.addChildSaProposal(
            Ikev2Proposals.parseChild(profile.espProposal) ?: Ikev2Proposals.defaultChild(),
        )
        child.addInternalAddressRequest(android.system.OsConstants.AF_INET)
            .addInternalAddressRequest(android.system.OsConstants.AF_INET6)
            .addInternalDnsServerRequest(android.system.OsConstants.AF_INET)
            .addInternalDnsServerRequest(android.system.OsConstants.AF_INET6)

        Log.i(TAG, "IKEv2 advanced params: mobike=on remoteId=${profile.effectiveRemoteId} mtu=${profile.mtu}")
        Ikev2VpnProfile.Builder(IkeTunnelConnectionParams(ike.build(), child.build()))
            .also { tune(it, profile) }
            .build()
    }.onFailure { Log.w(TAG, "IkeTunnelConnectionParams build failed", it) }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tune(builder: Ikev2VpnProfile.Builder, profile: Ikev2Profile) {
        builder.setMaxMtu(profile.mtu.coerceIn(1280, 1500))

        builder.setMetered(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setRequiresInternetValidation(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setAutomaticNattKeepaliveTimerEnabled(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyAuth(
        builder: Ikev2VpnProfile.Builder,
        profile: Ikev2Profile,
        auth: Ikev2Auth,
        cert: Pair<X509Certificate, PrivateKey>?,
        serverCa: X509Certificate?,
    ) {
        when (auth) {
            Ikev2Auth.EAP_MSCHAPV2 ->
                builder.setAuthUsernamePassword(profile.username, profile.plainPassword(), serverCa)
            Ikev2Auth.CERTIFICATE, Ikev2Auth.EAP_TLS -> {
                val (chainCert, key) = cert ?: throw Ikev2Exception(R.string.ikev2_cert_unavailable)
                builder.setAuthDigitalSignature(chainCert, key, serverCa)
            }
            Ikev2Auth.PSK -> builder.setAuthPsk(profile.plainPsk().toByteArray())
        }
    }

    private fun loadClientCert(context: Context, profile: Ikev2Profile): Pair<X509Certificate, PrivateKey> {
        val chain = try {
            KeyChain.getCertificateChain(context, profile.userCertAlias)
        } catch (e: Exception) {
            throw Ikev2Exception(R.string.ikev2_cert_unavailable, e)
        }
        val key = try {
            KeyChain.getPrivateKey(context, profile.userCertAlias)
        } catch (e: Exception) {
            throw Ikev2Exception(R.string.ikev2_cert_unavailable, e)
        }
        if (chain.isNullOrEmpty() || key == null) throw Ikev2Exception(R.string.ikev2_cert_unavailable)

        if (key !is RSAPrivateKey || key.encoded == null) {
            throw Ikev2Exception(R.string.ikev2_cert_not_exportable)
        }
        return (chain.first() as X509Certificate) to key
    }

    internal fun localIdentity(profile: Ikev2Profile, cert: X509Certificate?): String {
        profile.localId.takeIf { it.isNotBlank() }?.let { return it }
        cert?.let { certIdentity(it)?.let { san -> return san } }
        return profile.username.ifBlank { profile.server }
    }

    internal fun certIdentity(cert: X509Certificate): String? {
        val sans = runCatching { cert.subjectAlternativeNames }.getOrNull()
        sans?.forEach { entry ->
            val type = entry.getOrNull(0) as? Int ?: return@forEach
            val value = entry.getOrNull(1) as? String ?: return@forEach
            when (type) {
                2 -> return "@$value"
                1 -> return "@@$value"
                7 -> return value
            }
        }
        val dn = runCatching { cert.subjectX500Principal.name }.getOrNull() ?: return null
        val cn = Regex("CN=([^,]+)").find(dn)?.groupValues?.getOrNull(1)?.trim()
        return cn?.takeIf { it.isNotBlank() }?.let { if (it.contains('@')) "@@$it" else "@$it" }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ikeIdentification(value: String): android.net.ipsec.ike.IkeIdentification = when {
        value.startsWith("@@") -> IkeRfc822AddrIdentification(value.removePrefix("@@"))
        value.startsWith("@") -> IkeFqdnIdentification(value.removePrefix("@"))
        else -> when (val addr = runCatching { InetAddress.getByName(value) }.getOrNull()) {
            is Inet4Address -> IkeIpv4AddrIdentification(addr)
            is Inet6Address -> IkeIpv6AddrIdentification(addr)
            else -> IkeFqdnIdentification(value)
        }
    }

    private fun parseCa(pem: String): X509Certificate? {
        if (pem.isBlank()) return null
        return runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
        }.getOrElse { throw Ikev2Exception(R.string.ikev2_ca_invalid, it) }
    }
}

class Ikev2Exception(
    val messageRes: Int,
    cause: Throwable? = null,
) : Exception(cause?.message, cause)
