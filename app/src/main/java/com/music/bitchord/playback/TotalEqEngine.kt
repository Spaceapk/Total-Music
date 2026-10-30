package com.music.bitchord.playback

import kotlin.math.*

enum class TotalGraphicMode { TEN, THIRTY_ONE }

enum class TotalFilterType { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS }

data class TotalFilter(
    val type: TotalFilterType = TotalFilterType.PEAK,
    val frequency: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 0.707f,
    val enabled: Boolean = true,
)

data class TotalEqConfig(
    val bands: List<Float> = List(31) { 0f },
    val filters: List<TotalFilter> = emptyList(),
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val balance: Float = 0f,
    val width: Float = 1f,
    val crossfeed: Float = 0f,
    val autoHeadroom: Boolean = true,
    val limiter: Boolean = true,
    val graphicMode: TotalGraphicMode = TotalGraphicMode.THIRTY_ONE,
    val tenBands: List<Float> = List(10) { 0f },
) {
    fun sanitized() = copy(
        tenBands = List(10) { tenBands.getOrElse(it) { 0f }.safe(-12f, 12f) },
        bands = List(31) { bands.getOrElse(it) { 0f }.safe(-12f, 12f) },
        filters = filters.take(8).map { it.copy(frequency = it.frequency.safe(20f, 20000f, 1000f),
            gain = it.gain.safe(-12f, 12f), q = it.q.safe(0.2f, 10f, 0.707f)) },
        preamp = preamp.safe(-24f, 12f), bass = bass.safe(-12f, 12f), treble = treble.safe(-12f, 12f),
        balance = balance.safe(-1f, 1f), width = width.safe(0f, 2f, 1f), crossfeed = crossfeed.safe(0f, 0.5f),
    )
}

private fun Float.safe(min: Float, max: Float, fallback: Float = 0f) = if (isFinite()) coerceIn(min, max) else fallback

/** RBJ biquads: https://www.w3.org/TR/audio-eq-cookbook/. No platform session effects. */
data class TotalCoefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    fun responseDb(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz.coerceIn(0.0, sampleRate * 0.499) / sampleRate
        fun magnitude(x0: Double, x1: Double, x2: Double): Double {
            val re = x0 + x1 * cos(w) + x2 * cos(2 * w)
            val im = -x1 * sin(w) - x2 * sin(2 * w)
            return re * re + im * im
        }
        return 10 * log10((magnitude(b0, b1, b2) / magnitude(1.0, a1, a2)).coerceAtLeast(1e-20))
    }

    companion object {
        fun of(filter: TotalFilter, sampleRate: Int): TotalCoefficients {
            val f = filter.frequency.toDouble().coerceIn(10.0, sampleRate * 0.45)
            val w = 2 * PI * f / sampleRate
            val c = cos(w); val s = sin(w)
            val a = 10.0.pow(filter.gain / 40.0)
            val alpha = s / (2 * filter.q)
            val beta = 2 * sqrt(a) * alpha
            val b0: Double; val b1: Double; val b2: Double
            val a0: Double; val a1: Double; val a2: Double
            when (filter.type) {
                TotalFilterType.PEAK -> {
                    b0 = 1 + alpha * a; b1 = -2 * c; b2 = 1 - alpha * a
                    a0 = 1 + alpha / a; a1 = -2 * c; a2 = 1 - alpha / a
                }
                TotalFilterType.LOW_SHELF -> {
                    b0 = a * ((a + 1) - (a - 1) * c + beta)
                    b1 = 2 * a * ((a - 1) - (a + 1) * c)
                    b2 = a * ((a + 1) - (a - 1) * c - beta)
                    a0 = (a + 1) + (a - 1) * c + beta
                    a1 = -2 * ((a - 1) + (a + 1) * c)
                    a2 = (a + 1) + (a - 1) * c - beta
                }
                TotalFilterType.HIGH_SHELF -> {
                    b0 = a * ((a + 1) + (a - 1) * c + beta)
                    b1 = -2 * a * ((a - 1) + (a + 1) * c)
                    b2 = a * ((a + 1) + (a - 1) * c - beta)
                    a0 = (a + 1) - (a - 1) * c + beta
                    a1 = 2 * ((a - 1) - (a + 1) * c)
                    a2 = (a + 1) - (a - 1) * c - beta
                }
                TotalFilterType.LOW_PASS -> {
                    b0 = (1 - c) / 2; b1 = 1 - c; b2 = b0
                    a0 = 1 + alpha; a1 = -2 * c; a2 = 1 - alpha
                }
                TotalFilterType.HIGH_PASS -> {
                    b0 = (1 + c) / 2; b1 = -(1 + c); b2 = b0
                    a0 = 1 + alpha; a1 = -2 * c; a2 = 1 - alpha
                }
            }
            return TotalCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}

object TotalEqDesign {
    val frequencies = listOf(20f,25f,31.5f,40f,50f,63f,80f,100f,125f,160f,200f,250f,315f,400f,500f,630f,
        800f,1000f,1250f,1600f,2000f,2500f,3150f,4000f,5000f,6300f,8000f,10000f,12500f,16000f,20000f)

    val tenFrequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun activeFrequencies(config: TotalEqConfig) = if (config.graphicMode == TotalGraphicMode.TEN) tenFrequencies else frequencies
    fun activeGains(config: TotalEqConfig) = if (config.graphicMode == TotalGraphicMode.TEN) config.tenBands else config.bands

    fun coefficients(config: TotalEqConfig, sampleRate: Int): List<TotalCoefficients> = buildList {
        val hz = activeFrequencies(config)
        val q = if (config.graphicMode == TotalGraphicMode.TEN) 1.414f else 4.318f
        activeGains(config).take(hz.size).forEachIndexed { i, gain ->
            if (abs(gain) > 0.001f && hz[i] < sampleRate * 0.45)
                add(TotalCoefficients.of(TotalFilter(frequency = hz[i], gain = gain, q = q), sampleRate))
        }
        config.filters.filter { it.enabled }.forEach {
            if (it.type == TotalFilterType.LOW_PASS || it.type == TotalFilterType.HIGH_PASS || abs(it.gain) > 0.001f)
                add(TotalCoefficients.of(it, sampleRate))
        }
        if (abs(config.bass) > 0.001f) add(TotalCoefficients.of(TotalFilter(TotalFilterType.LOW_SHELF, 100f, config.bass), sampleRate))
        if (abs(config.treble) > 0.001f) add(TotalCoefficients.of(TotalFilter(TotalFilterType.HIGH_SHELF, 8000f, config.treble), sampleRate))
    }

    fun headroom(config: TotalEqConfig, coefficients: List<TotalCoefficients>, sampleRate: Int): Double {
        if (!config.autoHeadroom || coefficients.isEmpty()) return 0.0
        var peak = 0.0
        for (i in 0..1024) {
            val hz = 10.0 * (sampleRate * 0.049).pow(i / 1024.0)
            peak = max(peak, coefficients.sumOf { it.responseDb(hz, sampleRate) })
        }
        // Stereo widening can add up to 6 dB on hard-panned material. Reserve it too.
        val stereoPeak = 20 * log10(max(1.0, config.width.toDouble()))
        return -max(0.0, peak + stereoPeak + 0.5)
    }
}

/**
 * Linked-channel lookahead peak limiter. A 2 ms delay line lets the envelope
 * anticipate transients; a monotonic deque tracks the window peak in O(1).
 * Ceiling is -1 dBFS. This limits sample peaks, not inter-sample peaks: a true-peak
 * meter would need oversampling and is deliberately not claimed here.
 */
internal class PeakLimiter(rate: Int, private val channels: Int) {
    private val lookahead = max(1, rate / 500)
    private val capacity = lookahead + 1
    private val delay = FloatArray(capacity * channels)
    private val peaks = DoubleArray(capacity)
    private val deque = LongArray(capacity + 1)
    private var head = 0; private var tail = 0
    private var position = 0L
    private var gain = 1.0
    private val attack = 1 - exp(-1.0 / (rate * 0.0005))
    private val release = 1 - exp(-1.0 / (rate * 0.08))
    private val ceiling = 0.891250938 // -1 dBFS
    var reductionDb: Double = 0.0; private set

    private fun queueSize() = (tail - head + deque.size) % deque.size

    fun process(frame: FloatArray) {
        val slot = (position % capacity).toInt()
        var peak = 0.0
        for (ch in 0 until channels) {
            val value = frame[ch]
            peak = max(peak, abs(value.toDouble()))
            delay[slot * channels + ch] = value
        }
        peaks[slot] = peak
        while (queueSize() > 0) {
            val last = (tail - 1 + deque.size) % deque.size
            if (peaks[(deque[last] % capacity).toInt()] > peak) break
            tail = last
        }
        deque[tail] = position
        tail = (tail + 1) % deque.size
        val outputPosition = position - lookahead
        while (queueSize() > 0 && deque[head] < outputPosition) head = (head + 1) % deque.size
        if (outputPosition < 0) {
            frame.fill(0f)
            position++
            return
        }
        val windowPeak = if (queueSize() > 0) peaks[(deque[head] % capacity).toInt()] else 0.0
        val target = if (windowPeak > ceiling) ceiling / windowPeak else 1.0
        gain += (target - gain) * (if (target < gain) attack else release)
        val outputSlot = (outputPosition % capacity).toInt()
        var delayedPeak = 0.0
        for (ch in 0 until channels) delayedPeak = max(delayedPeak, abs(delay[outputSlot * channels + ch].toDouble()))
        if (delayedPeak * gain > ceiling) gain = ceiling / delayedPeak
        for (ch in 0 until channels) frame[ch] = (delay[outputSlot * channels + ch] * gain).toFloat()
        reductionDb = -20 * log10(max(1e-12, gain))
        position++
    }
}

/** Immutable targets are prepared off the render callback. Banks crossfade over 20 ms.
 * No allocations/locks/trigonometry in process(); states belong to the audio thread.
 */
class TotalEqEngine {
    private class Section(val c: TotalCoefficients, channels: Int) {
        val z1 = DoubleArray(channels); val z2 = DoubleArray(channels)
        fun sample(x: Double, ch: Int): Double {
            val y = c.b0 * x + z1[ch]
            z1[ch] = c.b1 * x - c.a1 * y + z2[ch]
            z2[ch] = c.b2 * x - c.a2 * y
            if (abs(z1[ch]) < 1e-25) z1[ch] = 0.0
            if (abs(z2[ch]) < 1e-25) z2[ch] = 0.0
            return y
        }
    }
    private class Bank(val config: TotalEqConfig?, val rate: Int, val channels: Int) {
        private val coefficients = config?.let { TotalEqDesign.coefficients(it, rate) }.orEmpty()
        private val sections = coefficients.map { Section(it, channels) }
        private val gain = config?.let { 10.0.pow((it.preamp + TotalEqDesign.headroom(it, coefficients, rate)) / 20) } ?: 1.0
        val frame = FloatArray(channels)
        fun limiterGainReduction(): Double = if (config?.limiter == true) limiter.reductionDb else 0.0
        private var lowL = 0.0; private var lowR = 0.0
        private val lowAlpha = 1 - exp(-2 * PI * 700 / rate)
        private val limiter = PeakLimiter(rate, channels)
        fun render(input: FloatArray, offset: Int) {
            for (ch in 0 until channels) {
                var v = input[offset + ch].toDouble()
                for (s in sections) v = s.sample(v, ch)
                frame[ch] = (v * gain).toFloat()
            }
            val c = config ?: return
            if (channels == 2) {
                var l = frame[0].toDouble(); var r = frame[1].toDouble()
                val mid = (l + r) * 0.5; val side = (l - r) * 0.5 * c.width
                l = mid + side; r = mid - side
                lowL += lowAlpha * (l - lowL); lowR += lowAlpha * (r - lowR)
                frame[0] = (((l + c.crossfeed * lowR) / (1 + c.crossfeed)) * min(1f, 1f - c.balance)).toFloat()
                frame[1] = (((r + c.crossfeed * lowL) / (1 + c.crossfeed)) * min(1f, 1f + c.balance)).toFloat()
            }
            if (c.limiter) limiter.process(frame)
        }
    }
    /** Last linked gain reduction in dB, for diagnostics/tests (0 when idle). */
    @Volatile var limiterReductionDb: Double = 0.0
        private set
    @Volatile private var rate = 48000
    @Volatile private var channels = 2
    @Volatile private var requested: TotalEqConfig? = null
    @Volatile private var pending = Bank(null, rate, channels)
    private var current = pending
    private var previous: Bank? = null
    private var remaining = 0

    fun setConfig(config: TotalEqConfig?) {
        val safe = config?.sanitized()
        if (safe == requested) return
        requested = safe
        pending = Bank(safe, rate, channels)
    }
    fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate > 0 && channelCount > 0)
        rate = sampleRate; channels = channelCount
        pending = Bank(requested, rate, channels)
        current = pending; previous = null; remaining = 0
    }
    fun flush() { configure(rate, channels) }
    fun process(samples: FloatArray, frames: Int) {
        val next = pending
        // Finish one transition before accepting another; rapid dragging stays bounded.
        if (next !== current && next.rate == rate && next.channels == channels && remaining == 0) {
            previous = current; current = next; remaining = max(1, rate / 50)
        }
        if (current.config == null && previous == null) return
        val fadeFrames = max(1, rate / 50)
        for (f in 0 until frames) {
            val offset = f * channels
            current.render(samples, offset)
            val old = previous
            if (old != null) {
                old.render(samples, offset)
                val mix = 1f - remaining.toFloat() / fadeFrames
                for (ch in 0 until channels) samples[offset + ch] = old.frame[ch] * (1 - mix) + current.frame[ch] * mix
                remaining--
                if (remaining == 0) previous = null
            } else {
                for (ch in 0 until channels) samples[offset + ch] = current.frame[ch]
            }
        }
        limiterReductionDb = current.limiterGainReduction()
    }
}
