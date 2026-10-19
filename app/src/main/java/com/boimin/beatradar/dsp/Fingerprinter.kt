package com.boimin.beatradar.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln1p

/** One fingerprint landmark: a hash of two spectral peaks and when the first one happened. */
data class Landmark(val hash: Int, val frame: Int)

/**
 * Audio fingerprint in the style of Shazam's "constellation" method: find strong, isolated spectral
 * peaks, pair nearby peaks, and hash (frequency 1, frequency 2, time gap). The same recording heard
 * through different speakers and rooms keeps most of its peaks, so its hashes line up again.
 *
 * Only one in four hashes is kept (chosen by hash value, so every device keeps the same ones) to keep
 * storage small: ~10–15 landmarks per second of music.
 */
class Fingerprinter(sampleRate: Int = 11_025) {

    private val fft = Fft(FRAME)
    private val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / FRAME) }
    private val frame = FloatArray(FRAME)
    private var fill = 0
    private val re = DoubleArray(FRAME)
    private val im = DoubleArray(FRAME)

    /** Band edges in FFT bins (~11 Hz each at 11 kHz): roughly 100 Hz … 5 kHz, one octave per band. */
    private val bands = intArrayOf(9, 19, 37, 74, 149, 297, 464).map { (it * 11_025.0 / sampleRate).toInt() }
    private val bandCount = bands.size - 1

    // The last (2 * NEIGHBOURHOOD + 1) frames of per-band maxima, for "is this a local peak in time" checks.
    private val span = 2 * NEIGHBOURHOOD + 1
    private val bandBin = Array(span) { IntArray(bandCount) }
    private val bandValue = Array(span) { DoubleArray(bandCount) }
    private val floor = DoubleArray(bandCount)
    private var frameIndex = 0

    private val recentPeaks = ArrayDeque<Long>() // packed (frame shl 16) or bin

    fun reset() {
        fill = 0
        frameIndex = 0
        recentPeaks.clear()
        floor.fill(0.0)
    }

    /** Feeds mono samples in [-1, 1]; calls [emit] for each kept landmark. */
    fun process(samples: FloatArray, count: Int, emit: (Landmark) -> Unit) {
        var i = 0
        while (i < count) {
            val take = minOf(FRAME - fill, count - i)
            System.arraycopy(samples, i, frame, fill, take)
            fill += take
            i += take
            if (fill == FRAME) {
                analyseFrame(emit)
                System.arraycopy(frame, HOP, frame, 0, FRAME - HOP)
                fill = FRAME - HOP
            }
        }
    }

    private fun analyseFrame(emit: (Landmark) -> Unit) {
        for (k in 0 until FRAME) {
            re[k] = frame[k] * window[k]
            im[k] = 0.0
        }
        fft.transform(re, im)
        val slot = frameIndex % span
        for (b in 0 until bandCount) {
            var bestBin = bands[b]
            var best = -1.0
            for (k in bands[b] until bands[b + 1]) {
                val v = ln1p(100 * hypot(re[k], im[k]))
                if (v > best) { best = v; bestBin = k }
            }
            bandBin[slot][b] = bestBin
            bandValue[slot][b] = best
        }

        // Decide peaks for the frame in the middle of the neighbourhood, now that its future is known.
        val centre = frameIndex - NEIGHBOURHOOD
        if (centre >= NEIGHBOURHOOD) {
            val cSlot = centre % span
            for (b in 0 until bandCount) {
                val v = bandValue[cSlot][b]
                floor[b] = 0.95 * floor[b] + 0.05 * v
                var isPeak = v > floor[b] + PEAK_MARGIN
                var f = centre - NEIGHBOURHOOD
                while (isPeak && f <= centre + NEIGHBOURHOOD) {
                    if (f != centre && bandValue[f % span][b] >= v) isPeak = false
                    f++
                }
                // The lowest band is kick drum and bass: shared by countless songs, so it never identifies one.
                if (isPeak && b > 0) addPeak(centre, bandBin[cSlot][b], emit)
            }
        }
        frameIndex++
    }

    private fun addPeak(frameNo: Int, bin: Int, emit: (Landmark) -> Unit) {
        while (recentPeaks.isNotEmpty() && frameNo - (recentPeaks.first() shr 16).toInt() > MAX_DT) recentPeaks.removeFirst()
        // Pair with the nearest earlier peaks (anchors) inside the target zone.
        var paired = 0
        for (idx in recentPeaks.indices.reversed()) {
            val packed = recentPeaks[idx]
            val anchorFrame = (packed shr 16).toInt()
            val anchorBin = (packed and 0xFFFF).toInt()
            val dt = frameNo - anchorFrame
            if (dt < 1) continue
            // Bins are halved so small speaker/room shifts still hash the same.
            val df = (bin shr 1) - (anchorBin shr 1)
            if (df < -63 || df > 63) continue
            // 8 bits anchor | 7 bits gap | 6 bits time gap.
            val hash = ((anchorBin shr 1) shl 13) or ((df + 64) shl 6) or dt
            if (keep(hash)) emit(Landmark(hash, anchorFrame))
            if (++paired == FAN_OUT) break
        }
        recentPeaks.addLast((frameNo.toLong() shl 16) or bin.toLong())
    }

    companion object {
        private const val FRAME = 1024
        private const val HOP = 512
        private const val NEIGHBOURHOOD = 3
        private const val PEAK_MARGIN = 0.5
        private const val MAX_DT = 63
        private const val FAN_OUT = 3

        /** Fingerprint frames per second at 11 025 Hz. */
        const val FRAMES_PER_SECOND = 11_025.0 / HOP

        /** Deterministic 1-in-4 selection by hash value. */
        internal fun keep(hash: Int): Boolean = (hash * -1640531535) ushr 30 == 0
    }
}

/** Finds which stored song a query best lines up with. */
object FingerprintMatcher {
    /**
     * Landmarks agreeing on one offset needed to call two recordings the same song. In tests, 12 s of
     * the same song through another speaker scores ~17; different songs with identical drums score
     * up to 5; unrelated music 0–1.
     */
    const val MIN_SCORE = 8

    /** Query hashes seen more often than this in one clip are ignored. */
    private const val MAX_REPEATS = 2

    data class Stored(val entryId: Long, val hash: Int, val frame: Int)
    data class Match(val entryId: Long, val score: Int)

    /**
     * Score = the largest number of query landmarks that agree on one time offset (±1 frame) against
     * one stored song. Unrelated music scatters across offsets; the same recording piles up.
     */
    fun best(query: List<Landmark>, stored: List<Stored>): Match? {
        if (query.isEmpty() || stored.isEmpty()) return null
        val byHash = HashMap<Int, MutableList<Int>>()
        for (q in query) byHash.getOrPut(q.hash) { mutableListOf() }.add(q.frame)
        // A hash that keeps repeating within the clip comes from a looped sound (a drum hit), which many
        // songs share; it says nothing about which song this is.
        byHash.values.removeAll { it.size > MAX_REPEATS }
        val counts = HashMap<Long, HashMap<Int, Int>>()
        for (s in stored) {
            val frames = byHash[s.hash] ?: continue
            val perOffset = counts.getOrPut(s.entryId) { HashMap() }
            for (qf in frames) perOffset.merge(s.frame - qf, 1, Int::plus)
        }
        var best: Match? = null
        for ((entry, offsets) in counts) {
            var top = 0
            for ((offset, c) in offsets) {
                val score = c + (offsets[offset - 1] ?: 0) + (offsets[offset + 1] ?: 0)
                if (score > top) top = score
            }
            if (best == null || top > best.score) best = Match(entry, top)
        }
        return best
    }
}
