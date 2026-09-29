@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.ui.components.FlagBadge
import dev.cluvex.zedsecure.ui.theme.goodPingColor
import dev.cluvex.zedsecure.ui.theme.ZedLime

@Composable
fun ConnectionInfoPill(
    info: NetworkInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevron",
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagBadge(countryCode = info.countryCode, size = 24.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = locationLine(info),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = info.ipv4 ?: stringResource(Res.string.info_no_ip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (info.loading) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(Modifier.size(20.dp))
                    }
                } else {
                    info.pingMs?.let { ping ->
                        Text(
                            text = "$ping ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,

                            color = if (ping < 250) {
                                goodPingColor(MaterialTheme.colorScheme.surfaceContainerHigh)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(Res.string.info_details),
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.size(10.dp))
                    DetailRow(stringResource(Res.string.info_ipv4), info.ipv4)
                    DetailRow(
                        stringResource(Res.string.info_ipv6),
                        info.ipv6 ?: stringResource(Res.string.info_no_ipv6),
                    )
                    DetailRow(stringResource(Res.string.info_isp), info.isp)
                    DetailRow(
                        stringResource(Res.string.info_location),
                        listOfNotNull(info.city, info.country).joinToString(", ").ifBlank { null },
                    )
                    Spacer(Modifier.size(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onRefresh, enabled = !info.loading) {
                            Icon(
                                painterResource(Res.drawable.ic_sync),
                                contentDescription = stringResource(Res.string.info_refresh),
                            )
                        }
                        Text(
                            stringResource(Res.string.info_refresh),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(74.dp),
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}

@Composable
private fun locationLine(info: NetworkInfo): String {
    val parts = listOfNotNull(info.city, info.country)
    return if (parts.isEmpty()) stringResource(Res.string.info_unknown_location)
    else parts.joinToString(", ")
}
