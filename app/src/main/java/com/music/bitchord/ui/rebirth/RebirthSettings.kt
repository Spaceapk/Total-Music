package com.music.bitchord.ui.rebirth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.player.PlayerVisualStyle
import com.music.bitchord.ui.player.rebirthColors

@Composable
fun RebirthSettings(style: PlayerVisualStyle, padding: PaddingValues, onEqualizer: () -> Unit, onAdvanced: () -> Unit) {
    val glass = style == PlayerVisualStyle.LIQUID_GLASS_PLUS
    val dynamic by AppSettings.playerDynamicBackground.collectAsStateWithLifecycle()
    val crystal by AppSettings.playerGlassEffects.collectAsStateWithLifecycle()
    val motion by AppSettings.playerAnimatedTransitions.collectAsStateWithLifecycle()
    val canvas by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val alternative by AppSettings.playerAlternativeControls.collectAsStateWithLifecycle()
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val artwork by TotalMusicSettings.artworkColors.collectAsStateWithLifecycle()
    val adaptive by TotalMusicSettings.adaptiveGlass.collectAsStateWithLifecycle()
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (glass) Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(40.dp)); Text("Tu espacio,\na tu manera", style = MaterialTheme.typography.headlineMedium); Text("Liquid Glass+", color = colors.primary) }
                } else Text("Hazlo\ntuyo.", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                Text("Experiencia visual", style = if (glass) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
                PlayerVisualStyle.entries.forEach { choice -> Surface(shape = RoundedCornerShape(if (glass) 18.dp else 28.dp), color = if (choice == style) colors.primaryContainer else colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().clickable { AppSettings.setPlayerVisualStyle(choice) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(choice == style, onClick = { AppSettings.setPlayerVisualStyle(choice) }); Text(when (choice) { PlayerVisualStyle.ORIGINAL -> "Original"; PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS -> "Google Expressive+"; PlayerVisualStyle.LIQUID_GLASS_PLUS -> "Liquid Glass+" }) }
                } }
                Surface(shape = RoundedCornerShape(if (glass) 24.dp else 36.dp), color = if (glass) colors.surfaceContainer else colors.secondaryContainer) { Column(Modifier.padding(8.dp)) {
                    EffectRow("Fondo dinámico", "Ambiente del reproductor", dynamic, AppSettings::setPlayerDynamicBackground)
                    if (glass) EffectRow("Cristal", "Iluminación y profundidad", crystal, AppSettings::setPlayerGlassEffects)
                    EffectRow("Carátula animada", "Cuando exista una animación disponible", canvas, AppSettings::setAnimatedCanvas)
                    EffectRow("Transiciones", "Movimiento entre estados", motion, AppSettings::setPlayerAnimatedTransitions)
                    if (!glass) EffectRow("Controles alternativos", "Geometría expresiva de reproducción", alternative, AppSettings::setPlayerAlternativeControls)
                } }
                Text("Modo de pantalla", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { mode -> FilterChip(theme == mode, onClick = { AppSettings.setThemeMode(mode) }, label = { Text(when (mode) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" }) }) } }
                EffectRow("Navegación adaptable", "Reducir etiquetas al desplazarte", adaptive, TotalMusicSettings::setAdaptiveGlass)
                if (!glass) EffectRow("Colores de carátula", "Aplicarlos en el reproductor", artwork, TotalMusicSettings::setArtworkColors)
                Surface(shape = RoundedCornerShape(if (glass) 20.dp else 32.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onEqualizer)) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Rounded.Equalizer, null); Column(Modifier.weight(1f)) { Text("Sonido", style = MaterialTheme.typography.titleLarge); Text("Ecualizador de diez bandas") }; Icon(Icons.Rounded.ChevronRight, null) }
                }
                OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) { Text("Todos los ajustes y herramientas") }
            }
        }
    }
}
@Composable
private fun EffectRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(description, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onCheckedChange = onChange)
    }
}
