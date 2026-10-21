package com.boimin.beatradar.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class BeatNetDetectorTest {
    /** The Kotlin network gives the same beat activations and tempo votes as the PyTorch model it was exported from. */
    @Test fun matchesThePyTorchModel() {
        val bytes = javaClass.getResourceAsStream("/beatnet_reference.bin")!!.use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("BRR1", String(ByteArray(4).also { b.get(it) }))
        val samples = b.int
        val frames = b.int
        val classes = b.int
        val x = FloatArray(samples) { b.float }
        val act = FloatArray(frames) { b.float }
        val votes = FloatArray(classes) { b.float }

        val det = BeatNetDetector()
        val got = FloatArray(frames)
        var f = 0
        // One sample at a time, so every frame boundary is seen.
        val one = FloatArray(1)
        for (s in x) {
            one[0] = s
            val before = f
            det.process(one, 1)
            // A frame completes when 512 + 128·f samples are in.
            if (f < frames && det.framesSeen() > before) {
                got[f] = det.lastActivation()
                f++
            }
        }
        assertEquals(frames, f)
        var worst = 0f
        for (i in 0 until frames) worst = maxOf(worst, abs(got[i] - act[i]))
        assertTrue("beat activation differs by $worst", worst < 2e-3f)
        val v = det.currentVotes()
        var worstVote = 0.0
        for (i in 0 until classes) worstVote = maxOf(worstVote, abs(v[i] - votes[i]))
        assertTrue("tempo vote differs by $worstVote", worstVote < 2e-3)
    }

    @Test fun noiseGetsLowConfidence() {
        val det = BeatNetDetector()
        val r = kotlin.random.Random(3)
        var last: BpmEstimate? = null
        repeat(10 * 11_025 / 256) {
            val buf = FloatArray(256) { (r.nextFloat() * 2 - 1) * 0.2f }
            det.process(buf)?.let { last = it }
        }
        assertTrue("confidence ${last?.confidence}", (last?.confidence ?: 0f) < 0.3f)
    }
}
