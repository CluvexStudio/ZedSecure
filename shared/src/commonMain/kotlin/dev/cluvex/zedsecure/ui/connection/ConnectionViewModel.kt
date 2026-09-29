package dev.cluvex.zedsecure.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ConnectionUiState(
    val state: ConnectionState = ConnectionState.Idle,
    val elapsedSeconds: Int = 0,
    val downloadBps: Long = 0,
    val uploadBps: Long = 0,
    val totalDownload: Long = 0,
    val totalUpload: Long = 0,
    val activeConfigName: String? = null,
    val error: String? = null,
    val sessionId: Int = 0,
)

class ConnectionViewModel : ViewModel() {
    val ui: StateFlow<ConnectionUiState> = VpnManager.status
        .map { s ->
            ConnectionUiState(
                state = s.state,
                elapsedSeconds = s.durationSeconds,
                downloadBps = s.downloadBps,
                uploadBps = s.uploadBps,
                totalDownload = s.totalDownload,
                totalUpload = s.totalUpload,
                activeConfigName = s.serverName,
                error = s.error,
                sessionId = s.sessionId,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionUiState())
}
