package com.rizky.beatradar.dsp

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/** A live tempo reading. [lastBeatAtMs] uses the same clock as the `nowMs` passed to [BeatEngine.feed]. */
data class BeatUpdate(val bpm: Float, val confidence: Float, val lastBeatAtMs: Long, val timeMs: Long)

/**
 * Glue between raw PCM and live tempo readings. Pure Kotlin so it can be unit tested.
 * Input is decimated to ~11 kHz before analysis, which keeps the cost to a few percent of one core.
 */
class BeatEngine(
    inputSampleRate: Int,
    private val listener: Listener,
    private val silenceResetMs: Long = 3_000,
    /** Time between sound reaching the mic and the buffer being read. */
    private val inputLatencyMs: Long = 0,
) {
    interface Listener {
        fun onBeat(update: BeatUpdate) {}
        fun onSilenceChanged(silent: Boolean) {}
    }

    private val decimation = (inputSampleRate / TARGET_RATE).coerceAtLeast(1)
    private val detector: BpmDetector = OnsetBpmDetector(sampleRate = inputSampleRate / decimation)
    private val fingerprinter = Fingerprinter(sampleRate = inputSampleRate / decimation)
    private val landmarks = ArrayList<Landmark>()

    private var scratch = FloatArray(0)
    private var acc = 0f
    private var accCount = 0

    private var silentSinceMs: Long? = null
    private var silent = false
    private val resetRequested = AtomicBoolean(false)
    private val recent = ArrayDeque<Float>()
    private var level = 0.0
    private var onsetBaseline = 0.0
    private var onsetCeiling = 1e-6

    /** Safe to call from any thread; the detector starts fresh before the next buffer. */
    fun requestReset() = resetRequested.set(true)

    fun feed(pcm: ShortArray, count: Int, nowMs: Long) {
        if (resetRequested.getAndSet(false)) reset()

        val bufferRms = rms(pcm, count)
        level = 0.9 * level + 0.1 * bufferRms
        if (bufferRms < SILENCE_RMS) {
            if (silentSinceMs == null) silentSinceMs = nowMs
            if (!silent && nowMs - silentSinceMs!! >= silenceResetMs) {
                silent = true
                reset()
                listener.onSilenceChanged(true)
            }
        } else {
            silentSinceMs = null
            if (silent) {
                silent = false
                listener.onSilenceChanged(false)
            }
        }
        if (silent) return

        val n = decimate(pcm, count)
        if (level >= QUIET_RMS) {
            fingerprinter.process(scratch, n) { lm -> synchronized(landmarks) { landmarks += lm } }
        }
        val estimate = detector.process(scratch, n) ?: return
        val bpm = smooth(estimate.bpm)
        val beatAt = nowMs - inputLatencyMs - (estimate.secondsSinceBeat * 1000).toLong()
        // A near-silent mic only hears noise, which can look faintly periodic: never call that a beat.
        val confidence = if (level < QUIET_RMS) 0f else estimate.confidence
        listener.onBeat(BeatUpdate(bpm, confidence, beatAt, nowMs))
    }

    /**
     * How strongly the latest buffer hit, 0..1, for the live graph: high on drum hits, ~0 between them.
     * Adapts to the music so both soft and loud songs use the full range. Call once per [feed].
     */
    fun takeOnsetLevel(): Float {
        val raw = detector.takeOnsetPeak()
        onsetBaseline = 0.97 * onsetBaseline + 0.03 * raw
        val hit = (raw - onsetBaseline * 1.3).coerceAtLeast(0.0)
        onsetCeiling = maxOf(hit, onsetCeiling * 0.995)
        return if (silent || level < QUIET_RMS) 0f else (hit / onsetCeiling).coerceIn(0.0, 1.0).toFloat()
    }

    /** Fingerprint landmarks produced since the last call. Safe to call from any thread. */
    fun drainLandmarks(): List<Landmark> = synchronized(landmarks) { landmarks.toList().also { landmarks.clear() } }

    private fun reset() {
        detector.reset()
        recent.clear()
    }

    /** Median of the last three readings, folding octave flips onto the current tempo so the number stays steady. */
    private fun smooth(raw: Float): Float {
        val ref = recent.lastOrNull()
        val value = if (ref == null) raw else {
            listOf(raw, raw * 2, raw / 2).minBy { abs(it - ref) }.takeIf { abs(it - ref) / ref < 0.04f } ?: raw
        }
        recent.addLast(value)
        if (recent.size > 3) recent.removeFirst()
        return recent.sorted()[recent.size / 2]
    }

    private fun decimate(pcm: ShortArray, count: Int): Int {
        val maxOut = count / decimation + 1
        if (scratch.size < maxOut) scratch = FloatArray(maxOut)
        var out = 0
        for (i in 0 until count) {
            acc += pcm[i] / 32768f
            if (++accCount == decimation) {
                scratch[out++] = acc / decimation
                acc = 0f
                accCount = 0
            }
        }
        return out
    }

    companion object {
        const val TARGET_RATE = 11_025
        /** About -80 dBFS: a muted or disconnected microphone. */
        const val SILENCE_RMS = 1e-4
        /** About -60 dBFS: a quiet room, no music worth measuring. */
        const val QUIET_RMS = 1e-3
        /** Noise scores 0.04–0.12 on this detector; real rhythms score 0.25 and up. */
        const val MIN_CONFIDENCE = 0.15f

        fun rms(pcm: ShortArray, count: Int): Double {
            if (count <= 0) return 0.0
            var sum = 0.0
            for (i in 0 until count) {
                val v = pcm[i] / 32768.0
                sum += v * v
            }
            return sqrt(sum / count)
        }
    }
}
