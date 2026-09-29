@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class ChainEngine { XRAY, PSIPHON, TOR }

internal val VpnProfile.chainEngine: ChainEngine
    get() = when {
        isTor -> ChainEngine.TOR
        isPsiphon -> ChainEngine.PSIPHON
        else -> ChainEngine.XRAY
    }

internal fun List<VpnProfile>.filterByChainQuery(query: String): List<VpnProfile> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true) }
}

@Composable
internal fun ChainSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.chain_search)) },
        singleLine = true,
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.chain_search_clear),
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ChainGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ChainCandidateRow(
    profile: VpnProfile,
    leading: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (picked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(leading, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${profile.transportLabel}  ·  ${profile.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            profile.lastPingMs?.let { ms ->
                Text(
                    stringResource(Res.string.chain_ping_ms, ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun LazyListScope.chainCandidateItems(
    options: List<VpnProfile>,
    leading: (VpnProfile) -> String,
    picked: (VpnProfile) -> Boolean,
    onSelect: (VpnProfile) -> Unit,
) {
    val groups = ChainEngine.entries.mapNotNull { engine ->
        options.filter { it.chainEngine == engine }.takeIf { it.isNotEmpty() }?.let { engine to it }
    }
    val showHeaders = groups.size > 1
    groups.forEach { (engine, list) ->
        if (showHeaders) {
            item(key = "hdr-${engine.name}") {
                ChainGroupHeader(
                    stringResource(
                        when (engine) {
                            ChainEngine.XRAY -> Res.string.chain_group_xray
                            ChainEngine.PSIPHON -> Res.string.chain_group_psiphon
                            ChainEngine.TOR -> Res.string.chain_group_tor
                        },
                    ),
                )
            }
        }
        items(list.size, key = { "${engine.name}-${list[it].id}" }) { i ->
            val p = list[i]
            ChainCandidateRow(
                profile = p,
                leading = leading(p),
                picked = picked(p),
                onClick = { onSelect(p) },
            )
        }
    }
}
