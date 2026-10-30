package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_close
import dev.cluvex.zedsecure.shared.resources.action_ok
import dev.cluvex.zedsecure.shared.resources.auto_excluded_empty
import dev.cluvex.zedsecure.shared.resources.auto_excluded_title
import dev.cluvex.zedsecure.ui.components.FlagBadge
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.servers.countryName
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExcludedCountriesDialog(
    available: List<String>,
    selected: List<String>,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val codes = remember(available, selected) { (available + selected).distinct().sortedBy { countryName(it) } }
    var picked by remember(selected) { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.auto_excluded_title)) },
        text = {
            if (codes.isEmpty()) {
                Text(stringResource(Res.string.auto_excluded_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(Modifier.heightIn(max = 420.dp).appVerticalScroll(rememberScrollState())) {
                    codes.forEach { code ->
                        val checked = code in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .toggleable(value = checked, role = Role.Checkbox) { on ->
                                    picked = if (on) picked + code else picked - code
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FlagBadge(countryCode = code, size = 24.dp)
                            Text(countryName(code), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(codes.filter { it in picked }) }) { Text(stringResource(Res.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}
