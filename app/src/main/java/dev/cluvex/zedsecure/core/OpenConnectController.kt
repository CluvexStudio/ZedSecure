package dev.cluvex.zedsecure.core

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.domain.config.OpenConnectProfile
import dev.cluvex.zedsecure.platform.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.infradead.libopenconnect.LibOpenConnect
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class OpenConnectController(
    private val service: VpnService,
    private val profile: OpenConnectProfile,
    private val filesDir: File,

    private val onLog: (Int, String) -> Unit = { _, _ -> },

    private val configureBuilder: (VpnService.Builder) -> Unit,

    private val fallbackDns: List<String> = emptyList(),

    private val onUp: () -> Unit,

    private val onDown: (String?) -> Unit,
) {
    @Volatile private var session: Session? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var stopped = false

    private val rxBytes = AtomicLong(0)
    private val txBytes = AtomicLong(0)
    private val lastRx = AtomicLong(0)
    private val lastTx = AtomicLong(0)

    val isRunning: Boolean get() = worker?.isAlive == true && !stopped

    fun traffic(): Pair<Long, Long> = rxBytes.get() to txBytes.get()

    fun pokeStats() {
        runCatching { session?.requestStats() }
    }

    fun trafficDelta(): Pair<Long, Long> {
        val rx = rxBytes.get()
        val tx = txBytes.get()
        val dRx = (rx - lastRx.getAndSet(rx)).coerceAtLeast(0)
        val dTx = (tx - lastTx.getAndSet(tx)).coerceAtLeast(0)
        return dRx to dTx
    }

    fun start(): Boolean {
        if (!ensureNativeLoaded()) {
            onDown("OpenConnect native library not available")
            return false
        }
        stopped = false
        val t = Thread({ runSession() }, "openconnect-worker")
        t.isDaemon = true
        worker = t
        t.start()
        return true
    }

    fun stop() {
        stopped = true
        AuthFormBus.cancel()
        SsoBus.cancel()
        CertTrustBus.reject()
        runCatching { session?.cancel() }

        runCatching { worker?.interrupt() }
    }

    private fun runSession() {
        val s = try {
            Session(profile.effectiveUserAgent())
        } catch (t: Throwable) {
            Log.e(TAG, "OpenConnect init failed", t)
            onDown("OpenConnect init failed: ${t.message}")
            return
        }
        session = s
        try {
            s.run()
        } catch (t: Throwable) {
            Log.e(TAG, "OpenConnect session crashed", t)
            if (!stopped) onDown("OpenConnect error: ${t.message}")
        } finally {
            runCatching { s.destroy() }
            session = null
        }
    }

    private inner class Session(userAgent: String) : LibOpenConnect(userAgent) {
        @Volatile private var tunPfd: ParcelFileDescriptor? = null
        private var caPath: String = ""
        private var certPath: String = ""
        private var keyPath: String = ""
        private var p12Path: String = ""

        private var passwordConsumed = false
        private var authgroupSet = false
        private var emptyFormStreak = 0

        override fun onProcessAuthForm(authForm: AuthForm): Int {
            authForm.authgroupOpt?.let { group ->
                if (profile.authgroup.isNotBlank()) selectChoice(group, profile.authgroup)
                if (!authgroupSet) {
                    authgroupSet = true
                    return OC_FORM_RESULT_NEWGROUP
                }
            }

            var sawInput = false
            for (opt in authForm.opts) {
                if (opt.flags and OC_FORM_OPT_IGNORE.toLong() != 0L) continue
                when (opt.type) {
                    OC_FORM_OPT_TEXT -> {
                        val saved = profile.formEntries[opt.name]
                        if (opt.value.isNullOrEmpty() && !saved.isNullOrBlank()) {
                            opt.value = saved
                        } else if (opt.value.isNullOrEmpty() && profile.username.isNotBlank() &&

                            isUsernameField(opt.name)
                        ) opt.value = profile.username
                        sawInput = true
                    }
                    OC_FORM_OPT_PASSWORD -> {
                        val saved = profile.formEntries[opt.name]
                        if (opt.value.isNullOrEmpty() && !saved.isNullOrBlank()) {
                            opt.value = saved
                        } else if (opt.value.isNullOrEmpty() && !passwordConsumed && profile.password.isNotEmpty()) {
                            opt.value = profile.password
                            passwordConsumed = true
                        }
                        sawInput = true
                    }
                    OC_FORM_OPT_SELECT -> {
                        if (opt.value.isNullOrEmpty()) {
                            profile.formEntries[opt.name]?.takeIf { it.isNotBlank() }
                                ?.let { selectChoice(opt, it) }
                        }
                        sawInput = true
                    }

                    OC_FORM_OPT_TOKEN -> sawInput = true

                    OC_FORM_OPT_SSO_TOKEN, OC_FORM_OPT_SSO_USER -> sawInput = true
                }
            }

            if (sawInput) {
                emptyFormStreak = 0
            } else if (++emptyFormStreak >= MAX_EMPTY_FORMS) {
                onLog(PRG_ERR, "$emptyFormStreak consecutive empty forms, aborting")
                return OC_FORM_RESULT_CANCELLED
            }

            if (!hasMissingInput(authForm)) return OC_FORM_RESULT_OK

            val filled = AuthFormBus.request(authForm) ?: return OC_FORM_RESULT_CANCELLED
            for (opt in authForm.opts) {
                if (opt.flags and OC_FORM_OPT_IGNORE.toLong() != 0L) continue
                filled[opt.name]?.let { v ->
                    if (opt.type == OC_FORM_OPT_SELECT) selectChoice(opt, v) else opt.value = v
                }
            }
            return if (hasMissingInput(authForm)) OC_FORM_RESULT_CANCELLED else OC_FORM_RESULT_OK
        }

        private fun hasMissingInput(form: AuthForm): Boolean {
            val authgroupName = form.authgroupOpt?.name
            return form.opts.any {
                it.flags and OC_FORM_OPT_IGNORE.toLong() == 0L &&
                    it.value.isNullOrEmpty() &&
                    when (it.type) {
                        OC_FORM_OPT_TEXT, OC_FORM_OPT_PASSWORD -> true
                        OC_FORM_OPT_SELECT -> it.name != authgroupName && it.choices.isNotEmpty()
                        else -> false
                    }
            }
        }

        private fun selectChoice(opt: FormOpt, wanted: String) {
            val match = opt.choices.firstOrNull {
                it.name?.equals(wanted, true) == true || it.label?.equals(wanted, true) == true
            }
            if (match == null && opt.choices.isNotEmpty()) {
                onLog(PRG_ERR, "auth group \"$wanted\" is not offered by the gateway; " +
                    "choices: " + opt.choices.joinToString(", ") { it.name ?: it.label.orEmpty() })
            }
            opt.value = match?.name ?: wanted
        }

        private fun isUsernameField(name: String?): Boolean {
            val n = name.orEmpty()
            return n.startsWith("user", true) || n.startsWith("uname", true)
        }

        override fun onOpenWebview(uri: String): Int {
            onLog(PRG_INFO, "SSO login required")
            val nav = SsoBus.open(uri) ?: run {
                onLog(PRG_ERR, "SSO cancelled")
                return -1
            }
            while (true) {
                val step = nav.next() ?: run {
                    onLog(PRG_ERR, "SSO cancelled")
                    return -1
                }
                val rc = runCatching {
                    webviewLoadChanged(step.uri, step.cookies, step.headers)
                }.getOrElse { t ->
                    onLog(PRG_ERR, "SSO failed: ${t.message}")
                    return -1
                }
                if (rc == 0) {
                    onLog(PRG_INFO, "SSO login complete")
                    SsoBus.finish()
                    return 0
                }
            }
        }

        override fun onReconnected() {
            onLog(PRG_INFO, "reconnected (rekey)")
        }

        override fun onProgress(level: Int, msg: String) {
            val line = msg.trimEnd()

            if (line.startsWith("Failed to open /dev/vhost-net")) {
                onLog(PRG_INFO, "$line (expected on Android: the ordinary tun path is used instead)")
                return
            }
            onLog(level, line)
        }

        override fun onValidatePeerCert(reason: String): Int {
            val actual = runCatching { getPeerCertHash() }.getOrNull().orEmpty()

            if (profile.serverCertSha256.isNotBlank()) {
                if (!isValidCertPin(profile.serverCertSha256)) {
                    onLog(PRG_ERR, "certificate pin needs a sha256:/pin-sha256: prefix. Gateway is: $actual")
                } else if (checkPeerCertHash(profile.serverCertSha256) == 0) {
                    return 0
                } else {
                    onLog(PRG_ERR, "certificate pin did not match. Gateway is: $actual")
                }
            } else {
                onLog(PRG_ERR, "server certificate not trusted ($reason). To pin it, use: $actual")
            }

            if (actual.isNotBlank() && CertTrustBus.request(actual, reason)) {
                onLog(PRG_INFO, "certificate pinned by the user")
                return 0
            }
            return 1
        }

        override fun onProtectSocket(fd: Int) {
            runCatching { service.protect(fd) }
        }

        override fun onStatsUpdate(stats: VPNStats) {
            rxBytes.set(stats.rxBytes)
            txBytes.set(stats.txBytes)
        }

        override fun onSetupTun() {
            val ip = getIPInfo()
            if (ip == null) {
                onDown("OpenConnect: no IP configuration from gateway")
                cancel()
                return
            }
            val pfd = try {
                buildTun(ip).establish()
            } catch (t: Throwable) {
                Log.e(TAG, "establish failed", t)
                null
            }
            if (pfd == null) {
                onDown("Could not establish VPN interface")
                cancel()
                return
            }
            tunPfd = pfd
            setupTunFD(pfd.fd)
            val proto = runCatching { getProtocol() }.getOrNull().orEmpty()
            val dtls = runCatching { getDTLSCipher() }.getOrNull()

            onLog(
                PRG_INFO,
                "connected: proto=$proto dtls=${dtls ?: "off (CSTP only)"} " +
                    "addr=${ip.addr ?: "-"}/${netmaskToPrefix(ip.netmask)} " +
                    "v6=${ip.addr6 ?: "-"} mtu=${ip.MTU} " +
                    "dns=${ip.DNS.orEmpty().ifEmpty { listOf("none from gateway") }.joinToString(",")} " +
                    "routes=${if (ip.splitIncludes.isEmpty()) "full" else "${ip.splitIncludes.size} split"} " +
                    "excludes=${ip.splitExcludes.orEmpty().size}",
            )
            onUp()
        }

        private fun buildTun(ip: IPInfo): VpnService.Builder = service.Builder().apply {
            setSession(profile.server.ifBlank { "OpenConnect" })
            setMtu(if (ip.MTU in 576..9000) ip.MTU else 1400)
            ip.addr?.takeIf { it.isNotBlank() }?.let { addAddress(it, netmaskToPrefix(ip.netmask)) }
            ip.addr6?.takeIf { it.isNotBlank() }?.let { addAddress(it, prefix6(ip.netmask6)) }
            val gatewayDns = ip.DNS.orEmpty().filter { it.isNotBlank() }
            val dns = gatewayDns.ifEmpty { fallbackDns.filter { it.isNotBlank() } }
            dns.forEach { d -> runCatching { addDnsServer(d) } }
            if (gatewayDns.isEmpty()) {
                onLog(
                    PRG_INFO,
                    "gateway assigned no DNS server; using ${dns.joinToString(", ").ifBlank { "none" }} " +
                        "so lookups do not go to the carrier's private resolver through the tunnel",
                )
            }

            (ip.domain.orEmpty().split(' ', ',') + ip.splitDNS.orEmpty())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { d -> runCatching { addSearchDomain(d) } }

            if (ip.splitIncludes.isEmpty()) {
                addRoute("0.0.0.0", 0)
                if (ip.addr6 != null) addRoute("::", 0)
            } else {
                ip.splitIncludes.forEach { r -> addCidrRoute(r) }
            }

            val excludes = ip.splitExcludes.orEmpty().filter { it.isNotBlank() }
            if (excludes.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= 33) {
                    excludes.forEach { r -> addCidrExclude(r) }
                } else {
                    onLog(PRG_ERR, "gateway sent ${excludes.size} split-exclude route(s); " +
                        "excluding routes needs Android 13+, they will be tunnelled")
                }
            }

            configureBuilder(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
        }

        @androidx.annotation.RequiresApi(33)
        private fun VpnService.Builder.addCidrExclude(cidr: String) {
            val slash = cidr.indexOf('/')
            runCatching {
                if (slash < 0) excludeRoute(android.net.IpPrefix(
                    java.net.InetAddress.getByName(cidr), if (cidr.contains(':')) 128 else 32))
                else excludeRoute(android.net.IpPrefix(
                    java.net.InetAddress.getByName(cidr.substring(0, slash)),
                    cidr.substring(slash + 1).toIntOrNull() ?: return))
            }
        }

        private fun VpnService.Builder.addCidrRoute(cidr: String) {
            val slash = cidr.indexOf('/')
            if (slash < 0) {
                runCatching { addRoute(cidr, if (cidr.contains(':')) 128 else 32) }
            } else {
                val addr = cidr.substring(0, slash)
                val pfx = cidr.substring(slash + 1).toIntOrNull() ?: return
                runCatching { addRoute(addr, pfx) }
            }
        }

        fun run() {
            setLogLevel(PRG_INFO)
            writeCertMaterial()

            if (parseURL(profile.serverUrl()) != 0) { onDown("Invalid gateway URL"); return }
            if (profile.protocol.isNotBlank() && setProtocol(profile.protocol) != 0) {
                onDown("Protocol not supported by this build: ${profile.protocol}"); return
            }

            if (profile.reportedOs.isNotBlank()) {
                setReportedOS(profile.reportedOs)
                if (OpenConnectProfile.isMobileOs(profile.reportedOs)) {
                    runCatching { setMobileInfo("1.0", profile.reportedOs, deviceUniqueId()) }
                }
            }

            if (profile.serverCertSha256.isNotBlank()) setSystemTrust(false)

            if (profile.sni.isNotBlank()) setSNI(profile.sni.trim())
            if (profile.mtu > 0) setReqMTU(profile.mtu.coerceAtLeast(MIN_MTU))
            if (profile.disableIpv6) disableIPv6()
            if (profile.proxy.isNotBlank()) {
                if (profile.proxyAuth.isNotBlank()) setProxyAuth(profile.proxyAuth.trim())
                if (setHTTPProxy(profile.proxy.trim()) != 0) {
                    onDown("Invalid proxy: ${profile.proxy}"); return
                }
            }

            if (caPath.isNotBlank()) setCAFile(caPath)

            if (p12Path.isNotBlank()) setClientCert(p12Path, p12Path)
            else if (certPath.isNotBlank()) setClientCert(certPath, keyPath.ifBlank { certPath })
            if (profile.clientKeyPassword.isNotBlank()) setKeyPassword(profile.clientKeyPassword)

            when (profile.tokenMode) {
                OpenConnectProfile.TOKEN_TOTP -> setTokenMode(OC_TOKEN_MODE_TOTP, profile.tokenSecret)
                OpenConnectProfile.TOKEN_HOTP -> setTokenMode(OC_TOKEN_MODE_HOTP, profile.tokenSecret)
                OpenConnectProfile.TOKEN_STOKEN -> setTokenMode(OC_TOKEN_MODE_STOKEN, profile.tokenSecret)
                OpenConnectProfile.TOKEN_OIDC -> setTokenMode(OC_TOKEN_MODE_OIDC, profile.tokenSecret)
            }

            if (profile.disableDtls) disableDTLS()

            if (stopped) { onDown(null); return }
            if (obtainCookie() != 0) { onDown("Authentication failed"); return }
            if (makeCSTPConnection() != 0) { onDown("CSTP connection failed"); return }
            if (!profile.disableDtls) setupDTLS(DTLS_ATTEMPT_PERIOD)

            val reconnectTimeout = profile.reconnectTimeoutSec
                .takeIf { it > 0 } ?: OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT
            val rc = mainloop(reconnectTimeout, RECONNECT_INTERVAL_MIN)
            runCatching { tunPfd?.close() }
            tunPfd = null
            cleanupCertMaterial()
            onDown(if (rc == 0 || stopped) null else "OpenConnect exited ($rc)")
        }

        private fun writeCertMaterial() {
            val dir = File(filesDir, "openconnect").apply { mkdirs() }

            cleanupCertMaterial()
            if (profile.caCertPem.isNotBlank()) {
                caPath = File(dir, "ca.pem").writeSecret(profile.caCertPem.encodeToByteArray())
            }
            if (profile.clientCertP12Base64.isNotBlank()) {
                val der = runCatching { decodeBase64(profile.clientCertP12Base64) }.getOrNull()
                if (der == null || der.isEmpty()) {
                    onLog(PRG_ERR, "client certificate bundle is not valid PKCS#12 data")
                } else {
                    p12Path = File(dir, "cert.p12").writeSecret(der)
                }
            }
            if (p12Path.isBlank() && profile.clientCertPem.isNotBlank()) {
                certPath = File(dir, "cert.pem").writeSecret(profile.clientCertPem.encodeToByteArray())
            }
            if (p12Path.isBlank() && profile.clientKeyPem.isNotBlank()) {
                keyPath = File(dir, "key.pem").writeSecret(profile.clientKeyPem.encodeToByteArray())
            }
        }

        private fun File.writeSecret(bytes: ByteArray): String {
            writeBytes(bytes)
            runCatching { setReadable(false, false); setReadable(true, true); setWritable(false, false) }
            return absolutePath
        }

        private fun cleanupCertMaterial() {
            runCatching { File(filesDir, "openconnect").listFiles()?.forEach { it.delete() } }
        }
    }

    companion object {
        private const val TAG = "OpenConnect"

        private const val MAX_EMPTY_FORMS = 3

        private const val MIN_MTU = 576

        private const val DTLS_ATTEMPT_PERIOD = 60

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        fun decodeBase64(text: String): ByteArray {
            val cleaned = text.filterNot { it.isWhitespace() }
            return runCatching { kotlin.io.encoding.Base64.Default.decode(cleaned) }
                .recoverCatching { kotlin.io.encoding.Base64.UrlSafe.decode(cleaned) }
                .getOrElse { ByteArray(0) }
        }

        fun deviceUniqueId(): String {
            val raw = DeviceIdentity.id.ifBlank { "zedsecure" }

            return raw.filter { it.isLetterOrDigit() }.lowercase().padEnd(16, '0').take(40)
        }

        private val PIN_PREFIX = Regex("^(sha1|sha256|pin-sha256):", RegexOption.IGNORE_CASE)

        fun isValidCertPin(pin: String): Boolean =
            pin.isBlank() || PIN_PREFIX.containsMatchIn(pin.trim())

        @Volatile private var nativeLoaded = false

        @Synchronized
        fun ensureNativeLoaded(): Boolean {
            if (nativeLoaded) return true
            return try {
                System.loadLibrary("openconnect")
                nativeLoaded = true
                true
            } catch (t: Throwable) {
                Log.w(TAG, "libopenconnect.so not available: ${t.message}")
                false
            }
        }

        fun netmaskToPrefix(netmask: String?): Int {
            if (netmask.isNullOrBlank()) return 32
            val parts = netmask.split('.')
            if (parts.size != 4) return netmask.toIntOrNull()?.coerceIn(0, 32) ?: 32
            var bits = 0
            for (p in parts) {
                val v = p.toIntOrNull() ?: return 32
                bits += Integer.bitCount(v and 0xFF)
            }
            return bits.coerceIn(0, 32)
        }

        fun prefix6(netmask6: String?): Int {
            if (netmask6.isNullOrBlank()) return 64
            val tail = netmask6.substringAfterLast('/', netmask6)
            return tail.toIntOrNull()?.coerceIn(0, 128) ?: 64
        }
    }
}

object AuthFormBus {
    class Prompt(val form: LibOpenConnect.AuthForm) {
        val banner: String? get() = form.banner
        val message: String? get() = form.message
        val error: String? get() = form.error
        val opts: List<LibOpenConnect.FormOpt> get() = form.opts
    }

    private val _pending = MutableStateFlow<Prompt?>(null)

    val pending: StateFlow<Prompt?> = _pending.asStateFlow()

    @Volatile private var inflight: CompletableFuture<Map<String, String>?>? = null

    fun request(form: LibOpenConnect.AuthForm): Map<String, String>? {
        val future = CompletableFuture<Map<String, String>?>()
        inflight = future
        _pending.value = Prompt(form)
        return try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            null
        } finally {
            _pending.value = null
            inflight = null
        }
    }

    fun submit(values: Map<String, String>) {
        inflight?.complete(values)
    }

    fun cancel() {
        inflight?.complete(null)
    }

    private const val TIMEOUT_MS = 120_000L
}

object SsoBus {
    class Step(val uri: String, val cookies: Array<String>, val headers: Array<String>)

    class Navigation internal constructor() {
        private val queue = java.util.concurrent.LinkedBlockingQueue<Any>()
        private object Done

        internal fun push(step: Step) { queue.put(step) }
        internal fun abort() { queue.put(Done) }

        fun next(): Step? = when (val v = queue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            is Step -> v
            else -> null
        }
    }

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    @Volatile private var nav: Navigation? = null

    fun open(uri: String): Navigation? {
        val n = Navigation()
        nav = n
        _pending.value = uri
        return n
    }

    fun report(uri: String, cookies: Array<String>, headers: Array<String>) {
        nav?.push(Step(uri, cookies, headers))
    }

    fun finish() {
        _pending.value = null
        nav = null
    }

    fun cancel() {
        nav?.abort()
        _pending.value = null
        nav = null
    }

    private const val TIMEOUT_MS = 300_000L
}

object CertTrustBus {
    class Prompt(val hash: String, val reason: String)

    private val _pending = MutableStateFlow<Prompt?>(null)

    val pending: StateFlow<Prompt?> = _pending.asStateFlow()

    @Volatile private var inflight: CompletableFuture<Boolean>? = null

    fun request(hash: String, reason: String): Boolean {
        val future = CompletableFuture<Boolean>()
        inflight = future
        _pending.value = Prompt(hash, reason)
        return try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            false
        } finally {
            _pending.value = null
            inflight = null
        }
    }

    fun accept() { inflight?.complete(true) }

    fun reject() { inflight?.complete(false) }

    private const val TIMEOUT_MS = 120_000L
}
