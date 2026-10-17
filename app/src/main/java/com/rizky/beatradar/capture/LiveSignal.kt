package com.rizky.beatradar.capture

/**
 * The last few seconds of beat hits (onset strength 0..1), for the live graph. Written by the capture
 * thread (one value per 50 ms buffer), read by the UI only while it is on screen.
 */
object LiveSignal {
    const val WINDOW_MS = 6_000L
    private const val CAPACITY = 160

    private val times = LongArray(CAPACITY)
    private val levels = FloatArray(CAPACITY)
    private var next = 0
    private var size = 0
    private var pushed = 0L

    @Synchronized
    fun push(timeMs: Long, onset: Float) {
        times[next] = timeMs
        levels[next] = onset
        next = (next + 1) % CAPACITY
        if (size < CAPACITY) size++
        pushed++
    }

    @Synchronized
    fun clear() {
        size = 0
    }

    /**
     * Copies samples oldest-first into the arrays; returns how many were copied. [outFirstIndex] receives
     * the running index of the oldest copied sample, so drawing can tie a sample's style to the sample itself.
     */
    @Synchronized
    fun snapshot(outTimes: LongArray, outLevels: FloatArray, outFirstIndex: LongArray): Int {
        val n = minOf(size, outTimes.size)
        outFirstIndex[0] = pushed - n
        val start = (next - n + CAPACITY) % CAPACITY
        for (i in 0 until n) {
            val idx = (start + i) % CAPACITY
            outTimes[i] = times[idx]
            outLevels[i] = levels[idx]
        }
        return n
    }

    const val SNAPSHOT_SIZE = CAPACITY
}
