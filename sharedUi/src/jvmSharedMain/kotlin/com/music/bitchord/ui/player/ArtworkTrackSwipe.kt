package com.music.bitchord.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Same direction and threshold as the classic player; attached only to artwork, never sliders. */
internal fun Modifier.artworkTrackSwipe(onNext: () -> Unit, onPrevious: () -> Unit): Modifier = composed {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    // The glass scene's elastic settle: underdamped, so the sleeve carries a
    // little past the mark and comes back — the limb of liquid glass rather
    // than a critically damped glide.
    val offset by animateFloatAsState(
        targetValue = drag * .35f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "artworkTrackSwipe",
    )
    this.graphicsLayer { translationX = offset }.pointerInput(threshold) {
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onDragCancel = { drag = 0f },
            onDragEnd = {
                val distance = drag
                drag = 0f
                when {
                    distance <= -threshold -> next()
                    distance >= threshold -> previous()
                }
            },
            onHorizontalDrag = { change, delta -> change.consume(); drag += delta },
        )
    }
}
