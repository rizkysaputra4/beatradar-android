package com.boimin.beatradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SettingsTest {

    @Test fun clipDefaults() {
        val s = Settings()
        assertTrue(s.saveClips)
        assertTrue(s.clipAllSongs)
        assertEquals(30, s.clipLimit)
        assertTrue(s.clipLimit in Settings.CLIP_LIMITS)
        assertEquals(listOf(10, 30, 50, 100), Settings.CLIP_LIMITS)
    }

    @Test fun matchesInsideRangeOnly() {
        val s = Settings(rangeEnabled = true, minBpm = 120, maxBpm = 130)
        assertTrue(s.matches(120f))
        assertTrue(s.matches(129.6f))
        assertFalse(s.matches(131f))
        assertFalse(s.matches(65f))
    }

    @Test fun rangeOffNeverMatches() {
        assertFalse(Settings(rangeEnabled = false).matches(125f))
    }

    @Test fun halfDoubleMatching() {
        val s = Settings(minBpm = 120, maxBpm = 130, halfDoubleMatch = true)
        assertTrue(s.matches(62.5f))
        assertTrue(s.matches(250f))
        assertFalse(s.matches(70f))
    }

    @Test fun csvEscapesCommasAndQuotes() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"Hello, \"\"World\"\"\"", CsvExport.escape("Hello, \"World\""))
    }

    @Test fun csvHasHeaderAndRow() {
        val out = ByteArrayOutputStream()
        CsvExport.write(listOf(BeatLogEntry(1, 0, "Song", "Artist, Band", "yt", 128f, 0.5f, true)), out)
        val lines = out.toString().trim().lines()
        assertEquals(2, lines.size)
        assertEquals("1970-01-01T00:00:00Z,Song,\"Artist, Band\",yt,128.0,0.50,true", lines[1])
    }
}
