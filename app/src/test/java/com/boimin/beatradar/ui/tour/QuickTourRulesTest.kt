package com.boimin.beatradar.ui.tour

import com.boimin.beatradar.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTourRulesTest {

    @Test
    fun newInstallsGetEachTipAfterQuickSetup() {
        // Never written, mid-setup: none seen. Every write stores the set, so finishing setup keeps it empty.
        assertEquals(emptySet<String>(), QuickTourRules.tipsSeen(stored = null, setupDone = false))
        val s = Settings(setupDone = true)
        listOf(TourTab.LOG, TourTab.LIBRARY, TourTab.SETS).forEach { assertTrue(QuickTourRules.showsTip(s, it)) }
        // Never over Quick Setup.
        assertFalse(QuickTourRules.showsTip(Settings(setupDone = false), TourTab.LOG))
    }

    @Test
    fun listensHintIsInThePageFromTheStart() {
        val fresh = Settings(setupDone = true)
        // No coach mark over Listen: its hint sits under the target chip, right after Quick Setup.
        assertTrue(QuickTourRules.tipsFor(TourTab.LISTEN).isEmpty())
        assertFalse(QuickTourRules.showsTip(fresh, TourTab.LISTEN))
        assertTrue(QuickTourRules.showsListenHint(fresh))
        assertFalse(QuickTourRules.showsListenHint(Settings(setupDone = false)))
        assertFalse(QuickTourRules.showsListenHint(QuickTourRules.tipSeen(fresh, TourTab.LISTEN)))
    }

    @Test
    fun upgradesDontGetTipsUninvited() {
        val seen = QuickTourRules.tipsSeen(stored = null, setupDone = true)
        val s = Settings(setupDone = true, tipsSeen = seen)
        TourTab.entries.forEach { assertFalse(QuickTourRules.showsTip(s, it)) }
    }

    @Test
    fun aTipShowsOnce() {
        val s = QuickTourRules.tipSeen(Settings(setupDone = true), TourTab.LOG)
        assertFalse(QuickTourRules.showsTip(s, TourTab.LOG))
        assertTrue(QuickTourRules.showsTip(s, TourTab.SETS))
        // Walking the whole tour covers them all.
        val toured = QuickTourRules.allTipsSeen(s)
        QuickTourRules.TIP_TABS.forEach { assertFalse(QuickTourRules.showsTip(toured, it)) }
        assertEquals(s.copy(tipsSeen = toured.tipsSeen), toured)
    }

    @Test
    fun tipsAreTheirTabsTourSteps() {
        listOf(TourTab.LOG, TourTab.LIBRARY, TourTab.SETS).forEach { tab ->
            val steps = QuickTourRules.tipsFor(tab)
            assertTrue(steps.isNotEmpty())
            steps.forEach { step ->
                assertEquals(tab, step.tab)
                assertNull(step.goTo)
            }
        }
        // Sets, the last tab, also shows where the leaderboard and Settings are.
        assertEquals(
            listOf(TourTarget.SETS_ACTIONS, TourTarget.HEADER_BEATS, TourTarget.HEADER_PROFILE),
            QuickTourRules.tipsFor(TourTab.SETS).map { it.target },
        )
    }

    @Test
    fun stepsWalkEveryTab() {
        assertEquals(TourTab.entries.toSet(), QuickTourRules.STEPS.map { it.tab }.toSet())
        // A saved index out of range stays in it.
        assertEquals(QuickTourRules.STEPS.first(), QuickTourRules.step(-1))
        assertEquals(QuickTourRules.STEPS.last(), QuickTourRules.step(99))
        assertTrue(QuickTourRules.isLast(QuickTourRules.STEPS.lastIndex))
        assertFalse(QuickTourRules.isLast(0))
    }

    @Test
    fun tabsChangeOnlyThroughAStepThatPointsAtThem() {
        QuickTourRules.STEPS.zipWithNext().forEach { (step, next) ->
            if (next.tab != step.tab) {
                assertEquals("$step should point at ${next.tab}", next.tab, step.goTo)
                assertEquals(QuickTourRules.fallback(next.tab), step.target)
            } else {
                assertEquals("$step moves nowhere", null, step.goTo)
            }
        }
    }

    @Test
    fun everyTabHasAFallbackTarget() {
        assertEquals(TourTab.entries.size, TourTab.entries.map(QuickTourRules::fallback).toSet().size)
    }
}
