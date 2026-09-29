@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.core.AutoSelect
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.theme.Personalization
import dev.cluvex.zedsecure.ui.theme.goodPingColor
import dev.cluvex.zedsecure.ui.theme.ZedLime
import dev.cluvex.zedsecure.ui.theme.readableOn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun autoSelectGroupLabel(profile: VpnProfile): String {
    val group = profile.autoSelectSettings()
    return when (group?.subscriptionId) {
        null -> stringResource(Res.string.auto_group_all)
        "" -> stringResource(Res.string.group_manual)
        else -> profile.name
    }
}

@Composable
fun autoSelectLiveLine(session: AutoSelect.Session, memberName: (String) -> String?): String? {
    val status = session.status ?: return null
    val name = session.selectedProfileId?.let(memberName) ?: return null
    val delay = status.member(status.selected)?.delayMs?.takeIf { it > 0 }
    return if (delay != null) stringResource(Res.string.auto_live_with_delay, name, delay.toString())
    else stringResource(Res.string.auto_live, name)
}

@Composable
fun AutoSelectCard(
    groupLabel: String,
    memberCount: Int,
    active: Boolean,
    live: AutoSelect.Session?,
    memberName: (String) -> String?,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    personalization: Personalization = Personalization.Default,
) {
    val container = if (active) {
        personalization.serverActiveColor ?: MaterialTheme.colorScheme.primaryContainer
    } else {
        personalization.serverCardColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = when {
        active && personalization.serverActiveColor != null -> personalization.serverActiveColor.readableOn()
        !active && personalization.serverCardColor != null -> personalization.serverCardColor.readableOn()
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val phase = live?.status?.phase
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(personalization.cornerStyle.serverCardDp.dp),
        color = container,
        contentColor = onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(phaseColor(phase)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_bolt),
                    contentDescription = null,
                    tint = phaseColor(phase).readableOn(),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.auto_title_for, groupLabel),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = live?.let { autoSelectLiveLine(it, memberName) }
                    ?: phase?.let { phaseLabel(it) }
                    ?: stringResource(Res.string.auto_card_idle, memberCount.toString())
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active) {
                Icon(
                    painterResource(Res.drawable.ic_check_circle),
                    contentDescription = stringResource(Res.string.config_active),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDetails) {
                Icon(
                    painterResource(Res.drawable.ic_info),
                    contentDescription = stringResource(Res.string.auto_details),
                )
            }
        }
    }
}

@Composable
fun AutoSelectSheet(
    groupLabel: String,
    members: List<VpnProfile>,
    live: AutoSelect.Session?,
    onDismiss: () -> Unit,
) {
    val byId = members.associateBy { it.id }
    val memberName: (String) -> String? = { id -> byId[id]?.name }
    val status = live?.status
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(phaseColor(status?.phase)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = phaseColor(status?.phase).readableOn(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.auto_title_for, groupLabel),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status?.let { phaseLabel(it.phase) } ?: stringResource(Res.string.auto_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (live != null && status != null) {
                val selectedName = live.selectedProfileId?.let(memberName)
                if (selectedName != null) {
                    Surface(
                        shape = MaterialTheme.shapes.largeIncreased,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(Res.string.auto_now_on),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                            Text(
                                selectedName,
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            status.member(status.selected)?.let { m ->
                                Text(
                                    memberFacts(m),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            if (!status.pinned.isNullOrBlank() && AutoSelect.supportsPinning) {
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(onClick = { AutoSelect.pin(""); Unit }) {
                                    Text(stringResource(Res.string.auto_unpin))
                                }
                            }
                        }
                    }
                }

                val events = status.events.asReversed().take(6)
                if (events.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.auto_history),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    events.forEach { e -> EventRow(e, live) { tag -> live.memberProfiles[tag]?.let(memberName) ?: tag } }
                }
            }

            Text(
                stringResource(Res.string.auto_members),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            if (live != null && status != null) {
                val rows = live.memberProfiles.entries.sortedWith(
                    compareBy<Map.Entry<String, String>>(
                        { status.member(it.key)?.health?.ordinal?.let(::healthRank) ?: 9 },
                        { status.member(it.key)?.delayMs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE },
                    ),
                )
                rows.forEach { (tag, profileId) ->
                    LiveMemberRow(
                        name = memberName(profileId) ?: tag,
                        member = status.member(tag),
                        selected = tag == status.selected,
                        pinned = tag == status.pinned,
                        onPin = if (AutoSelect.supportsPinning) {
                            { AutoSelect.pin(tag); Unit }
                        } else null,
                    )
                }
            } else {
                members.sortedBy { (it.lastPingMs ?: -1).let { p -> if (p > 0) p else Int.MAX_VALUE } }
                    .forEach { StoredMemberRow(it) }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.auto_how_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        stringResource(Res.string.auto_how_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: AutoSelect.Event, live: AutoSelect.Session, nameOf: (String) -> String) {
    val to = nameOf(e.to)
    val from = e.from.takeIf { it.isNotBlank() }?.let(nameOf).orEmpty()
    val text = when (e.kind) {
        AutoSelect.Reason.Startup -> stringResource(Res.string.auto_reason_startup, to)
        AutoSelect.Reason.Failover -> stringResource(Res.string.auto_reason_failover, to, from)
        AutoSelect.Reason.Faster -> stringResource(Res.string.auto_reason_faster, to, from)
        AutoSelect.Reason.Pinned -> stringResource(Res.string.auto_reason_pinned, to)
        AutoSelect.Reason.Recovered -> stringResource(Res.string.auto_reason_recovered, to)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (e.kind == AutoSelect.Reason.Failover) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            ago(e.atMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveMemberRow(
    name: String,
    member: AutoSelect.Member?,
    selected: Boolean,
    pinned: Boolean,
    onPin: (() -> Unit)?,
) {
    val health = member?.health ?: AutoSelect.Health.Unknown
    val emphasis by animateFloatAsState(if (selected) 1f else 0.985f, label = "member-emphasis")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().scale(emphasis),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        HealthChip(health)
                        if (pinned) Chip(stringResource(Res.string.auto_pinned), MaterialTheme.colorScheme.tertiaryContainer)
                        member?.let { m ->
                            Text(
                                memberFacts(m),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (onPin != null && !pinned) {
                    IconButton(onClick = onPin) {
                        Icon(
                            painterResource(Res.drawable.ic_push_pin),
                            contentDescription = stringResource(Res.string.auto_pin),
                        )
                    }
                }
            }
            val error = member?.lastError?.takeIf {
                it.isNotBlank() && (health == AutoSelect.Health.Dead || health == AutoSelect.Health.Suspect)
            }
            AnimatedVisibility(visible = error != null) {
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StoredMemberRow(p: VpnProfile) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            p.lastPingMs?.let { ping ->
                Text(
                    if (ping > 0) "$ping ms" else "$ping",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        ping <= 0 -> MaterialTheme.colorScheme.error
                        ping < 200 -> goodPingColor(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ping < 500 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun HealthChip(h: AutoSelect.Health) {
    val (label, color) = when (h) {
        AutoSelect.Health.Alive -> stringResource(Res.string.auto_health_alive) to MaterialTheme.colorScheme.primaryContainer
        AutoSelect.Health.Suspect -> stringResource(Res.string.auto_health_suspect) to MaterialTheme.colorScheme.tertiaryContainer
        AutoSelect.Health.Dead -> stringResource(Res.string.auto_health_dead) to MaterialTheme.colorScheme.errorContainer
        AutoSelect.Health.Unknown -> stringResource(Res.string.auto_health_unknown) to MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Chip(label, color)
}

@Composable
private fun Chip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color.readableOn(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun memberFacts(m: AutoSelect.Member): String = buildString {
    if (m.delayMs > 0) {
        append("${m.delayMs} ms")
        if (m.jitterMs > 0) append(" ±${m.jitterMs}")
    }
    if (m.connections > 0) {
        if (isNotEmpty()) append(" · ")
        append(stringResource(Res.string.auto_connections, m.connections.toString()))
    }
}

@Composable
private fun phaseLabel(p: AutoSelect.Phase): String = stringResource(
    when (p) {
        AutoSelect.Phase.Starting -> Res.string.auto_state_starting
        AutoSelect.Phase.Ok -> Res.string.auto_state_ok
        AutoSelect.Phase.Degraded -> Res.string.auto_state_degraded
        AutoSelect.Phase.Down -> Res.string.auto_state_down
        AutoSelect.Phase.Idle -> Res.string.auto_state_idle
    },
)

@Composable
private fun phaseColor(p: AutoSelect.Phase?): Color = when (p) {
    AutoSelect.Phase.Degraded -> MaterialTheme.colorScheme.tertiary
    AutoSelect.Phase.Down -> MaterialTheme.colorScheme.error
    AutoSelect.Phase.Ok -> ZedLime
    else -> MaterialTheme.colorScheme.primary
}

private fun healthRank(ordinal: Int): Int = when (AutoSelect.Health.entries[ordinal]) {
    AutoSelect.Health.Alive -> 0
    AutoSelect.Health.Suspect -> 1
    AutoSelect.Health.Unknown -> 2
    AutoSelect.Health.Dead -> 3
}

@Composable
private fun ago(atMs: Long): String {
    val seconds = ((currentTimeMillis() - atMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> stringResource(Res.string.auto_ago_now)
        seconds < 3600 -> stringResource(Res.string.auto_ago_minutes, ((seconds + 30) / 60).toString())
        else -> stringResource(Res.string.auto_ago_hours, (seconds / 3600).toString())
    }
}
