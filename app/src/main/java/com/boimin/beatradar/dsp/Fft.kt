package com.boimin.beatradar.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Minimal in-place radix-2 complex FFT. Tables are precomputed once per size. */
internal class Fft(private val n: Int) {
    init {
        require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }
    }

    private val bits = Integer.numberOfTrailingZeros(n)
    private val cosTable = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinTable = DoubleArray(n / 2) { sin(2 * PI * it / n) }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - bits)
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val tre = re[l] * cosTable[k] + im[l] * sinTable[k]
                    val tim = -re[l] * sinTable[k] + im[l] * cosTable[k]
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }
}
