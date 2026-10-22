package com.boimin.beatradar.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {

    @Test
    fun needFollowsMinimumAndLatest() {
        assertEquals(UpdateNeed.NONE, UpdateRules.need(current = 3, min = 0, latest = 0))
        assertEquals(UpdateNeed.NONE, UpdateRules.need(current = 3, min = 3, latest = 3))
        assertEquals(UpdateNeed.SUGGESTED, UpdateRules.need(current = 3, min = 2, latest = 4))
        assertEquals(UpdateNeed.REQUIRED, UpdateRules.need(current = 3, min = 4, latest = 5))
        // A minimum alone (latest not set) still forces.
        assertEquals(UpdateNeed.REQUIRED, UpdateRules.need(current = 3, min = 4, latest = 0))
    }

    @Test
    fun suggestionIsOfferedOncePerVersion() {
        assertTrue(UpdateRules.offerSuggestion(UpdateNeed.SUGGESTED, latest = 4, dismissedLatest = 0))
        assertFalse(UpdateRules.offerSuggestion(UpdateNeed.SUGGESTED, latest = 4, dismissedLatest = 4))
        assertTrue(UpdateRules.offerSuggestion(UpdateNeed.SUGGESTED, latest = 5, dismissedLatest = 4))
        assertFalse(UpdateRules.offerSuggestion(UpdateNeed.REQUIRED, latest = 5, dismissedLatest = 0))
        assertFalse(UpdateRules.offerSuggestion(UpdateNeed.NONE, latest = 5, dismissedLatest = 0))
    }

    @Test
    fun refusalRequiresANewerBuild() {
        assertEquals(4, UpdateRules.minAfterRefusal(current = 3, knownMin = 0))
        assertEquals(6, UpdateRules.minAfterRefusal(current = 3, knownMin = 6))
    }
}
