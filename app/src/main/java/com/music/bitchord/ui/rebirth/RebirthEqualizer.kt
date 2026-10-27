package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.*
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors
import com.music.bitchord.ui.screens.BandFader

@Composable
fun RebirthEqualizer(style: PlayerVisualStyle, padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config by TotalMusicSettings.config.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(config)
    val enabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val mode by AppSettings.equalizerMode.collectAsStateWithLifecycle()
    val rate by TotalMusicSettings.sampleRate.collectAsStateWithLifecycle()
    val profiles by TotalMusicSettings.profiles.collectAsStateWithLifecycle()
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val toneX by AppSettings.equalizerToneX.collectAsStateWithLifecycle()
    val toneY by AppSettings.equalizerToneY.collectAsStateWithLifecycle()
    val focused by AppSettings.equalizerFocused.collectAsStateWithLifecycle()
    val balance by AppSettings.equalizerBalance.collectAsStateWithLifecycle()
    val manualBands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val latestManual by rememberUpdatedState(manualBands)
    var presets by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (glass) Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) { Text("Estudio de sonido", style = MaterialTheme.typography.headlineSmall); Text("Total EQ", color = colors.primary) }
                } else Text("Escucha\na tu manera.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Total EQ", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); Switch(enabled, onCheckedChange = { AppSettings.setEqualizerEnabled(it) }) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualizerMode.entries.forEach { choice -> FilterChip(mode == choice,
                        onClick = { AppSettings.setEqualizerMode(choice) },
                        label = { Text(when (choice) { EqualizerMode.TOTAL -> "Total EQ"; EqualizerMode.MANUAL -> "Manual"; EqualizerMode.DYNAMIC -> "Dinámico" }) }) }
                }
                if (mode == EqualizerMode.DYNAMIC) {
                    EqPanel(glass, "Tono dinámico") {
                        ValueControl("Inclinación", toneX.toFloat(), -5f..5f, enabled) { AppSettings.setEqualizerTone(it.toInt(), toneY) }
                        ValueControl("Contorno", toneY.toFloat(), -5f..5f, enabled) { AppSettings.setEqualizerTone(toneX, it.toInt()) }
                        ToggleControl("Enfoque", focused, enabled, AppSettings::setEqualizerFocused)
                    }
                }
                if (mode == EqualizerMode.MANUAL) {
                    EqPanel(glass, "Bandas manuales") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach { preset ->
                                OutlinedButton(onClick = { AppSettings.setEqualizerPreset(preset) }, enabled = enabled) { Text(preset.name) }
                            }
                        }
                        manualBands.forEachIndexed { index, gain -> ValueControl("Banda ${index + 1}", gain, -12f..12f, enabled) { value ->
                            AppSettings.setEqualizerBands(latestManual.toMutableList().also { it[index] = value })
                        } }
                        TextButton(onClick = { AppSettings.setEqualizerPreset(EqualizerPreset.FLAT) }, enabled = enabled) { Text("Restablecer bandas") }
                    }
                }
                if (mode != EqualizerMode.TOTAL) ValueControl("Balance", balance, -1f..1f, enabled, change = AppSettings::setEqualizerBalance)
                if (mode == EqualizerMode.TOTAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TotalGraphicMode.entries.forEach { graphic -> FilterChip(config.graphicMode == graphic, onClick = { TotalMusicSettings.update(latest.copy(graphicMode = graphic)); AppSettings.setEqualizerMode(EqualizerMode.TOTAL) }, label = { Text(if (graphic == TotalGraphicMode.TEN) "10 bandas" else "31 bandas") }) } }
                Box { OutlinedButton(onClick = { presets = true }) { Text("Presets y perfiles") }
                    DropdownMenu(presets, onDismissRequest = { presets = false }) { (TotalMusicSettings.factoryProfiles + profiles).forEach { (name, preset) -> DropdownMenuItem(text = { Text(name) }, onClick = { TotalMusicSettings.update(preset.copy(graphicMode = latest.graphicMode)); AppSettings.setEqualizerMode(EqualizerMode.TOTAL); presets = false }) } } }
                Surface(shape = RoundedCornerShape(if (glass) 24.dp else 40.dp), color = if (glass) colors.surfaceContainer else colors.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(if (glass) 8.dp else 12.dp)) {
                        val frequencies = TotalEqDesign.activeFrequencies(config)
                        val gains = TotalEqDesign.activeGains(config)
                        frequencies.forEachIndexed { i, hz ->
                            val active = enabled && hz < rate * .45f
                            fun change(value: Float) { AppSettings.setEqualizerMode(EqualizerMode.TOTAL); TotalMusicSettings.update(if (latest.graphicMode == TotalGraphicMode.TEN) latest.copy(tenBands = latest.tenBands.toMutableList().also { it[i] = value }) else latest.copy(bands = latest.bands.toMutableList().also { it[i] = value })) }
                            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(String.format(java.util.Locale.getDefault(), "%+.1f", gains[i]), style = MaterialTheme.typography.labelSmall)
                                BandFader(gains[i], active, Modifier.width(48.dp).height(190.dp).semantics { contentDescription = "$hz Hz"; progressBarRangeInfo = ProgressBarRangeInfo(gains[i], -12f..12f); if (!active) disabled(); setProgress { if (active) { change(it.coerceIn(-12f, 12f)); true } else false } }, allowHorizontalScroll = true, onChange = ::change)
                                Text(if (hz >= 1000) "${(hz / 1000).toInt()}k" else hz.toInt().toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                TextButton(onClick = { saving = true }) { Text("Guardar perfil actual") }
                profiles.forEach { (name, _) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    TextButton(onClick = { TotalMusicSettings.deleteProfile(name) }) { Text("Eliminar perfil") }
                } }
                Text("Preamplificación", style = MaterialTheme.typography.titleMedium)
                Slider(config.preamp, onValueChange = { TotalMusicSettings.update(latest.copy(preamp = it)) }, valueRange = -24f..12f, enabled = enabled)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Protección de nivel", Modifier.weight(1f)); Switch(config.limiter, onCheckedChange = { TotalMusicSettings.update(latest.copy(limiter = it)) }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { reset = true }) { Text("Restablecer curva") }; TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar detalles" else "Controles avanzados") } }
                if (advanced) RebirthEqAdvanced(glass, enabled)
                }
                Text("Salida de audio", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, AppSettings.audioSessionId.value)
                        putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        android.widget.Toast.makeText(context, "Este dispositivo no ofrece un panel de sonido del sistema.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Panel de sonido del dispositivo") }
            }
        }
        if (saving) AlertDialog(onDismissRequest = { saving = false }, title = { Text("Guardar perfil") },
            text = { OutlinedTextField(profileName, { profileName = it.take(48) }, singleLine = true, label = { Text("Nombre") }, supportingText = { Text("Un nombre existente reemplaza ese perfil.") }) },
            confirmButton = { TextButton(enabled = profileName.isNotBlank(), onClick = { TotalMusicSettings.saveProfile(profileName); saving = false; profileName = "" }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancelar") } })
        if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("¿Restablecer la curva?") }, text = { Text("Los perfiles guardados se conservarán.") }, confirmButton = { TextButton(onClick = { TotalMusicSettings.update(TotalEqConfig(graphicMode = latest.graphicMode)); reset = false }) { Text("Restablecer") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancelar") } })
    }
}
