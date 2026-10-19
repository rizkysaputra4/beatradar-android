package com.boimin.beatradar.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class FingerprinterTest {

    private val rate = 11_025

    /** Unrelated music: a random chord every 250 ms with its own kick, fixed by [seed]. */
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
            0.5f * lp + r.nextDouble(-0.03, 0.03).toFloat()
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
        val full = songWithDrums(128.0, seed = 7, seconds = 40.0)
        val library = stored(1, landmarks(full)) + stored(2, landmarks(songWithDrums(140.0, seed = 99, seconds = 40.0)))
        // 12 s from the middle, heard through "another speaker".
        val clip = throughSpeaker(full.copyOfRange(rate * 21, rate * 33), seed = 3)
        val m = FingerprintMatcher.best(landmarks(clip), library)
        requireNotNull(m)
        assertTrue("matched ${m.entryId} with score ${m.score}", m.entryId == 1L && m.score >= FingerprintMatcher.MIN_SCORE + 2)
    }

    @Test fun differentSongsDoNotMatch() {
        val library = stored(1, landmarks(songWithDrums(128.0, seed = 7, seconds = 40.0)))
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

    /** Melody (chords every half beat) over the same kick + hat sound every song shares. */
    private fun songWithDrums(bpm: Double, seed: Int, seconds: Double): FloatArray {
        val r = Random(seed)
        val out = FloatArray((seconds * rate).toInt())
        val beat = 60.0 / bpm
        val notes = doubleArrayOf(196.0, 220.0, 247.0, 262.0, 294.0, 330.0, 349.0, 392.0, 440.0, 494.0, 523.0, 587.0, 659.0, 698.0, 784.0, 880.0, 988.0, 1047.0)
        var t = 0.0
        while (t < seconds) {
            val s0 = (t * rate).toInt()
            val e = minOf(out.size, ((t + beat / 2) * rate).toInt())
            val fr = DoubleArray(3) { notes[r.nextInt(notes.size)] }
            for (k in s0 until e) {
                val tt = (k - s0).toDouble() / rate
                var v = 0.0
                for (f in fr) v += 0.08 * sin(2 * PI * f * tt)
                out[k] += (v * exp(-tt * 3)).toFloat()
            }
            t += beat / 2
        }
        var b = 0
        while (b * beat < seconds) {
            val s0 = (b * beat * rate).toInt()
            for (k in 0 until (0.15 * rate).toInt()) {
                if (s0 + k >= out.size) break
                val tt = k.toDouble() / rate
                out[s0 + k] += (0.8 * sin(2 * PI * (60 + 90 * exp(-tt * 30)) * tt) * exp(-tt * 18)).toFloat()
            }
            b++
        }
        return out
    }

    @Test fun sameDrumsDifferentSongsDoNotMatch() {
        val library = stored(1, landmarks(songWithDrums(128.0, seed = 7, seconds = 40.0)))
        for ((bpm, seed) in listOf(124.0 to 33, 128.0 to 34, 100.0 to 21)) {
            val clip = songWithDrums(bpm, seed, 12.0)
            val score = FingerprintMatcher.best(landmarks(clip), library)?.score ?: 0
            assertTrue("different song ($bpm BPM, seed $seed) scored $score", score < FingerprintMatcher.MIN_SCORE)
        }
    }

}
