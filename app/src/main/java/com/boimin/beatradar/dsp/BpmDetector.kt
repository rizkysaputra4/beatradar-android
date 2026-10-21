package com.boimin.beatradar.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.roundToInt

/**
 * @property secondsSinceBeat how long before the end of the audio fed so far the most recent beat landed,
 * so callers can animate in time with the music.
 * @property settled false for the quick first guesses made from a short window; those can be off.
 */
data class BpmEstimate(val bpm: Float, val confidence: Float, val secondsSinceBeat: Float = 0f, val settled: Boolean = true)

interface BpmDetector {
    /** Feeds mono samples in [-1, 1]. Returns the newest estimate produced while consuming them, if any. */
    fun process(samples: FloatArray, count: Int = samples.size): BpmEstimate?
    fun reset()

    /** Strongest onset (beat-like hit) seen since the last call, in detector units; 0 if none. */
    fun takeOnsetPeak(): Double = 0.0

    /** Readings that flip an octave should be folded back onto the current tempo (it can't tell them apart well). */
    val foldsOctaves: Boolean get() = true
}

/**
 * Tempo + beat-phase estimator: log-compressed spectral flux onset envelope -> autocorrelation ->
 * comb-filtered tempo scoring with a log-normal tempo prior (reduces octave errors) -> phase search.
 *
 * It listens two ways at once and goes with whichever is surer: across the whole spectrum, and weighted toward
 * the low bands where kicks and bass live (which shrugs off chatter and clatter, but has nothing to go on when a
 * song or a speaker has little bass).
 *
 * Gives a rough first answer after [minWindowSeconds], a settled one from [settleWindowSeconds], and keeps
 * refining as the window grows to [maxWindowSeconds]. Cheap: at 11 025 Hz it runs ~86 FFTs of 512 points per second.
 */
class OnsetBpmDetector(
    sampleRate: Int = 11_025,
    private val minBpm: Float = 60f,
    private val maxBpm: Float = 200f,
    minWindowSeconds: Float = 1.5f,
    settleWindowSeconds: Float = 3f,
    maxWindowSeconds: Float = 8f,
    estimateEverySeconds: Float = 0.5f,
) : BpmDetector {

    private val sampleRate = sampleRate.toDouble()
    private val frameSize = 512
    private val hop = 128
    private val fps = sampleRate.toDouble() / hop
    private val envLength = (maxWindowSeconds * fps).toInt()
    private val minFrames = (minWindowSeconds * fps).toInt()
    private val settleFrames = (settleWindowSeconds * fps).toInt()
    private val framesPerEstimate = (estimateEverySeconds * fps).toInt().coerceAtLeast(1)
    private val maxLag = minOf(envLength - 1, (COMB_HARMONICS * 60.0 * fps / minBpm).toInt() + 2)

    private val fft = Fft(frameSize)
    private val window = DoubleArray(frameSize) { 0.5 - 0.5 * cos(2 * PI * it / frameSize) }
    private val frame = FloatArray(frameSize)
    private var frameFill = 0
    private val re = DoubleArray(frameSize)
    private val im = DoubleArray(frameSize)
    private val magnitude = DoubleArray(frameSize / 2)

    private val full = Track(DoubleArray(frameSize / 2) { 1.0 })
    /**
     * Beats live in kicks, bass and the body of snares; crowd chatter, clatter and hiss sit higher up. So bands
     * above [BEAT_BAND_HZ] count progressively less. In noisy rooms this more than doubles correct readings.
     */
    private val low = Track(DoubleArray(frameSize / 2) { k ->
        val hz = k * sampleRate / frameSize
        if (hz <= BEAT_BAND_HZ) 1.0 else Math.pow(BEAT_BAND_HZ / hz, HIGH_BAND_ROLLOFF)
    })

    private var framesSinceEstimate = 0
    private var onsetPeak = 0.0

    override fun reset() {
        frameFill = 0
        full.reset()
        low.reset()
        framesSinceEstimate = 0
    }

    override fun process(samples: FloatArray, count: Int): BpmEstimate? {
        var result: BpmEstimate? = null
        var samplesAfterResult = 0
        var i = 0
        while (i < count) {
            val take = minOf(frameSize - frameFill, count - i)
            System.arraycopy(samples, i, frame, frameFill, take)
            frameFill += take
            i += take
            if (result != null) samplesAfterResult += take
            if (frameFill == frameSize) {
                spectrum()
                full.push()
                // Hits for the ripples come from the low view: kicks and bass, not clatter.
                if (low.push() > onsetPeak) onsetPeak = low.lastFlux
                System.arraycopy(frame, hop, frame, 0, frameSize - hop)
                frameFill = frameSize - hop
                if (low.count >= minFrames && ++framesSinceEstimate >= framesPerEstimate) {
                    framesSinceEstimate = 0
                    estimate()?.let {
                        result = it
                        samplesAfterResult = 0
                    }
                }
            }
        }
        return result?.let { it.copy(secondsSinceBeat = it.secondsSinceBeat + (samplesAfterResult / sampleRate).toFloat()) }
    }

    private fun spectrum() {
        for (k in 0 until frameSize) {
            re[k] = frame[k] * window[k]
            im[k] = 0.0
        }
        fft.transform(re, im)
        for (k in 1 until frameSize / 2) magnitude[k] = ln1p(COMPRESSION * hypot(re[k], im[k]))
    }

    override fun takeOnsetPeak(): Double = onsetPeak.also { onsetPeak = 0.0 }

    private fun estimate(): BpmEstimate? {
        val a = low.estimate()
        val b = full.estimate()
        return when {
            a == null -> b
            b == null -> a
            b.confidence <= a.confidence -> a
            // Same tempo, an octave apart: the low view picks the octave better, so keep its reading.
            sameFamily(a.bpm, b.bpm) -> a.copy(confidence = b.confidence)
            else -> b
        }
    }

    private fun sameFamily(x: Float, y: Float): Boolean =
        listOf(1f, 2f, 0.5f).any { abs(x * it - y) <= y * 0.03f }

    /** One way of listening: an onset envelope built from spectral flux with these per-band [weights]. */
    private inner class Track(private val weights: DoubleArray) {
        private val prevSpectrum = DoubleArray(frameSize / 2)
        private var havePrev = false
        private val envelope = DoubleArray(envLength)
        private var envWrite = 0
        var count = 0
            private set
        var lastFlux = 0.0
            private set

        private val linear = DoubleArray(envLength)
        private val sums = DoubleArray(envLength + 1)
        private val acf = DoubleArray(maxLag + 1)
        private var usableLag = 0

        fun reset() {
            havePrev = false
            envWrite = 0
            count = 0
        }

        /** Adds this frame's flux to the envelope and returns it. */
        fun push(): Double {
            var flux = 0.0
            for (k in 1 until frameSize / 2) {
                val mag = magnitude[k] * weights[k]
                if (havePrev) {
                    val d = mag - prevSpectrum[k]
                    if (d > 0) flux += d
                }
                prevSpectrum[k] = mag
            }
            havePrev = true
            envelope[envWrite] = flux
            envWrite = (envWrite + 1) % envLength
            if (count < envLength) count++
            lastFlux = flux
            return flux
        }

        fun estimate(): BpmEstimate? {
            val n = count
            val start = (envWrite - n + envLength) % envLength
            for (k in 0 until n) linear[k] = envelope[(start + k) % envLength]

            // Adaptive threshold: keep only what rises above the local mean.
            val radius = (0.1 * fps).toInt().coerceAtLeast(1)
            var prefix = 0.0
            for (k in 0 until n) { prefix += linear[k]; sums[k + 1] = prefix }
            var mean = 0.0
            for (k in 0 until n) {
                val lo = maxOf(0, k - radius)
                val hi = minOf(n, k + radius + 1)
                val v = linear[k] - (sums[hi] - sums[lo]) / (hi - lo)
                linear[k] = if (v > 0) v else 0.0
                mean += linear[k]
            }
            mean /= n

            // Lags beyond ~2/3 of the window are too noisy to trust.
            usableLag = minOf(maxLag, n * 2 / 3)
            for (lag in 0..usableLag) {
                var s = 0.0
                for (k in lag until n) s += (linear[k] - mean) * (linear[k - lag] - mean)
                acf[lag] = s / (n - lag)
            }
            val energy = acf[0]
            if (energy <= 1e-12) return null

            val steps = ((maxBpm - minBpm) / GRID_STEP).toInt() + 1
            var best = -1
            var bestScore = 0.0
            var prevScore = 0.0
            var nextScore = 0.0
            var bestStrength = 0.0
            var lastScore = 0.0
            for (s in 0 until steps) {
                val bpm = minBpm + s * GRID_STEP
                val tau = 60.0 * fps / bpm
                var comb = 0.0
                var weights = 0.0
                for (m in 1..COMB_HARMONICS) {
                    val lag = m * tau
                    if (lag > usableLag) break
                    comb += acfAt(lag) / m
                    weights += 1.0 / m
                }
                val score = if (weights > 0) {
                    val octaves = ln(bpm / PRIOR_CENTER_BPM) / LN2
                    comb / weights * HARMONIC_WEIGHT_SUM * exp(-0.5 * octaves * octaves / (PRIOR_OCTAVE_SPREAD * PRIOR_OCTAVE_SPREAD))
                } else 0.0
                if (s == best + 1 && best >= 0) nextScore = score
                if (best < 0 || score > bestScore) {
                    best = s
                    bestScore = score
                    prevScore = lastScore
                    nextScore = 0.0
                    bestStrength = if (weights > 0) comb / weights / energy else 0.0
                }
                lastScore = score
            }
            if (best < 0 || bestScore <= 0) return null

            var bpm = minBpm + best * GRID_STEP
            if (best in 1 until steps - 1) {
                val denom = prevScore - 2 * bestScore + nextScore
                if (denom < 0) bpm += (0.5 * (prevScore - nextScore) / denom).toFloat() * GRID_STEP
            }
            return BpmEstimate(
                bpm,
                bestStrength.coerceIn(0.0, 1.0).toFloat(),
                secondsSinceLastBeat(n, 60.0 * fps / bpm),
                settled = n >= settleFrames,
            )
        }

        /** Finds the beat grid offset that best lines up with recent onsets. */
        private fun secondsSinceLastBeat(n: Int, tau: Double): Float {
            val beats = minOf(8, (n / tau).toInt())
            var bestOffset = 0
            var bestSum = -1.0
            for (offset in 0 until tau.roundToInt().coerceAtLeast(1)) {
                var sum = 0.0
                for (b in 0 until beats) {
                    val idx = n - 1 - offset - (b * tau).roundToInt()
                    if (idx < 0) break
                    // Allow +-1 frame of jitter around each expected beat.
                    sum += maxOf(linear[idx], if (idx > 0) linear[idx - 1] else 0.0, if (idx < n - 1) linear[idx + 1] else 0.0)
                }
                if (sum > bestSum) { bestSum = sum; bestOffset = offset }
            }
            // The flux peak sits near the middle of the analysis frame.
            val samplesAgo = bestOffset * hop + frameSize / 2
            return (samplesAgo / sampleRate).toFloat()
        }

        private fun acfAt(lag: Double): Double {
            val i = floor(lag).toInt()
            val f = lag - i
            return if (i + 1 > usableLag) acf[i] else acf[i] * (1 - f) + acf[i + 1] * f
        }
    }

    private companion object {
        const val COMPRESSION = 100.0
        const val BEAT_BAND_HZ = 250.0
        /** Weight falls as (250 Hz / f)^1.5: 0.35 at 500 Hz, 0.13 at 1 kHz, 0.04 at 2 kHz. */
        const val HIGH_BAND_ROLLOFF = 1.5
        const val COMB_HARMONICS = 4
        const val HARMONIC_WEIGHT_SUM = 1.0 + 1.0 / 2 + 1.0 / 3 + 1.0 / 4
        const val GRID_STEP = 0.5f
        const val PRIOR_CENTER_BPM = 128.0
        const val PRIOR_OCTAVE_SPREAD = 1.0
        val LN2 = ln(2.0)
    }
}
