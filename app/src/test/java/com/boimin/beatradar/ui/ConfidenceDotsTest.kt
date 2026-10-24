package com.boimin.beatradar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceDotsTest {

    @Test
    fun dotsFollowConfidence() {
        assertEquals(0, confidenceDots(0))
        assertEquals(1, confidenceDots(1))
        assertEquals(1, confidenceDots(29))
        assertEquals(2, confidenceDots(30))
        assertEquals(2, confidenceDots(54))
        assertEquals(3, confidenceDots(55))
        assertEquals(3, confidenceDots(79))
        assertEquals(CONFIDENCE_DOTS, confidenceDots(80))
        assertEquals(CONFIDENCE_DOTS, confidenceDots(100))
    }

    @Test
    fun anUnsureBeatSkipsAroundTheDots() {
        val dots = (0 until 64).map(::skippedDot)
        assertTrue(dots.all { it in 0 until CONFIDENCE_DOTS })
        // Not just counting 1-2-3-4, and every dot gets a turn.
        assertTrue(dots != (0 until 64).map { it % CONFIDENCE_DOTS })
        assertEquals((0 until CONFIDENCE_DOTS).toSet(), dots.toSet())
    }
}
