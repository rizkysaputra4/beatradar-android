package com.boimin.beatradar.meta

import android.content.Context
import com.boimin.beatradar.app
import androidx.room.withTransaction
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.SongKey

/**
 * The one place a log entry gets its name, so its saved clip is always deleted at the same time and an entry
 * that turns out to be a song already in the log is merged into it (PLAN §5b).
 * Also tracks a "play this clip to Shazam" request started from the log.
 */
object SongNamer {
    private const val IDENTIFY_TIMEOUT_MS = 3 * 60_000L

    @Volatile private var pendingEntryId: Long? = null
    @Volatile private var pendingSinceMs = 0L

    /**
     * Names [entryId] unless it already has a name from an equal or stronger source ([NameSource.shouldReplace]);
     * [manual] (the user typing a name) always applies. Returns the id the song lives on afterwards (another
     * entry when merged), or null when nothing changed.
     */
    suspend fun name(
        context: Context,
        entryId: Long,
        title: String,
        artist: String?,
        sourceLabel: String?,
        source: NameSource,
        manual: Boolean = false,
    ): Long? {
        val db = context.app.database
        val dao = db.beatLog()
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val cleanArtist = artist?.trim()?.takeIf { it.isNotEmpty() }
        return db.withTransaction {
            val entry = dao.byId(entryId) ?: return@withTransaction null
            if (!manual && !NameSource.shouldReplace(entry.nameSource, source)) return@withTransaction null
            val key = SongKey.of(cleanTitle, cleanArtist)
            dao.setName(entryId, cleanTitle, cleanArtist, sourceLabel, source, key)
            if (entry.clipPath != null) {
                ClipStore.delete(entry.clipPath)
                dao.setClip(entryId, null)
            }
            val same = dao.findByKey(key, excludeId = entryId) ?: return@withTransaction entryId
            // Keep the older entry and add this one's plays to it.
            val self = dao.byId(entryId) ?: return@withTransaction null
            val (keep, drop) = if (same.id < entryId) same to self else self to same
            dao.absorb(keep.id, drop.timestamp, drop.playCount, drop.inRange)
            dao.movePrints(drop.id, keep.id)
            if (NameSource.shouldReplace(keep.nameSource, source) || (manual && keep.id != entryId)) {
                dao.setName(keep.id, cleanTitle, cleanArtist, sourceLabel, source, key)
            }
            drop.clipPath?.let(ClipStore::delete)
            dao.delete(drop.id)
            keep.id
        }
    }

    fun startIdentify(entryId: Long) {
        pendingEntryId = entryId
        pendingSinceMs = System.currentTimeMillis()
    }

    /** True while a log clip is being identified; Shazam results then belong to that entry. */
    fun isIdentifying(nowMs: Long = System.currentTimeMillis()): Boolean =
        pendingEntryId != null && nowMs - pendingSinceMs < IDENTIFY_TIMEOUT_MS

    /** Takes the pending request if [r] arrived while it was active. */
    fun consumePending(r: Recognition): Long? {
        val id = pendingEntryId ?: return null
        if (r.timeMs < pendingSinceMs || !isIdentifying(r.timeMs)) return null
        pendingEntryId = null
        ClipStore.stop()
        return id
    }
}
