@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private enum class CrossSlot { CARRIER, EXIT }

@Composable
fun CrossChainSheet(
    exits: List<VpnProfile>,
    carriers: List<VpnProfile>,

    pairError: (inner: VpnProfile, outer: VpnProfile) -> String?,
    onDismiss: () -> Unit,
    onSave: (name: String, innerId: String, outerId: String) -> Unit,
    initialName: String = "",
    initialInnerId: String = "",
    initialOuterId: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var innerId by remember { mutableStateOf(initialInnerId) }
    var outerId by remember { mutableStateOf(initialOuterId) }
    var query by remember { mutableStateOf("") }

    var slot by remember {
        mutableStateOf(if (initialOuterId.isEmpty()) CrossSlot.CARRIER else CrossSlot.EXIT)
    }

    val inner = exits.firstOrNull { it.id == innerId }
    val outer = carriers.firstOrNull { it.id == outerId }

    val sameProfile = innerId.isNotEmpty() && innerId == outerId
    val incompatible = if (inner != null && outer != null && !sameProfile) pairError(inner, outer) else null
    val valid = inner != null && outer != null && !sameProfile && incompatible == null

    val options = (if (slot == CrossSlot.CARRIER) carriers else exits).filterByChainQuery(query)
    val emptyText = stringResource(
        if (slot == CrossSlot.CARRIER) Res.string.crosschain_no_carriers else Res.string.crosschain_no_exits,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                item(key = "head") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.crosschain_add_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(Res.string.crosschain_desc),
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

                item(key = "slots") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SlotCard(
                            label = stringResource(Res.string.crosschain_carrier),
                            picked = outer,
                            active = slot == CrossSlot.CARRIER,
                            onClick = { slot = CrossSlot.CARRIER },
                            modifier = Modifier.weight(1f),
                        )
                        SlotCard(
                            label = stringResource(Res.string.crosschain_exit),
                            picked = inner,
                            active = slot == CrossSlot.EXIT,
                            onClick = { slot = CrossSlot.EXIT },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "hint") {
                    Text(
                        stringResource(
                            if (slot == CrossSlot.CARRIER) Res.string.crosschain_carrier_hint
                            else Res.string.crosschain_exit_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "search") { ChainSearchField(value = query, onValueChange = { query = it }) }

                chainCandidateItems(
                    options = options,
                    leading = { p ->
                        val id = if (slot == CrossSlot.CARRIER) outerId else innerId
                        if (p.id == id) "●" else "○"
                    },
                    picked = { p -> p.id == (if (slot == CrossSlot.CARRIER) outerId else innerId) },
                    onSelect = { p ->
                        if (slot == CrossSlot.CARRIER) {
                            outerId = p.id

                            if (innerId.isEmpty()) { slot = CrossSlot.EXIT; query = "" }
                        } else {
                            innerId = p.id
                            if (outerId.isEmpty()) { slot = CrossSlot.CARRIER; query = "" }
                        }
                    },
                )

                if (options.isEmpty()) {
                    item(key = "none") {
                        Text(
                            if (query.isBlank()) emptyText
                            else stringResource(Res.string.chain_no_results, query),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (query.isBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (valid) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(Res.string.crosschain_summary, inner.name, outer.name),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (incompatible != null) {
                    Text(
                        incompatible,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (sameProfile) {
                    Text(
                        stringResource(Res.string.crosschain_same_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (inner?.isPsiphon == true) {
                    Text(
                        stringResource(Res.string.crosschain_psiphon_quic_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inner?.isTor == true) {
                    Text(
                        stringResource(Res.string.crosschain_tor_bridge_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (inner != null && !inner.isManagedTunnel) {
                    Text(
                        stringResource(Res.string.crosschain_fragment_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { onSave(name.trim(), innerId, outerId) },
                    enabled = valid,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SlotCard(
    label: String,
    picked: VpnProfile?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary,
            )
            Text(
                picked?.name ?: stringResource(Res.string.crosschain_not_set),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (picked == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (picked != null) {
                Text(
                    picked.transportLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
