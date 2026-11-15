package dev.cluvex.zedsecure.ui.zx

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Floating HUD style navigation bar used by the ZX design. */
@Composable
fun ZxNavBar(
    tabs: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
) {
    val c = Zx.colors
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(c.surface.copy(alpha = 0.94f))
                .border(1.dp, c.line, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { dest ->
                ZxNavItem(
                    destination = dest,
                    selected = current == dest,
                    onClick = { onSelect(dest) },
                    modifier = Modifier.weight(1f).tourTarget(TourTargets.nav(dest.name)),
                )
            }
        }
    }
}

@Composable
private fun ZxNavItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zx.colors
    val tint by animateColorAsState(if (selected) c.neonA else c.muted, tween(250), label = "zx-nav-tint")
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "zx-nav-lift")
    val glow = c.neonA
    Column(
        modifier
            .height(52.dp)
            .clip(CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (lift > 0.01f) {
                    val w = size.width * 0.5f * lift
                    val y = size.height - 2.dp.toPx()
                    drawLine(glow.copy(alpha = 0.25f * lift), Offset(size.width / 2f - w, y), Offset(size.width / 2f + w, y), 7.dp.toPx())
                    drawLine(glow, Offset(size.width / 2f - w, y), Offset(size.width / 2f + w, y), 2.dp.toPx())
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(destination.icon),
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = 1f + 0.12f * lift
                    scaleX = s
                    scaleY = s
                    translationY = -2.dp.toPx() * lift
                },
        )
        if (selected) {
            Text(
                stringResource(destination.labelRes).uppercase(),
                style = zxCaption().copy(fontSize = 9.sp),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
