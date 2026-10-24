package com.boimin.beatradar.ui.setup

import com.boimin.beatradar.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSetupRulesTest {

    private fun check(p: SetupPreset, min: Int, max: Int, halfDouble: Boolean) {
        val s = QuickSetupRules.apply(Settings(rangeEnabled = false, minOpen = true), p)
        assertTrue(s.rangeEnabled)
        assertEquals(min, s.targetLow)
        assertEquals(max, s.targetHigh)
        assertEquals(halfDouble, s.halfDoubleMatch)
        assertEquals(p, QuickSetupRules.presetOf(s))
    }

    @Test
    fun presetsSetTheTarget() {
        check(SetupPreset.RUN, 160, 180, halfDouble = true)
        check(SetupPreset.BRISK_WALK, 110, 125, halfDouble = false)
        check(SetupPreset.CYCLING, 80, 100, halfDouble = true)
        check(SetupPreset.DJ, 120, 130, halfDouble = true)
    }

    @Test
    fun freeTurnsAlertsOff() {
        val s = QuickSetupRules.apply(Settings(halfDoubleMatch = true), SetupPreset.FREE)
        assertFalse(s.rangeEnabled)
        assertFalse(s.halfDoubleMatch)
        // The range is kept for when alerts come back on.
        assertEquals(120, s.minBpm)
        assertEquals(SetupPreset.FREE, QuickSetupRules.presetOf(s))
    }

    @Test
    fun presetsLeaveTheRestAlone() {
        val before = Settings(vibrate = false, contribute = false, clipLimit = 50)
        val after = QuickSetupRules.apply(before, SetupPreset.RUN)
        assertEquals(before.copy(minBpm = 160, maxBpm = 180, halfDoubleMatch = true), after)
    }

    @Test
    fun ownRangeMayBeOpen() {
        val s = QuickSetupRules.custom(Settings(rangeEnabled = false), 140, null)
        assertTrue(s.rangeEnabled)
        assertEquals(140, s.targetLow)
        assertNull(s.targetHigh)
        assertNull(QuickSetupRules.presetOf(s))
    }

    @Test
    fun aNewInstallMatchesNoPreset() {
        // 120–130 without half/double: nothing is picked for the user.
        assertNull(QuickSetupRules.presetOf(Settings()))
    }

    @Test
    fun consentAsTheFirstRunQuestions() {
        val both = QuickSetupRules.consent(Settings(saveClips = false), contribute = true, shareClips = true)
        assertTrue(both.sharingConsentAsked)
        assertTrue(both.contribute)
        assertTrue(both.shareClips)
        assertTrue(both.saveClips)

        val none = QuickSetupRules.consent(Settings(saveClips = false), contribute = false, shareClips = true)
        assertTrue(none.sharingConsentAsked)
        assertFalse(none.contribute)
        assertFalse(none.shareClips)
        assertFalse(none.saveClips)

        val noClips = QuickSetupRules.consent(Settings(saveClips = true), contribute = true, shareClips = false)
        assertFalse(noClips.shareClips)
        assertTrue(noClips.saveClips)
    }

    @Test
    fun onlyNewInstallsSeeIt() {
        // An upgrade that answered the old sharing questions has no setup_done yet: done.
        assertTrue(QuickSetupRules.setupDone(stored = null, sharingConsentAsked = true))
        // A new install: not done.
        assertFalse(QuickSetupRules.setupDone(stored = null, sharingConsentAsked = false))
        // Once written, the stored value wins.
        assertFalse(QuickSetupRules.setupDone(stored = false, sharingConsentAsked = true))
        assertTrue(QuickSetupRules.setupDone(stored = true, sharingConsentAsked = false))
    }

    @Test
    fun runningAgainKeepsChoices() {
        val s = QuickSetupRules.finish(QuickSetupRules.apply(Settings(), SetupPreset.CYCLING))
        assertTrue(s.setupDone)
        val again = QuickSetupRules.again(s)
        assertFalse(again.setupDone)
        assertEquals(s.copy(setupDone = false), again)
        assertEquals(SetupPreset.CYCLING, QuickSetupRules.presetOf(again))
    }

    @Test
    fun sampleNicknamesLookLikeTheServers() {
        val nick = QuickSetupRules.sampleNickname(4821)
        assertTrue(nick, Regex("[A-Z][a-z]+ [A-Z][a-z]+ \\d{4}").matches(nick))
        assertEquals(nick, QuickSetupRules.sampleNickname(4821))
    }
}
