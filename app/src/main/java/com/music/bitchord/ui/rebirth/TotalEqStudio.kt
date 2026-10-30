package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.TotalEqConfig
import com.music.bitchord.playback.TotalEqDesign
import com.music.bitchord.playback.TotalFilterType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** A/B monitor and the combined, draggable response. Shared by every theme. */
@Composable
internal fun TotalEqStudioHeader(enabled: Boolean, glass: Boolean) {
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val original by TotalMusicSettings.compareOriginal.collectAsStateWithLifecycle()
    val rate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(config)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { TotalMusicSettings.setCompareOriginal(false) }
    }
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) TotalMusicSettings.setCompareOriginal(false)
    }
    var parametric by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableIntStateOf(0) }
    val coefficients = remember(config, rate) { TotalEqDesign.coefficients(config, rate) }
    val headroom = remember(config, rate, coefficients) { TotalEqDesign.headroom(config, coefficients, rate) }
    val response = remember(coefficients, config.preamp, headroom, rate) {
        List(192) { i ->
            val hz = 20.0 * 1000.0.pow(i / 191.0)
            (coefficients.sumOf { it.responseDb(hz, rate) } + config.preamp + headroom).toFloat()
        }
    }
    val frequencies = TotalEqDesign.activeFrequencies(config)
    val gains = TotalEqDesign.activeGains(config)
    var selectedBand by remember { mutableIntStateOf(0) }
    val description = stringResource(R.string.total_eq_curve_access)
    EqPanel(glass, stringResource(R.string.total_eq_studio_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = original, enabled = enabled, onClick = { TotalMusicSettings.setCompareOriginal(true) },
                label = { Text(stringResource(R.string.total_eq_ab_original)) })
            FilterChip(selected = !original, enabled = enabled, onClick = { TotalMusicSettings.setCompareOriginal(false) },
                label = { Text(stringResource(R.string.total_eq_ab_processed)) })
        }
        Text(
            stringResource(if (original) R.string.total_eq_ab_original_hint else R.string.total_eq_ab_processed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !parametric, onClick = { parametric = false }, label = { Text(stringResource(R.string.total_eq_curve_graphic)) })
            FilterChip(selected = parametric, onClick = { parametric = true }, label = { Text(stringResource(R.string.total_eq_parametric)) })
        }
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        val node = MaterialTheme.colorScheme.tertiary
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }
                .pointerInput(enabled, parametric, frequencies, config.filters) {
                    if (!enabled) return@pointerInput
                    fun xToHz(x: Float) = exp((x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * ln(1000f) + ln(20f))
                    fun yToDb(y: Float) = (24f - 48f * (y / size.height.coerceAtLeast(1))).coerceIn(-12f, 12f)
                    fun report(position: Offset) {
                        if (parametric) {
                            val filters = latest.filters
                            if (filters.isEmpty()) return
                            val index = selectedFilter.coerceIn(filters.indices)
                            val f = filters[index].copy(
                                frequency = xToHz(position.x).coerceIn(20f, 20000f),
                                gain = if (filters[index].type == TotalFilterType.LOW_PASS || filters[index].type == TotalFilterType.HIGH_PASS) filters[index].gain else yToDb(position.y),
                            )
                            TotalMusicSettings.update(latest.copy(filters = filters.toMutableList().also { it[index] = f }))
                        } else {
                            val hz = xToHz(position.x)
                            val index = frequencies.indices.minByOrNull { abs(ln(frequencies[it] / hz)) } ?: return
                            selectedBand = index
                            val gain = (yToDb(position.y) * 10).roundToInt() / 10f
                            TotalMusicSettings.update(if (latest.graphicMode == com.music.bitchord.playback.TotalGraphicMode.TEN)
                                latest.copy(tenBands = latest.tenBands.toMutableList().also { it[index] = gain })
                            else latest.copy(bands = latest.bands.toMutableList().also { it[index] = gain }))
                        }
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (parametric && latest.filters.isNotEmpty()) {
                                selectedFilter = latest.filters.indices.minByOrNull {
                                    abs(ln(latest.filters[it].frequency / xToHz(offset.x)))
                                } ?: 0
                            }
                            report(offset)
                        },
                        onDrag = { change, _ -> change.consume(); report(change.position) },
                    )
                },
        ) {
            val left = 0f
            for (i in 0..4) {
                val y = size.height * i / 4
                drawLine(grid, Offset(left, y), Offset(size.width, y), 1f)
            }
            listOf(20f, 100f, 1000f, 10000f, 20000f).forEach { hz ->
                val x = ln(hz / 20f) / ln(1000f) * size.width
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val path = Path()
            response.forEachIndexed { i, db ->
                val x = size.width * i / response.lastIndex
                val y = size.height * (24f - db.coerceIn(-24f, 24f)) / 48f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(2.5.dp.toPx()))
            if (!parametric) {
                frequencies.forEachIndexed { i, hz ->
                    val x = ln(hz / 20f) / ln(1000f) * size.width
                    val y = size.height * (24f - gains.getOrElse(i) { 0f }.coerceIn(-24f, 24f)) / 48f
                    drawCircle(if (i == selectedBand) line else node, radius = if (i == selectedBand) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x, y))
                }
            } else {
                config.filters.forEachIndexed { i, f ->
                    val x = ln(f.frequency / 20f) / ln(1000f) * size.width
                    val y = size.height * (24f - f.gain.coerceIn(-24f, 24f)) / 48f
                    drawCircle(if (i == selectedFilter) line else node, radius = 6.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("20 Hz", style = MaterialTheme.typography.labelSmall)
            Text("+24 / 0 / −24 dB", style = MaterialTheme.typography.labelSmall)
            Text("20 kHz", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            stringResource(R.string.total_eq_response, rate, headroom.toFloat()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(if (parametric) R.string.total_eq_curve_parametric_hint else R.string.total_eq_curve_graphic_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!config.autoHeadroom || !config.limiter) {
            Text(stringResource(R.string.total_eq_protection_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Factory preset keys are persisted profile names and must not change with the
 * UI language. Only their presentation is localized; custom names are shown as
 * the user saved them.
 */
@Composable
internal fun totalEqProfileLabel(name: String): String = when (name) {
    "Flat" -> stringResource(R.string.eq_preset_flat)
    "Bass Boost" -> stringResource(R.string.eq_preset_bass_boost)
    "Deep Bass" -> stringResource(R.string.total_eq_preset_deep_bass)
    "Rock" -> stringResource(R.string.eq_preset_rock)
    "Pop" -> stringResource(R.string.total_eq_preset_pop)
    "Jazz" -> stringResource(R.string.eq_preset_jazz)
    "Clásica" -> stringResource(R.string.eq_preset_classical)
    "Electrónica" -> stringResource(R.string.eq_preset_electronic)
    "Vocal" -> stringResource(R.string.eq_preset_vocal)
    "Acústica" -> stringResource(R.string.eq_preset_acoustic)
    "Hip-Hop" -> stringResource(R.string.eq_preset_hip_hop)
    else -> name
}

/** Custom profile load/rename/delete. Factory presets stay read-only. */
@Composable
internal fun TotalEqProfileRows(onLoad: (TotalEqConfig) -> Unit) {
    val profiles by TotalMusicSettings.profiles.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    profiles.forEach { (profileName, config) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(profileName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            androidx.compose.material3.TextButton(onClick = { onLoad(config) }) { Text(stringResource(R.string.total_eq_load)) }
            androidx.compose.material3.TextButton(onClick = { renaming = profileName; name = profileName }) { Text(stringResource(R.string.total_eq_rename_profile)) }
            androidx.compose.material3.TextButton(onClick = { deleting = profileName }) { Text(stringResource(R.string.rebirth_eq_delete_profile)) }
        }
    }
    renaming?.let { old ->
        RebirthDialog(style = com.music.bitchord.ui.player.PlayerVisualStyle.ORIGINAL,
            title = stringResource(R.string.total_eq_rename_profile), onDismiss = { renaming = null },
            confirmLabel = stringResource(R.string.save), confirmEnabled = name.isNotBlank(),
            onConfirm = { if (TotalMusicSettings.renameProfile(old, name)) renaming = null }) {
            RebirthTextField(value = name, onValueChange = { name = it.take(48) }, label = stringResource(R.string.total_eq_profile_name))
        }
    }
    deleting?.let { old ->
        RebirthDialog(style = com.music.bitchord.ui.player.PlayerVisualStyle.ORIGINAL,
            title = stringResource(R.string.rebirth_eq_delete_profile), onDismiss = { deleting = null },
            confirmLabel = stringResource(R.string.delete), onConfirm = { TotalMusicSettings.deleteProfile(old); deleting = null }) {
            Text(old)
        }
    }
}
