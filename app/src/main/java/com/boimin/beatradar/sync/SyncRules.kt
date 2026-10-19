package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.data.UploadState
import com.boimin.beatradar.dsp.BeatEngine
import com.boimin.beatradar.net.ApiJson
import com.boimin.beatradar.net.ItemResult
import com.boimin.beatradar.net.LogEntryIn
import com.boimin.beatradar.net.LogEntryOut
import com.boimin.beatradar.net.MeasurementIn
import java.time.Instant

/** What goes up to the Library and how answers are read (PLAN §7). Pure, so it is unit-tested. */
object UploadRules {
    const val MAX_ITEMS = 100
    /** Stays well under the server's 2 MB body limit (a whole song's fingerprint is ~40 KB in base64). */
    const val MAX_BATCH_BYTES = 1_500_000

    /** Only names from these sources may carry fingerprints (the server ignores others). */
    fun reliable(source: NameSource?): Boolean =
        source == NameSource.MEDIA_SESSION || source == NameSource.SHAZAM || source == NameSource.NOW_PLAYING

    fun titleSource(source: NameSource?): String = when (source) {
        NameSource.MEDIA_SESSION, null -> "media_session"
        NameSource.SHAZAM -> "shazam"
        NameSource.NOW_PLAYING -> "now_playing"
        NameSource.SERVER -> "server"
        NameSource.MANUAL -> "manual"
    }

    /** Each play is its own measurement, so a song heard again on another day counts again. */
    fun clientRef(entry: BeatLogEntry): String = "${entry.id}:${entry.playCount}"

    fun parseClientRef(ref: String): Pair<Long, Int>? {
        val parts = ref.split(':')
        if (parts.size != 2) return null
        return (parts[0].toLongOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
    }

    /** The measurement for [entry], or null when it can't be sent (no name, or pulled from another phone). */
    fun measurement(entry: BeatLogEntry, prints: String?): MeasurementIn? {
        val title = entry.title?.takeIf { it.isNotBlank() } ?: return null
        if (entry.remoteInstallId != null) return null
        return MeasurementIn(
            clientRef = clientRef(entry),
            title = title,
            artist = entry.artist.orEmpty(),
            titleSource = titleSource(entry.nameSource),
            bpm = entry.bpm,
            // Entries logged before confidence was stored were only ever logged at a confident reading.
            confidence = entry.confidence.takeIf { it > 0f } ?: BeatEngine.MIN_CONFIDENCE,
            heardAt = Instant.ofEpochMilli(entry.timestamp).toString(),
            prints = prints?.takeIf { reliable(entry.nameSource) },
        )
    }

    /** Splits into batches of at most [MAX_ITEMS] items and about [MAX_BATCH_BYTES]. */
    fun batches(items: List<MeasurementIn>): List<List<MeasurementIn>> {
        val out = mutableListOf<List<MeasurementIn>>()
        var current = mutableListOf<MeasurementIn>()
        var bytes = 0
        for (item in items) {
            val size = ApiJson.encodeToString(MeasurementIn.serializer(), item).length + 1
            if (current.isNotEmpty() && (current.size >= MAX_ITEMS || bytes + size > MAX_BATCH_BYTES)) {
                out += current
                current = mutableListOf()
                bytes = 0
            }
            current += item
            bytes += size
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    enum class Outcome { SENT, SKIPPED, RETRY_LATER }

    /** accepted and duplicate both mean the server has it; refused items are not retried, except the daily cap. */
    fun outcome(r: ItemResult): Outcome = when (r.status) {
        "accepted", "duplicate" -> Outcome.SENT
        "rejected" -> if (r.reason == "daily_cap") Outcome.RETRY_LATER else Outcome.SKIPPED
        else -> Outcome.RETRY_LATER
    }

    fun state(outcome: Outcome): Int = when (outcome) {
        Outcome.SENT -> UploadState.SENT
        Outcome.SKIPPED -> UploadState.SKIPPED
        Outcome.RETRY_LATER -> UploadState.PENDING
    }
}

/** The personal log backup (PLAN §7a): what is pushed, and how pulled entries are merged. Pure. */
object BackupRules {
    const val MAX_PUSH = 500

    /** Own entries go up without an install id (the server uses the caller's); pulled ones keep their origin. */
    fun toPush(entry: BeatLogEntry): LogEntryIn = LogEntryIn(
        installId = entry.remoteInstallId,
        clientRef = entry.remoteClientRef ?: entry.id.toString(),
        title = entry.title,
        artist = entry.artist,
        bpm = entry.bpm,
        confidence = entry.confidence,
        inRange = entry.inRange,
        heardAt = Instant.ofEpochMilli(entry.timestamp).toString(),
        updatedAt = Instant.ofEpochMilli(entry.updatedAt.coerceAtLeast(entry.timestamp)).toString(),
        deleted = entry.syncState == SyncState.DELETED,
    )

    sealed interface Action {
        data object Skip : Action
        data object Insert : Action
        data class Update(val localId: Long) : Action
        data class Delete(val localId: Long) : Action
    }

    /**
     * Newest `updatedAt` wins. [local] is the matching local row (null if none); [own] = the entry came from this
     * phone (its id). An own entry missing locally was trimmed or deleted here and is not brought back.
     */
    fun decide(local: BeatLogEntry?, remote: LogEntryOut, own: Boolean): Action {
        val remoteUpdated = millis(remote.updatedAt)
        if (local == null) return if (own || remote.deleted) Action.Skip else Action.Insert
        if (remoteUpdated <= local.updatedAt) return Action.Skip
        return if (remote.deleted) Action.Delete(local.id) else Action.Update(local.id)
    }

    fun millis(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}
