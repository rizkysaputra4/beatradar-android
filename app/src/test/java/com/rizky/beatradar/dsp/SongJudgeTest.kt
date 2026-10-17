package com.rizky.beatradar.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongJudgeTest {

    private val range = { bpm: Float -> bpm in 120f..130f }

    /** Feeds one reading every 500 ms from [fromMs] to [toMs] (exclusive) and returns all verdicts. */
    private fun SongJudge.run(bpm: (Long) -> Float, fromMs: Long, toMs: Long, conf: Float = 0.3f, rangeOn: Boolean = true) =
        (fromMs until toMs step 500).map { t -> onBeat(bpm(t), conf, t, rangeOn, range) }

    @Test fun matchesAfterTenSecondsInRange() {
        val j = SongJudge()
        val verdicts = j.run({ 125f }, 0, 12_000)
        val firstMatch = verdicts.indexOfFirst { it.matched }
        assertEquals(20, firstMatch) // t = 10 000 ms
        assertEquals(0.5f, verdicts[10].progress, 0.01f)
        val events = verdicts.flatMap { it.events }
        assertEquals(1, events.filterIsInstance<SongJudge.Event.Match>().size)
        assertEquals(1, events.filterIsInstance<SongJudge.Event.SongLogged>().size)
        assertTrue(verdicts[20].events.any { it is SongJudge.Event.Match })
    }

    @Test fun leavingTheRangeRestartsTheCountdown() {
        val j = SongJudge()
        j.run({ 125f }, 0, 6_000)
        val out = j.run({ 140f }, 6_000, 8_000)
        assertEquals(0f, out.last().progress)
        val back = j.run({ 125f }, 8_000, 18_500)
        assertFalse(back.first().matched)
        assertTrue(back.last().matched)
    }

    @Test fun shortDipIsForgiven() {
        val j = SongJudge()
        j.run({ 125f }, 0, 5_000)
        j.run({ 133f }, 5_000, 6_000) // two readings outside: under the 1.5 s grace
        val rest = j.run({ 125f }, 6_000, 10_500)
        assertTrue(rest.last().matched)
    }

    @Test fun vibratesOncePerStayInRange() {
        val j = SongJudge()
        val events = j.run({ 125f }, 0, 40_000).flatMap { it.events }
        assertEquals(1, events.filterIsInstance<SongJudge.Event.Match>().size)
    }

    @Test fun logModeLogsEachSteadySongOnce() {
        val j = SongJudge()
        val events = j.run({ t -> if (t < 15_000) 98f else 140f }, 0, 30_000, rangeOn = false).flatMap { it.events }
        assertEquals(listOf(98f, 140f), events.filterIsInstance<SongJudge.Event.SongLogged>().map { it.bpm })
        assertTrue(events.none { it is SongJudge.Event.Match })
    }

    @Test fun nextSongInRangeGetsItsOwnMatch() {
        val j = SongJudge()
        val events = j.run({ t -> if (t < 15_000) 121f else 128f }, 0, 30_000).flatMap { it.events }
        assertEquals(2, events.filterIsInstance<SongJudge.Event.Match>().size)
    }

    @Test fun lowConfidenceDoesNotCount() {
        val j = SongJudge()
        val v = j.run({ 125f }, 0, 15_000, conf = 0.01f)
        assertTrue(v.all { it.progress == 0f && it.events.isEmpty() })
    }

    @Test fun songLoggedFirstThenMatchedLaterIsReportedAsMatch() {
        val j = SongJudge()
        // Steady but out of range for 12 s (logged), then a new song in range.
        val e1 = j.run({ 100f }, 0, 12_000).flatMap { it.events }
        assertEquals(listOf(false), e1.filterIsInstance<SongJudge.Event.SongLogged>().map { it.matched })
        val e2 = j.run({ 125f }, 12_000, 23_000).flatMap { it.events }
        assertEquals(1, e2.filterIsInstance<SongJudge.Event.Match>().size)
    }
}

