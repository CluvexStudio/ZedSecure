package dev.cluvex.zedsecure.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.platform.encodeQr

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) }
    Surface(color = Color.White, modifier = modifier) {
        if (matrix == null) return@Surface
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)) {
            val module = size.minDimension / matrix.size
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (!matrix[x, y]) continue
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * module, y * module),

                        size = Size(module + 0.6f, module + 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
fun QrDialog(
    title: String,
    text: String,
    copyLabel: String,
    shareLabel: String,
    closeLabel: String,
    unsupportedLabel: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val encodable = remember(text) { encodeQr(text) != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (encodable) {
                    QrCode(text, Modifier.fillMaxWidth())
                } else {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(unsupportedLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(closeLabel) } },
        dismissButton = {
            Row2(
                first = { TextButton(onClick = onCopy) { Text(copyLabel) } },
                second = { TextButton(onClick = onShare) { Text(shareLabel) } },
            )
        },
    )
}

@Composable
private fun Row2(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        first()
        second()
    }
}
