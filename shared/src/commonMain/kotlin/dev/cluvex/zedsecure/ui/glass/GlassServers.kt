package dev.cluvex.zedsecure.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_sub_meta
import dev.cluvex.zedsecure.shared.resources.config_active
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
import dev.cluvex.zedsecure.shared.resources.glass_timeout
import dev.cluvex.zedsecure.shared.resources.servers_actions
import dev.cluvex.zedsecure.ui.bento.BentoFlag
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import org.jetbrains.compose.resources.stringResource

private const val SingBoxSuffix = " · sing-box"

@Composable
fun GlassServerRow(
    name: String,
    detail: String,
    country: String?,
    pingMs: Int?,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    position: Int,
    count: Int,
    twoColumns: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = Glass.colors
    val shape: Shape = if (twoColumns) RoundedCornerShape(22.dp) else GlassShapes.row(position, count)
    val last = twoColumns || position >= count - 1
    val singBox = detail.endsWith(SingBoxSuffix)
    val plain = if (singBox) detail.removeSuffix(SingBoxSuffix) else detail
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inset = if (country != null) 58.dp else 16.dp
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (twoColumns) {
                    Modifier.glassCell(c, shape)
                } else {
                    Modifier
                        .clip(shape)
                        .background(c.cell)
                        .then(if (position == 0 && c.translucent) Modifier.glassHighlight(shape, c.highlight) else Modifier)
                },
            )
            .glassClickable(onClick)
            .then(
                if (last) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val stroke = maxOf(1f, 0.5.dp.toPx())
                        val start = inset.toPx()
                        drawRect(
                            c.separator,
                            topLeft = Offset(if (rtl) 0f else start, size.height - stroke),
                            size = Size(size.width - start, stroke),
                        )
                    }
                },
            )
            .heightIn(min = 62.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selecting) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.tint else Color.Transparent)
                    .then(if (selected) Modifier else Modifier.border(1.5.dp, c.label3, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(GlassIcons.CheckBold, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        if (country != null) {
            BentoFlag(country, size = 30.dp, corner = 15.dp, container = c.fill2, content = c.label)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = GlassType.body(), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    plain,
                    style = GlassType.subhead().copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (singBox) {
                    Box(
                        Modifier.clip(GlassShapes.capsule).background(c.fill2).padding(horizontal = 7.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("sing-box", style = GlassType.caption().copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c.label2, maxLines = 1)
                    }
                }
            }
        }
        if (pingMs != null) {
            Text(
                if (pingMs > 0) ltrIsolate("%d ms".format(pingMs)) else stringResource(Res.string.glass_timeout),
                style = GlassType.subhead(),
                color = c.pingColor(pingMs),
                maxLines = 1,
            )
        }
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (active && !selecting) {
                Icon(GlassIcons.Check, contentDescription = stringResource(Res.string.config_active), tint = c.tint, modifier = Modifier.size(19.dp))
            }
        }
        Box {
            MoreCircle(
                label = stringResource(Res.string.servers_actions),
                onClick = { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = c.menu,
            ) {
                menu { menuOpen = false }
            }
        }
    }
}

@Composable
private fun Modifier.glassClickable(onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, glassRowIndication(), role = Role.Button, onClick = onClick)

@Composable
private fun MoreCircle(label: String, onClick: () -> Unit) {
    val c = Glass.colors
    GlassToolIcon(icon = null, label = label, onClick = onClick) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.6.dp.toPx()
            drawCircle(c.tint, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
            val r = 1.5.dp.toPx()
            val gap = 4.5.dp.toPx()
            for (i in -1..1) drawCircle(c.tint, radius = r, center = Offset(center.x + gap * i, center.y))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassGroupChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val shape = GlassShapes.capsule
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(c.tint) else Modifier.glassModule(c, shape))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = glassRowIndication(),
                role = Role.Tab,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = GlassType.subhead().copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Color.White else c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                ltrIsolate(count.toString()),
                style = GlassType.footnote(),
                color = if (selected) Color.White.copy(alpha = 0.8f) else c.label2,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun GlassProviderCard(
    sub: dev.cluvex.zedsecure.domain.config.Subscription,
    serverCount: Int,
    updatedAgo: String?,
    updating: Boolean,
    animate: Boolean,
    onUpdate: () -> Unit,
    onManage: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val now = dev.cluvex.zedsecure.platform.currentTimeMillis()
    val used = sub.used
    val total = sub.total
    val fraction = if (used != null && total != null && total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    val expireAt = sub.expireAt
    val expired = expireAt != null && expireAt <= now
    val daysLeft = expireAt?.let { ((it - now) / 86_400_000L).toInt() }
    val exhausted = fraction != null && fraction >= 1f
    Column(
        modifier
            .fillMaxWidth()
            .glassModule(c, GlassShapes.cell)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title?.takeIf { it.isNotBlank() } ?: sub.name,
                    style = GlassType.headline(),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Res.string.bento_sub_meta, serverCount) + (updatedAgo?.let { " · $it" } ?: ""),
                    style = GlassType.footnote(),
                    color = c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (updating) {
                GlassSpinner(c.tint, animate = animate)
            } else {
                GlassCapsuleButton(
                    text = stringResource(Res.string.subs_update),
                    onClick = onUpdate,
                    icon = GlassIcons.Refresh,
                )
            }
        }
        if (fraction != null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).clip(GlassShapes.capsule).background(c.fill)) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .heightIn(min = 6.dp)
                        .clip(GlassShapes.capsule)
                        .background(if (exhausted) c.red else c.tint),
                )
            }
        }
        val usageText = when {
            used != null && total != null -> stringResource(
                Res.string.subs_usage,
                ltrIsolate(dev.cluvex.zedsecure.ui.format.formatBytes(used)),
                ltrIsolate(dev.cluvex.zedsecure.ui.format.formatBytes(total)),
            )
            used != null -> stringResource(Res.string.subs_usage_unlimited, ltrIsolate(dev.cluvex.zedsecure.ui.format.formatBytes(used)))
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
                Text(usageText.orEmpty(), style = GlassType.footnote(), color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (expiryText != null) {
                    val warn = expired || exhausted || (daysLeft != null && daysLeft <= 3)
                    Text(expiryText, style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold), color = if (warn) c.red else c.label2, maxLines = 1)
                }
            }
        }
        val links = listOfNotNull(
            sub.supportUrl?.let { stringResource(Res.string.subs_support) to it },
            sub.webPageUrl?.let { stringResource(Res.string.subs_website) to it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (label, url) -> GlassChip(label, selected = false) { onOpenUrl(url) } }
            GlassChip(stringResource(Res.string.subs_title), selected = false, onClick = onManage)
        }
    }
}
