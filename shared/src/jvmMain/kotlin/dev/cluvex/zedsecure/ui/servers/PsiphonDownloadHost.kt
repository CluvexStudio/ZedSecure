@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.psiphon.PsiphonBinaryManager
import dev.cluvex.zedsecure.core.psiphon.PsiphonDownloadBus
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.ic_download
import dev.cluvex.zedsecure.shared.resources.psiphon_download_action
import dev.cluvex.zedsecure.shared.resources.psiphon_download_failed
import dev.cluvex.zedsecure.shared.resources.psiphon_download_prompt
import dev.cluvex.zedsecure.shared.resources.psiphon_download_retry
import dev.cluvex.zedsecure.shared.resources.psiphon_download_size
import dev.cluvex.zedsecure.shared.resources.psiphon_download_title
import dev.cluvex.zedsecure.shared.resources.psiphon_downloading
import dev.cluvex.zedsecure.ui.format.formatBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PsiphonDownloadHost() {
    val pending by PsiphonDownloadBus.pending.collectAsStateWithLifecycle()

    pending?.let {
        PsiphonDownloadSheet(
            onDismiss = { PsiphonDownloadBus.dismiss() },
            onSuccess = { PsiphonDownloadBus.complete(true) },
        )
    }
}

@Composable
fun PsiphonDownloadSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadFailed by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(PsiphonBinaryManager.ESTIMATED_SIZE_BYTES) }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        label = "psiphon_download_progress",
    )

    fun startDownload() {
        downloadFailed = false
        isDownloading = true
        progressFraction = 0f
        bytesDownloaded = 0L

        currentJob?.cancel()
        currentJob = scope.launch {
            val result = PsiphonBinaryManager.download { downloaded, total ->
                bytesDownloaded = downloaded
                totalBytes = total
                if (total > 0L) {
                    progressFraction = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
            }

            isDownloading = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                downloadFailed = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.psiphon_download_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.psiphon_download_size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!isDownloading && !downloadFailed) {
                Text(
                    text = stringResource(Res.string.psiphon_download_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = { startDownload() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.5f).height(48.dp),
                    ) {
                        Text(
                            stringResource(Res.string.psiphon_download_action),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else if (isDownloading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.psiphon_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        drawStopIndicator = {},
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (downloadFailed) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.psiphon_download_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                        Button(
                            onClick = { startDownload() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.5f).height(48.dp),
                        ) {
                            Text(stringResource(Res.string.psiphon_download_retry))
                        }
                    }
                }
            }
        }
    }
}
