package com.music.bitchord

import com.music.bitchord.playback.*
import com.music.bitchord.data.settings.TotalMusicSettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TotalEqEngineTest {
    private fun measuredDb(config: TotalEqConfig, hz: Double, rate: Int = 48000): Double {
        val engine = TotalEqEngine()
        engine.setConfig(config.copy(autoHeadroom = false, limiter = false))
        engine.configure(rate, 1)
        var inputEnergy = 0.0; var outputEnergy = 0.0
        val buffer = FloatArray(256)
        var frame = 0
        repeat(rate / 256 * 2) {
            for (i in buffer.indices) buffer[i] = (0.01 * sin(2 * PI * hz * (frame + i) / rate)).toFloat()
            if (frame > rate) for (v in buffer) inputEnergy += v * v
            engine.process(buffer, buffer.size)
            if (frame > rate) for (v in buffer) outputEnergy += v * v
            frame += buffer.size
        }
        return 10 * log10(outputEnergy / inputEnergy)
    }

    @Test fun everyGraphicBandChangesItsOwnFrequencyAcrossSampleRates() {
        for (rate in listOf(32000, 44100, 48000, 96000)) {
            TotalEqDesign.frequencies.forEachIndexed { band, hz ->
                if (hz >= rate * 0.45) return@forEachIndexed
                val config = TotalEqConfig(bands = List(31) { if (it == band) 6f else 0f })
                assertEquals("$rate Hz / band $hz", 6.0, measuredDb(config, hz.toDouble(), rate), 0.12)
            }
        }
    }

    @Test fun tenIndependentBandsProcessAudioAtExactRequestedFrequencies() {
        assertEquals(listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f), TotalEqDesign.tenFrequencies)
        for (rate in listOf(32000, 44100, 48000, 96000)) {
            TotalEqDesign.tenFrequencies.forEachIndexed { index, hz ->
                if (hz >= rate * 0.45) return@forEachIndexed
                for (gain in listOf(-6f, 6f)) {
                    val config = TotalEqConfig(graphicMode = TotalGraphicMode.TEN,
                        tenBands = List(10) { if (it == index) gain else 0f })
                    assertEquals(1, TotalEqDesign.coefficients(config, rate).size)
                    assertEquals("$rate Hz / $hz Hz", gain.toDouble(), measuredDb(config, hz.toDouble(), rate), 0.12)
                }
            }
        }
    }

    @Test fun switchingGraphicModesKeepsIndependentBanksAndRespectsNyquist() {
        val config = TotalEqConfig(bands = List(31) { 6f }, tenBands = List(10) { -6f }, graphicMode = TotalGraphicMode.TEN)
        assertEquals(10, TotalEqDesign.coefficients(config, 48000).size)
        assertEquals(9, TotalEqDesign.coefficients(config, 32000).size)
        assertEquals(31, TotalEqDesign.coefficients(config.copy(graphicMode = TotalGraphicMode.THIRTY_ONE), 48000).size)
        val safe = config.copy(tenBands = listOf(Float.NaN, 99f, -99f)).sanitized()
        assertEquals(listOf(0f, 12f, -12f), safe.tenBands.take(3))
        assertEquals(10, safe.tenBands.size)
    }

    @Test fun parametricShapesProduceMeasuredFiltering() {
        fun curve(type: TotalFilterType, gain: Float = 6f) = TotalEqConfig(filters = listOf(TotalFilter(type, 1000f, gain)))
        assertEquals(6.0, measuredDb(curve(TotalFilterType.PEAK), 1000.0), 0.02)
        assertTrue(measuredDb(curve(TotalFilterType.LOW_SHELF), 50.0) > 5.9)
        assertTrue(measuredDb(curve(TotalFilterType.HIGH_SHELF), 12000.0) > 5.9)
        assertTrue(measuredDb(curve(TotalFilterType.LOW_PASS, 0f), 8000.0) < -35)
        assertTrue(measuredDb(curve(TotalFilterType.HIGH_PASS, 0f), 100.0) < -35)
    }

    @Test fun graphMatchesTheMeasuredDigitalResponse() {
        for (type in TotalFilterType.entries) {
            val config = TotalEqConfig(filters = listOf(TotalFilter(type, 2000f, 8f, 2f)))
            val c = TotalEqDesign.coefficients(config, 48000)
            for (hz in listOf(100.0, 1000.0, 2000.0, 5000.0, 16000.0)) {
                assertEquals(type.name, c.sumOf { it.responseDb(hz, 48000) }, measuredDb(config, hz), 0.1)
            }
        }
    }

    @Test fun bypassIsBitExactAndDisableReturnsToDry() {
        val engine = TotalEqEngine(); engine.configure(48000, 2)
        val input = FloatArray(512) { sin(it * 0.37).toFloat() }
        val output = input.copyOf(); engine.process(output, 256)
        assertArrayEquals(input, output, 0f)
        engine.setConfig(TotalEqConfig(bass = 12f))
        repeat(10) { engine.process(output, 256) }
        engine.setConfig(null)
        repeat(10) { input.copyInto(output); engine.process(output, 256) }
        assertArrayEquals(input, output, 0f)
    }

    @Test fun limiterBoundsLinkedStereoUnderWorstBoost() {
        val engine = TotalEqEngine()
        engine.setConfig(TotalEqConfig(bands = List(31) { 12f }, preamp = 12f, bass = 12f, treble = 12f,
            autoHeadroom = false, width = 2f))
        engine.configure(48000, 2)
        val buffer = FloatArray(512)
        repeat(200) { block ->
            for (i in buffer.indices) buffer[i] = sin((block * 512 + i) * 0.021).toFloat()
            engine.process(buffer, 256)
            assertTrue(buffer.all { it.isFinite() && abs(it) <= 0.89126f })
        }
    }

    @Test fun everyFactoryPresetChangesAudioAndAutoHeadroomPreventsClipping() {
        val names = setOf("Flat", "Rock", "Pop", "Jazz", "Clásica", "Electrónica", "Vocal", "Bass Boost")
        assertTrue(names.all { it in TotalMusicSettings.factoryProfiles })
        for ((name, preset) in TotalMusicSettings.factoryProfiles) {
            val engine = TotalEqEngine()
            engine.setConfig(preset.copy(graphicMode = TotalGraphicMode.TEN))
            engine.configure(48000, 1)
            val buffer = FloatArray(512)
            var peak = 0f
            repeat(100) { block ->
                for (i in buffer.indices) buffer[i] = (0.8 * sin((block * 512 + i) * 0.021)).toFloat()
                engine.process(buffer, buffer.size)
                peak = maxOf(peak, buffer.maxOf { abs(it) })
            }
            assertTrue("$name produced silence", buffer.any { it != 0f })
            assertTrue("$name clipped: $peak", peak <= 0.89126f)
        }
    }

    @Test fun preampIsAudibleWhenProtectionAllowsIt() {
        assertEquals(-6.0, measuredDb(TotalEqConfig(preamp = -6f), 1000.0), 0.02)
        assertEquals(6.0, measuredDb(TotalEqConfig(preamp = 6f), 1000.0), 0.02)
    }

    @Test fun balanceWidthAndCrossfeedChangeStereoSamples() {
        fun run(config: TotalEqConfig): FloatArray {
            val engine = TotalEqEngine(); engine.setConfig(config.copy(limiter = false)); engine.configure(48000, 2)
            val buffer = FloatArray(512) { if (it % 2 == 0) 0.1f else 0f }
            repeat(4) { for (i in buffer.indices) buffer[i] = if (i % 2 == 0) 0.1f else 0f; engine.process(buffer, 256) }
            return buffer
        }
        assertEquals(0f, run(TotalEqConfig(balance = 1f))[510], 0f)
        val mono = run(TotalEqConfig(width = 0f)); assertEquals(mono[510], mono[511], 0f)
        assertTrue(run(TotalEqConfig(crossfeed = 0.5f))[511] > 0.03f)
    }

    @Test fun invalidPreferencesAreSanitizedAndHighQTransitionsStayFinite() {
        val dirty = TotalEqConfig(bands = listOf(Float.NaN), width = Float.POSITIVE_INFINITY,
            filters = listOf(TotalFilter(frequency = -1f, gain = Float.NaN, q = Float.NaN))).sanitized()
        assertEquals(0f, dirty.bands[0], 0f); assertEquals(1f, dirty.width, 0f)
        val engine = TotalEqEngine(); engine.configure(44100, 2)
        val buffer = FloatArray(512)
        repeat(100) { n ->
            engine.setConfig(TotalEqConfig(filters = listOf(TotalFilter(TotalFilterType.PEAK, if (n % 2 == 0) 20f else 19000f, 12f, 10f))))
            for (i in buffer.indices) buffer[i] = sin(i * 0.1).toFloat()
            engine.process(buffer, 256)
            assertTrue(buffer.all { it.isFinite() && abs(it) <= 1.01f })
        }
    }
}
