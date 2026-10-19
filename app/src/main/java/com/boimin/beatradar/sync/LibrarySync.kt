package com.boimin.beatradar.sync

import android.content.Context
import android.util.Log
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.account.AccountRepository
import com.boimin.beatradar.account.IntegrityCheck
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.data.AppDatabase
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.ClipEncoder
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.Placeholders
import com.boimin.beatradar.data.PrintUpload
import com.boimin.beatradar.data.UnnamedForget
import com.boimin.beatradar.data.SettingsRepository
import com.boimin.beatradar.data.SongKey
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.data.UploadState
import com.boimin.beatradar.dsp.Landmark
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.ClipConsentIn
import com.boimin.beatradar.net.WantedClips
import com.boimin.beatradar.net.LogPage
import com.boimin.beatradar.net.LogPush
import com.boimin.beatradar.net.Landmarks
import com.boimin.beatradar.net.MeasurementBatch
import com.boimin.beatradar.net.PushResult
import com.boimin.beatradar.net.RejectResult
import com.boimin.beatradar.net.Suggestion
import com.boimin.beatradar.net.SuggestionList
import com.boimin.beatradar.net.UnnamedBatch
import com.boimin.beatradar.net.UnnamedResult
import com.boimin.beatradar.meta.SongNamer
import com.boimin.beatradar.net.UploadResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything that talks to the server in the background: Library uploads (PLAN §7), Play Integrity (§7b) and,
 * when signed in, the log backup push and pull (§7a). Run by [SyncWorker]; safe to run again at any time.
 */
class LibrarySync(
    private val context: Context,
    private val db: AppDatabase,
    private val account: AccountRepository,
    private val settings: SettingsRepository,
) {
    private val dao = db.beatLog()
    private val running = Mutex()
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val _uploaded = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    /** Song slugs the server just took measurements for, so open Library pages can refresh their counters. */
    val uploaded: SharedFlow<Set<String>> = _uploaded

    /** Returns false when it should be tried again later (offline, server trouble). */
    suspend fun run(): Boolean = running.withLock {
        try {
            if (!account.ensureInstall()) return@withLock false
            IntegrityCheck.runOnce(context, account)
            uploadMeasurements()
            uploadUnnamed()
            forgetUnnamed()
            syncClips()
            fetchSuggestions()
            if (account.user.value != null) {
                pushLog()
                pullLog()
            }
            true
        } catch (e: ApiException) {
            Log.i(TAG, "sync stopped: ${e.code} ${e.message}")
            // Offline or a server error: retry later. A client error won't fix itself by retrying soon.
            !(e.offline || e.http >= 500)
        }
    }

    private suspend fun uploadMeasurements() {
        if (!settings.settings.first().contribute) {
            dao.skipPendingUploads()
            return
        }
        repeat(MAX_ROUNDS) {
            val pending = dao.pendingUploads(UploadRules.MAX_ITEMS)
            if (pending.isEmpty()) return
            val byRef = pending.associateBy { UploadRules.clientRef(it) }
            val items = pending.mapNotNull { e -> UploadRules.measurement(e, printsFor(e)) ?: run { skip(e); null } }
            var stop = false
            val slugs = mutableSetOf<String>()
            for (batch in UploadRules.batches(items)) {
                val result: UploadResult = account.api.post("v1/measurements", MeasurementBatch(batch))
                for (r in result.results) {
                    val entry = byRef[r.clientRef] ?: continue
                    val outcome = UploadRules.outcome(r)
                    if (outcome == UploadRules.Outcome.RETRY_LATER) {
                        stop = true
                        continue
                    }
                    dao.setUploaded(entry.id, entry.playCount, UploadRules.state(outcome), r.songSlug)
                    r.songSlug?.let(slugs::add)
                    // The server keeps this song's fingerprint now; the phone recognises it through the server.
                    if (r.printsAccepted) dao.deletePrints(entry.id)
                }
            }
            if (slugs.isNotEmpty()) _uploaded.tryEmit(slugs)
            if (stop) return
        }
    }

    /**
     * Fingerprints of songs that finished without a name (PLAN §5c), so the crowd can name them later. Only with
     * Contribute on. The local fingerprints stay until the entry is named (offline recognition).
     */
    private suspend fun uploadUnnamed() {
        if (!settings.settings.first().contribute) {
            dao.skipPendingPrints()
            return
        }
        dao.markUnnamedPrintsPending(System.currentTimeMillis() - UnnamedRules.QUIET_MS)
        repeat(MAX_ROUNDS) {
            // A long song still playing keeps gaining landmarks: it goes up once it has finished.
            val playing = Monitor.state.value.takeIf { it.active }?.entryId
            val pending = dao.pendingUnnamedPrints(UnnamedRules.MAX_ITEMS).filter { it.id != playing }
            if (pending.isEmpty()) return
            val items = pending.mapNotNull { e ->
                val prints = dao.printsOf(e.id, Landmarks.MAX_UPLOAD).map { Landmark(it.hash, it.frame) }
                UnnamedRules.upload(e, if (prints.isEmpty()) "" else Landmarks.pack(prints))
                    ?: run { dao.setPrintUpload(e.id, PrintUpload.SKIPPED); null }
            }
            var stop = false
            for (batch in UnnamedRules.batches(items)) {
                val result: UnnamedResult = account.api.post("v1/unnamed", UnnamedBatch(batch))
                for (r in result.results) {
                    val id = r.clientRef.toLongOrNull() ?: continue
                    val state = UnnamedRules.state(r)
                    if (state == null) {
                        stop = true
                        continue
                    }
                    dao.setPrintUpload(id, state)
                    r.suggestion?.let { showSuggestion(id, it) }
                }
            }
            if (stop) return
        }
    }

    /**
     * Clips of unknown songs (PLAN §5d), only with *Help name unknown songs* on: the consent once, then the clips the
     * server asks for (a few per unknown song), re-encoded to AAC. Turning it off withdraws the consent, which deletes
     * this phone's clips on the server.
     */
    private suspend fun syncClips() {
        val sharing = ClipRules.sharing(settings.settings.first())
        when (ClipRules.consentStep(sharing, prefs.getBoolean(KEY_CLIP_CONSENT, false))) {
            ClipRules.ConsentStep.WITHDRAW -> {
                account.api.delete<Unit>("v1/unnamed/clips")
                prefs.edit().putBoolean(KEY_CLIP_CONSENT, false).apply()
            }
            ClipRules.ConsentStep.SEND -> {
                account.api.post<ClipConsentIn, Unit>("v1/unnamed/clips/consent", ClipConsentIn(BuildConfig.VERSION_NAME))
                prefs.edit().putBoolean(KEY_CLIP_CONSENT, true).apply()
            }
            ClipRules.ConsentStep.NONE -> {}
        }
        if (!sharing) return
        val wanted: WantedClips = account.api.get("v1/unnamed/clips/wanted")
        val entries = wanted.clientRefs.mapNotNull { it.toLongOrNull() }.mapNotNull { dao.byId(it) }
        for ((id, path) in ClipRules.toUpload(wanted.clientRefs, entries) { File(it).isFile }) {
            val out = File(context.cacheDir, "clip-$id.m4a")
            try {
                val encoded = withContext(Dispatchers.IO) { ClipEncoder.encode(File(path), out) }
                val bytes = out.readBytes()
                if (bytes.size > ClipRules.MAX_BYTES) continue
                account.api.putBytes("v1/unnamed/$id/clip", bytes, "audio/mp4", mapOf("x-clip-duration-ms" to encoded.durationMs.toString()))
            } catch (e: ApiException) {
                // Offline or server trouble: try again later. Not wanted any more, or refused: skip this one.
                if (e.offline || e.http >= 500) throw e
                Log.i(TAG, "clip $id not sent: ${e.code}")
            } catch (e: Exception) {
                Log.w(TAG, "clip $id couldn't be encoded", e)
            } finally {
                out.delete()
            }
        }
    }

    /** Entries named another way, or deleted, after their fingerprint went up: the server can drop it. */
    private suspend fun forgetUnnamed() {
        repeat(MAX_ROUNDS) {
            val ids = dao.unnamedToForget(UnnamedRules.MAX_ITEMS)
            if (ids.isEmpty()) return
            for (id in ids) {
                try {
                    account.api.delete<Unit>("v1/unnamed/$id")
                } catch (e: ApiException) {
                    if (e.offline || e.http >= 500) throw e
                }
                dao.forgotten(id)
                dao.byId(id)?.let { if (it.printUpload == PrintUpload.SENT) dao.setPrintUpload(id, PrintUpload.NONE) }
            }
        }
    }

    /** `GET /v1/suggestions` is the truth: show its names on still-unnamed entries, drop the rest. */
    suspend fun fetchSuggestions() {
        val list: SuggestionList = account.api.get("v1/suggestions")
        val ids = list.items.mapNotNull { it.clientRef.toLongOrNull() }
        val plan = UnnamedRules.plan(list.items, dao.unnamedAmong(ids).toSet())
        for ((id, s) in plan.show) showSuggestion(id, s)
        dao.clearSuggestionsExcept(plan.show.keys.toList())
        if (plan.forget.isNotEmpty()) dao.forgetUnnamed(plan.forget.map(::UnnamedForget))
    }

    private suspend fun showSuggestion(id: Long, s: Suggestion) =
        dao.setSuggestion(id, s.slug, s.title, s.artist, s.score, s.state)

    /**
     * The user said yes: the server records it, then the entry is named with source `server` under the usual rules
     * (merge with an entry of that name, clip deleted, upload queued). Returns false if the suggestion is gone.
     */
    suspend fun acceptSuggestion(entryId: Long): Boolean {
        val s = dao.byId(entryId)?.suggestion ?: return false
        try {
            account.api.postEmpty<Unit>("v1/suggestions/$entryId/accept")
        } catch (e: ApiException) {
            if (e.http == 404) {
                dao.clearSuggestion(entryId)
                return false
            }
            throw e
        }
        dao.setPrintUpload(entryId, PrintUpload.NONE)
        dao.clearSuggestion(entryId)
        val id = SongNamer.name(context, entryId, s.title, s.artist, "BeatRadar Library", NameSource.SERVER) ?: entryId
        dao.setSongSlug(id, s.slug)
        return true
    }

    /** The user said no: that song is never suggested for this entry again; the next match (if any) shows instead. */
    suspend fun rejectSuggestion(entryId: Long) {
        val next = try {
            account.api.postEmpty<RejectResult>("v1/suggestions/$entryId/reject").next
        } catch (e: ApiException) {
            if (e.http != 404) throw e
            null
        }
        if (next != null) showSuggestion(entryId, next) else dao.clearSuggestion(entryId)
    }

    private suspend fun skip(e: BeatLogEntry) = dao.setUploaded(e.id, e.playCount, UploadState.SKIPPED, null)

    private suspend fun printsFor(e: BeatLogEntry): String? {
        if (!UploadRules.reliable(e.nameSource)) return null
        val prints = dao.printsOf(e.id, Landmarks.MAX_UPLOAD)
        return if (prints.isEmpty()) null else Landmarks.pack(prints.map { Landmark(it.hash, it.frame) })
    }

    private suspend fun pushLog() {
        repeat(MAX_ROUNDS) {
            val changed = dao.changedForBackup(BackupRules.MAX_PUSH)
            if (changed.isEmpty()) return
            account.api.post<LogPush, PushResult>("v1/me/log", LogPush(changed.map(BackupRules::toPush)))
            for (e in changed) {
                if (e.syncState == SyncState.DELETED) dao.dropTombstone(e.id, e.updatedAt) else dao.markClean(e.id, e.updatedAt)
            }
            if (changed.size < BackupRules.MAX_PUSH) return
        }
    }

    private suspend fun pullLog() {
        val userId = account.user.value?.id ?: return
        val me = account.installId
        val cursorKey = "log_cursor_$userId"
        var cursor = prefs.getString(cursorKey, null)
        repeat(MAX_ROUNDS * 10) {
            val page: LogPage = account.api.get("v1/me/log", mapOf("since" to cursor))
            for (r in page.entries) {
                val own = r.installId == me
                val local = if (own) r.clientRef.toLongOrNull()?.let { dao.byId(it) } else dao.byRemote(r.installId, r.clientRef)
                when (val action = BackupRules.decide(local, r, own)) {
                    BackupRules.Action.Skip -> Unit
                    BackupRules.Action.Insert -> dao.insert(
                        BeatLogEntry(
                            timestamp = BackupRules.millis(r.heardAt), title = r.title, artist = r.artist, sourceApp = null,
                            bpm = r.bpm, confidence = r.confidence, inRange = r.inRange,
                            placeholderNo = if (r.title == null) Placeholders.next(context, dao.maxPlaceholderNo()) else null,
                            nameSource = r.title?.let { NameSource.MANUAL },
                            matchKey = r.title?.let { SongKey.of(it, r.artist) },
                            songSlug = r.songSlug,
                            syncState = SyncState.CLEAN, updatedAt = BackupRules.millis(r.updatedAt),
                            remoteInstallId = r.installId, remoteClientRef = r.clientRef,
                        ),
                    )
                    is BackupRules.Action.Update -> dao.applyRemote(
                        action.localId, r.title, r.artist, r.bpm, r.confidence, r.inRange, BackupRules.millis(r.heardAt),
                        r.title?.let { SongKey.of(it, r.artist) }, r.songSlug, BackupRules.millis(r.updatedAt),
                    )
                    is BackupRules.Action.Delete -> {
                        dao.byId(action.localId)?.clipPath?.let(ClipStore::delete)
                        dao.delete(action.localId)
                    }
                }
            }
            cursor = page.cursor
            prefs.edit().putString(cursorKey, cursor).apply()
            if (!page.hasMore) {
                dao.trim()
                return
            }
        }
    }

    /** After signing in: this phone's whole log goes up, and everything from the account comes down. */
    suspend fun onSignedIn(userId: String) {
        prefs.edit().remove("log_cursor_$userId").apply()
        dao.markAllDirty()
    }

    private companion object {
        const val TAG = "BeatRadar"
        const val MAX_ROUNDS = 20
        /** The server has this install's consent to share clips. */
        const val KEY_CLIP_CONSENT = "clip_consent_sent"
    }
}
