package com.boimin.beatradar.data

import kotlin.math.roundToInt

/** A week of listening, computed on the phone from the log (works offline and for guests). */
data class Recap(
    val fromMs: Long,
    val toMs: Long,
    /** Different songs heard (log entries). */
    val songs: Int,
    /** Plays, counting a song heard again. */
    val plays: Int,
    /** Songs that were in the target range when heard. */
    val inTarget: Int,
    /** Plays per 10-BPM band, from 60 up: (band start, plays). */
    val bands: List<Pair<Int, Int>>,
    /** The band with the most plays: "the BPM you lived in". */
    val topBand: Int?,
    /** Play-weighted average tempo. */
    val averageBpm: Int?,
    /** Most played songs (label, plays), at most 3. */
    val topSongs: List<Pair<String, Int>>,
) {
    val worthSharing: Boolean get() = songs >= RecapCalc.MIN_SONGS
}

object RecapCalc {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    /** Fewer songs than this make a sad card: no Sunday notification. */
    const val MIN_SONGS = 5
    private const val BAND = 10

    /** The 7 days up to [nowMs]. */
    fun week(entries: List<BeatLogEntry>, nowMs: Long): Recap {
        val from = nowMs - WEEK_MS
        val week = entries.filter { it.timestamp in from..nowMs && it.syncState != SyncState.DELETED }
        val plays = week.sumOf { it.playCount.coerceAtLeast(1) }
        val byBand = week.groupBy { band(it.bpm) }.mapValues { (_, e) -> e.sumOf { it.playCount.coerceAtLeast(1) } }
        val bands = (Settings.BPM_FLOOR until Settings.BPM_CEILING step BAND).map { it to (byBand[it] ?: 0) }
        val average = if (plays == 0) null else (week.sumOf { it.bpm.toDouble() * it.playCount.coerceAtLeast(1) } / plays).roundToInt()
        return Recap(
            fromMs = from,
            toMs = nowMs,
            songs = week.size,
            plays = plays,
            inTarget = week.count { it.inRange },
            bands = bands,
            topBand = bands.filter { it.second > 0 }.maxByOrNull { it.second }?.first,
            averageBpm = average,
            topSongs = week.filter { it.title != null }
                .sortedWith(compareByDescending<BeatLogEntry> { it.playCount }.thenByDescending { it.timestamp })
                .take(3)
                .map { it.label to it.playCount.coerceAtLeast(1) },
        )
    }

    /** The 10-BPM band a tempo falls in (60–69 → 60), inside the dial's range. */
    fun band(bpm: Float): Int {
        val b = (bpm.roundToInt() / BAND) * BAND
        return b.coerceIn(Settings.BPM_FLOOR, Settings.BPM_CEILING - BAND)
    }
}
