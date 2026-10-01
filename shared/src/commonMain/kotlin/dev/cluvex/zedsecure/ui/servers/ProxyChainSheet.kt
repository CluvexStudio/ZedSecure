@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProxyChainSheet(
    candidates: List<VpnProfile>,
    onDismiss: () -> Unit,
    onSave: (name: String, memberIds: List<String>) -> Unit,
    initialName: String = "",
    initialMemberIds: List<String> = emptyList(),
) {
    var name by remember { mutableStateOf(initialName) }
    var query by remember { mutableStateOf("") }
    val byId = remember(candidates) { candidates.associateBy { it.id } }

    val selected = remember {
        mutableStateListOf<String>().apply { addAll(initialMemberIds.filter { byId.containsKey(it) }) }
    }

    val droppedCount = remember(initialMemberIds, byId) {
        initialMemberIds.count { !byId.containsKey(it) }
    }

    val udpWarning = remember(selected.toList(), byId) {
        selected.withIndex().firstOrNull { (i, id) ->
            i > 0 && byId[id]?.needsUdpHop == true && byId[selected[i - 1]]?.carriesUdpHop == false
        }?.let { (i, id) -> byId[id]?.name to byId[selected[i - 1]]?.name }
    }
    val available = candidates.filter { it.id !in selected }.filterByChainQuery(query)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 8.dp,
                ),
            ) {
                item(key = "head") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.proxychain_add_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(Res.string.proxychain_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.manual_remark)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (candidates.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(Res.string.proxychain_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (selected.isNotEmpty()) {
                    item(key = "sel-hdr") {
                        Text(
                            stringResource(Res.string.proxychain_selected),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item(key = "sel-list") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selected.forEachIndexed { i, id ->
                                val p = byId[id] ?: return@forEachIndexed
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${i + 1}.  ${p.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                if (i == selected.lastIndex) {
                                                    stringResource(Res.string.proxychain_exit)
                                                } else {
                                                    p.transportLabel
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { if (i > 0) selected.swap(i, i - 1) }) { Text("▲") }
                                        IconButton(onClick = { if (i < selected.lastIndex) selected.swap(i, i + 1) }) { Text("▼") }
                                        IconButton(onClick = { selected.removeAt(i) }) { Text("✕") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (candidates.any { it.id !in selected }) {
                    item(key = "search") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(Res.string.proxychain_available),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ChainSearchField(value = query, onValueChange = { query = it })
                        }
                    }
                }

                chainCandidateItems(
                    options = available,
                    leading = { "＋" },
                    picked = { false },
                    onSelect = { picked ->
                        if (picked.isServerless) {
                            selected.removeAll { id -> byId[id]?.isServerless == true }
                            selected.add(0, picked.id)
                        } else {
                            selected.add(picked.id)
                        }
                    },
                )

                if (available.isEmpty() && query.isNotBlank()) {
                    item(key = "noresult") {
                        Text(
                            stringResource(Res.string.chain_no_results, query),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (droppedCount > 0) {
                    Text(
                        stringResource(Res.string.proxychain_members_dropped, droppedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (udpWarning != null) {
                    Text(
                        stringResource(
                            Res.string.proxychain_udp_hop_warning,
                            udpWarning.first.orEmpty(),
                            udpWarning.second.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (selected.size < 2) {
                    Text(
                        stringResource(Res.string.proxychain_min),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onSave(name.trim(), selected.toList()) },
                    enabled = selected.size >= 2 && udpWarning == null,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}
