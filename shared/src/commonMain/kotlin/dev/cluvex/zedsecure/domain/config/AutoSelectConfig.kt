package dev.cluvex.zedsecure.domain.config

object AutoSelectTags {
    const val GROUP = "proxy"

    private const val PREFIX = "proxy@"

    fun member(index: Int): String = "$PREFIX$index"

    fun isMember(tag: String): Boolean =
        tag.length > PREFIX.length && tag.startsWith(PREFIX) && tag.substring(PREFIX.length).all { it.isDigit() }
}

object AutoSelectIds {
    const val ALL = "auto:all"
    const val MANUAL = "auto:manual"
    private const val SUBSCRIPTION = "auto:sub:"

    fun of(subscriptionId: String?): String = when (subscriptionId) {
        null -> ALL
        "" -> MANUAL
        else -> SUBSCRIPTION + subscriptionId
    }

    fun isAuto(id: String?): Boolean = id != null &&
        (id == ALL || id == MANUAL || (id.startsWith(SUBSCRIPTION) && id.length > SUBSCRIPTION.length))

    fun subscriptionOf(id: String): String? = when {
        id == ALL -> null
        id == MANUAL -> ""
        id.startsWith(SUBSCRIPTION) -> id.removePrefix(SUBSCRIPTION)
        else -> throw IllegalArgumentException("not an auto-select id: $id")
    }
}

sealed interface AutoMember {
    val profileId: String

    data class Server(override val profileId: String, val server: ServerConfig) : AutoMember

    data class Custom(override val profileId: String, val rawJson: String) : AutoMember

    data class SingBox(override val profileId: String, val fragment: String, val carrier: String?) : AutoMember
}

data class AutoSelectTuning(
    val probeUrl: String = "",
    val probeTimeoutSec: Int = 5,

    val activeIntervalSec: Int = 30,

    val sweepIntervalMin: Int = 10,

    val switchMarginPercent: Int = 30,

    val retry: Boolean = true,

    val initialProfileId: String? = null,
)

data class AutoSelectBuild(
    val json: String,

    val memberProfiles: Map<String, String>,

    val skipped: List<String>,
)

class AutoSelectNoMembersException(val skipped: Int) :
    Exception("auto-select has no usable server ($skipped skipped)")
