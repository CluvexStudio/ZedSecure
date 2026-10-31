@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.config.Subscription
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_sub_meta
import dev.cluvex.zedsecure.shared.resources.bento_updated_days
import dev.cluvex.zedsecure.shared.resources.bento_updated_hours
import dev.cluvex.zedsecure.shared.resources.bento_updated_minutes
import dev.cluvex.zedsecure.shared.resources.bento_updated_now
import dev.cluvex.zedsecure.shared.resources.config_active
import dev.cluvex.zedsecure.shared.resources.ic_check
import dev.cluvex.zedsecure.shared.resources.ic_more_vert
import dev.cluvex.zedsecure.shared.resources.ic_sync
import dev.cluvex.zedsecure.shared.resources.servers_actions
import dev.cluvex.zedsecure.shared.resources.subs_days_left
import dev.cluvex.zedsecure.shared.resources.subs_expired
import dev.cluvex.zedsecure.shared.resources.subs_expires_today
import dev.cluvex.zedsecure.shared.resources.subs_one_day_left
import dev.cluvex.zedsecure.shared.resources.subs_quota_exhausted
import dev.cluvex.zedsecure.shared.resources.subs_support
import dev.cluvex.zedsecure.shared.resources.subs_title
import dev.cluvex.zedsecure.shared.resources.subs_update
import dev.cluvex.zedsecure.shared.resources.subs_usage
import dev.cluvex.zedsecure.shared.resources.subs_usage_unlimited
import dev.cluvex.zedsecure.shared.resources.subs_website
import dev.cluvex.zedsecure.ui.format.formatBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SING_BOX_SUFFIX = " · sing-box"

@Composable
fun BentoServerRow(
    name: String,
    detail: String,
    country: String?,
    pingMs: Int?,
    showPing: Boolean,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = Bento.colors
    val singBox = detail.endsWith(SING_BOX_SUFFIX)
    val plain = if (singBox) detail.removeSuffix(SING_BOX_SUFFIX) else detail
    val highlight = (selecting && selected) || (!selecting && active)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BentoMetrics.rowMin())
            .clip(shape)
            .background(if (highlight) c.selected else c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BentoFlag(country, size = 38.dp, corner = 13.dp, fallback = name.firstOrNull()?.uppercase())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    plain,
                    style = BentoType.body().copy(fontSize = 12.5.sp),
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (singBox) {
                    Box(
                        Modifier.height(18.dp).clip(RoundedCornerShape(9.dp)).background(c.lime).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("sing-box", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c.onLime, maxLines = 1)
                    }
                }
            }
        }
        if (showPing && pingMs != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (pingMs > 0) "%d ms".format(pingMs) else "—",
                style = BentoType.number(13.5.sp),
                color = c.pingColor(pingMs),
                maxLines = 1,
            )
        }
        if (highlight) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(Res.drawable.ic_check), contentDescription = stringResource(Res.string.config_active), tint = c.ink, modifier = Modifier.size(22.dp))
        }
        Box {
            BentoIconButton(
                Res.drawable.ic_more_vert,
                stringResource(Res.string.servers_actions),
                { menuOpen = true },
                container = Color.Transparent,
                content = c.muted,
                size = 44.dp,
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = MaterialTheme.shapes.largeIncreased,
            ) {
                menu { menuOpen = false }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoProviderCard(
    sub: Subscription,
    serverCount: Int,
    updatedAgo: String?,
    updating: Boolean,
    onUpdate: () -> Unit,
    onManage: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val tone = c.violet
    val now = dev.cluvex.zedsecure.platform.currentTimeMillis()
    val used = sub.used
    val total = sub.total
    val fraction = if (used != null && total != null && total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    val expireAt = sub.expireAt
    val expired = expireAt != null && expireAt <= now
    val daysLeft = expireAt?.let { ((it - now) / 86_400_000L).toInt() }
    val exhausted = fraction != null && fraction >= 1f
    BentoCard(
        tone = tone,
        modifier = modifier.fillMaxWidth(),
        shape = if (Bento.prefs.corners == dev.cluvex.zedsecure.domain.model.BentoCorners.Soft) RoundedCornerShape(20.dp) else RoundedCornerShape(28.dp),
        onClick = onManage,
        motif = MaterialShapes.Cookie12Sided,
        motifSize = 150.dp,
        motifRotation = -12f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sub.title?.takeIf { it.isNotBlank() } ?: sub.name, style = BentoType.title(21.sp), color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.bento_sub_meta, serverCount) + (updatedAgo?.let { " · $it" } ?: ""),
                    style = BentoType.body().copy(fontSize = 12.5.sp),
                    color = tone.content.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (updating) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = tone.content, strokeWidth = 2.5.dp)
                }
            } else {
                MotifButton(
                    shape = MaterialShapes.Cookie12Sided,
                    container = tone.content,
                    content = tone.container,
                    icon = Res.drawable.ic_sync,
                    label = stringResource(Res.string.subs_update),
                    onClick = onUpdate,
                    size = 44.dp,
                )
            }
        }
        if (fraction != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(tone.content.copy(alpha = 0.2f))) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (exhausted) c.bad else tone.content),
                )
            }
        }
        val usageText = when {
            used != null && total != null -> stringResource(Res.string.subs_usage, formatBytes(used), formatBytes(total))
            used != null -> stringResource(Res.string.subs_usage_unlimited, formatBytes(used))
            else -> null
        }
        val expiryText = when {
            expired -> stringResource(Res.string.subs_expired)
            exhausted -> stringResource(Res.string.subs_quota_exhausted)
            daysLeft == null -> null
            daysLeft <= 0 -> stringResource(Res.string.subs_expires_today)
            daysLeft == 1 -> stringResource(Res.string.subs_one_day_left)
            else -> stringResource(Res.string.subs_days_left, daysLeft)
        }
        if (usageText != null || expiryText != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(usageText.orEmpty(), style = BentoType.number(12.5.sp, FontWeight.Medium), color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (expiryText != null) {
                    Text(
                        expiryText,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = if (expired || exhausted || (daysLeft != null && daysLeft <= 3)) (if (c.dark) c.danger else c.bad) else tone.content,
                        maxLines = 1,
                    )
                }
            }
        }
        val links = listOfNotNull(
            sub.supportUrl?.let { stringResource(Res.string.subs_support) to it },
            sub.webPageUrl?.let { stringResource(Res.string.subs_website) to it },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            links.forEach { (label, url) -> LinkChip(label, tone) { onOpenUrl(url) } }
            LinkChip(stringResource(Res.string.subs_title), tone, onManage)
        }
    }
}

@Composable
private fun LinkChip(label: String, tone: BentoTone, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(tone.content.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = tone.content, maxLines = 1)
    }
}

@Composable
fun bentoUpdatedAgo(lastUpdated: Long): String? {
    if (lastUpdated <= 0L) return null
    val minutes = ((dev.cluvex.zedsecure.platform.currentTimeMillis() - lastUpdated) / 60_000L).coerceAtLeast(0L).toInt()
    return when {
        minutes < 1 -> stringResource(Res.string.bento_updated_now)
        minutes < 60 -> stringResource(Res.string.bento_updated_minutes, minutes)
        minutes < 48 * 60 -> stringResource(Res.string.bento_updated_hours, minutes / 60)
        else -> stringResource(Res.string.bento_updated_days, minutes / (24 * 60))
    }
}
