package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.*
import kotlin.math.*

/** All controls write to the existing DSP configuration; this surface owns no audio state. */
@Composable
internal fun RebirthEqAdvanced(glass: Boolean, enabled: Boolean) {
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(config)
    val rate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    val response = remember(config, rate) {
        val coefficients = TotalEqDesign.coefficients(config, rate)
        val headroom = TotalEqDesign.headroom(config, coefficients, rate)
        List(160) { index ->
            val hz = 20.0 * 1000.0.pow(index / 159.0)
            (coefficients.sumOf { it.responseDb(hz, rate) } + config.preamp + headroom).toFloat()
        }
    }
    EqPanel(glass, "Respuesta · $rate Hz") {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("20 Hz"); Text("±24 dB"); Text("20 kHz") }
        ToggleControl("Margen automático", config.autoHeadroom, enabled) { TotalMusicSettings.update(latest.copy(autoHeadroom = it)) }
    }
    EqPanel(glass, "Espacio y tono") {
        ValueControl("Graves", config.bass, -12f..12f, enabled) { TotalMusicSettings.update(latest.copy(bass = it)) }
        ValueControl("Agudos", config.treble, -12f..12f, enabled) { TotalMusicSettings.update(latest.copy(treble = it)) }
        ValueControl("Balance", config.balance, -1f..1f, enabled) { TotalMusicSettings.update(latest.copy(balance = it)) }
        ValueControl("Amplitud estéreo", config.width, 0f..2f, enabled) { TotalMusicSettings.update(latest.copy(width = it)) }
        ValueControl("Crossfeed", config.crossfeed, 0f..0.5f, enabled) { TotalMusicSettings.update(latest.copy(crossfeed = it)) }
    }
    config.filters.forEachIndexed { index, filter ->
        EqPanel(glass, "Filtro ${index + 1}") {
            fun change(value: TotalFilter) {
                TotalMusicSettings.update(latest.copy(filters = latest.filters.toMutableList().also { it[index] = value }))
            }
            ToggleControl("Activo", filter.enabled, enabled) { change(latest.filters[index].copy(enabled = it)) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalFilterType.entries.forEach { type -> FilterChip(type == filter.type,
                    onClick = { change(latest.filters[index].copy(type = type)) }, enabled = enabled,
                    label = { Text(when (type) { TotalFilterType.PEAK -> "Pico"; TotalFilterType.LOW_SHELF -> "Graves"; TotalFilterType.HIGH_SHELF -> "Agudos"; TotalFilterType.LOW_PASS -> "Paso bajo"; TotalFilterType.HIGH_PASS -> "Paso alto" }) }) }
            }
            ValueControl("Frecuencia · ${filter.frequency.roundToInt()} Hz", ln(filter.frequency), ln(20f)..ln(20000f), enabled, showValue = false) { change(latest.filters[index].copy(frequency = exp(it))) }
            if (filter.type != TotalFilterType.LOW_PASS && filter.type != TotalFilterType.HIGH_PASS)
                ValueControl("Ganancia", filter.gain, -12f..12f, enabled) { change(latest.filters[index].copy(gain = it)) }
            ValueControl("Q", filter.q, 0.2f..10f, enabled) { change(latest.filters[index].copy(q = it)) }
            TextButton(onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters.filterIndexed { i, _ -> i != index })) }, enabled = enabled) { Text("Eliminar filtro") }
        }
    }
    OutlinedButton(onClick = { TotalMusicSettings.update(latest.copy(filters = latest.filters + TotalFilter())) }, enabled = enabled && config.filters.size < 8) { Text("Añadir filtro paramétrico") }
}

@Composable
internal fun EqPanel(glass: Boolean, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(if (glass) 22.dp else 36.dp),
        color = if (glass) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
internal fun ValueControl(title: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    showValue: Boolean = true, change: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, Modifier.weight(1f))
            if (showValue) Text(String.format(java.util.Locale.getDefault(), "%.2f", value))
        }
        Slider(value, onValueChange = change, enabled = enabled, valueRange = range)
    }
}

@Composable
internal fun ToggleControl(title: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f)); Switch(checked, onCheckedChange = change, enabled = enabled)
    }
}
