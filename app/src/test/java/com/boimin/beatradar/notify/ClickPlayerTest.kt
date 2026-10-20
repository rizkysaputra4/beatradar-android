package com.boimin.beatradar.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickPlayerTest {

    @Test fun oneBarIsFourBeatsAtTheTempo() {
        // 120 BPM: 0.5 s a beat, 2 s a bar at 44.1 kHz.
        assertEquals(88_200, ClickPlayer.bar(120).size)
        assertEquals(4 * (44_100 * 60.0 / 126).toInt().let { if ((44_100 * 60.0 / 126) - it >= 0.5) it + 1 else it }, ClickPlayer.bar(126).size)
    }

    @Test fun clicksStartOnEveryBeatAndTheFirstIsAccented() {
        val bar = ClickPlayer.bar(120)
        val beat = bar.size / 4
        val peak = { from: Int -> (from until from + 600).maxOf { abs(bar[it].toInt()) } }
        assertTrue(peak(0) > peak(beat))
        for (b in 1 until 4) assertTrue(peak(b * beat) > 5_000)
        assertEquals(0, bar[beat - 1].toInt(), "silence before the next click")
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
}
