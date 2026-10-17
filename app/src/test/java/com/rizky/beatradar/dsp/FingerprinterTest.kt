package com.rizky.beatradar.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class FingerprinterTest {

    private val rate = 11_025

    /** A synthetic "song": a random chord every 250 ms over a drum pattern, fixed by [seed]. */
    private fun song(seed: Int, seconds: Double): FloatArray {
        val r = Random(seed)
        val out = FloatArray((seconds * rate).toInt())
        val step = rate / 4
        var start = 0
        while (start < out.size) {
            val freqs = DoubleArray(3) { 150.0 + r.nextDouble() * 2500 }
            for (k in 0 until minOf(step, out.size - start)) {
                val t = k.toDouble() / rate
                var v = 0.0
                for (f in freqs) v += sin(2 * PI * f * t) * 0.15
                v += 0.4 * sin(2 * PI * 60 * t) * exp(-t * 20) // kick
                out[start + k] = v.toFloat()
            }
            start += step
        }
        return out
    }

    /** Simulates a different speaker and room: gain change, muffled highs, background noise. */
    private fun throughSpeaker(x: FloatArray, seed: Int): FloatArray {
        val r = Random(seed)
        var lp = 0f
        return FloatArray(x.size) {
            lp = 0.6f * lp + 0.4f * x[it]
            0.5f * lp + r.nextDouble(-0.08, 0.08).toFloat()
        }
    }

    private fun landmarks(x: FloatArray): List<Landmark> {
        val f = Fingerprinter(rate)
        val out = mutableListOf<Landmark>()
        var i = 0
        while (i < x.size) {
            val n = minOf(rate / 20, x.size - i)
            f.process(x.copyOfRange(i, i + n), n) { out += it }
            i += n
        }
        return out
    }

    private fun stored(id: Long, lms: List<Landmark>) = lms.map { FingerprintMatcher.Stored(id, it.hash, it.frame) }

    @Test fun recognisesTheSameSongFromAnotherSpeakerAndAnotherPart() {
        val full = song(seed = 7, seconds = 40.0)
        val library = stored(1, landmarks(full)) + stored(2, landmarks(song(seed = 99, seconds = 40.0)))
        // 12 s from the middle, heard through "another speaker".
        val clip = throughSpeaker(full.copyOfRange(rate * 21, rate * 33), seed = 3)
        val m = FingerprintMatcher.best(landmarks(clip), library)
        requireNotNull(m)
        assertTrue("matched ${m.entryId} with score ${m.score}", m.entryId == 1L && m.score >= FingerprintMatcher.MIN_SCORE + 2)
    }

    @Test fun differentSongsDoNotMatch() {
        val library = stored(1, landmarks(song(seed = 7, seconds = 40.0)))
        for (seed in listOf(11, 12, 13)) {
            val clip = throughSpeaker(song(seed, 12.0), seed)
            val score = FingerprintMatcher.best(landmarks(clip), library)?.score ?: 0
            assertTrue("unrelated song $seed scored $score", score < FingerprintMatcher.MIN_SCORE)
        }
    }

    @Test fun keepsAboutTenLandmarksPerSecond() {
        val lms = landmarks(song(seed = 5, seconds = 20.0))
        val perSecond = lms.size / 20.0
        assertTrue("landmarks per second: $perSecond", perSecond in 5.0..20.0)
    }
}
