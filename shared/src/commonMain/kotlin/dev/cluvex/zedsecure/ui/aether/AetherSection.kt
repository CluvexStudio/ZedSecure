package dev.cluvex.zedsecure.ui.aether

import dev.cluvex.zedsecure.domain.config.AetherProfile
import kotlinx.coroutines.flow.StateFlow

/** One matrix cell: protocol × transport, run for real. */
data class MatrixRow(
    val key: String,
    val proto: String,
    val trans: String,
    /** idle | running | done */
    val phase: String = "idle",
    val ok: Boolean = false,
    val ms: Long? = null,
    val exit: String? = null,
    val note: String = "",
)

/** Shared Aether section state (phase strings keep common code free of
 * platform enums). Implemented on desktop; other platforms pass null. */
data class AetherUiState(
    /** idle | working | live | error */
    val phase: String = "idle",
    val profile: AetherProfile = AetherProfile(),
    val socksAddr: String = "127.0.0.1:1819",
    val httpAddr: String = "127.0.0.1:1820",
    val torExit: String? = null,
    val psiExit: String? = null,
    val statusLine: String = "",
    val detailLine: String = "",
    val torPercent: Int? = null,
    val upBytes: Long = 0L,
    val downBytes: Long = 0L,
    val upRate: Long = 0L,
    val downRate: Long = 0L,
    val clients: Int = 0,
    val sysProxy: Boolean = false,
    val logTail: List<String> = emptyList(),
    val matrix: List<MatrixRow> = emptyList(),
    val matrixRunning: Boolean = false,
)

/** Desktop implements this; MainScaffold renders it when non-null. */
interface AetherBridge {
    val state: StateFlow<AetherUiState>
    fun updateProfile(p: AetherProfile)
    fun toggle()
    fun disconnect()
    fun setSysProxy(on: Boolean)
    fun refreshClients(): Int
    fun runMatrix()
    fun stopMatrix()
}
