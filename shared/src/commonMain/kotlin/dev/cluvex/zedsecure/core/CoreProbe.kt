package dev.cluvex.zedsecure.core

object CoreProbe {
    @Volatile
    var measureDelay: (url: String) -> Long = { -1L }

    data class DelayOutcome(val ms: Long, val error: String?) {
        val ok: Boolean get() = ms > 0
    }

    @Volatile
    var measureDelayDetailed: (url: String) -> DelayOutcome = { DelayOutcome(measureDelay(it), null) }

    @Volatile
    var measureOutboundDelay: (configJson: String, url: String) -> Long = { _, _ -> -1L }
}
