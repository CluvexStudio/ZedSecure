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

    private fun linux(host: String, port: Int, on: Boolean): Boolean {
        if (!on) {
            exec("gsettings", "set", "org.gnome.system.proxy", "mode", "none")

            exec("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "ProxyType", "0")
            return true
        }

        var ok = exec("gsettings", "set", "org.gnome.system.proxy", "mode", "manual").first == 0
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "host", host)
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "port", port.toString())
        for (proto in listOf("http", "https", "ftp")) {
            exec("gsettings", "set", "org.gnome.system.proxy.$proto", "host", host)
            exec("gsettings", "set", "org.gnome.system.proxy.$proto", "port", port.toString())
        }
        exec("gsettings", "set", "org.gnome.system.proxy", "ignore-hosts",
            "['localhost','127.0.0.0/8','::1','10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']")

        exec("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "ProxyType", "1")
        exec("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "socksProxy", "socks://$host $port")
        exec("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "httpProxy", "http://$host $port")
        exec("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "httpsProxy", "http://$host $port")
        return ok
    }

    private fun windows(host: String, port: Int, on: Boolean): Boolean {
        val key = """HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings"""
        return if (on) {
            exec("reg", "add", key, "/v", "ProxyServer", "/t", "REG_SZ", "/d", "socks=$host:$port", "/f").first == 0 &&
                exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f").first == 0
        } else {
            exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f").first == 0
        }
    }

    private fun macos(host: String, port: Int, on: Boolean): Boolean {
        val service = primaryMacService() ?: "Wi-Fi"
        return if (on) {
            exec("networksetup", "-setsocksfirewallproxy", service, host, port.toString()).first == 0 &&
                exec("networksetup", "-setsocksfirewallproxystate", service, "on").first == 0
        } else {
            exec("networksetup", "-setsocksfirewallproxystate", service, "off").first == 0
        }
    }

    private fun primaryMacService(): String? {
        val (code, out) = exec("networksetup", "-listallnetworkservices")
        if (code != 0) return null
        return out.lineSequence().drop(1).map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("*") }
    }
}
