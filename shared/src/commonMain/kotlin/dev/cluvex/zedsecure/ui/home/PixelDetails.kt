@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_close
import dev.cluvex.zedsecure.shared.resources.action_copy
import dev.cluvex.zedsecure.shared.resources.copied
import dev.cluvex.zedsecure.shared.resources.home_ping
import dev.cluvex.zedsecure.shared.resources.ic_close
import dev.cluvex.zedsecure.shared.resources.ic_fill_content_copy
import dev.cluvex.zedsecure.shared.resources.ic_fill_location_on
import dev.cluvex.zedsecure.shared.resources.ic_fill_public
import dev.cluvex.zedsecure.shared.resources.ic_fill_router
import dev.cluvex.zedsecure.shared.resources.ic_fill_speed
import dev.cluvex.zedsecure.shared.resources.ic_fill_sync
import dev.cluvex.zedsecure.shared.resources.info_details
import dev.cluvex.zedsecure.shared.resources.info_ipv6
import dev.cluvex.zedsecure.shared.resources.info_location
import dev.cluvex.zedsecure.shared.resources.info_no_ip
import dev.cluvex.zedsecure.shared.resources.info_no_ipv6
import dev.cluvex.zedsecure.shared.resources.info_refresh
import dev.cluvex.zedsecure.shared.resources.info_tap_to_refresh
import dev.cluvex.zedsecure.shared.resources.info_unknown_location
import dev.cluvex.zedsecure.ui.components.FlagBadge
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.theme.goodPingColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val TileOuter = 26.dp
private val TileInner = 8.dp
private val TilePressed = 20.dp
private val TileGap = 4.dp
private val BadgeSize = 40.dp
private const val SLOW_PING_MS = 250

private class TileCorners(val topStart: Boolean, val topEnd: Boolean, val bottomEnd: Boolean, val bottomStart: Boolean)

@Composable
internal fun PixelDetailsPanel(
    info: NetworkInfo,
    onClose: () -> Unit,
    onRefreshPing: () -> Unit,
    onRefreshPlace: () -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val busy = info.loading || info.pingLoading || info.placeLoading
    val refreshLabel = stringResource(Res.string.info_refresh)
    Column(modifier.fillMaxWidth().widthIn(max = 440.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    stringResource(Res.string.info_details),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = cs.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Res.string.info_tap_to_refresh),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefreshAllButton(busy = busy, label = refreshLabel, onClick = onRefreshAll)
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.action_close),
                    tint = cs.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            LocationTile(info, refreshLabel, onRefreshPlace, Modifier.weight(1f).fillMaxHeight())
            PingTile(info, refreshLabel, onRefreshPing, Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(TileGap))
        AddressTile(info, refreshLabel, onRefreshPlace, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RefreshAllButton(busy: Boolean, label: String, onClick: () -> Unit) {
    val turn = if (busy) {
        rememberInfiniteTransition(label = "info-refresh").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "info-refresh-turn",
        ).value
    } else {
        0f
    }
    FilledTonalIconButton(onClick = onClick, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.ic_fill_sync),
            contentDescription = label,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun LocationTile(info: NetworkInfo, refreshLabel: String, onRefresh: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    DetailTile(
        corners = TileCorners(topStart = true, topEnd = false, bottomEnd = false, bottomStart = false),
        loading = info.placeLoading,
        onClick = onRefresh,
        onClickLabel = refreshLabel,
        badgeShape = MaterialShapes.Cookie9Sided,
        badgeColor = cs.secondaryContainer,
        badge = {
            if (info.countryCode != null) {
                FlagBadge(countryCode = info.countryCode, size = 22.dp)
            } else {
                BadgeIcon(Res.drawable.ic_fill_location_on, cs.onSecondaryContainer)
            }
        },
        label = stringResource(Res.string.info_location),
        vertical = false,
        modifier = modifier,
    ) {
        val country = localCountry(info.countryCode) ?: info.country
        val waiting = country == null && (info.placeLoading || info.loading)
        Text(
            when {
                country != null -> country
                waiting -> stringResource(Res.string.info_no_ip)
                else -> stringResource(Res.string.info_unknown_location)
            },
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = tileInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        info.city?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = tileSubInk(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PingTile(info: NetworkInfo, refreshLabel: String, onRefresh: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val ping = info.pingMs
    val slow = ping != null && ping >= SLOW_PING_MS
    DetailTile(
        corners = TileCorners(topStart = false, topEnd = true, bottomEnd = false, bottomStart = false),
        loading = info.pingLoading,
        onClick = onRefresh,
        onClickLabel = refreshLabel,
        badgeShape = MaterialShapes.SoftBurst,
        badgeColor = if (slow) cs.errorContainer else cs.tertiaryContainer,
        badge = { BadgeIcon(Res.drawable.ic_fill_speed, if (slow) cs.onErrorContainer else cs.onTertiaryContainer) },
        label = stringResource(Res.string.home_ping),
        vertical = false,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (ping != null) {
                RollingText(
                    "%d".format(ping),
                    MaterialTheme.typography.titleLargeEmphasized,
                    if (slow) cs.error else goodPingColor(cs.primaryContainer),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = tileSubInk(),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            } else {
                Text("—", style = MaterialTheme.typography.titleLargeEmphasized, color = tileSubInk())
            }
        }
    }
}

@Composable
private fun AddressTile(info: NetworkInfo, refreshLabel: String, onRefresh: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val platform = LocalPlatform.current
    val copiedText = stringResource(Res.string.copied)
    val shown = info.ipv4 ?: info.ipv6
    DetailTile(
        corners = TileCorners(topStart = false, topEnd = false, bottomEnd = true, bottomStart = true),
        loading = info.placeLoading,
        onClick = onRefresh,
        onClickLabel = refreshLabel,
        badgeShape = MaterialShapes.Cookie4Sided,
        badgeColor = cs.primary,
        badge = { BadgeIcon(Res.drawable.ic_fill_public, cs.onPrimary) },
        label = "IP",
        vertical = false,
        modifier = modifier,
        trailing = {
            FilledTonalIconButton(
                onClick = {
                    if (shown != null) {
                        platform.copyToClipboard(shown)
                        platform.toast(copiedText)
                    }
                },
                enabled = shown != null,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_fill_content_copy),
                    contentDescription = stringResource(Res.string.action_copy),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        Text(
            shown?.let { ltrIsolate(it) } ?: stringResource(Res.string.info_no_ip),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = tileInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.ipv4 != null) {
            Text(
                "${stringResource(Res.string.info_ipv6)} · ${info.ipv6?.let { ltrIsolate(it) } ?: stringResource(Res.string.info_no_ipv6)}",
                style = MaterialTheme.typography.bodySmall,
                color = tileSubInk(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info.isp?.takeIf { it.isNotBlank() }?.let { isp ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_fill_router),
                    contentDescription = null,
                    tint = tileSubInk(),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(isp, style = MaterialTheme.typography.bodySmall, color = tileSubInk(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun tileColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)

@Composable
private fun tileInk(): Color = MaterialTheme.colorScheme.onPrimaryContainer

@Composable
private fun tileSubInk(): Color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f)

@Composable
private fun DetailTile(
    corners: TileCorners,
    loading: Boolean,
    onClick: () -> Unit,
    onClickLabel: String,
    badgeShape: RoundedPolygon,
    badgeColor: Color,
    badge: @Composable () -> Unit,
    label: String,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val inner by animateDpAsState(
        if (pressed) TilePressed else TileInner,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "tile-corner",
    )
    val dim by animateFloatAsState(
        if (loading) 0.55f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tile-dim",
    )
    val shape = RoundedCornerShape(
        topStart = if (corners.topStart) TileOuter else inner,
        topEnd = if (corners.topEnd) TileOuter else inner,
        bottomEnd = if (corners.bottomEnd) TileOuter else inner,
        bottomStart = if (corners.bottomStart) TileOuter else inner,
    )
    Box(
        modifier
            .clip(shape)
            .background(tileColor())
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        val labelText: @Composable () -> Unit = {
            Text(label, style = MaterialTheme.typography.labelMedium, color = tileSubInk(), maxLines = 1)
        }
        if (vertical) {
            Column {
                TileBadge(loading, badgeShape, badgeColor, badge)
                Spacer(Modifier.height(10.dp))
                labelText()
                Column(Modifier.graphicsLayer { alpha = dim }, content = content)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileBadge(loading, badgeShape, badgeColor, badge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    labelText()
                    Column(Modifier.graphicsLayer { alpha = dim }, content = content)
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    }
}

@Composable
private fun TileBadge(loading: Boolean, shape: RoundedPolygon, color: Color, content: @Composable () -> Unit) {
    val clip = shape.toShape()
    AnimatedContent(
        targetState = loading,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.7f))
        },
        contentAlignment = Alignment.Center,
        label = "tile-badge",
    ) { busy ->
        if (busy) {
            LoadingIndicator(Modifier.size(BadgeSize), color = color)
        } else {
            Box(Modifier.size(BadgeSize).clip(clip).background(color), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

internal fun localCountry(code: String?): String? {
    val iso = code?.trim()?.takeIf { it.length == 2 } ?: return null
    val name = runCatching { java.util.Locale.Builder().setRegion(iso).build() }.getOrNull()
        ?.getDisplayCountry(java.util.Locale.getDefault()) ?: return null
    return name.takeIf { it.isNotBlank() && !it.equals(iso, ignoreCase = true) }
}

@Composable
private fun BadgeIcon(icon: DrawableResource, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}
