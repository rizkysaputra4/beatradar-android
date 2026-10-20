package com.boimin.beatradar.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlameTest {
    @Test fun growsAStepAtATimeWithContributions() {
        assertEquals(0, Flame.level(0))
        assertEquals(Flame.MIN_DP, Flame.sizeDp(0))
        assertEquals(1, Flame.level(1))
        assertEquals(2, Flame.level(25))
        assertEquals(2, Flame.level(99))
        assertEquals(Flame.STEPS.size, Flame.level(1_000_000))
        val sizes = listOf(0L, 1, 25, 100, 300, 1_000, 3_000, 10_000).map(Flame::sizeDp)
        assertTrue("every step is bigger", sizes.zipWithNext().all { (a, b) -> b > a })
        assertEquals(26, Flame.sizeDp(1_000_000))
    }
}
