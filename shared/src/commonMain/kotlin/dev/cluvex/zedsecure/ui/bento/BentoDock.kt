package dev.cluvex.zedsecure.ui.bento

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.BentoDockLabels
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

val BentoDockHeight: Dp = 56.dp
val BentoDockMargin: Dp = 20.dp

@Composable
fun BentoDock(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val labels = Bento.prefs.dockLabels
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = BentoDockMargin),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it * 2 } + fadeOut(),
        ) {
            val shape = RoundedCornerShape(30.dp)
            Row(
                Modifier
                    .shadow(10.dp, shape, ambientColor = c.ink.copy(alpha = 0.18f), spotColor = c.ink.copy(alpha = 0.18f))
                    .clip(shape)
                    .background(c.dock)
                    .border(1.dp, c.dockLine, shape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopDestination.entries.forEach { dest ->
                    val active = dest == current
                    DockItem(
                        destination = dest,
                        active = active,
                        showLabel = when (labels) {
                            BentoDockLabels.Active -> active
                            BentoDockLabels.Always -> true
                            BentoDockLabels.Never -> false
                        },
                        onClick = {
                            if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(dest)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: TopDestination,
    active: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val bg by animateColorAsState(if (active) c.lime else c.lime.copy(alpha = 0f), spring(stiffness = Spring.StiffnessMedium), label = "dock-bg")
    val fg by animateColorAsState(if (active) c.onLime else c.muted, spring(stiffness = Spring.StiffnessMedium), label = "dock-fg")
    val label = stringResource(destination.labelRes)
    Row(
        Modifier
            .tourTarget(TourTargets.nav(destination.name))
            .height(BentoDockHeight - 8.dp)
            .widthIn(min = 50.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .semantics {
                if (!showLabel) contentDescription = label
                selected = active
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = fg),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = if (showLabel) 16.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(destination.icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = showLabel,
            enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
