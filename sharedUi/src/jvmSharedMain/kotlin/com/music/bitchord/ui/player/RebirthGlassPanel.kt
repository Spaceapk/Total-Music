package com.music.bitchord.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun RebirthGlassPanel(
    shape: RoundedCornerShape,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(if (enabled) PlayerPlatform.host.glassPanelModifier(shape) else Modifier),
        shape = shape,
        color = if (enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}
