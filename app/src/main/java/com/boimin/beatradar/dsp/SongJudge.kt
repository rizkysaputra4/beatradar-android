package com.boimin.beatradar.dsp

import kotlin.math.abs

/**
 * Turns live tempo readings into decisions:
 * - **Match**: the tempo stayed inside the target range for [holdMs] (short dips under [graceMs] are forgiven).
 * - **SongLogged**: the tempo stayed steady for [holdMs]; fires once per song, and again when a steady new tempo
 *   appears (a new song).
 */
class SongJudge(
    private val holdMs: Long = 10_000,
    private val graceMs: Long = 1_500,
    private val minConfidence: Float = BeatEngine.MIN_CONFIDENCE,
    private val steadyTolerance: Float = 3f,
    private val newSongDelta: Float = 4f,
) {
    sealed interface Event {
        data class SongLogged(val bpm: Float, val matched: Boolean) : Event
        data class Match(val bpm: Float) : Event
    }

    /** [progress] is 0..1 towards a match verdict; it stays at 1 once [matched]. */
    data class Verdict(val progress: Float, val matched: Boolean, val events: List<Event>)

    private var inRangeSince: Long? = null
    private var lastInRangeAt = 0L
    private var matched = false

    private var steadySince = 0L
    private var steadySum = 0.0
    private var steadyCount = 0
    private var loggedBpm: Float? = null

    fun onBeat(bpm: Float, confidence: Float, nowMs: Long, rangeEnabled: Boolean, inRange: (Float) -> Boolean): Verdict {
        val events = mutableListOf<Event>()
        val confident = confidence >= minConfidence

        if (confident) trackSteadiness(bpm, nowMs, events)

        if (rangeEnabled) {
            if (confident && inRange(bpm)) {
                if (inRangeSince == null) inRangeSince = nowMs
                lastInRangeAt = nowMs
            } else if (nowMs - lastInRangeAt > graceMs) {
                // Out of range for real: the countdown (and any verdict) starts over.
                inRangeSince = null
                matched = false
            }
            val since = inRangeSince
            if (!matched && since != null && nowMs - since >= holdMs) {
                matched = true
                if (loggedBpm == null) {
                    loggedBpm = bpm
                    events += Event.SongLogged(bpm, matched = true)
                }
                events += Event.Match(bpm)
            }
        }
        return Verdict(progress(nowMs, rangeEnabled), matched, events)
    }

    /** Range settings changed: start the countdown over, keep the song log state. */
    fun restartCountdown() {
        inRangeSince = null
        matched = false
    }

    /** Silence or a new track: forget everything about the current song. */
    fun reset() {
        restartCountdown()
        steadyCount = 0
        loggedBpm = null
    }

    private fun progress(nowMs: Long, rangeEnabled: Boolean): Float = when {
        !rangeEnabled -> 0f
        matched -> 1f
        else -> inRangeSince?.let { ((nowMs - it).toFloat() / holdMs).coerceIn(0f, 1f) } ?: 0f
    }

    private fun trackSteadiness(bpm: Float, nowMs: Long, events: MutableList<Event>) {
        val avg = if (steadyCount > 0) (steadySum / steadyCount).toFloat() else null
        if (avg == null || (abs(bpm - avg) > steadyTolerance && !isOctave(bpm, avg))) {
            steadySince = nowMs
            steadySum = 0.0
            steadyCount = 0
        }
        // An octave flip is the same tempo: add it at the running octave so the average stays meaningful.
        steadySum += if (avg != null && steadyCount > 0) listOf(bpm, bpm * 2, bpm / 2).minBy { abs(it - avg) } else bpm
        steadyCount++
        if (nowMs - steadySince < holdMs) return

        val steady = (steadySum / steadyCount).toFloat()
        val logged = loggedBpm
        if (logged == null) {
            loggedBpm = steady
            events += Event.SongLogged(steady, matched)
        } else if (abs(steady - logged) > newSongDelta && !isOctave(steady, logged) && !isRelated(steady, logged)) {
            // A different steady tempo: a new song started at steadySince. Its countdown counts from then.
            matched = false
            inRangeSince = inRangeSince?.let { maxOf(it, steadySince) }
            loggedBpm = steady
            events += Event.SongLogged(steady, matched = false)
        }
    }

    private fun isOctave(a: Float, b: Float): Boolean =
        abs(a * 2 - b) <= steadyTolerance * 2 || abs(a - b * 2) <= steadyTolerance * 2

    /**
     * 3:2 and 4:3 of the logged tempo: the detector locking onto a triplet feel or a different accent in another
     * section of the same song, far more often than the next song happening to sit at exactly that ratio.
     */
    private fun isRelated(a: Float, b: Float): Boolean {
        val r = maxOf(a, b) / minOf(a, b)
        return RELATED_RATIOS.any { abs(r - it) / it <= RATIO_TOLERANCE }
    }

    private companion object {
        val RELATED_RATIOS = floatArrayOf(1.5f, 4f / 3f)
        const val RATIO_TOLERANCE = 0.02f
    }
}
