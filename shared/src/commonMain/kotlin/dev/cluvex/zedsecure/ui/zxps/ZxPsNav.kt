package dev.cluvex.zedsecure.ui.zxps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** iOS-27 style floating glass bar: one liquid bubble slides under the selected tab. */
@Composable
fun ZxPsNavBar(
    tabs: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
) {
    val c = ZxPs.colors
    val p = ZxPs.prefs
    val shape = CircleShape
    val index = tabs.indexOf(current).coerceAtLeast(0)
    val height = if (p.navLabels) 64.dp else 54.dp
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(height).zxPsGlass(shape, p.glass, p.sheen, c.dark, strong = true),
        ) {
            val inner = 5.dp
            val slot = (maxWidth - inner * 2) / tabs.size.coerceAtLeast(1)
            val x by animateDpAsState(
                inner + slot * index,
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                label = "zxps-bubble",
            )
            Box(
                Modifier
                    .offset(x = x, y = inner)
                    .width(slot)
                    .height(height - inner * 2)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(c.a.copy(alpha = 0.34f), c.b.copy(alpha = 0.30f)))),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = inner), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { dest ->
                    ZxPsNavItem(
                        destination = dest,
                        selected = current == dest,
                        labels = p.navLabels,
                        onClick = { onSelect(dest) },
                        modifier = Modifier.weight(1f).height(height).tourTarget(TourTargets.nav(dest.name)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZxPsNavItem(
    destination: TopDestination,
    selected: Boolean,
    labels: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ZxPs.colors
    val tint by animateColorAsState(if (selected) c.ink else c.muted, tween(220), label = "zxps-nav-tint")
    val lift by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 380f),
        label = "zxps-nav-lift",
    )
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(destination.icon),
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer {
                val s = 1f + 0.14f * lift
                scaleX = s
                scaleY = s
            },
        )
        if (labels) {
            Text(
                stringResource(destination.labelRes),
                color = tint,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
