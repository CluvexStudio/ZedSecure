package dev.cluvex.zedsecure.domain.power

object PowerPolicy {
    data class Cadence(

        val tickMs: Long,

        val notifySeconds: Int,

        val autoPollSeconds: Int,
    )

    fun cadence(screenOn: Boolean): Cadence = if (screenOn) {
        Cadence(tickMs = 1_000L, notifySeconds = 1, autoPollSeconds = 3)
    } else {
        Cadence(tickMs = 10_000L, notifySeconds = 30, autoPollSeconds = 45)
    }
}
