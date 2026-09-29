package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.config.AutoSelectTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object AutoSelect {
    var readStatus: () -> String? = { null }

    var pinMember: (group: String, member: String) -> Boolean = { _, _ -> false }

    var networkChanged: () -> Unit = {}

    var supportsPinning: Boolean = false

    data class Session(
        val profileId: String,
        val memberProfiles: Map<String, String>,
        val status: Status? = null,
    ) {
        val selectedProfileId: String? get() = status?.selected?.let { memberProfiles[it] }

        val exitGeneration: Int get() = ((status?.switches ?: 0) - 1).coerceAtLeast(0)
    }

    fun exitGeneration(): Int = _session.value?.exitGeneration ?: 0

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    @kotlin.concurrent.Volatile
    private var pending: Session? = null

    fun prepare(profileId: String, memberProfiles: Map<String, String>) {
        pending = Session(profileId, memberProfiles)
    }

    fun clearPrepared() {
        pending = null
    }

    fun activate(): Boolean {
        val next = pending ?: return false
        pending = null
        _session.value = next
        return true
    }

    fun end() {
        _session.value = null
    }

    fun poll(): List<Event> {
        val current = _session.value ?: return emptyList()

        val status = parse(readStatus()).firstOrNull { it.tag == AutoSelectTags.GROUP }
            ?.let { s -> s.copy(members = s.members.map { it.copy(lastProbeAgoMs = -1, lastSuccessAgoMs = -1) }) }
            ?: return emptyList()
        val previous = current.status
        if (status != previous) _session.value = current.copy(status = status)
        val seenUntil = previous?.events?.maxOfOrNull { it.atMs } ?: Long.MIN_VALUE
        return if (previous == null) emptyList() else status.events.filter { it.atMs > seenUntil }
    }

    fun pin(memberTag: String): Boolean = pinMember(AutoSelectTags.GROUP, memberTag).also { if (it) poll() }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(raw: String?): List<Status> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Status.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    @Serializable
    data class Status(
        val tag: String = "",
        val selected: String = "",
        val pinned: String? = null,
        val state: String = "starting",
        val switches: Int = 0,
        val events: List<Event> = emptyList(),
        val members: List<Member> = emptyList(),
    ) {
        val phase: Phase get() = Phase.of(state)

        fun member(tag: String): Member? = members.firstOrNull { it.tag == tag }
    }

    @Serializable
    data class Event(
        val from: String = "",
        val to: String = "",
        val reason: String = "",
        val atMs: Long = 0,
    ) {
        val kind: Reason get() = Reason.of(reason)
    }

    @Serializable
    data class Member(
        val tag: String = "",
        val state: String = "unknown",
        val delayMs: Long = -1,
        val lastDelayMs: Long = -1,
        val jitterMs: Long = 0,
        val samples: Int = 0,
        val score: Double = -1.0,
        val failRatio: Double = 0.0,
        val connections: Int = 0,
        val lastProbeAgoMs: Long = -1,
        val lastSuccessAgoMs: Long = -1,
        val lastError: String? = null,
    ) {
        val health: Health get() = Health.of(state)
    }

    enum class Phase {
        Starting, Ok, Degraded, Down, Idle;

        companion object {
            fun of(s: String) = when (s) {
                "ok" -> Ok
                "degraded" -> Degraded
                "down" -> Down
                "idle" -> Idle
                else -> Starting
            }
        }
    }

    enum class Health {
        Unknown, Alive, Suspect, Dead;

        companion object {
            fun of(s: String) = when (s) {
                "alive" -> Alive
                "suspect" -> Suspect
                "dead" -> Dead
                else -> Unknown
            }
        }
    }

    enum class Reason {
        Startup, Failover, Faster, Pinned, Recovered;

        companion object {
            fun of(s: String) = when (s) {
                "failover" -> Failover
                "faster" -> Faster
                "pinned" -> Pinned
                "recovered" -> Recovered
                else -> Startup
            }
        }
    }
}
