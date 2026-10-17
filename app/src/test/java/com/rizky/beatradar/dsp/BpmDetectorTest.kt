package com.rizky.beatradar.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BpmDetectorTest {

    private val rate = 11_025

    /** The newest estimate, with its beat phase re-expressed relative to the end of [samples]. */
    private fun lastEstimate(samples: FloatArray): BpmEstimate? {
        val detector = OnsetBpmDetector(sampleRate = rate)
        var last: BpmEstimate? = null
        var samplesSince = 0
        val chunk = rate / 10
        var i = 0
        while (i < samples.size) {
            val n = minOf(chunk, samples.size - i)
            val e = detector.process(samples.copyOfRange(i, i + n))
            if (e != null) { last = e; samplesSince = 0 } else samplesSince += n
            i += n
        }
        return last?.let { it.copy(secondsSinceBeat = it.secondsSinceBeat + samplesSince.toFloat() / rate) }
    }

    private fun assertTempo(expected: Double, samples: FloatArray) {
        val estimate = lastEstimate(samples)
        requireNotNull(estimate) { "no estimate for $expected BPM" }
        assertEquals("tempo for $expected BPM", expected, estimate.bpm.toDouble(), 2.0)
        assertTrue("confidence ${estimate.confidence}", estimate.confidence > 0.1f)
    }

    @Test fun clickTrack60() = assertTempo(60.0, TestSignals.clickTrack(60.0, 14.0, rate))
    @Test fun clickTrack90() = assertTempo(90.0, TestSignals.clickTrack(90.0, 14.0, rate))
    @Test fun clickTrack128() = assertTempo(128.0, TestSignals.clickTrack(128.0, 14.0, rate))
    @Test fun clickTrack174() = assertTempo(174.0, TestSignals.clickTrack(174.0, 14.0, rate))
    @Test fun fractionalTempo() = assertTempo(123.5, TestSignals.clickTrack(123.5, 14.0, rate))
    @Test fun drumLoopWithHats100() = assertTempo(100.0, TestSignals.drumLoop(100.0, 14.0, rate))
    @Test fun drumLoopWithHats140() = assertTempo(140.0, TestSignals.drumLoop(140.0, 14.0, rate))

    @Test fun silenceGivesNoEstimate() = assertNull(lastEstimate(FloatArray(rate * 12)))

    @Test fun firstEstimateArrivesWithinFourSeconds() {
        val estimate = lastEstimate(TestSignals.clickTrack(128.0, 3.6, rate))
        requireNotNull(estimate) { "no estimate after 3.6 s" }
        assertEquals(128.0, estimate.bpm.toDouble(), 3.0)
    }

    @Test fun nothingBeforeThreeSeconds() = assertNull(lastEstimate(TestSignals.clickTrack(128.0, 2.8, rate)))

    @Test fun beatPhasePointsAtTheLastClick() {
        for (seconds in listOf(9.0, 9.13, 9.31)) {
            val bpm = 120.0
            val estimate = requireNotNull(lastEstimate(TestSignals.clickTrack(bpm, seconds, rate)))
            val period = 60.0 / bpm
            // Clicks start at t = 0, so the last one landed (seconds mod period) before the end.
            val expected = seconds % period
            var error = kotlin.math.abs(estimate.secondsSinceBeat - expected)
            error = minOf(error, period - error)
            assertTrue("phase error ${"%.3f".format(error)} s at $seconds s", error < 0.04)
        }
    }
}
