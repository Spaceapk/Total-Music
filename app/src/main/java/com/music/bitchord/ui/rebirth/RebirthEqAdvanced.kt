package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.TotalEqConfig
import com.music.bitchord.playback.TotalEqDesign
import com.music.bitchord.playback.TotalFilter
import com.music.bitchord.playback.TotalFilterType
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The part of the equalizer that is about the curve rather than the bands:
 * the combined response, the tone and stereo controls, and the parametric
 * filters.
 *
 * Every control writes to [TotalMusicSettings] and nothing here owns audio
 * state — the same contract [com.music.bitchord.ui.screens.EqualizerScreen]
 * keeps, because both screens are allowed to be open on the same session.
 * [glass] only picks corner radii and the panel fill.
 */
@Composable
internal fun RebirthEqAdvanced(glass: Boolean, enabled: Boolean) {
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(config)
    val rate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    val headroom = remember(config, rate) {
        TotalEqDesign.headroom(config, TotalEqDesign.coefficients(config, rate), rate)
    }
    val response = remember(config, rate) {
        val coefficients = TotalEqDesign.coefficients(config, rate)
        val trim = TotalEqDesign.headroom(config, coefficients, rate)
        List(160) { index ->
            val hz = 20.0 * 1000.0.pow(index / 159.0)
            (coefficients.sumOf { it.responseDb(hz, rate) } + config.preamp + trim).toFloat()
        }
    }
    EqPanel(glass, stringResource(R.string.total_eq_response_chart)) {
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            for (i in 0..4) {
                val y = size.height * i / 4
                drawLine(grid, Offset(0f, y), Offset(size.width, y))
            }
            val path = Path()
            response.forEachIndexed { index, gain ->
                val x = size.width * index / response.lastIndex
                val y = size.height * (24f - gain.coerceIn(-24f, 24f)) / 48f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(2.dp.toPx()))
        }
        Text(
            text = stringResource(R.string.total_eq_response, rate, headroom.toFloat()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.rebirth_eq_axis_low), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.rebirth_eq_axis_mid), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.rebirth_eq_axis_high), style = MaterialTheme.typography.labelSmall)
        }
        ToggleControl(stringResource(R.string.total_eq_headroom), config.autoHeadroom, enabled) {
            TotalMusicSettings.update(latest.copy(autoHeadroom = it))
        }
        ToggleControl(stringResource(R.string.total_eq_limiter), config.limiter, enabled) {
            TotalMusicSettings.update(latest.copy(limiter = it))
        }
    }
    EqPanel(glass, stringResource(R.string.total_eq_effects)) {
        ValueControl(stringResource(R.string.total_eq_bass), config.bass, -12f..12f, enabled) {
            TotalMusicSettings.update(latest.copy(bass = it))
        }
        ValueControl(stringResource(R.string.total_eq_treble), config.treble, -12f..12f, enabled) {
            TotalMusicSettings.update(latest.copy(treble = it))
        }
        ValueControl(stringResource(R.string.equalizer_balance), config.balance, -1f..1f, enabled) {
            TotalMusicSettings.update(latest.copy(balance = it))
        }
        ValueControl(stringResource(R.string.total_eq_width), config.width, 0f..2f, enabled) {
            TotalMusicSettings.update(latest.copy(width = it))
        }
        ValueControl(stringResource(R.string.total_eq_crossfeed), config.crossfeed, 0f..0.5f, enabled) {
            TotalMusicSettings.update(latest.copy(crossfeed = it))
        }
        Text(
            text = stringResource(R.string.total_eq_effects_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    EqPanel(glass, stringResource(R.string.total_eq_parametric)) {
        config.filters.forEachIndexed { index, filter ->
            FilterEditor(
                index = index,
                filter = filter,
                config = config,
                enabled = enabled,
                glass = glass,
                onChange = { TotalMusicSettings.update(it) },
            )
        }
        OutlinedButton(
            onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters + TotalFilter())) },
            enabled = enabled && config.filters.size < 8,
            shape = RoundedCornerShape(if (glass) 16.dp else 24.dp),
        ) {
            Text(stringResource(R.string.total_eq_add_filter), maxLines = 2)
        }
    }
}

@Composable
private fun FilterEditor(
    index: Int,
    filter: TotalFilter,
    config: TotalEqConfig,
    enabled: Boolean,
    glass: Boolean,
    onChange: (TotalEqConfig) -> Unit,
) {
    val latest by rememberUpdatedState(config)
    fun change(value: TotalFilter) {
        onChange(latest.copy(filters = latest.filters.toMutableList().also { it[index] = value }))
    }
    Surface(
        shape = RoundedCornerShape(if (glass) 18.dp else 26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.5f else 0.9f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleControl(stringResource(R.string.rebirth_eq_filter, index + 1), filter.enabled, enabled) {
                change(latest.filters[index].copy(enabled = it))
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TotalFilterType.entries.forEach { type ->
                    FilterChip(
                        selected = type == filter.type,
                        onClick = { change(latest.filters[index].copy(type = type)) },
                        enabled = enabled,
                        label = { Text(type.rebirthLabel(), maxLines = 1) },
                    )
                }
            }
            ValueControl(
                title = stringResource(R.string.total_eq_frequency),
                value = ln(filter.frequency),
                range = ln(20f)..ln(20000f),
                enabled = enabled,
                readout = "${filter.frequency.roundToInt()} Hz",
            ) { change(latest.filters[index].copy(frequency = exp(it))) }
            if (filter.type != TotalFilterType.LOW_PASS && filter.type != TotalFilterType.HIGH_PASS) {
                ValueControl(stringResource(R.string.total_eq_gain), filter.gain, -12f..12f, enabled) {
                    change(latest.filters[index].copy(gain = it))
                }
            }
            ValueControl(stringResource(R.string.rebirth_eq_q), filter.q, 0.2f..10f, enabled) {
                change(latest.filters[index].copy(q = it))
            }
            TextButton(
                enabled = enabled,
                onClick = {
                    onChange(latest.copy(filters = latest.filters.filterIndexed { i, _ -> i != index }))
                },
            ) {
                Text(stringResource(R.string.total_eq_remove_filter))
            }
        }
    }
}

/** One panel, in either material. */
@Composable
internal fun EqPanel(glass: Boolean, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(if (glass) 20.dp else 30.dp),
        color = if (glass) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun ValueControl(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    readout: String? = null,
    change: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = readout ?: String.format(Locale.getDefault(), "%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = change,
            enabled = enabled,
            valueRange = range,
        )
    }
}

@Composable
internal fun ToggleControl(title: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

/** The filter names, in the same words the original panel uses. */
@Composable
private fun TotalFilterType.rebirthLabel(): String = stringResource(
    when (this) {
        TotalFilterType.PEAK -> R.string.total_eq_peak
        TotalFilterType.LOW_SHELF -> R.string.total_eq_low_shelf
        TotalFilterType.HIGH_SHELF -> R.string.total_eq_high_shelf
        TotalFilterType.LOW_PASS -> R.string.total_eq_low_pass
        TotalFilterType.HIGH_PASS -> R.string.total_eq_high_pass
    },
)
