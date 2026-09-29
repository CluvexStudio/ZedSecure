package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.ic_map
import dev.cluvex.zedsecure.shared.resources.ic_speedometer
import dev.cluvex.zedsecure.shared.resources.map_fab
import dev.cluvex.zedsecure.shared.resources.speedtest_fab
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val FAB_SIZE = 54.dp
private val EDGE_MARGIN = 14.dp
private val FAB_GAP = 10.dp

private val TOP_LIMIT = 120.dp
private val BOTTOM_LIMIT = 28.dp

@Composable
fun HomeFabCluster(
    visible: Boolean,
    showSpeedTest: Boolean,
    reduceMotion: Boolean,
    atEnd: Boolean,
    yFraction: Float,
    onMove: (atEnd: Boolean, yFraction: Float) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fabPx = with(density) { FAB_SIZE.toPx() }
        val marginPx = with(density) { EDGE_MARGIN.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val minY = with(density) { TOP_LIMIT.toPx() }

        val clusterPx = fabPx * 2 + with(density) { FAB_GAP.toPx() }
        val maxY = (with(density) { maxHeight.toPx() - BOTTOM_LIMIT.toPx() } - clusterPx)
            .coerceAtLeast(minY)

        val restX = if (atEnd) maxWidthPx - fabPx - marginPx else marginPx
        val restY = minY + yFraction.coerceIn(0f, 1f) * (maxY - minY)

        var dragging by remember { mutableStateOf(false) }
        var rawX by remember { mutableFloatStateOf(restX) }
        var rawY by remember { mutableFloatStateOf(restY) }

        val motion = if (reduceMotion) tween<Float>(0) else
            spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        val animX by animateFloatAsState(if (dragging) rawX else restX, motion, label = "fab-x")
        val animY by animateFloatAsState(if (dragging) rawY else restY, motion, label = "fab-y")

        AnimatedVisibility(
            visible = visible,
            enter = if (reduceMotion) fadeIn(tween(0)) else
                slideInHorizontally(spring(stiffness = Spring.StiffnessLow)) { w -> if (atEnd) w else -w } +
                    fadeIn() + scaleIn(initialScale = 0.7f),
            exit = if (reduceMotion) fadeOut(tween(0)) else
                slideOutHorizontally { w -> if (atEnd) w else -w } + fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { translationX = animX; translationY = animY },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(FAB_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,

                modifier = Modifier.tourTarget(TourTargets.HOME_TOOLS).pointerInput(maxWidthPx, maxY) {
                    detectDragGestures(
                        onDragStart = {
                            rawX = restX
                            rawY = restY
                            dragging = true
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            rawX = (rawX + delta.x).coerceIn(0f, (maxWidthPx - fabPx).coerceAtLeast(0f))
                            rawY = (rawY + delta.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = {
                            dragging = false

                            val centre = rawX + fabPx / 2f
                            val end = centre > maxWidthPx / 2f
                            val fraction =
                                if (maxY > minY) ((rawY - minY) / (maxY - minY)).coerceIn(0f, 1f) else 0f
                            onMove(end, fraction)
                        },
                        onDragCancel = { dragging = false },
                    )
                },
            ) {
                FabButton(Res.drawable.ic_map, stringResource(Res.string.map_fab), onOpenMap)
                AnimatedVisibility(
                    visible = showSpeedTest,
                    enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = if (reduceMotion) fadeOut(tween(0)) else fadeOut() + scaleOut(targetScale = 0.6f),
                ) {
                    FabButton(
                        Res.drawable.ic_speedometer,
                        stringResource(Res.string.speedtest_fab),
                        onOpenSpeedTest,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun FabButton(icon: DrawableResource, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.size(FAB_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(26.dp))
        }
    }
}
