package com.rizky.beatradar.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic drum patterns with a known tempo. */
object TestSignals {

    fun clickTrack(bpm: Double, seconds: Double, sampleRate: Int, noise: Double = 0.01, seed: Int = 1): FloatArray {
        val out = FloatArray((seconds * sampleRate).toInt())
        val rnd = Random(seed)
        for (i in out.indices) out[i] = (rnd.nextDouble(-noise, noise)).toFloat()
        addHits(out, sampleRate, periodSeconds = 60.0 / bpm, offset = 0.0) { t -> 0.8 * sin(2 * PI * 1000 * t) * exp(-t * 60) }
        return out
    }

    /** Kick on every beat, quieter hi-hat on the off-beat eighths, plus noise. */
    fun drumLoop(bpm: Double, seconds: Double, sampleRate: Int, seed: Int = 2): FloatArray {
        val out = FloatArray((seconds * sampleRate).toInt())
        val rnd = Random(seed)
        for (i in out.indices) out[i] = (rnd.nextDouble(-0.01, 0.01)).toFloat()
        val beat = 60.0 / bpm
        addHits(out, sampleRate, beat, 0.0) { t -> 0.9 * sin(2 * PI * (60 + 90 * exp(-t * 30)) * t) * exp(-t * 18) }
        val hatRnd = Random(seed + 1)
        addHits(out, sampleRate, beat, beat / 2) { t -> 0.25 * hatRnd.nextDouble(-1.0, 1.0) * exp(-t * 120) }
        return out
    }

    fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }

    private fun addHits(out: FloatArray, sampleRate: Int, periodSeconds: Double, offset: Double, hit: (Double) -> Double) {
        val length = (0.15 * sampleRate).toInt()
        var t = offset
        while (t * sampleRate < out.size) {
            val start = (t * sampleRate).toInt()
            for (k in 0 until length) {
                val idx = start + k
                if (idx >= out.size) break
                out[idx] += hit(k.toDouble() / sampleRate).toFloat()
            }
            t += periodSeconds
        }
    }
}
