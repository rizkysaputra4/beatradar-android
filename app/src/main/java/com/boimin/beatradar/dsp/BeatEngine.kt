package com.boimin.beatradar.dsp

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A live tempo reading. [lastBeatAtMs] uses the same clock as the `nowMs` passed to [BeatEngine.feed].
 * Unsettled readings are quick first guesses from the first seconds of a song: fine to show, not to judge by.
 */
data class BeatUpdate(val bpm: Float, val confidence: Float, val lastBeatAtMs: Long, val timeMs: Long, val settled: Boolean = true)

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
    /** Builds the tempo detector for the decimated rate (~11 kHz): the trained network, which needs exactly 11 025 Hz. */
    detectorFactory: (Int) -> BpmDetector = { rate -> if (rate == TARGET_RATE) BeatNetDetector() else OnsetBpmDetector(sampleRate = rate) },
) {
    interface Listener {
        fun onBeat(update: BeatUpdate) {}
        /** A hit the mic just heard (drum, pluck, chord), [strength] 0..1 relative to the song's own hits. */
        fun onHit(atMs: Long, strength: Float) {}
        fun onSilenceChanged(silent: Boolean) {}
        /**
         * The gap between two songs: the sound fell far below the music for a moment. A room is never silent, so this
         * is relative to how loud the music was, unlike [onSilenceChanged]. Detection has already started over.
         */
        fun onSongGap() {}
    }

    private val decimation = (inputSampleRate / TARGET_RATE).coerceAtLeast(1)
    private val detector: BpmDetector = detectorFactory(inputSampleRate / decimation)
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
    private var onsetReads = 0
    private var lastHitMs = Long.MIN_VALUE / 2
    private var settled = false
    /** The sound level over the last ~0.2 s. */
    private var shortLevel = 0.0
    /** How loud the music has been lately: follows [shortLevel] up at once, sinks slowly. */
    private var musicLevel = 0.0
    private var quietSinceMs: Long? = null
    private var lastFeedMs: Long? = null

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
        detectGap(bufferRms, nowMs)

        val n = decimate(pcm, count)
        if (level >= QUIET_RMS) {
            fingerprinter.process(scratch, n) { lm -> synchronized(landmarks) { landmarks += lm } }
        }
        val estimate = detector.process(scratch, n)
        val strength = onsetLevel()
        if (strength >= HIT_MIN && nowMs - lastHitMs >= HIT_GAP_MS) {
            lastHitMs = nowMs
            listener.onHit(nowMs - inputLatencyMs, strength)
        }
        if (estimate == null) return
        // Once the window is long enough to trust, start the smoothing over: an early guess may be an octave off.
        if (estimate.settled && !settled) recent.clear()
        settled = estimate.settled
        val bpm = smooth(estimate.bpm)
        val beatAt = nowMs - inputLatencyMs - (estimate.secondsSinceBeat * 1000).toLong()
        // A near-silent mic only hears noise, which can look faintly periodic: never call that a beat.
        // Quick guesses come from little audio, where noise can score higher, so they must be clearer.
        val confidence = when {
            level < QUIET_RMS -> 0f
            !estimate.settled && estimate.confidence < MIN_EARLY_CONFIDENCE -> 0f
            else -> estimate.confidence
        }
        listener.onBeat(BeatUpdate(bpm, confidence, beatAt, nowMs, estimate.settled))
    }

    /**
     * How strongly the latest buffer hit, 0..1: high on drum hits, ~0 between them. Adapts to the music so
     * soft and loud songs both use the full range, but only a clear jump over the recent average counts,
     * so steady noise (a fan, traffic) stays at 0.
     */
    private fun onsetLevel(): Float {
        val raw = detector.takeOnsetPeak()
        val hit = (raw - onsetBaseline * 2.0).coerceAtLeast(0.0)
        // A plain running mean at first, so the average is right from the start, then a slow-moving one.
        onsetReads++
        onsetBaseline += (raw - onsetBaseline) * maxOf(0.03, 1.0 / onsetReads)
        onsetCeiling = maxOf(hit, onsetCeiling * 0.995)
        if (onsetReads <= ONSET_WARMUP_READS || silent || level < QUIET_RMS) return 0f
        return (hit / onsetCeiling).coerceIn(0.0, 1.0).toFloat()
    }

    private fun detectGap(bufferRms: Double, nowMs: Long) {
        val dt = (nowMs - (lastFeedMs ?: nowMs)).coerceIn(0, 500).toDouble()
        lastFeedMs = nowMs
        shortLevel += (bufferRms - shortLevel) * (1 - kotlin.math.exp(-dt / SHORT_LEVEL_MS))
        musicLevel = maxOf(shortLevel, musicLevel * kotlin.math.exp(-dt / MUSIC_LEVEL_MS))
        if (musicLevel < GAP_MIN_MUSIC_RMS || shortLevel >= musicLevel * GAP_RATIO) {
            quietSinceMs = null
            return
        }
        val since = quietSinceMs ?: nowMs.also { quietSinceMs = it }
        if (nowMs - since >= GAP_MS) {
            quietSinceMs = null
            // The quiet is the new reference: the next song has to be music again before another gap counts.
            musicLevel = shortLevel
            reset()
            listener.onSongGap()
        }
    }

    /** Fingerprint landmarks produced since the last call. Safe to call from any thread. */
    fun drainLandmarks(): List<Landmark> = synchronized(landmarks) { landmarks.toList().also { landmarks.clear() } }

    private fun reset() {
        detector.reset()
        recent.clear()
        settled = false
        onsetReads = 0
        onsetBaseline = 0.0
        onsetCeiling = 1e-6
    }

    /**
     * Median of the last three readings. For a detector that can't tell octaves apart well, octave flips are folded
     * onto the current tempo so the number stays steady.
     */
    private fun smooth(raw: Float): Float {
        val ref = recent.lastOrNull()
        val value = if (ref == null || !detector.foldsOctaves) raw else {
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
        /**
         * The network's confidence is the share of its tempo vote on the reading, so it reads as a probability. On
         * held-out songs through a simulated phone, readings at 0.4 and up are shown for ~91% of songs and right for
         * ~85% (MusicBench); room noise, chatter and clatter alone stay under it nearly always (NoiseBench).
         */
        const val MIN_CONFIDENCE = 0.4f
        /** Quick first guesses (before ~4 s) must be surer. */
        const val MIN_EARLY_CONFIDENCE = 0.5f
        /** A gap between songs: the sound stays under this share of the music's level (~ -16 dB) ... */
        const val GAP_RATIO = 0.15
        /** ... for this long. */
        const val GAP_MS = 1_000L
        /** Music this quiet is too close to the room's own noise to tell a gap from it. */
        const val GAP_MIN_MUSIC_RMS = 3e-3
        /** Time constants: the short level follows ~0.2 s; the music level sinks by e in 10 s. */
        const val SHORT_LEVEL_MS = 200.0
        const val MUSIC_LEVEL_MS = 10_000.0
        /** Weaker hits than this (relative to the song's strongest) are not reported. */
        const val HIT_MIN = 0.3f
        /** One hit can spill over two reads; hits closer than this count once. */
        const val HIT_GAP_MS = 110L
        /** Reads (~0.2 s) to learn the average level before any hit is reported. */
        const val ONSET_WARMUP_READS = 4

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
