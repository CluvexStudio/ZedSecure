package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogSource { Xray, SingBox, Tor, Psiphon, DnsTunnel, MasterDns, Ssh, OpenConnect, Ikev2, SniSpoof, Tun, App }

data class LogEntry(val time: Long, val source: LogSource, val level: Char, val text: String)

object LogBus {
    private const val CAPACITY = 3_000

    private const val SLACK = 256

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    var source: ((active: Boolean) -> Unit)? = null

    private var watchers = 0

    private val _ownTags = MutableStateFlow<Set<String>>(emptySet())
    val ownTags: StateFlow<Set<String>> = _ownTags.asStateFlow()

    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")

    private val legacy = Regex("^([VDIWEF])/([^:\\s]+):?\\s?(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun noteOwnTag(tag: String) {
        if (tag !in _ownTags.value) _ownTags.update { it + tag }
    }

    fun sourceOfTag(tag: String): LogSource = when (tag) {
        "XrayController", "FrontCore", "CoreProbe", "CoreCrash", "GoLog", "xray", "Auto-select" -> LogSource.Xray
        "sing-box", "SingBoxEngine" -> LogSource.SingBox
        "Tor", "TorController" -> LogSource.Tor
        "Psiphon" -> LogSource.Psiphon
        "DnsTunnel", "zeddns", "DesktopDns" -> LogSource.DnsTunnel
        "MasterDns", "MasterDnsController" -> LogSource.MasterDns
        "Ssh", "SSH" -> LogSource.Ssh
        "OpenConnect" -> LogSource.OpenConnect
        "Ikev2Controller", "Ikev2EventReceiver", "IKEv2" -> LogSource.Ikev2
        "SniSpoof" -> LogSource.SniSpoof
        "HevTunCore", "ZepTunCore", "tun" -> LogSource.Tun
        else -> LogSource.App
    }

    fun log(source: LogSource, level: Char, text: String, time: Long = currentTimeMillis()) {
        add(listOf(LogEntry(time, source, level, clean(text))))
    }

    fun append(line: String) {
        val m = legacy.find(line.trim())
        if (m == null) {
            log(LogSource.App, 'I', line)
            return
        }
        val (level, tag, rest) = m.destructured
        log(sourceOfTag(tag.trimEnd(':')), level.first(), rest.ifBlank { line })
    }

    fun add(newEntries: List<LogEntry>) {
        if (newEntries.isEmpty()) return
        _entries.update { prev ->
            val incoming = newEntries.sortedBy { it.time }
            val merged = if (prev.isEmpty() || incoming.first().time >= prev.last().time) {
                prev + incoming
            } else {
                merge(prev, incoming)
            }
            if (merged.size > CAPACITY + SLACK) merged.subList(merged.size - CAPACITY, merged.size) else merged
        }
    }

    private fun merge(a: List<LogEntry>, b: List<LogEntry>): List<LogEntry> {
        val out = ArrayList<LogEntry>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                j >= b.size -> out += a[i++]
                i >= a.size -> out += b[j++]
                b[j].time < a[i].time -> out += b[j++]
                else -> out += a[i++]
            }
        }
        return out
    }

    fun clean(text: String): String = ansi.replace(text, "").trimEnd()

    fun levelIn(text: String, fallback: Char = 'I'): Char {
        val t = text.uppercase()
        return when {
            "[ERROR]" in t || "[ERR]" in t || "ERROR[" in t || " ERROR " in t || "FATAL" in t || "PANIC" in t -> 'E'
            "[WARNING]" in t || "[WARN]" in t || "WARN[" in t || " WARN " in t -> 'W'
            "[DEBUG]" in t || "DEBUG[" in t || " DEBUG " in t -> 'D'
            else -> fallback
        }
    }

    fun attach() {
        watchers++
        if (watchers == 1) source?.invoke(true)
    }

    fun detach() {
        if (watchers > 0) watchers--
        if (watchers == 0) source?.invoke(false)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun render(entry: LogEntry): String = "${clock(entry.time)} ${entry.level}/${entry.source.name}: ${entry.text}"

    fun clock(time: Long): String {
        val local = time + java.util.TimeZone.getDefault().getOffset(time)
        val day = ((local % 86_400_000L) + 86_400_000L) % 86_400_000L
        val h = day / 3_600_000L
        val m = day / 60_000L % 60
        val s = day / 1_000L % 60
        val ms = day % 1_000L
        return "${pad(h)}:${pad(m)}:${pad(s)}.${ms.toString().padStart(3, '0')}"
    }

    private fun pad(v: Long): String = v.toString().padStart(2, '0')

    fun snapshot(): String = _entries.value.joinToString("\n") { render(it) }

    fun recent(count: Int): List<String> = _entries.value.takeLast(count).map { render(it) }
}
