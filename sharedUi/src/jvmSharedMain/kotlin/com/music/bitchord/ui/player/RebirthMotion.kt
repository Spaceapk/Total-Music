package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Motion primitives shared by the two Plus players.
 *
 * The two scenes draw the same behaviour and differ in how it is timed and
 * how far it is taken. What both answer the same way is *whether* there is
 * motion at all: the player's "Animated transitions" switch, and the app's
 * "Reduce animation" accessibility setting. Everything here collapses to its
 * final shape when either is off, exactly once, at the composition site.
 */

/** The decelerate half of Material 3's emphasized curve — for things arriving. */
internal val RebirthEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** The accelerate half — for things leaving. */
internal val RebirthEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** The M3 way: settles without overshoot. */
internal fun rebirthSettleSpring(): AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Platform accessibility motion reduction, provided by the Android host. */
val LocalMotionReduced = staticCompositionLocalOf { false }

/** Whether the player's motion is on at all: its switch, the app's and the system's. */
@Composable
internal fun rememberRebirthMotionEnabled(): Boolean {
    val animated by PlayerSettings.animatedTransitions.collectAsStateWithLifecycle()
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    return animated && !reduced && !LocalMotionReduced.current
}

/**
 * One piece of the scene's entrance, arriving [delayMillis] after the player
 * opens. [elastic] overshoots slightly on arrival — the glass scene's entry —
 * otherwise it is a plain M3 decelerate slide-and-fade.
 */
@Composable
internal fun RebirthEnter(
    delayMillis: Int,
    motion: Boolean,
    modifier: Modifier = Modifier,
    elastic: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!motion) {
        androidx.compose.foundation.layout.Box(modifier) { content() }
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(
            tween(durationMillis = 340, delayMillis = delayMillis, easing = RebirthEmphasizedDecelerate),
        ) + slideInVertically(
            tween(durationMillis = 440, delayMillis = delayMillis, easing = RebirthEmphasizedDecelerate),
        ) { it / 6 },
        exit = fadeOut(tween(durationMillis = 120)),
    ) {
        if (elastic) {
            val overshoot by animateFloatAsState(
                targetValue = if (shown) 1f else 0.92f,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow),
            )
            androidx.compose.foundation.layout.Box(
                Modifier.graphicsLayer {
                    scaleX = overshoot
                    scaleY = overshoot
                },
            ) { content() }
        } else {
            content()
        }
    }
}

/**
 * Press feedback for a surface the finger can push.
 *
 * [elastic] is the glass scene's answer — a squashy settle with a small
 * overshoot and a touch more width than height, so a panel reads as a slab of
 * soft glass rather than a button. The expressive scene asks for `false`:
 * same mechanism, critically damped, the M3 way.
 */
@Composable
internal fun Modifier.rebirthPress(
    elastic: Boolean = false,
    pressedScale: Float = if (elastic) 0.90f else 0.94f,
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val spec: AnimationSpec<Float> = if (elastic) {
        spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
    } else {
        rebirthSettleSpring()
    }
    val animatedScaleX by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spec,
    )
    val animatedScaleY by animateFloatAsState(
        targetValue = if (pressed) pressedScale * 0.97f else 1f,
        animationSpec = spec,
    )
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val mine = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!mine.pressed) break
                    }
                } finally {
                    pressed = false
                }
            }
        }
        .graphicsLayer {
            scaleX = animatedScaleX
            scaleY = animatedScaleY
        }
}

/** The artwork's play/pause emphasis both scenes draw — tighter at rest. */
@Composable
internal fun rebirthArtworkScale(playing: Boolean, motion: Boolean, elastic: Boolean): Float {
    val target = if (playing || !motion) 1f else 0.92f
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motion) {
            spring(
                dampingRatio = if (elastic) 0.6f else 0.74f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            androidx.compose.animation.core.snap()
        },
    )
    return value
}

/**
 * The page transition — the movement between the player, the queue and the
 * lyrics that the "Animated transitions" switch promises. A fade with a short
 * vertical slide, emphasized decelerate in, emphasized accelerate out. With
 * motion off the page swaps in place.
 */
internal fun rebirthPageTransition(motion: Boolean): androidx.compose.animation.ContentTransform =
    if (motion) {
        fadeIn(tween(durationMillis = 240, easing = RebirthEmphasizedDecelerate)) +
            slideInVertically(
                tween(durationMillis = 300, easing = RebirthEmphasizedDecelerate),
            ) { it / 14 } togetherWith
            fadeOut(tween(durationMillis = 160, easing = RebirthEmphasizedAccelerate)) +
            slideOutVertically(
                tween(durationMillis = 200, easing = RebirthEmphasizedAccelerate),
            ) { -it / 18 }
    } else {
        EnterTransition.None togetherWith ExitTransition.None
    }
