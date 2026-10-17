package com.rizky.beatradar.meta

import android.content.Context
import com.rizky.beatradar.app
import com.rizky.beatradar.data.ClipStore

/**
 * The one place a log entry gets its name, so its saved clip is always deleted at the same time.
 * Also tracks a "play this clip to Shazam" request started from the log.
 */
object SongNamer {
    private const val IDENTIFY_TIMEOUT_MS = 3 * 60_000L

    @Volatile private var pendingEntryId: Long? = null
    @Volatile private var pendingSinceMs = 0L

    suspend fun name(context: Context, entryId: Long, title: String, artist: String?, source: String?) {
        val dao = context.app.database.beatLog()
        val entry = dao.byId(entryId) ?: return
        dao.updateName(entryId, title, artist, source)
        if (entry.clipPath != null) {
            ClipStore.delete(entry.clipPath)
            dao.setClip(entryId, null)
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
