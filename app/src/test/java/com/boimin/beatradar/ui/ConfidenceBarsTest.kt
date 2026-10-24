package com.boimin.beatradar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceBarsTest {

    @Test
    fun barsFollowConfidence() {
        assertEquals(0, confidenceBars(0))
        assertEquals(1, confidenceBars(1))
        assertEquals(1, confidenceBars(29))
        assertEquals(2, confidenceBars(30))
        assertEquals(2, confidenceBars(54))
        assertEquals(3, confidenceBars(55))
        assertEquals(3, confidenceBars(79))
        assertEquals(CONFIDENCE_BARS, confidenceBars(80))
        assertEquals(CONFIDENCE_BARS, confidenceBars(100))
    }
}
