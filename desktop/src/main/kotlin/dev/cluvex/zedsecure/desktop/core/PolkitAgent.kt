package dev.cluvex.zedsecure.desktop.core

import java.io.File

object PolkitAgent {
    fun available(
        env: Map<String, String> = System.getenv(),
        processes: () -> List<String> = ::runningCommands,
    ): Boolean = desktopHasAgent(env) || processes().any(::isAgent)

    internal fun desktopHasAgent(env: Map<String, String>): Boolean =
        listOfNotNull(env["XDG_CURRENT_DESKTOP"], env["XDG_SESSION_DESKTOP"], env["DESKTOP_SESSION"])
            .flatMap { it.split(':', ';') }
            .map { it.trim().lowercase().substringAfterLast('/') }
            .filter { it.isNotEmpty() }
            .any { name -> AGENT_DESKTOPS.any { name == it || name.startsWith("$it-") } }

    internal fun isAgent(command: String): Boolean {
        val name = command.trim().substringAfterLast('/').lowercase()
        if (name.isEmpty()) return false
        return name in AGENT_PROCESSES || ("polkit" in name && ("agent" in name || "auth" in name) && name != HELPER)
    }

    private fun runningCommands(): List<String> {
        val uid = ownUid() ?: return emptyList()
        return File("/proc").listFiles { f -> f.isDirectory && f.name.all(Char::isDigit) }.orEmpty().mapNotNull { dir ->
            runCatching {
                val owner = File(dir, "status").useLines { lines ->
                    lines.firstOrNull { it.startsWith("Uid:") }?.split(Regex("\\s+"))?.getOrNull(1)
                }
                if (owner != uid) return@runCatching null
                File(dir, "cmdline").readText().substringBefore('\u0000').takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    private fun ownUid(): String? = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Uid:") }?.split(Regex("\\s+"))?.getOrNull(1)
        }
    }.getOrNull()

    private const val HELPER = "polkit-agent-helper-1"

    private val AGENT_DESKTOPS = listOf(
        "gnome", "kde", "plasma", "xfce", "cinnamon", "x-cinnamon", "mate", "lxqt", "lxde", "budgie",
        "deepin", "dde", "pantheon", "unity", "cosmic", "ukui", "enlightenment", "trinity",
    )

    private val AGENT_PROCESSES = setOf(
        "gnome-shell", "cinnamon", "polkit-gnome-authentication-agent-1", "polkit-kde-authentication-agent-1",
        "polkit-mate-authentication-agent-1", "polkit-efl-authentication-agent-1", "lxpolkit", "lxqt-policykit-agent",
        "xfce-polkit", "hyprpolkitagent", "mate-polkit", "soteria", "polkit-dumb-agent", "budgie-polkit-dialog",
        "dde-polkit-agent", "io.elementary.desktop.agent-polkit", "ukui-polkit", "cosmic-osd", "ts-polkitagent",
    )
}
