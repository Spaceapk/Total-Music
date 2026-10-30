package com.music.bitchord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.*
import kotlin.math.*
import java.util.Locale

@Composable
internal fun TotalEqControls(enabled: Boolean) {
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val profiles by TotalMusicSettings.profiles.collectAsStateWithLifecycle()
    val rate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    var presets by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var selectedBand by remember { mutableIntStateOf(15) }
    var advanced by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(config)
    val allProfiles = TotalMusicSettings.factoryProfiles.mapValues { (_, c) -> c.copy(graphicMode = config.graphicMode) }
    val frequencies = TotalEqDesign.activeFrequencies(config)
    val gains = TotalEqDesign.activeGains(config)
    val selected = profiles.entries.firstOrNull { it.value == config }?.key
        ?: allProfiles.entries.firstOrNull { it.value == config }?.key
        ?: stringResource(R.string.total_eq_custom)

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Total EQ", style = MaterialTheme.typography.headlineLarge)
        com.music.bitchord.ui.rebirth.TotalEqStudioHeader(enabled = enabled, glass = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalGraphicMode.entries.forEach { mode ->
                FilterChip(selected = config.graphicMode == mode, enabled = enabled,
                    onClick = { selectedBand = 0; TotalMusicSettings.update(latest.copy(graphicMode = mode)) },
                    label = { Text(stringResource(if (mode == TotalGraphicMode.TEN) R.string.total_eq_10_bands else R.string.total_eq_31_bands)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { presets = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(selected, maxLines = 1) }
                DropdownMenu(expanded = presets, onDismissRequest = { presets = false }, modifier = Modifier.heightIn(max = 380.dp)) {
                    allProfiles.forEach { (label, c) -> DropdownMenuItem(text = { Text(label) }, onClick = { TotalMusicSettings.update(c); selectedBand = 0; presets = false }) }
                    if (profiles.isNotEmpty()) HorizontalDivider()
                    profiles.forEach { (label, c) -> DropdownMenuItem(text = { Text(label) }, onClick = { TotalMusicSettings.update(c); selectedBand = 0; presets = false }) }
                }
            }
            FilledTonalButton(onClick = { name = ""; saving = true }, enabled = enabled) { Text(stringResource(R.string.total_eq_save)) }
        }
        com.music.bitchord.ui.rebirth.TotalEqProfileRows { preset ->
            TotalMusicSettings.update(preset.copy(graphicMode = latest.graphicMode)); selectedBand = 0
        }
        Text(stringResource(if (config.graphicMode == TotalGraphicMode.TEN) R.string.total_eq_10_bands else R.string.total_eq_31_bands), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.total_eq_drag_bands), style = MaterialTheme.typography.bodySmall)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.height(198.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("+12", style = MaterialTheme.typography.labelSmall)
                Text("0 dB", style = MaterialTheme.typography.labelSmall)
                Text("−12", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                frequencies.forEachIndexed { index, hz ->
                    val value = gains[index]
                    val active = enabled && hz < rate * 0.45f
                    val label = if (hz >= 1000) "${(hz / 1000).toString().removeSuffix(".0")}k" else hz.toString().removeSuffix(".0")
                    val frequencyLabel = "${hz} Hz"
                    val gainLabel = String.format(Locale.getDefault(), "%+.1f dB", value)
                    fun change(gain: Float) {
                        selectedBand = index
                        TotalMusicSettings.update(if (latest.graphicMode == TotalGraphicMode.TEN)
                            latest.copy(tenBands = latest.tenBands.toMutableList().also { it[index] = gain })
                        else latest.copy(bands = latest.bands.toMutableList().also { it[index] = gain }))
                    }
                    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.getDefault(), "%+.1f", value), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        BandFader(value = value, enabled = active, allowHorizontalScroll = true, modifier = Modifier.width(48.dp).semantics {
                            contentDescription = frequencyLabel
                            stateDescription = gainLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(value, -12f..12f, 47)
                            if (!active) disabled()
                            setProgress { requested ->
                                if (active) { change((requested * 2).roundToInt().div(2f).coerceIn(-12f, 12f)); true } else false
                            }
                        }, onChange = ::change)
                        Spacer(Modifier.height(8.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        }
        TextButton(onClick = { TotalMusicSettings.update(if (latest.graphicMode == TotalGraphicMode.TEN) latest.copy(tenBands = List(10) { 0f })
                else latest.copy(bands = List(31) { 0f })) }, enabled = enabled) {
            Text(stringResource(R.string.equalizer_reset))
        }
        EqValueSlider(stringResource(R.string.total_eq_preamp), config.preamp, -24f..12f, enabled) { TotalMusicSettings.update(latest.copy(preamp = it)) }
        EqToggle(stringResource(R.string.total_eq_headroom), config.autoHeadroom, enabled) { TotalMusicSettings.update(latest.copy(autoHeadroom = it)) }
        EqToggle(stringResource(R.string.total_eq_limiter), config.limiter, enabled) { TotalMusicSettings.update(latest.copy(limiter = it)) }
        OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (advanced) R.string.total_eq_hide_advanced else R.string.total_eq_show_advanced))
        }
        if (advanced) {
            val coefficients = remember(config, rate) { TotalEqDesign.coefficients(config, rate) }
            val headroom = remember(config, rate) { TotalEqDesign.headroom(config, coefficients, rate) }
            val response = remember(config, rate) { List(256) { i ->
                val hz = 20.0 * 1000.0.pow(i / 255.0)
                (coefficients.sumOf { it.responseDb(hz, rate) } + config.preamp + headroom).toFloat()
            } }
            val line = MaterialTheme.colorScheme.primary
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(100.dp).pointerInput(frequencies) {
                detectTapGestures { offset -> selectedBand = frequencies.indices.minByOrNull { abs(ln(frequencies[it] / 20f) / ln(1000f) - offset.x / size.width) } ?: 0 }
            }) {
                for (i in 0..6) {
                    val x = size.width * i / 6
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                }
                for (i in 0..4) {
                    val y = size.height * i / 4
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                }
                val path = Path()
                response.forEachIndexed { i, db ->
                    val x = size.width * i / response.lastIndex
                    val y = size.height * (24 - db.coerceIn(-24f, 24f)) / 48
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = Stroke(3.dp.toPx()))
                val x = (ln(frequencies[selectedBand.coerceIn(frequencies.indices)] / 20f) / ln(1000f)) * size.width
                drawLine(line.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
            Text(stringResource(R.string.total_eq_response, rate, headroom.toFloat()), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("20 Hz"); Text("±24 dB"); Text("20 kHz")
            }
            Text(stringResource(R.string.total_eq_parametric), style = MaterialTheme.typography.titleLarge)
            config.filters.forEachIndexed { index, filter ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EqToggle("${index + 1}. ${filter.type.label()}", filter.enabled, enabled) { value ->
                            TotalMusicSettings.update(latest.copy(filters = latest.filters.toMutableList().also { it[index] = it[index].copy(enabled = value) }))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TotalFilterType.entries.forEach { type -> FilterChip(selected = type == filter.type,
                                onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters.toMutableList().also { it[index] = it[index].copy(type = type) })) },
                                enabled = enabled, label = { Text(type.label()) }) }
                        }
                        fun change(f: TotalFilter) = TotalMusicSettings.update(latest.copy(filters = latest.filters.toMutableList().also { it[index] = f }))
                        EqValueSlider(stringResource(R.string.total_eq_frequency), ln(filter.frequency), ln(20f)..ln(20000f), enabled,
                            display = "${filter.frequency.roundToInt()} Hz") { change(latest.filters[index].copy(frequency = exp(it))) }
                        if (filter.type != TotalFilterType.LOW_PASS && filter.type != TotalFilterType.HIGH_PASS)
                            EqValueSlider(stringResource(R.string.total_eq_gain), filter.gain, -12f..12f, enabled) { change(latest.filters[index].copy(gain = it)) }
                        EqValueSlider("Q", filter.q, 0.2f..10f, enabled) { change(latest.filters[index].copy(q = it)) }
                        TextButton(onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters.filterIndexed { i, _ -> i != index })) }, enabled = enabled) {
                            Text(stringResource(R.string.total_eq_remove_filter))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters + TotalFilter())) }, enabled = enabled && config.filters.size < 8) {
                Text(stringResource(R.string.total_eq_add_filter))
            }
            Text(stringResource(R.string.total_eq_effects), style = MaterialTheme.typography.titleLarge)
            EqValueSlider(stringResource(R.string.total_eq_bass), config.bass, -12f..12f, enabled) { TotalMusicSettings.update(latest.copy(bass = it)) }
            EqValueSlider(stringResource(R.string.total_eq_treble), config.treble, -12f..12f, enabled) { TotalMusicSettings.update(latest.copy(treble = it)) }
            EqValueSlider(stringResource(R.string.equalizer_balance), config.balance, -1f..1f, enabled) { TotalMusicSettings.update(latest.copy(balance = it)) }
            EqValueSlider(stringResource(R.string.total_eq_width), config.width, 0f..2f, enabled) { TotalMusicSettings.update(latest.copy(width = it)) }
            EqValueSlider("Crossfeed", config.crossfeed, 0f..0.5f, enabled) { TotalMusicSettings.update(latest.copy(crossfeed = it)) }
            Text(stringResource(R.string.total_eq_effects_note), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { TotalMusicSettings.update(TotalEqConfig(graphicMode = latest.graphicMode)) }, enabled = enabled) { Text(stringResource(R.string.equalizer_reset)) }
        }
    }
    if (saving) AlertDialog(onDismissRequest = { saving = false }, title = { Text(stringResource(R.string.total_eq_save)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, singleLine = true,
            label = { Text(stringResource(R.string.total_eq_profile_name)) }, supportingText = { Text(stringResource(R.string.total_eq_overwrite)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { TotalMusicSettings.saveProfile(name); saving = false }) { Text(stringResource(R.string.total_eq_save)) } },
        dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun TotalFilterType.label(): String = stringResource(when (this) {
    TotalFilterType.PEAK -> R.string.total_eq_peak
    TotalFilterType.LOW_SHELF -> R.string.total_eq_low_shelf
    TotalFilterType.HIGH_SHELF -> R.string.total_eq_high_shelf
    TotalFilterType.LOW_PASS -> R.string.total_eq_low_pass
    TotalFilterType.HIGH_PASS -> R.string.total_eq_high_pass
})

@Composable
private fun EqToggle(label: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = value, onCheckedChange = change, enabled = enabled)
    }
}

@Composable
private fun EqValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    display: String = String.format(Locale.getDefault(), "%.2f", value), change: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(display) }
        Slider(value = value, onValueChange = change, valueRange = range, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
