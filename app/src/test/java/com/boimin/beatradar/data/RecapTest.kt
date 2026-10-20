package com.boimin.beatradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun entry(daysAgo: Int, bpm: Float, title: String? = "Song $bpm", plays: Int = 1, inRange: Boolean = false) =
        BeatLogEntry(timestamp = now - daysAgo * day, title = title, artist = "A", sourceApp = null, bpm = bpm, confidence = 0.5f, inRange = inRange, playCount = plays)

    @Test fun summarisesTheLastSevenDays() {
        val log = listOf(
            entry(1, 128f, plays = 3, inRange = true),
            entry(2, 124f, inRange = true),
            entry(3, 172f),
            entry(6, 87f, title = null),
            entry(9, 140f), // last week
        )
        val r = RecapCalc.week(log, now)
        assertEquals(4, r.songs)
        assertEquals(6, r.plays)
        assertEquals(2, r.inTarget)
        assertEquals(120, r.topBand)
        assertEquals(4, r.bands.first { it.first == 120 }.second)
        assertEquals("Song 128.0", r.topSongs.first().first)
        assertEquals(3, r.topSongs.size, "unnamed songs aren't top songs")
        assertFalse(r.worthSharing)
    }

    @Test fun emptyWeek() {
        val r = RecapCalc.week(emptyList(), now)
        assertEquals(0, r.songs)
        assertNull(r.topBand)
        assertNull(r.averageBpm)
    }

    @Test fun enoughSongsAreWorthACard() {
        assertTrue(RecapCalc.week((1..5).map { entry(1, 100f + it) }, now).worthSharing)
    }

    @Test fun bandsStayOnTheDial() {
        assertEquals(60, RecapCalc.band(55f))
        assertEquals(120, RecapCalc.band(129.4f))
        assertEquals(190, RecapCalc.band(200f))
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
}
