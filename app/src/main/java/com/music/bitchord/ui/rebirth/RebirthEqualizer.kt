package com.music.bitchord.ui.rebirth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.TotalEqConfig
import com.music.bitchord.playback.TotalEqDesign
import com.music.bitchord.playback.TotalGraphicMode
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors
import com.music.bitchord.ui.screens.BandFader
import java.util.Locale

/**
 * The equalizer, in both rebirth styles.
 *
 * Same controls either way — Total EQ's ten or thirty-one graphic bands, the
 * dynamic and manual modes, presets, saved profiles, preamp, limiter and the
 * advanced page — arranged by the style. The expressive screen is a stack of
 * editorial cards with the curve at full width; the glass one is a compact
 * console, with the band strip in its own frosted panel and the mode as a
 * segmented row.
 *
 * The DSP is untouched: every control writes through [AppSettings] and
 * [TotalMusicSettings], exactly as the original screen does.
 */
@Composable
fun RebirthEqualizer(style: PlayerVisualStyle, padding: PaddingValues) {
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    val enabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val mode by AppSettings.equalizerMode.collectAsStateWithLifecycle()
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val profiles by TotalMusicSettings.profiles.collectAsStateWithLifecycle()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            if (glass) {
                GlassEqualizer(
                    padding = padding,
                    enabled = enabled,
                    mode = mode,
                    config = config,
                    profiles = profiles,
                    advanced = advanced,
                    onAdvanced = { advanced = !advanced },
                    onSaveProfile = { saving = true },
                    dspAvailable = outputStatus.dspAvailable,
                )
            } else {
                ExpressiveEqualizer(
                    padding = padding,
                    enabled = enabled,
                    mode = mode,
                    config = config,
                    profiles = profiles,
                    advanced = advanced,
                    onAdvanced = { advanced = !advanced },
                    onSaveProfile = { saving = true },
                    dspAvailable = outputStatus.dspAvailable,
                )
            }
        }
        if (saving) {
            RebirthDialog(
                style = style,
                title = stringResource(R.string.rebirth_eq_save_profile),
                onDismiss = { saving = false },
                confirmLabel = stringResource(R.string.save),
                confirmEnabled = profileName.isNotBlank(),
                onConfirm = {
                    TotalMusicSettings.saveProfile(profileName.trim())
                    profileName = ""
                    saving = false
                },
            ) {
                RebirthTextField(
                    value = profileName,
                    onValueChange = { profileName = it.take(48) },
                    label = stringResource(R.string.total_eq_profile_name),
                )
                Text(
                    text = stringResource(R.string.rebirth_eq_saved_profile_hint),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpressiveEqualizer(
    padding: PaddingValues,
    enabled: Boolean,
    mode: EqualizerMode,
    config: TotalEqConfig,
    profiles: Map<String, TotalEqConfig>,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    onSaveProfile: () -> Unit,
    dspAvailable: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.rebirth_eq_editorial),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        EnableRow(enabled = enabled, onToggle = AppSettings::setEqualizerEnabled)
        ModeRow(mode = mode, glass = false)
        when (mode) {
            EqualizerMode.DYNAMIC -> DynamicCard(enabled)
            EqualizerMode.MANUAL -> ManualCard(enabled)
            EqualizerMode.TOTAL -> TotalCard(
                config = config,
                enabled = enabled,
                profiles = profiles,
                advanced = advanced,
                onAdvanced = onAdvanced,
                onSaveProfile = onSaveProfile,
            )
        }
        if (advanced) {
            RebirthEqAdvanced(false, enabled)
        }
        OutputStatusNotice(dspAvailable, glass = false)
        SystemPanelButton()
        SystemPanelNotice(glass = false)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GlassEqualizer(
    padding: PaddingValues,
    enabled: Boolean,
    mode: EqualizerMode,
    config: TotalEqConfig,
    profiles: Map<String, TotalEqConfig>,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    onSaveProfile: () -> Unit,
    dspAvailable: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.rebirth_eq_studio),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.total_eq_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        EnableRow(enabled = enabled, onToggle = AppSettings::setEqualizerEnabled)
        ModeRow(mode = mode, glass = true)
        when (mode) {
            EqualizerMode.DYNAMIC -> DynamicCard(enabled, glass = true)
            EqualizerMode.MANUAL -> ManualCard(enabled, glass = true)
            EqualizerMode.TOTAL -> TotalCard(
                config = config,
                enabled = enabled,
                profiles = profiles,
                advanced = advanced,
                onAdvanced = onAdvanced,
                onSaveProfile = onSaveProfile,
                glass = true,
            )
        }
        if (advanced) {
            RebirthEqAdvanced(true, enabled)
        }
        OutputStatusNotice(dspAvailable, glass = true)
        SystemPanelButton(glass = true)
        SystemPanelNotice(glass = true)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EnableRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.total_eq_name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ModeRow(mode: EqualizerMode, glass: Boolean) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EqualizerMode.entries.forEach { entry ->
            FilterChip(
                selected = mode == entry,
                onClick = { AppSettings.setEqualizerMode(entry) },
                label = {
                    Text(
                        text = stringResource(
                            when (entry) {
                                EqualizerMode.TOTAL -> R.string.total_eq_name
                                EqualizerMode.MANUAL -> R.string.equalizer_manual
                                EqualizerMode.DYNAMIC -> R.string.equalizer_dynamic
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
    if (!glass) Spacer(Modifier.height(2.dp))
}

@Composable
private fun DynamicCard(enabled: Boolean, glass: Boolean = false) {
    val tilt by AppSettings.equalizerToneX.collectAsStateWithLifecycle()
    val contour by AppSettings.equalizerToneY.collectAsStateWithLifecycle()
    val focused by AppSettings.equalizerFocused.collectAsStateWithLifecycle()
    EqCard(title = stringResource(R.string.rebirth_eq_dynamic_title), glass = glass) {
        ValueRow(
            label = stringResource(R.string.equalizer_tilt),
            value = tilt.toFloat(),
            range = -5f..5f,
            enabled = enabled,
            onChange = { AppSettings.setEqualizerTone(it.toInt(), contour) },
        )
        ValueRow(
            label = stringResource(R.string.equalizer_contour),
            value = contour.toFloat(),
            range = -5f..5f,
            enabled = enabled,
            onChange = { AppSettings.setEqualizerTone(tilt, it.toInt()) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.equalizer_focused),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = focused, onCheckedChange = AppSettings::setEqualizerFocused, enabled = enabled)
        }
    }
}

@Composable
private fun ManualCard(enabled: Boolean, glass: Boolean = false) {
    val balance by AppSettings.equalizerBalance.collectAsStateWithLifecycle()
    val bands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val latest by remember(bands) { mutableStateOf(bands) }
    EqCard(title = stringResource(R.string.rebirth_eq_manual_title), glass = glass) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach { preset ->
                OutlinedButton(onClick = { AppSettings.setEqualizerPreset(preset) }, enabled = enabled) {
                    Text(preset.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        bands.forEachIndexed { index, gain ->
            ValueRow(
                label = stringResource(R.string.rebirth_eq_band, index + 1),
                value = gain,
                range = -12f..12f,
                enabled = enabled,
                onChange = { value ->
                    AppSettings.setEqualizerBands(latest.toMutableList().also { it[index] = value })
                },
            )
        }
        ValueRow(
            label = stringResource(R.string.equalizer_balance),
            value = balance,
            range = -1f..1f,
            enabled = enabled,
            onChange = AppSettings::setEqualizerBalance,
        )
        TextButton(onClick = { AppSettings.setEqualizerPreset(EqualizerPreset.FLAT) }, enabled = enabled) {
            Text(stringResource(R.string.equalizer_reset))
        }
    }
}

@Composable
private fun TotalCard(
    config: TotalEqConfig,
    enabled: Boolean,
    profiles: Map<String, TotalEqConfig>,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    onSaveProfile: () -> Unit,
    glass: Boolean = false,
) {
    val sampleRate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    var presetsOpen by remember { mutableStateOf(false) }
    val latest = config
    EqCard(title = stringResource(R.string.total_eq_name), glass = glass) {
        TotalEqStudioHeader(enabled = enabled, glass = glass)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TotalGraphicMode.entries.forEach { graphic ->
                FilterChip(
                    selected = config.graphicMode == graphic,
                    onClick = {
                        TotalMusicSettings.update(latest.copy(graphicMode = graphic))
                        AppSettings.setEqualizerMode(EqualizerMode.TOTAL)
                    },
                    label = {
                        Text(
                            stringResource(
                                if (graphic == TotalGraphicMode.TEN) {
                                    R.string.total_eq_10_bands
                                } else {
                                    R.string.total_eq_31_bands
                                },
                            ),
                        )
                    },
                )
            }
        }
        Box {
            OutlinedButton(onClick = { presetsOpen = true }) {
                Text(stringResource(R.string.rebirth_eq_profiles))
            }
            DropdownMenu(presetsOpen, onDismissRequest = { presetsOpen = false }) {
                (TotalMusicSettings.factoryProfiles + profiles).forEach { (name, preset) ->
                    DropdownMenuItem(
                        text = { Text(totalEqProfileLabel(name)) },
                        onClick = {
                            TotalMusicSettings.update(preset.copy(graphicMode = latest.graphicMode))
                            AppSettings.setEqualizerMode(EqualizerMode.TOTAL)
                            presetsOpen = false
                        },
                    )
                }
            }
        }
        BandStrip(config = config, enabled = enabled, glass = glass)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveProfile) {
                Text(stringResource(R.string.rebirth_eq_save_profile), maxLines = 2)
            }
            OutlinedButton(
                onClick = { TotalMusicSettings.update(TotalEqConfig(graphicMode = latest.graphicMode)) },
            ) {
                Text(stringResource(R.string.rebirth_eq_reset_curve), maxLines = 2)
            }
        }
        TotalEqProfileRows { preset ->
            TotalMusicSettings.update(preset.copy(graphicMode = latest.graphicMode))
        }
        ValueRow(
            label = stringResource(R.string.total_eq_preamp),
            value = config.preamp,
            range = -24f..12f,
            enabled = enabled,
            onChange = { TotalMusicSettings.update(latest.copy(preamp = it)) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.total_eq_limiter),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = config.limiter,
                onCheckedChange = { TotalMusicSettings.update(latest.copy(limiter = it)) },
            )
        }
        TextButton(onClick = onAdvanced) {
            Text(
                stringResource(
                    if (advanced) R.string.rebirth_eq_advanced_hide else R.string.rebirth_eq_advanced_show,
                ),
            )
        }
    }
}

/**
 * The graphic bands.
 *
 * One strip, shared by both styles because it is a control and not a screen:
 * the expressive page gives it the full width under its card, the glass page
 * gives it a panel of its own. Gains are written through the same
 * [TotalMusicSettings.update] the original screen uses, and a band above
 * Nyquist is drawn disabled rather than silently dropped.
 */
@Composable
private fun BandStrip(config: TotalEqConfig, enabled: Boolean, glass: Boolean) {
    val sampleRate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    val latest by remember(config) { mutableStateOf(config) }
    // "+12.0" and "60" have to fit above and below a fader. At the default
    // font scale 44dp does; at 200 % it does not, so the strip is widened by
    // the same factor the text grew. The row scrolls anyway, so the extra
    // width costs nothing but finger travel.
    val bandWidth = (
        (if (glass) 44f else 48f) * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    ).dp
    Surface(
        shape = RoundedCornerShape(if (glass) 20.dp else 30.dp),
        color = if (glass) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (glass) 6.dp else 10.dp),
        ) {
            val frequencies = TotalEqDesign.activeFrequencies(latest)
            val gains = TotalEqDesign.activeGains(latest)
            frequencies.forEachIndexed { index, hz ->
                val active = enabled && hz < sampleRate * 0.45f
                val gain = gains.getOrElse(index) { 0f }
                fun change(value: Float) {
                    AppSettings.setEqualizerMode(EqualizerMode.TOTAL)
                    TotalMusicSettings.update(
                        if (latest.graphicMode == TotalGraphicMode.TEN) {
                            latest.copy(tenBands = latest.tenBands.toMutableList().also { it[index] = value })
                        } else {
                            latest.copy(bands = latest.bands.toMutableList().also { it[index] = value })
                        },
                    )
                }
                Column(
                    Modifier.width(bandWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%+.1f", gain),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                    BandFader(
                        value = gain,
                        enabled = active,
                        modifier = Modifier
                            .width(bandWidth)
                            .height(if (glass) 170.dp else 190.dp)
                            .semantics {
                                contentDescription = "${hz.toInt()} Hz"
                                progressBarRangeInfo = ProgressBarRangeInfo(gain, -12f..12f)
                                if (!active) disabled()
                                setProgress { target ->
                                    if (!active) {
                                        false
                                    } else {
                                        change(target.coerceIn(-12f, 12f))
                                        true
                                    }
                                }
                            },
                        allowHorizontalScroll = true,
                        onChange = ::change,
                    )
                    Text(
                        text = if (hz >= 1000f) "${(hz / 1000f).toInt()}k" else hz.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqCard(title: String, glass: Boolean, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(if (glass) 20.dp else 30.dp),
        color = if (glass) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = if (glass) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun ValueRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = String.format(Locale.getDefault(), "%+.1f", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
        )
    }
}

/** Opens the platform's own audio-effect panel, where the device has one. */
@Composable
private fun OutputStatusNotice(dspAvailable: Boolean, glass: Boolean) {
    if (dspAvailable) return
    EqPanel(glass, stringResource(R.string.equalizer_bitstream_title)) {
        Text(
            stringResource(R.string.equalizer_bitstream_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.equalizer_bitstream_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SystemPanelNotice(glass: Boolean) {
    Text(
        stringResource(R.string.system_equalizer_footer),
        modifier = Modifier.padding(horizontal = if (glass) 4.dp else 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SystemPanelButton(glass: Boolean = false) {
    val context = LocalContext.current
    val message = stringResource(R.string.rebirth_eq_system_panel_missing)
    Button(
        onClick = {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, AppSettings.audioSessionId.value)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        shape = RoundedCornerShape(if (glass) 18.dp else 26.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.rebirth_eq_system_panel), maxLines = 2)
    }
}

/** The preset names are the app's own, and stay out of the layouts above. */
@Composable
private fun EqualizerPreset.label(): String = stringResource(
    when (this) {
        EqualizerPreset.FLAT -> R.string.eq_preset_flat
        EqualizerPreset.ACOUSTIC -> R.string.eq_preset_acoustic
        EqualizerPreset.BASS_BOOST -> R.string.eq_preset_bass_boost
        EqualizerPreset.BASS_CUT -> R.string.eq_preset_bass_cut
        EqualizerPreset.CLASSICAL -> R.string.eq_preset_classical
        EqualizerPreset.ELECTRONIC -> R.string.eq_preset_electronic
        EqualizerPreset.HIP_HOP -> R.string.eq_preset_hip_hop
        EqualizerPreset.JAZZ -> R.string.eq_preset_jazz
        EqualizerPreset.LATE_NIGHT -> R.string.eq_preset_late_night
        EqualizerPreset.LOUDNESS -> R.string.eq_preset_loudness
        EqualizerPreset.ROCK -> R.string.eq_preset_rock
        EqualizerPreset.SMALL_SPEAKERS -> R.string.eq_preset_small_speakers
        EqualizerPreset.SPOKEN_WORD -> R.string.eq_preset_spoken_word
        EqualizerPreset.TREBLE_BOOST -> R.string.eq_preset_treble_boost
        EqualizerPreset.TREBLE_CUT -> R.string.eq_preset_treble_cut
        EqualizerPreset.VOCAL -> R.string.eq_preset_vocal
        EqualizerPreset.CUSTOM -> R.string.eq_preset_custom
    },
)
