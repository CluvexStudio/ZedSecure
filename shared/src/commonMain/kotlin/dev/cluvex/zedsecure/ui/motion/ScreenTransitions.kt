package dev.cluvex.zedsecure.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.cluvex.zedsecure.domain.model.ScreenTransition
import kotlin.math.abs

fun screenTransition(
    style: ScreenTransition,
    forward: Boolean,

    direction: Int = 1,

    reduceMotion: Boolean = false,
): ContentTransform {
    if (reduceMotion) return fadeIn(tween(120)) togetherWith fadeOut(tween(90))
    val sign = (if (forward) 1 else -1) * direction
    return when (style) {
        ScreenTransition.Fade -> fadeIn(tween(260)) togetherWith fadeOut(tween(180))

        ScreenTransition.Slide ->
            (slideInHorizontally(tween(320)) { w -> sign * w / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(320)) { w -> -sign * w / 3 } + fadeOut(tween(180)))

        ScreenTransition.Push ->
            (slideInHorizontally(tween(340)) { w -> sign * w } + fadeIn(tween(120))) togetherWith
                (slideOutHorizontally(tween(340)) { w -> -sign * w / 3 } + fadeOut(tween(340)))

        ScreenTransition.Depth -> if (forward) {
            (scaleIn(tween(300), initialScale = 0.88f) + fadeIn(tween(220))) togetherWith
                (scaleOut(tween(300), targetScale = 1.12f) + fadeOut(tween(180)))
        } else {
            (scaleIn(tween(300), initialScale = 1.12f) + fadeIn(tween(220))) togetherWith
                (scaleOut(tween(300), targetScale = 0.88f) + fadeOut(tween(180)))
        }

        ScreenTransition.Elastic ->
            (
                slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                ) { h -> h / 8 } +
                    scaleIn(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        initialScale = 0.9f,
                    ) + fadeIn(tween(180))
                ) togetherWith
                (scaleOut(tween(220), targetScale = 0.96f) + fadeOut(tween(160)))

        ScreenTransition.Curtain -> if (forward) {
            (slideInVertically(tween(360)) { h -> h / 2 } + fadeIn(tween(200))) togetherWith
                (scaleOut(tween(360), targetScale = 0.94f) + fadeOut(tween(260)))
        } else {
            (scaleIn(tween(360), initialScale = 0.94f) + fadeIn(tween(200))) togetherWith
                (slideOutVertically(tween(360)) { h -> h / 2 } + fadeOut(tween(260)))
        }

        ScreenTransition.Flip -> fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(160))
    }
}

@Composable
fun AnimatedVisibilityScope.transitionDecoration(
    style: ScreenTransition,
    forward: Boolean,
    direction: Int = 1,
    reduceMotion: Boolean = false,
): Modifier {
    if (style != ScreenTransition.Flip || reduceMotion) return Modifier
    val sign = (if (forward) 1 else -1) * direction
    val angle by transition.animateFloat(
        transitionSpec = { tween(360) },
        label = "flip-angle",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 78f * sign
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> -78f * sign
        }
    }
    return Modifier.graphicsLayer {
        rotationY = angle

        cameraDistance = 14f * density
        transformOrigin = TransformOrigin(if (sign > 0) 0.15f else 0.85f, 0.5f)

        alpha = 1f - (abs(angle) / 110f)
    }
}

@Composable
fun layoutSign(): Int = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
