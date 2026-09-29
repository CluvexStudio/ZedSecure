@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.MasterDnsProfile
import dev.cluvex.zedsecure.ui.components.PickerField

@Composable
fun MasterDnsSheet(
    initial: MasterDnsProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, MasterDnsProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var domains by remember { mutableStateOf(initial?.domains ?: "") }
    var key by remember { mutableStateOf(initial?.encryptionKey ?: "") }
    var method by remember { mutableStateOf(initial?.encryptionMethod ?: MasterDnsProfile.ENC_XOR) }
    var resolvers by remember { mutableStateOf(initial?.resolvers ?: "") }
    var balancing by remember { mutableStateOf(initial?.balancingStrategy ?: 3) }
    var duplication by remember { mutableStateOf(initial?.packetDuplication ?: 3) }
    var compression by remember { mutableStateOf(initial?.compression ?: 0) }
    var autoDisable by remember { mutableStateOf(initial?.autoDisableDeadResolvers ?: true) }
    var advancedOpen by remember { mutableStateOf(false) }

    val valid = domains.isNotBlank() && key.isNotBlank() &&
        resolvers.split(',', '\n').any { it.trim().isNotEmpty() && !it.trim().startsWith("#") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.master_dns_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Field(name, Res.string.manual_remark) { name = it }
            Field(domains, Res.string.master_dns_domains, placeholder = "v.example.com") { domains = it }
            Field(key, Res.string.master_dns_key) { key = it }
            PickerField(
                label = stringResource(Res.string.master_dns_method),
                options = listOf(
                    MasterDnsProfile.ENC_NONE to "None",
                    MasterDnsProfile.ENC_XOR to "XOR",
                    MasterDnsProfile.ENC_CHACHA20 to "ChaCha20",
                    MasterDnsProfile.ENC_AES128 to "AES-128-GCM",
                    MasterDnsProfile.ENC_AES192 to "AES-192-GCM",
                    MasterDnsProfile.ENC_AES256 to "AES-256-GCM",
                ),
                selected = method,
                onSelect = { method = it },
            )

            val badResolvers = MasterDnsProfile.invalidResolvers(resolvers)
            val allBad = badResolvers.isNotEmpty() &&
                badResolvers.size == MasterDnsProfile.splitResolvers(resolvers).size
            Field(
                resolvers,
                Res.string.master_dns_resolvers,
                placeholder = "8.8.8.8\n1.1.1.1:53",
                minLines = 2,
                error = if (allBad) stringResource(Res.string.master_dns_resolvers_invalid) else null,
                supporting = if (badResolvers.isNotEmpty() && !allBad) {
                    stringResource(Res.string.master_dns_resolvers_skipped, badResolvers.joinToString(", "))
                } else null,
            ) { resolvers = it }
            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(stringResource(Res.string.master_dns_advanced))
            }
            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField(
                        label = stringResource(Res.string.master_dns_balancing),
                        options = listOf(
                            1 to "Random", 2 to "Round Robin", 3 to "Least Loss",
                            4 to "Lowest Latency", 5 to "Hybrid", 6 to "Loss then Latency",
                            7 to "Least Loss (top random)", 8 to "Least Loss (top round-robin)",
                        ),
                        selected = balancing,
                        onSelect = { balancing = it },
                    )
                    Stepper(Res.string.master_dns_duplication, duplication, min = 1, max = 10) { duplication = it }
                    PickerField(
                        label = stringResource(Res.string.master_dns_compression),
                        options = listOf(0 to "Off", 1 to "ZSTD", 2 to "LZ4", 3 to "ZLIB"),
                        selected = compression,
                        onSelect = { compression = it },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.master_dns_auto_disable),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(checked = autoDisable, onCheckedChange = { autoDisable = it })
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        name,
                        MasterDnsProfile(
                            domains = domains.trim(),
                            encryptionKey = key,
                            encryptionMethod = method,
                            resolvers = resolvers.trim(),

                            listenPort = initial?.listenPort ?: 18000,
                            balancingStrategy = balancing,
                            packetDuplication = duplication,
                            compression = compression,
                            autoDisableDeadResolvers = autoDisable,
                        ),
                    )
                },
                enabled = valid && !allBad,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    minLines: Int = 1,
    numeric: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Stepper(labelRes: StringResource, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        androidx.compose.material3.IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
