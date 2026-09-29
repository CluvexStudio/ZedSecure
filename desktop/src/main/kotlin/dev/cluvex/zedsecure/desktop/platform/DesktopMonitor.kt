package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.ui.platform.LiveStats
import java.io.File

object DesktopMonitor {
    private var lastCpuNanos: Map<Long, Long> = emptyMap()
    private var lastSampleAt: Long = 0L

    fun sample(): LiveStats {
        val self = ProcessHandle.current()
        val processes = listOf(self) + runCatching { self.descendants().toList() }.getOrDefault(emptyList())
        val linux = Os.current == Os.LINUX
        val heat = if (linux) processorHeat() else null
        val battery = if (linux) battery() else null
        return LiveStats(
            cpuPercent = cpuPercent(processes),
            coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memoryBytes = if (linux) processes.sumOfOrNull { statusField(it.pid(), "VmRSS:")?.times(1024) } else null,
            threads = if (linux) processes.sumOfOrNull { statusField(it.pid(), "Threads:") }?.toInt()
            else Thread.activeCount(),
            tempC = heat?.first,
            thermalHeadroom = heat?.second,
            batteryPercent = battery?.percent,
            charging = battery?.charging ?: false,
            currentMilliAmps = battery?.milliAmps,
            processUptimeMs = self.info().startInstant().map {
                System.currentTimeMillis() - it.toEpochMilli()
            }.orElse(null),
        )
    }

    private fun cpuPercent(processes: List<ProcessHandle>): Float? {
        val now = System.nanoTime()
        val current = HashMap<Long, Long>()
        var spent = 0L
        for (process in processes) {
            val info = process.info()
            val nanos = info.totalCpuDuration().map { it.toNanos() }.orElse(null) ?: continue
            current[process.pid()] = nanos
            val before = lastCpuNanos[process.pid()]
            spent += when {
                before != null -> (nanos - before).coerceAtLeast(0L)
                lastSampleAt != 0L && info.startInstant().map {
                    it.toEpochMilli() >= System.currentTimeMillis() - (now - lastSampleAt) / 1_000_000
                }.orElse(false) -> nanos
                else -> 0L
            }
        }
        val first = lastSampleAt == 0L
        val elapsed = now - lastSampleAt
        lastCpuNanos = current
        lastSampleAt = now
        if (first || elapsed <= 0) return null
        return (spent.toFloat() / elapsed * 100f).coerceAtLeast(0f)
    }

    private fun statusField(pid: Long, name: String): Long? = runCatching {
        File("/proc/$pid/status").useLines { lines ->
            lines.firstOrNull { it.startsWith(name) }?.filter { it.isDigit() }?.toLongOrNull()
        }
    }.getOrNull()

    private inline fun List<ProcessHandle>.sumOfOrNull(value: (ProcessHandle) -> Long?): Long? {
        var total = 0L
        var any = false
        for (p in this) value(p)?.let { total += it; any = true }
        return if (any) total else null
    }

    private fun processorHeat(): Pair<Float, Float?>? = runCatching {
        File("/sys/class/hwmon").listFiles().orEmpty().sortedBy { it.name }.forEach { dir ->
            val name = File(dir, "name").takeIf { it.isFile }?.readText()?.trim() ?: return@forEach
            if (name != "coretemp" && name != "k10temp" && name != "zenpower" && name != "cpu_thermal") return@forEach
            val input = milli(File(dir, "temp1_input")) ?: return@forEach
            val crit = milli(File(dir, "temp1_crit")) ?: milli(File(dir, "temp1_max"))
                ?: if (name == "k10temp" || name == "zenpower") 95f else null
            return@runCatching input to crit?.let { (input / it).coerceIn(0f, 1.2f) }
        }
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
            .sortedBy { it.name }.forEach { zone ->
                val type = File(zone, "type").takeIf { it.isFile }?.readText()?.trim() ?: return@forEach
                if (type != "x86_pkg_temp" && type != "cpu-thermal" && type != "acpitz") return@forEach
                val temp = milli(File(zone, "temp")) ?: return@forEach
                val crit = (0..9).firstNotNullOfOrNull { i ->
                    val kind = File(zone, "trip_point_${i}_type").takeIf { it.isFile }?.readText()?.trim()
                    if (kind == "critical") milli(File(zone, "trip_point_${i}_temp")) else null
                }
                return@runCatching temp to crit?.let { (temp / it).coerceIn(0f, 1.2f) }
            }
        null
    }.getOrNull()

    private fun milli(file: File): Float? =
        runCatching { file.readText().trim().toFloat() / 1000f }.getOrNull()?.takeIf { it in 1f..150f }

    private class Battery(val percent: Int?, val charging: Boolean, val milliAmps: Int?)

    private fun battery(): Battery? = runCatching {
        val supply = File("/sys/class/power_supply").listFiles().orEmpty().firstOrNull {
            File(it, "type").takeIf { t -> t.isFile }?.readText()?.trim() == "Battery"
        } ?: return@runCatching null
        fun read(name: String) = File(supply, name).takeIf { it.isFile }?.readText()?.trim()
        val status = read("status").orEmpty()
        val microAmps = read("current_now")?.toLongOrNull() ?: run {
            val microWatts = read("power_now")?.toLongOrNull()
            val microVolts = read("voltage_now")?.toLongOrNull()
            if (microWatts != null && microVolts != null && microVolts > 0) microWatts * 1_000_000 / microVolts else null
        }
        Battery(
            percent = read("capacity")?.toIntOrNull()?.coerceIn(0, 100),
            charging = status == "Charging" || status == "Full",
            milliAmps = microAmps?.let { (it / 1000).toInt() }?.takeIf { it != 0 },
        )
    }.getOrNull()

    fun cryptoAcceleration(): String = runCatching {
        if (Os.current != Os.LINUX) return@runCatching ""
        val flags = File("/proc/cpuinfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("flags") || it.startsWith("Features") }
        }?.substringAfter(':')?.trim()?.split(' ')?.toSet().orEmpty()
        val found = listOfNotNull(
            "AES".takeIf { "aes" in flags },
            "PCLMUL".takeIf { "pclmulqdq" in flags || "pmull" in flags },
            "SHA".takeIf { "sha_ni" in flags || "sha2" in flags },
            "AVX2".takeIf { "avx2" in flags },
        )
        "already=[${found.joinToString(" ")}]"
    }.getOrDefault("")
}
