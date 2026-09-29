package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.domain.model.ConnectButtonStyle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.action_connect
import dev.cluvex.zedsecure.shared.resources.action_disconnect
import dev.cluvex.zedsecure.shared.resources.ic_bolt
import dev.cluvex.zedsecure.shared.resources.ic_close
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AltConnectControl(
    style: ConnectButtonStyle,
    state: ConnectionState,
    onToggle: () -> Unit,
    customColor: Color? = null,
    customActiveColor: Color? = null,

    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (style) {
        ConnectButtonStyle.Ring -> RingConnect(state, onToggle, customColor, customActiveColor, compact, modifier)
        ConnectButtonStyle.Bar -> BarConnect(state, onToggle, customColor, customActiveColor, compact, modifier)
        ConnectButtonStyle.Icon -> IconConnect(state, onToggle, customColor, customActiveColor, compact, modifier)
        ConnectButtonStyle.Switch -> SwitchConnect(state, onToggle, customColor, customActiveColor, compact, modifier)

        ConnectButtonStyle.Pill, ConnectButtonStyle.Hero -> Unit
    }
}

@Composable
private fun containerFor(
    state: ConnectionState,
    custom: Color?,
    customActive: Color?,
): Color {
    val idle = custom ?: MaterialTheme.colorScheme.primary
    val active = customActive ?: MaterialTheme.colorScheme.secondaryContainer
    val target = when {
        state == ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
        state.isActive -> active
        else -> idle
    }
    val animated by animateColorAsState(target, label = "alt-connect-container")
    return animated
}

@Composable
private fun contentFor(state: ConnectionState, container: Color): Color = when {
    state == ConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
    else -> if (container.luminance() > 0.5f) Color(0xFF10131A) else Color.White
}

@Composable
private fun label(state: ConnectionState): String = stringResource(
    when {
        state == ConnectionState.Disconnecting -> Res.string.state_disconnecting

        state.isTransitioning -> Res.string.action_cancel
        state.isActive -> Res.string.action_disconnect
        else -> Res.string.action_connect
    },
)

private val ConnectionState.acceptsTap: Boolean
    get() = this != ConnectionState.Disconnecting

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RingConnect(
    state: ConnectionState,
    onToggle: () -> Unit,
    custom: Color?,
    customActive: Color?,
    compact: Boolean,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "ring-scale")
    val ring by animateDpAsState(if (state.isActive) 8.dp else 3.dp, label = "ring-width")
    val container = containerFor(state, custom, customActive)
    val content = contentFor(state, container)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onToggle,
            enabled = state.acceptsTap,
            shape = CircleShape,
            color = container,
            contentColor = content,
            interactionSource = interaction,
            modifier = Modifier
                .size(if (compact) 104.dp else 132.dp)
                .scale(scale)
                .border(ring, content.copy(alpha = 0.35f), CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.isTransitioning) {
                    ContainedLoadingIndicator(Modifier.size(if (compact) 32.dp else 38.dp))
                } else {
                    Icon(
                        painterResource(if (state.isActive) Res.drawable.ic_close else Res.drawable.ic_bolt),
                        contentDescription = label(state),
                        modifier = Modifier.size(if (compact) 38.dp else 46.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BarConnect(
    state: ConnectionState,
    onToggle: () -> Unit,
    custom: Color?,
    customActive: Color?,
    compact: Boolean,
    modifier: Modifier,
) {
    val container = containerFor(state, custom, customActive)
    val content = contentFor(state, container)
    Surface(
        onClick = onToggle,
        enabled = state.acceptsTap,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.isTransitioning) {
                ContainedLoadingIndicator(Modifier.size(20.dp))
            } else {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = if (state.isActive) 1f else 0.55f)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label(state),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconConnect(
    state: ConnectionState,
    onToggle: () -> Unit,
    custom: Color?,
    customActive: Color?,
    compact: Boolean,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) 34.dp else 24.dp, label = "icon-radius")
    val container = containerFor(state, custom, customActive)
    val content = contentFor(state, container)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onToggle,
            enabled = state.acceptsTap,
            shape = RoundedCornerShape(radius),
            color = container,
            contentColor = content,
            interactionSource = interaction,
            modifier = Modifier.size(if (compact) 64.dp else 76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.isTransitioning) {
                    ContainedLoadingIndicator(Modifier.size(26.dp))
                } else {
                    Icon(
                        painterResource(if (state.isActive) Res.drawable.ic_close else Res.drawable.ic_bolt),
                        contentDescription = label(state),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchConnect(
    state: ConnectionState,
    onToggle: () -> Unit,
    custom: Color?,
    customActive: Color?,
    compact: Boolean,
    modifier: Modifier,
) {
    val container = containerFor(state, custom, customActive)
    val content = contentFor(state, container)
    val align by animateFloatAsState(if (state.isActive) 1f else -1f, label = "switch-align")
    Surface(
        onClick = onToggle,
        enabled = state.acceptsTap,
        shape = RoundedCornerShape(34.dp),
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth().height(if (compact) 56.dp else 68.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(if (compact) 6.dp else 8.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.BiasAlignment(align, 0f),
            ) {
                Box(
                    Modifier
                        .size(if (compact) 44.dp else 52.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isTransitioning) {
                        ContainedLoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(
                            painterResource(
                                if (state.isActive) Res.drawable.ic_close else Res.drawable.ic_bolt,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = label(state),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "switch-label",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
