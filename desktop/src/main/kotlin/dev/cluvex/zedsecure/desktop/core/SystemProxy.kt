package dev.cluvex.zedsecure.desktop.core

object SystemProxy {
    fun set(host: String, port: Int): Boolean = when (Os.current) {
        Os.LINUX -> linux(host, port, on = true)
        Os.WINDOWS -> windows(host, port, on = true)
        Os.MACOS -> macos(host, port, on = true)
        else -> false
    }

    fun clear(): Boolean = when (Os.current) {
        Os.LINUX -> linux("", 0, on = false)
        Os.WINDOWS -> windows("", 0, on = false)
        Os.MACOS -> macos("", 0, on = false)
        else -> false
    }

    private val bypass = listOf("localhost", "127.0.0.0/8", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")

    private fun linux(host: String, port: Int, on: Boolean): Boolean {
        val gnome = gnome(host, port, on)
        val kde = kde(host, port, on)
        return gnome || kde
    }

    private fun gnome(host: String, port: Int, on: Boolean): Boolean {
        if (!onPath("gsettings")) return false
        if (!on) return exec("gsettings", "set", "org.gnome.system.proxy", "mode", "none").first == 0
        if (exec("gsettings", "set", "org.gnome.system.proxy", "mode", "manual").first != 0) return false
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "host", host)
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "port", port.toString())
        for (proto in listOf("http", "https", "ftp")) {
            exec("gsettings", "set", "org.gnome.system.proxy.$proto", "host", host)
            exec("gsettings", "set", "org.gnome.system.proxy.$proto", "port", port.toString())
        }
        exec("gsettings", "set", "org.gnome.system.proxy", "ignore-hosts", bypass.joinToString(",", "[", "]") { "'$it'" })
        return true
    }

    private fun kde(host: String, port: Int, on: Boolean): Boolean {
        val tool = listOf("kwriteconfig6", "kwriteconfig5").firstOrNull(::onPath) ?: return false
        fun write(key: String, value: String) =
            exec(tool, "--file", "kioslaverc", "--group", "Proxy Settings", "--key", key, value).first == 0
        val ok = if (on) {
            write("ProxyType", "1") &&
                write("socksProxy", "socks://$host $port") &&
                write("httpProxy", "http://$host $port") &&
                write("httpsProxy", "http://$host $port") &&
                write("NoProxyFor", bypass.joinToString(","))
        } else {
            write("ProxyType", "0")
        }
        exec("dbus-send", "--type=signal", "/KIO/Scheduler", "org.kde.KIO.Scheduler.reparseSlaveConfiguration", "string:")
        return ok
    }

    private fun onPath(tool: String): Boolean =
        System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
            .any { dir -> dir.isNotEmpty() && java.io.File(dir, tool).canExecute() }

    private fun windows(host: String, port: Int, on: Boolean): Boolean {
        val key = """HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings"""
        return if (on) {
            exec("reg", "add", key, "/v", "ProxyServer", "/t", "REG_SZ", "/d", "$host:$port", "/f").first == 0 &&
                exec("reg", "add", key, "/v", "ProxyOverride", "/t", "REG_SZ", "/d",
                    "localhost;127.*;10.*;172.16.*;192.168.*;<local>", "/f").first == 0 &&
                exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f").first == 0
        } else {
            exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f").first == 0
        }
    }

    private fun macos(host: String, port: Int, on: Boolean): Boolean {
        val services = macServices().ifEmpty { listOf("Wi-Fi") }
        val kinds = listOf("webproxy", "securewebproxy", "socksfirewallproxy")
        val results = services.map { service ->
            if (on) {
                val set = kinds.all { networksetup("-set$it", service, host, port.toString()) } &&
                    kinds.all { networksetup("-set${it}state", service, "on") }
                networksetup("-setproxybypassdomains", service, *MAC_BYPASS.toTypedArray())
                set
            } else {
                kinds.map { networksetup("-set${it}state", service, "off") }.all { it }
            }
        }
        return results.any { it }
    }

    private val MAC_BYPASS = listOf("localhost", "127.0.0.1", "::1", "*.local", "169.254/16", "10/8", "172.16/12", "192.168/16")

    private fun networksetup(vararg args: String): Boolean =
        exec(NETWORKSETUP, *args).first == 0

    private const val NETWORKSETUP = "/usr/sbin/networksetup"

    internal fun macServices(listing: String? = null): List<String> {
        val out = listing ?: exec(NETWORKSETUP, "-listallnetworkservices").let { (code, text) -> if (code == 0) text else return emptyList() }
        return out.lineSequence().drop(1).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }
            .toList()
    }
}
