package com.music.bitchord.ui.rebirth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.player.PlayerVisualStyle

/** The four existing experiences, expressed through the original persisted preferences. */
internal enum class AppearanceTheme(val title: String, val style: PlayerVisualStyle, val expressive: Boolean) {
    GLASS("Liquid Glass", PlayerVisualStyle.ORIGINAL, false),
    GLASS_PLUS("Liquid Glass+", PlayerVisualStyle.LIQUID_GLASS_PLUS, false),
    GOOGLE("Google Expressive", PlayerVisualStyle.ORIGINAL, true),
    GOOGLE_PLUS("Google Expressive+", PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS, true);

    companion object {
        fun current(style: PlayerVisualStyle, expressive: Boolean): AppearanceTheme = when (style) {
            PlayerVisualStyle.LIQUID_GLASS_PLUS -> GLASS_PLUS
            PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> GOOGLE_PLUS
            PlayerVisualStyle.ORIGINAL -> if (expressive) GOOGLE else GLASS
        }
    }
}

@Composable
internal fun AppearanceThemePicker() {
    val style by AppSettings.playerVisualStyle.collectAsStateWithLifecycle()
    val expressive by TotalMusicSettings.expressive.collectAsStateWithLifecycle()
    val active = AppearanceTheme.current(style, expressive)
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.appearance_and_themes), style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints {
            // One column on narrow / large-font windows; two balanced columns otherwise.
            val columns = if (maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppearanceTheme.entries.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { entry ->
                            val selected = entry == active
                            val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            Surface(color = tint, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(22.dp)).semantics {
                                    this.selected = selected
                                    role = Role.RadioButton
                                }.clickable {
                                    TotalMusicSettings.setExpressive(entry.expressive)
                                    AppSettings.setPlayerVisualStyle(entry.style)
                                }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Stylized previews only: actual player compositions remain untouched.
                                    val glass = !entry.expressive
                                    Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(if (glass) 18.dp else 28.dp))
                                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = if (glass) .18f else .65f), MaterialTheme.colorScheme.tertiary.copy(alpha = .25f))))) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(if (glass) 6.dp else 12.dp)).background(Color.White.copy(alpha = .5f)))
                                            Box(Modifier.fillMaxWidth(.7f).height(5.dp).background(Color.White.copy(alpha = .6f)))
                                        }
                                    }
                                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                                    RadioButton(selected = selected, onClick = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
