package dev.cluvex.zedsecure.desktop

import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.ui.platform.AutomaticRendering
import java.io.File

object DesktopRendering {
    enum class Source { External, Flag, Environment, Setting, Automatic }

    data class Decision(val software: Boolean, val source: Source, val automatic: AutomaticRendering)

    @Volatile var automatic: AutomaticRendering = AutomaticRendering.Gpu
        private set

    fun automaticFor(os: Os, env: Map<String, String>, nvidiaDriver: String?): AutomaticRendering = when {
        os == Os.WINDOWS -> AutomaticRendering.SoftwareWindows
        os == Os.LINUX && nvidiaDriver != null && isWayland(env) && driverBefore555(nvidiaDriver) ->
            AutomaticRendering.SoftwareNvidiaWayland
        else -> AutomaticRendering.Gpu
    }

    fun decide(
        mode: RenderingMode,
        args: Array<String>,
        env: Map<String, String>,
        skikoProperty: String?,
        os: Os,
        nvidiaDriver: String?,
    ): Decision {
        val automatic = automaticFor(os, env, nvidiaDriver)
        val external = env["SKIKO_RENDER_API"]?.takeIf { it.isNotBlank() } ?: skikoProperty?.takeIf { it.isNotBlank() }
        if (external != null) return Decision(external.uppercase().startsWith("SOFTWARE"), Source.External, automatic)
        if ("--software-rendering" in args) return Decision(true, Source.Flag, automatic)
        if ("--gpu-rendering" in args) return Decision(false, Source.Flag, automatic)
        when (env["ZEDSECURE_RENDERING"]?.trim()?.lowercase()) {
            "software" -> return Decision(true, Source.Environment, automatic)
            "gpu" -> return Decision(false, Source.Environment, automatic)
        }
        when (env["ZEDSECURE_SOFTWARE_RENDERING"]?.trim()?.lowercase()) {
            "1", "true", "yes" -> return Decision(true, Source.Environment, automatic)
            "0", "false", "no" -> return Decision(false, Source.Environment, automatic)
        }
        return when (mode) {
            RenderingMode.Software -> Decision(true, Source.Setting, automatic)
            RenderingMode.Gpu -> Decision(false, Source.Setting, automatic)
            RenderingMode.Auto -> Decision(automatic != AutomaticRendering.Gpu, Source.Automatic, automatic)
        }
    }

    fun apply(mode: RenderingMode, args: Array<String>): Decision {
        val decision = decide(
            mode = mode,
            args = args,
            env = System.getenv(),
            skikoProperty = System.getProperty("skiko.renderApi"),
            os = Os.current,
            nvidiaDriver = nvidiaDriverVersion(),
        )
        automatic = decision.automatic
        if (decision.software && decision.source != Source.External) System.setProperty("skiko.renderApi", "SOFTWARE")
        return decision
    }

    private fun isWayland(env: Map<String, String>): Boolean =
        env["XDG_SESSION_TYPE"].equals("wayland", ignoreCase = true) || !env["WAYLAND_DISPLAY"].isNullOrBlank()

    internal fun driverBefore555(version: String): Boolean {
        val major = Regex("""(\d{3,})\.\d+""").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: return true
        return major < 555
    }

    private fun nvidiaDriverVersion(): String? =
        runCatching { File("/proc/driver/nvidia/version").takeIf { it.isFile }?.readText() }.getOrNull()
}
