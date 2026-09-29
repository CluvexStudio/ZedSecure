package dev.cluvex.zedsecure.core

import android.app.Activity
import android.os.Build
import android.security.KeyChain
import android.security.keystore.KeyProperties
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.ui.servers.Ikev2CertBridge
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine

object Ikev2CertBridgeAndroid {
    private var activityRef: WeakReference<Activity>? = null

    fun install(activity: Activity) {
        activityRef = WeakReference(activity)
        Ikev2CertBridge.pickClientCertAlias = { pickClientCertAlias() }
        Ikev2CertBridge.readCaCertPem = { bytes -> readCaCertPem(bytes) }
        Ikev2CertBridge.supportsAdvancedIke = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
        Ikev2CertBridge.validateProposal = { spec, child ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Ikev2Proposals.isParsable(spec, child)
            } else {
                true
            }
        }
    }

    private suspend fun pickClientCertAlias(): String? {
        val activity = activityRef?.get() ?: return null
        return suspendCancellableCoroutine { cont ->

            KeyChain.choosePrivateKeyAlias(
                activity,
                { alias -> if (cont.isActive) cont.resume(alias) },
                arrayOf(KeyProperties.KEY_ALGORITHM_RSA), null, null, -1, null,
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun readCaCertPem(bytes: ByteArray): String? = runCatching {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        val b64 = Base64.encode(cert.encoded)
        buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            b64.chunked(64).forEach { append(it).append('\n') }
            append("-----END CERTIFICATE-----\n")
        }
    }.onFailure { Log.e("Ikev2CertBridge", "CA read failed", it) }.getOrNull()
}
