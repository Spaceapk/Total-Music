package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors

/**
 * The rebirth settings home.
 *
 * It is a *front door*, not a replacement for the settings themselves: the
 * player style, the four effects and the equalizer are here because they are
 * what the two styles are about, and every other setting is one tap away
 * through the groups the app already has. That is deliberate — the alternative
 * the 1.7.0 prototype took, drawing five switches and then escaping to the old
 * screen with a back-looking button, is exactly the half-built screen this
 * change is meant to remove.
 *
 * [onSection] takes a string-resource id, or [REBIRTH_ALL_SETTINGS] for the
 * whole list; a null [style] is not allowed — Original never routes here.
 */

/** Opens every settings group, unfiltered. */
internal const val REBIRTH_ALL_SETTINGS = 0

@Composable
fun RebirthSettings(
    style: PlayerVisualStyle,
    padding: PaddingValues,
    onEqualizer: () -> Unit,
    onSection: (Int) -> Unit,
) {
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            if (style == PlayerVisualStyle.LIQUID_GLASS_PLUS) {
                GlassSettingsHome(padding = padding, onEqualizer = onEqualizer, onSection = onSection)
            } else {
                ExpressiveSettingsHome(padding = padding, onEqualizer = onEqualizer, onSection = onSection)
            }
        }
    }
}

@Composable
private fun ExpressiveSettingsHome(padding: PaddingValues, onEqualizer: () -> Unit, onSection: (Int) -> Unit) {
    val background by AppSettings.playerDynamicBackground.collectAsStateWithLifecycle()
    val glassEffects by AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val alternative by AppSettings.playerAlternativeControls.collectAsStateWithLifecycle()
    val motion by AppSettings.playerAnimatedTransitions.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.rebirth_settings_expressive_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        PlayerStylePicker()
        RebirthSectionHeader(title = stringResource(R.string.rebirth_visual_experience))
        EffectRow(
            title = stringResource(R.string.rebirth_effect_background),
            description = stringResource(R.string.rebirth_effect_background_hint),
            icon = Icons.Rounded.Gradient,
            checked = background,
        ) { AppSettings.setPlayerDynamicBackground(it) }
        EffectRow(
            title = stringResource(R.string.rebirth_effect_glass),
            description = stringResource(R.string.rebirth_effect_glass_hint),
            icon = Icons.Rounded.BlurOn,
            checked = glassEffects,
        ) { AppSettings.setPlayerGlassEffects(it) }
        EffectRow(
            title = stringResource(R.string.rebirth_effect_controls),
            description = stringResource(R.string.rebirth_effect_controls_hint),
            icon = Icons.Rounded.Animation,
            checked = alternative,
        ) { AppSettings.setPlayerAlternativeControls(it) }
        EffectRow(
            title = stringResource(R.string.rebirth_effect_motion),
            description = stringResource(R.string.rebirth_effect_motion_hint),
            icon = Icons.Rounded.Animation,
            checked = motion,
        ) { AppSettings.setPlayerAnimatedTransitions(it) }
        RebirthSectionHeader(title = stringResource(R.string.rebirth_sound))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onEqualizer),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.equalizer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.rebirth_equalizer_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
        RebirthSectionHeader(title = stringResource(R.string.settings))
        SettingsGroupRows(onSection = onSection, glass = false)
    }
}

@Composable
private fun GlassSettingsHome(padding: PaddingValues, onEqualizer: () -> Unit, onSection: (Int) -> Unit) {
    val background by AppSettings.playerDynamicBackground.collectAsStateWithLifecycle()
    val glassEffects by AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val alternative by AppSettings.playerAlternativeControls.collectAsStateWithLifecycle()
    val motion by AppSettings.playerAnimatedTransitions.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.rebirth_settings_glass_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.rebirth_visual_experience),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlayerStylePicker(glass = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassToggle(
                title = stringResource(R.string.rebirth_effect_background),
                description = stringResource(R.string.rebirth_effect_background_hint),
                checked = background,
            ) { AppSettings.setPlayerDynamicBackground(it) }
            GlassToggle(
                title = stringResource(R.string.rebirth_effect_glass),
                description = stringResource(R.string.rebirth_effect_glass_hint),
                checked = glassEffects,
            ) { AppSettings.setPlayerGlassEffects(it) }
            GlassToggle(
                title = stringResource(R.string.rebirth_effect_controls),
                description = stringResource(R.string.rebirth_effect_controls_hint),
                checked = alternative,
            ) { AppSettings.setPlayerAlternativeControls(it) }
            GlassToggle(
                title = stringResource(R.string.rebirth_effect_motion),
                description = stringResource(R.string.rebirth_effect_motion_hint),
                checked = motion,
            ) { AppSettings.setPlayerAnimatedTransitions(it) }
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onEqualizer),
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.equalizer), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.rebirth_equalizer_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
        SettingsGroupRows(onSection = onSection, glass = true)
    }
}

/**
 * Which player the app draws with.
 *
 * The three names are product names and stay untranslated, as they do in the
 * settings row that has offered them since 1.7.0.
 */
@Composable
private fun PlayerStylePicker(glass: Boolean = false) {
    AppearanceThemePicker()
}

private fun PlayerVisualStyle.label(): String = when (this) {
    PlayerVisualStyle.ORIGINAL -> "Original"
    PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> "Google Expressive+"
    PlayerVisualStyle.LIQUID_GLASS_PLUS -> "Liquid Glass+"
}

@Composable
private fun SettingsGroupRows(onSection: (Int) -> Unit, glass: Boolean) {
    val groups = listOf(
        R.string.playback,
        R.string.audio_quality,
        R.string.downloads,
        R.string.appearance,
        R.string.performance,
        R.string.local_music,
        R.string.storage,
        R.string.your_data,
        R.string.miscellaneous,
        R.string.language,
        R.string.advanced_options,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { group ->
            Surface(
                shape = RoundedCornerShape(if (glass) 18.dp else 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.55f else 0.9f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (glass) 18.dp else 22.dp))
                    .clickable { onSection(group) },
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(group),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(if (glass) 18.dp else 22.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (glass) 18.dp else 22.dp))
                .clickable { onSection(REBIRTH_ALL_SETTINGS) },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.rebirth_all_settings),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun EffectRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun GlassToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
