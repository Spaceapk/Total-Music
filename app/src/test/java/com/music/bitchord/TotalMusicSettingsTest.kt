package com.music.bitchord

import android.content.SharedPreferences
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.playback.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class TotalMusicSettingsTest {
    private fun preferences(data: MutableMap<String, Any> = mutableMapOf()): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> { data[args!![0] as String] = args[1]; proxy }
                method.name == "apply" -> null
                method.name == "commit" -> true
                method.name == "clear" -> { data.clear(); proxy }
                method.name == "remove" -> { data.remove(args!![0]); proxy }
                else -> null
            }
        }
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> data.toMap()
                "contains" -> data.containsKey(args!![0])
                else -> if (method.name.startsWith("get")) data[args!![0]] ?: args[1] else null
            }
        } as SharedPreferences
    }

    @Test fun visualOptionsAreOptInAndSurviveReload() {
        val prefs = preferences(); TotalMusicSettings.init(prefs)
        assertFalse(TotalMusicSettings.artworkColors.value)
        assertFalse(TotalMusicSettings.adaptiveGlass.value)
        TotalMusicSettings.setArtworkColors(true); TotalMusicSettings.setAdaptiveGlass(true)
        TotalMusicSettings.init(prefs)
        assertTrue(TotalMusicSettings.artworkColors.value)
        assertTrue(TotalMusicSettings.adaptiveGlass.value)
        TotalMusicSettings.setArtworkColors(false); TotalMusicSettings.setAdaptiveGlass(false)
        TotalMusicSettings.init(prefs)
        assertFalse(TotalMusicSettings.artworkColors.value)
        assertFalse(TotalMusicSettings.adaptiveGlass.value)
    }

    @Test fun themeAndAdvancedAudioSurviveReload() {
        val prefs = preferences(); TotalMusicSettings.init(prefs)
        val config = TotalEqConfig(bands = List(31) { it / 3f - 5f }, preamp = -4f, bass = 3f,
            filters = listOf(TotalFilter(TotalFilterType.HIGH_PASS, 55f, 0f, 0.8f)), width = 1.2f, crossfeed = 0.2f)
        TotalMusicSettings.update(config); TotalMusicSettings.setExpressive(true)
        TotalMusicSettings.init(prefs)
        assertEquals(config, TotalMusicSettings.config.value)
        assertTrue(TotalMusicSettings.expressive.value)
    }

    @Test fun namedProfilesCanBeCreatedReplacedAndDeleted() {
        val prefs = preferences(); TotalMusicSettings.init(prefs)
        TotalMusicSettings.update(TotalEqConfig(bass = 4f)); TotalMusicSettings.saveProfile("Headphones")
        TotalMusicSettings.update(TotalEqConfig(bass = 8f)); TotalMusicSettings.saveProfile("Headphones")
        TotalMusicSettings.init(prefs)
        assertEquals(1, TotalMusicSettings.profiles.value.size)
        assertEquals(8f, TotalMusicSettings.profiles.value.getValue("Headphones").bass, 0f)
        TotalMusicSettings.deleteProfile("Headphones"); TotalMusicSettings.init(prefs)
        assertTrue(TotalMusicSettings.profiles.value.isEmpty())
    }

    @Test fun profilesRenamePreservesOrderAndRejectsCollisions() {
        TotalMusicSettings.init(preferences())
        TotalMusicSettings.saveProfile("A"); TotalMusicSettings.saveProfile("B")
        assertTrue(TotalMusicSettings.renameProfile("A", "C"))
        assertEquals(listOf("C", "B"), TotalMusicSettings.profiles.value.keys.toList())
        assertFalse(TotalMusicSettings.renameProfile("C", "B"))
        assertFalse(TotalMusicSettings.renameProfile("C", "Rock"))
        assertFalse(TotalMusicSettings.renameProfile("missing", "D"))
    }

    @Test fun abMonitorIsTransientAndDoesNotRewriteSavedCurve() {
        val prefs = preferences(); TotalMusicSettings.init(prefs)
        val config = TotalEqConfig(bass = 5f); TotalMusicSettings.update(config)
        TotalMusicSettings.setCompareOriginal(true)
        assertTrue(TotalMusicSettings.compareOriginal.value)
        assertEquals(config, TotalMusicSettings.config.value)
        TotalMusicSettings.init(prefs)
        assertFalse(TotalMusicSettings.compareOriginal.value)
    }

    @Test fun brokenImportedJsonFallsBackWithoutLosingLegacyPreferences() {
        val data = mutableMapOf<String, Any>("total_eq" to "broken", "total_eq_profiles" to "[", "equalizer_bands" to "1,2,3")
        TotalMusicSettings.init(preferences(data))
        assertEquals(TotalEqConfig(), TotalMusicSettings.config.value)
        assertTrue(TotalMusicSettings.profiles.value.isEmpty())
        assertEquals("1,2,3", data["equalizer_bands"])
    }

    @Test fun newInstallUsesTenBandsButOldPreferencesKeep31Bands() {
        TotalMusicSettings.init(preferences())
        assertEquals(TotalGraphicMode.TEN, TotalMusicSettings.config.value.graphicMode)
        val data = mutableMapOf<String, Any>("total_eq" to """{"bands":[5,4,3],"preamp":-2}""")
        TotalMusicSettings.init(preferences(data))
        assertEquals(TotalGraphicMode.THIRTY_ONE, TotalMusicSettings.config.value.graphicMode)
        assertEquals(5f, TotalMusicSettings.config.value.bands[0], 0f)
        assertEquals(-2f, TotalMusicSettings.config.value.preamp, 0f)
    }

    @Test fun bothGraphicBanksAndModeSurviveReloadAndNamedProfile() {
        val prefs = preferences(); TotalMusicSettings.init(prefs)
        val config = TotalEqConfig(graphicMode = TotalGraphicMode.TEN, tenBands = List(10) { it - 5f },
            bands = List(31) { 3f }, preamp = -5f)
        TotalMusicSettings.update(config); TotalMusicSettings.saveProfile("Ten bands")
        TotalMusicSettings.init(prefs)
        assertEquals(config, TotalMusicSettings.config.value)
        assertEquals(config, TotalMusicSettings.profiles.value.getValue("Ten bands"))
        TotalMusicSettings.factoryProfiles.values.forEach {
            assertEquals(10, it.tenBands.size)
            assertTrue(it.tenBands.all { gain -> gain.isFinite() && gain in -12f..12f })
        }
    }

    @Test fun factoryPresetsHave31FiniteBoundedBands() {
        assertEquals(11, TotalMusicSettings.factoryProfiles.size)
        TotalMusicSettings.factoryProfiles.values.forEach { c ->
            assertEquals(31, c.bands.size)
            assertTrue(c.bands.all { it.isFinite() && it in -12f..12f })
        }
    }
}
