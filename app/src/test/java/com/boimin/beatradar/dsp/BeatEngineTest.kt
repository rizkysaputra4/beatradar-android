package com.boimin.beatradar.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatEngineTest {

    private val rate = 44_100

    private class Recorder : BeatEngine.Listener {
        val beats = mutableListOf<BeatUpdate>()
        val silence = mutableListOf<Boolean>()
        val hits = mutableListOf<Long>()
        override fun onBeat(update: BeatUpdate) { beats += update }
        override fun onHit(atMs: Long, strength: Float) { hits += atMs }
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
        // A quick guess after ~1.5 s, settled from ~4 s, then every 0.5 s: about 23 readings in 13 s.
        assertTrue("got ${rec.beats.size}", rec.beats.size in 21..25)
        assertTrue("first at ${rec.beats.first().timeMs}", rec.beats.first().timeMs <= 2_100)
        val settled = rec.beats.filter { it.settled }
        assertTrue("first settled at ${settled.first().timeMs}", settled.first().timeMs <= 4_600)
        settled.drop(2).forEach { assertEquals(128f, it.bpm, 2f) }
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

    @Test fun reportsEachHitRightAway() {
        val rec = Recorder()
        val engine = BeatEngine(rate, rec)
        engine.play(TestSignals.toPcm16(TestSignals.clickTrack(120.0, 8.0, rate)), 0)
        // Clicks every 500 ms: one hit each from the second click on (the first ~0.2 s is warm-up),
        // reported within a buffer or two.
        assertTrue("first hit at ${rec.hits.firstOrNull()}", (rec.hits.firstOrNull() ?: Long.MAX_VALUE) <= 600)
        assertTrue("got ${rec.hits.size} hits for 15 clicks", rec.hits.size == 15)
        rec.hits.forEach { at ->
            val late = at - (at / 500) * 500
            assertTrue("hit at $at is $late ms after its click", late <= 110)
        }
    }

    @Test fun steadyNoiseMakesNoHits() {
        for (amplitude in listOf(0.01, 0.05)) {
            val rec = Recorder()
            val engine = BeatEngine(rate, rec)
            val r = kotlin.random.Random(amplitude.hashCode())
            engine.play(ShortArray(rate * 12) { (r.nextDouble(-amplitude, amplitude) * 32767).toInt().toShort() }, 0)
            assertTrue("noise $amplitude gave ${rec.hits.size} hits", rec.hits.size <= 2)
        }
    }
}
