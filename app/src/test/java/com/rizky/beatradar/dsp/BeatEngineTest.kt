package com.rizky.beatradar.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatEngineTest {

    private val rate = 44_100

    private class Recorder : BeatEngine.Listener {
        val beats = mutableListOf<BeatUpdate>()
        val silence = mutableListOf<Boolean>()
        override fun onBeat(update: BeatUpdate) { beats += update }
        override fun onSilenceChanged(silent: Boolean) { silence += silent }
    }

    /** Feeds [pcm] in 50 ms buffers starting at [startMs]; returns the end time. */
    private fun BeatEngine.play(pcm: ShortArray, startMs: Long): Long {
        val chunk = rate / 20
        var t = startMs
        var i = 0
        while (i < pcm.size) {
            val n = minOf(chunk, pcm.size - i)
            t += 50
            feed(pcm.copyOfRange(i, i + n), n, t)
            i += n
        }
        return t
    }

    @Test fun readsContinuouslyEveryHalfSecond() {
        val rec = Recorder()
        val engine = BeatEngine(rate, rec)
        engine.play(TestSignals.toPcm16(TestSignals.clickTrack(128.0, 13.0, rate)), 0)
        // First reading after ~3 s, then every 0.5 s: about 20 readings in 13 s.
        assertTrue("got ${rec.beats.size}", rec.beats.size in 18..22)
        assertTrue("first at ${rec.beats.first().timeMs}", rec.beats.first().timeMs <= 3_600)
        rec.beats.drop(2).forEach { assertEquals(128f, it.bpm, 2f) }
    }

    @Test fun beatTimesStayOnTheGrid() {
        val rec = Recorder()
        val engine = BeatEngine(rate, rec)
        engine.play(TestSignals.toPcm16(TestSignals.clickTrack(120.0, 12.0, rate)), 0)
        // Clicks at multiples of 500 ms (feed time == audio time here).
        for (u in rec.beats.drop(2)) {
            val off = Math.floorMod(u.lastBeatAtMs, 500L)
            assertTrue("beat at ${u.lastBeatAtMs}", off < 40 || off > 460)
        }
    }

    @Test fun silenceIsReportedAndRestartsDetection() {
        val rec = Recorder()
        val engine = BeatEngine(rate, rec)
        var t = engine.play(TestSignals.toPcm16(TestSignals.clickTrack(128.0, 6.0, rate)), 0)
        t = engine.play(ShortArray(rate * 4), t)
        assertEquals(listOf(true), rec.silence)
        val before = rec.beats.size
        engine.play(TestSignals.toPcm16(TestSignals.clickTrack(100.0, 6.0, rate)), t)
        assertEquals(listOf(true, false), rec.silence)
        assertEquals(100f, rec.beats.last().bpm, 2f)
        assertTrue(rec.beats.size > before)
    }

    @Test fun noiseIsNeverABeat() {
        for (amplitude in listOf(0.0005, 0.01, 0.05)) {
            val rec = Recorder()
            val engine = BeatEngine(rate, rec)
            val r = kotlin.random.Random(amplitude.hashCode())
            engine.play(ShortArray(rate * 12) { (r.nextDouble(-amplitude, amplitude) * 32767).toInt().toShort() }, 0)
            assertTrue("noise $amplitude gave a confident beat", rec.beats.none { it.confidence >= BeatEngine.MIN_CONFIDENCE })
        }
    }

    @Test fun onsetLevelSpikesOnHitsAndRestsBetween() {
        val engine = BeatEngine(rate, Recorder())
        val pcm = TestSignals.toPcm16(TestSignals.clickTrack(120.0, 8.0, rate))
        val chunk = rate / 20
        val levels = mutableListOf<Float>()
        var t = 0L
        for (start in pcm.indices step chunk) {
            val n = minOf(chunk, pcm.size - start)
            t += 50
            engine.feed(pcm.copyOfRange(start, start + n), n, t)
            levels += engine.takeOnsetLevel()
        }
        // After warm-up: clicks every 500 ms = every 10th buffer. Hit buffers are strong, the rest near zero.
        val steady = levels.drop(40)
        val strong = steady.count { it > 0.5f }
        val quiet = steady.count { it < 0.12f }
        assertTrue("strong=$strong of ${steady.size}", strong in steady.size / 10 - 2..steady.size / 10 + 4)
        assertTrue("quiet=$quiet of ${steady.size}", quiet >= steady.size * 0.7)
    }
}
