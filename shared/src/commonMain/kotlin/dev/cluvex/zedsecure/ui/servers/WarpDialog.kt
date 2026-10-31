package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.net.WarpMasque
import dev.cluvex.zedsecure.domain.config.ShareLink
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun WarpDialog(onDismiss: () -> Unit, onRegistered: (link: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.warp_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.warp_add_body))
                Text(
                    stringResource(Res.string.warp_add_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.warp_registering))
                    }
                }
                error?.let {
                    Text(
                        stringResource(Res.string.warp_failed, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val port = VpnManager.activeSocksPort
                            .takeIf { VpnManager.status.value.state == ConnectionState.Connected }
                        runCatching { WarpMasque.register(port) }
                            .onSuccess { busy = false; onRegistered(ShareLink.build(it)) }
                            .onFailure { busy = false; error = it.message ?: it.toString() }
                    }
                },
            ) { Text(stringResource(if (error == null) Res.string.warp_add_action else Res.string.action_retry)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
