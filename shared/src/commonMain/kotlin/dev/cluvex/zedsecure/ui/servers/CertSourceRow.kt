@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CertSourceRow(
    label: String,
    value: String?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    onChoose: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    value?.takeIf { it.isNotBlank() } ?: emptyLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onChoose != null) {
                OutlinedButton(onClick = onChoose) {
                    Text(stringResource(Res.string.cert_choose))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onImport != null) {
                TextButton(onClick = onImport) { Text(stringResource(Res.string.cert_import_file)) }
            }
            if (onClear != null && !value.isNullOrBlank()) {
                TextButton(onClick = onClear) { Text(stringResource(Res.string.cert_clear)) }
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
