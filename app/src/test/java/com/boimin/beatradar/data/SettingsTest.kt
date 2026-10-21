package com.boimin.beatradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun inTargetFollowsTheRangeEvenWhenAlertsAreOff() {
        val s = Settings(rangeEnabled = false, minBpm = 120, maxBpm = 130, halfDoubleMatch = true)
        assertFalse("no alerts while the range is off", s.matches(125f))
        assertTrue(s.inTarget(125f))
        assertTrue("half/double tempo counts", s.inTarget(250f))
        assertFalse(s.inTarget(140f))
        // Changing the range changes the answer for the same tempo.
        assertTrue(s.copy(minBpm = 135, maxBpm = 145).inTarget(140f))
    }

    @Test fun openEndedTarget() {
        val atLeast = Settings(minBpm = 140, maxBpm = 150, maxOpen = true)
        assertTrue("no upper limit", atLeast.inTarget(190f))
        assertFalse(atLeast.inTarget(139f))
        assertEquals(140, atLeast.targetLow)
        assertEquals(null, atLeast.targetHigh)
        assertEquals(140..Settings.BPM_CEILING, atLeast.targetRange)

        val upTo = Settings(minBpm = 120, maxBpm = 110, minOpen = true)
        assertTrue("no lower limit", upTo.inTarget(70f))
        assertFalse(upTo.inTarget(111f))
        assertEquals(Settings.BPM_FLOOR..110, upTo.targetRange)
    }

    @Test fun openEndCountsHalfDoubleBeyondTheScale() {
        // 105 heard as half of 210: at least 140 with half/double on.
        assertTrue(Settings(minBpm = 140, maxOpen = true, halfDoubleMatch = true).inTarget(105f))
        assertFalse(Settings(minBpm = 140, maxOpen = true).inTarget(105f))
    }

    @Test fun anyBpmRangeWithHalfDouble() {
        assertTrue(Settings.bpmIn(100f, 95..110, halfDouble = false))
        assertFalse(Settings.bpmIn(200f, 95..110, halfDouble = false))
        assertTrue(Settings.bpmIn(200f, 95..110, halfDouble = true))
    }

    @Test fun firstRunQuestionsAreAskedAndClipsStayOffUntilAllowed() {
        val s = Settings()
        assertFalse(s.sharingConsentAsked)
        assertTrue(s.contribute)
        assertFalse(s.shareClips)
    }

    @Test fun setNamesAndTempoSpans() {
        assertEquals("Morning run", SetRules.cleanName("  Morning   run "))
        assertEquals(null, SetRules.cleanName("   "))
        assertEquals(SetRules.MAX_NAME, SetRules.cleanName("x".repeat(100))!!.length)
        assertEquals(null, SetRules.spanBpm(null, null))
        assertEquals(124..124, SetRules.spanBpm(124.2f, 123.8f))
        assertEquals(118..128, SetRules.spanBpm(118f, 128.4f))
    }
}
