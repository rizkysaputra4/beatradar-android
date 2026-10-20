package com.boimin.beatradar.capture

import com.boimin.beatradar.notify.ClickPlayer
import com.boimin.beatradar.widget.BeatWidget
import com.boimin.beatradar.widget.ListenTile
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.app
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.data.Fingerprint
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.Placeholders
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.data.SongKey
import com.boimin.beatradar.data.UploadState
import com.boimin.beatradar.net.RecognizedSong
import com.boimin.beatradar.dsp.BeatEngine
import com.boimin.beatradar.dsp.BeatUpdate
import com.boimin.beatradar.dsp.FingerprintMatcher
import com.boimin.beatradar.dsp.Fingerprinter
import com.boimin.beatradar.dsp.Landmark
import com.boimin.beatradar.dsp.SongJudge
import com.boimin.beatradar.meta.NowPlayingHub
import com.boimin.beatradar.meta.Recognition
import com.boimin.beatradar.meta.SongNamer
import com.boimin.beatradar.meta.TrackInfo
import com.boimin.beatradar.notify.Haptics
import com.boimin.beatradar.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Foreground service that listens through the microphone, shows a live tempo, decides whether the
 * song stays inside the user's range for 10 s, logs each song and buzzes on a match.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** All song/log bookkeeping runs here, one task at a time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.Default.limitedParallelism(1)

    private lateinit var notifier: Notifier
    private lateinit var haptics: Haptics

    private var captureJob: Job? = null
    private var foreground = false

    @Volatile private var settings = Settings()
    @Volatile private var engine: BeatEngine? = null

    private val judge = SongJudge()
    private var lastConfidentAtMs = 0L

    /** The song currently playing, once it is in the log. [learning]: what is heard now is saved as its fingerprint. */
    private data class LoggedSong(
        val id: Long,
        val timeMs: Long,
        val nameSource: NameSource?,
        val inRange: Boolean,
        val learning: Boolean,
        val placeholderNo: Int? = null,
    ) {
        val named: Boolean get() = nameSource != null
    }
    private data class SongName(val title: String, val artist: String?, val label: String?, val source: NameSource)

    private var lastLogged: LoggedSong? = null
    private var pendingRecognition: Recognition? = null
    /** An unnamed song waiting for its clip: entry id and when 12 s of it will have been heard (elapsed clock). */
    private var pendingClip: Pair<Long, Long>? = null

    /** Fingerprint landmarks of the last [QUERY_SECONDS] seconds, to recognise what is playing now. */
    private val recentPrints = ArrayDeque<Landmark>()
    /**
     * Landmarks not saved yet. They are held back for [QUERY_SECONDS], the time a song change takes to notice,
     * so the start of the next song is not filed under the previous one.
     */
    private val unsaved = ArrayDeque<Landmark>()
    private var printsStored = 0
    /** The last [ClipStore.SECONDS] seconds of audio at 22 kHz, for saved clips. */
    private val clipRing = ShortArray(ClipStore.SAMPLE_RATE * ClipStore.SECONDS)
    private var clipWrite = 0
    private var clipFilled = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = Notifier(this)
        haptics = Haptics(this)

        scope.launch {
            app.settings.settings
                .distinctUntilChanged()
                .collect { s ->
                    val old = settings
                    settings = s
                    val rangeChanged = s.rangeEnabled != old.rangeEnabled || s.minBpm != old.minBpm ||
                        s.maxBpm != old.maxBpm || s.halfDoubleMatch != old.halfDoubleMatch
                    if (rangeChanged) launch(serial) {
                        judge.restartCountdown()
                        Monitor.update { it.copy(matchProgress = 0f, matched = false) }
                    }
                }
        }
        // Notification refreshes at most every 2 s, and only while running.
        scope.launch {
            Monitor.state.collect { st ->
                if (foreground && st.active) notifier.updateOngoing(st)
                delay(NOTIFICATION_MIN_INTERVAL_MS)
            }
        }
        scope.launch {
            NowPlayingHub.track.collect { t ->
                val label = t?.takeIf { it.playing }?.let { appLabel(it.packageName) }
                Monitor.update { it.copy(phoneApp = label) }
            }
        }
        scope.launch {
            // Pausing and resuming the same track is not a song change; only a different title is.
            var first = true
            NowPlayingHub.track
                .map { t -> t?.takeIf { it.playing && !it.title.isNullOrBlank() } }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.packageName == b.packageName && a.title == b.title && a.artist == b.artist }
                .collect { t ->
                    val wasFirst = first
                    first = false
                    launch(serial) { onTrackChanged(t, firstSeen = wasFirst) }
                }
        }
        scope.launch {
            NowPlayingHub.recognitions.collect { r -> launch(serial) { onRecognition(r) } }
        }
        // The home-screen widget and the Quick Settings tile follow the live state, at most once a second.
        scope.launch {
            Monitor.state.collect { st ->
                BeatWidget.update(this@CaptureService, st)
                ListenTile.refresh(this@CaptureService)
                delay(1_000)
            }
        }
        scope.launch(serial) {
            while (true) {
                delay(PRINT_FLUSH_MS)
                pullPrints()
                saveSettledPrints()
                savePendingClip()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> if (foreground) pause() else stopSelf()
            ACTION_RESUME -> if (foreground) resume() else stopSelf()
            // STOP, or restarted by the system.
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        scope.cancel()
        if (Monitor.state.value.active) Monitor.update { MonitorState(phoneApp = it.phoneApp) }
        super.onDestroy()
    }

    private fun start() {
        // The microphone would measure the practice click instead of the music.
        ClickPlayer.stop()
        captureJob?.cancel()
        Monitor.update { MonitorState(status = MonitorStatus.LISTENING, phoneApp = it.phoneApp) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, Notifier.ID_LISTENING, notifier.ongoing(Monitor.state.value), type)
        foreground = true
        scope.launch(serial) { resetSong() }
        synchronized(clipRing) { clipFilled = 0 }
        startCapture()
    }

    private fun pause() {
        if (!Monitor.state.value.active) return
        captureJob?.cancel()
        captureJob = null
        scope.launch(serial) { resetSong() }
        Monitor.update { it.copy(status = MonitorStatus.PAUSED, bpm = null, lastBeatAtMs = null, matchProgress = 0f, matched = false) }
    }

    private fun resume() {
        if (Monitor.state.value.status != MonitorStatus.PAUSED) return
        Monitor.update { it.copy(status = MonitorStatus.LISTENING) }
        startCapture()
    }

    private fun stopEverything() {
        captureJob?.cancel()
        captureJob = null
        Monitor.update { MonitorState(phoneApp = it.phoneApp) }
        BeatWidget.update(this, Monitor.state.value)
        ListenTile.refresh(this)
        foreground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startCapture() {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            if (!capture()) launch(Dispatchers.Main) { stopEverything() }
        }
    }

    /** Returns false if the microphone could not be used. */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested before the service starts.
    private suspend fun capture(): Boolean {
        val record = runCatching { createRecord() }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        val e = BeatEngine(SAMPLE_RATE, engineListener, inputLatencyMs = INPUT_LATENCY_MS)
        engine = e
        val buffer = ShortArray(SAMPLE_RATE / 40) // 25 ms reads keep hits and the beat animation tight
        var lastLevelLogMs = 0L
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
            while (currentCoroutineContext().isActive) {
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) return false
                if (n == 0) continue
                val now = SystemClock.elapsedRealtime()
                e.feed(buffer, n, now)
                if (settings.saveClips) recordClip(buffer, n)
                if (BuildConfig.DEBUG && now - lastLevelLogMs >= 5_000) {
                    lastLevelLogMs = now
                    Log.d(TAG, "mic level rms=%.5f bpm=%s".format(BeatEngine.rms(buffer, n), Monitor.state.value.bpm))
                }
            }
            return true
        } finally {
            engine = null
            runCatching { record.stop() }
            record.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer * 2, SAMPLE_RATE / 2 * 2))
            .build()
    }

    private val engineListener = object : BeatEngine.Listener {
        override fun onBeat(update: BeatUpdate) {
            scope.launch(serial) { onBeatUpdate(update) }
        }

        override fun onHit(atMs: Long, strength: Float) {
            MicHits.emit(MicHit(atMs, strength))
        }

        override fun onSilenceChanged(silent: Boolean) {
            if (silent) scope.launch(serial) {
                resetSong()
                Monitor.update { it.copy(bpm = null, lastBeatAtMs = null, matchProgress = 0f, matched = false) }
            }
        }
    }

    private suspend fun onBeatUpdate(u: BeatUpdate) {
        val s = settings
        // Quick first guesses are shown but never counted towards a match or the log.
        val verdict = judge.onBeat(u.bpm, if (u.settled) u.confidence else 0f, u.timeMs, s.rangeEnabled, s::matches)
        val confident = u.confidence >= BeatEngine.MIN_CONFIDENCE
        if (confident) lastConfidentAtMs = u.timeMs
        val hearing = confident || u.timeMs - lastConfidentAtMs < NO_BEAT_AFTER_MS
        Monitor.update {
            it.copy(
                bpm = if (hearing) u.bpm else null,
                bpmSettled = hearing && u.settled,
                lastBeatAtMs = if (hearing) u.lastBeatAtMs else null,
                matchProgress = verdict.progress,
                matched = verdict.matched,
                alreadyListed = verdict.matched && it.alreadyListed,
            )
        }
        for (event in verdict.events) {
            when (event) {
                is SongJudge.Event.SongLogged -> logSong(event.bpm, u.confidence)
                is SongJudge.Event.Match -> onMatch(event.bpm, s)
            }
        }
    }

    /**
     * Alerts once per song. Within one play, a song that already alerted stays quiet however often
     * its tempo dips out of range and back. Across plays (phone, PC speaker, cafe...), a song already
     * matched before, recognised by title or by audio fingerprint, is not announced or logged again.
     */
    private suspend fun onMatch(bpm: Float, s: Settings) {
        val current = lastLogged
        if (current != null && current.inRange) {
            Monitor.update { it.copy(alreadyListed = true) }
            return
        }
        val dao = app.database.beatLog()
        val st = Monitor.state.value
        val earlier = findSameSong(st.title, st.artist, excludeId = current?.id ?: -1)
        if (earlier != null && earlier.inRange) {
            adoptEarlier(earlier, current)
            applyServerName()
            Monitor.update { it.copy(alreadyListed = true) }
            app.requestSync()
            return
        }
        applyServerName()
        lastLogged?.let {
            dao.updateInRange(it.id, true)
            lastLogged = it.copy(inRange = true)
            // An in-range song without a name gets its clip now: the last 12 s are all in range.
            if (s.saveClips && !it.named) saveClip(it.id)
        }
        Monitor.update { it.copy(alreadyListed = false) }
        app.requestSync()
        if (s.vibrate) haptics.matchFound()
        notifier.match(bpm, st.title, st.artist)
    }

    /** A new steady song. If it is one we already know (title or fingerprint), keep using that entry. */
    private suspend fun logSong(bpm: Float, confidence: Float) {
        val now = System.currentTimeMillis()
        val name = currentSongName(now)
        if (name != null) Monitor.update { it.copy(title = name.title, artist = name.artist) }
        val dao = app.database.beatLog()
        val earlier = findSameSong(name?.title, name?.artist, excludeId = -1)
        if (earlier != null) {
            // Matching the song already playing (e.g. its chorus again) is no reason to stop learning it.
            if (earlier.id == lastLogged?.id) lastMatchCertain = true
            adoptEarlier(earlier, current = null)
            val adopted = lastLogged
            if (name != null && adopted != null && NameSource.shouldReplace(earlier.nameSource, name.source)) {
                nameLogged(adopted, name)
            } else if (earlier.title != null) {
                Monitor.update { it.copy(title = earlier.title, artist = earlier.artist) }
            }
            applyServerName()
            app.requestSync()
            return
        }
        val placeholder = if (name == null) Placeholders.next(this, dao.maxPlaceholderNo()) else null
        val id = dao.insert(
            BeatLogEntry(
                timestamp = now, title = name?.title, artist = name?.artist, sourceApp = name?.label,
                bpm = bpm, confidence = confidence, inRange = false,
                placeholderNo = placeholder,
                nameSource = name?.source,
                matchKey = name?.let { SongKey.of(it.title, it.artist) },
                phoneApp = Monitor.state.value.phoneApp,
                uploadState = if (name != null) UploadState.PENDING else UploadState.NONE,
                updatedAt = now,
            ),
        )
        dao.trim()
        dao.trimPrints()
        lastLogged = LoggedSong(id, now, nameSource = name?.source, inRange = false, learning = true, placeholderNo = placeholder)
        Monitor.update { it.copy(placeholderNo = placeholder, entryId = id) }
        if (placeholder != null) {
            scheduleClip(id)
            app.requestSyncAfterSong()
        }
        printsStored = 0
        storeRecentPrints(id)
        applyServerName()
        app.requestSync()
    }

    /** Continue with an entry already in the log: bump it to now and drop the duplicate this play created. */
    private suspend fun adoptEarlier(earlier: BeatLogEntry, current: LoggedSong?) {
        val dao = app.database.beatLog()
        val now = System.currentTimeMillis()
        if (current != null && current.id != earlier.id) {
            dao.byId(current.id)?.clipPath?.let(ClipStore::delete)
            dao.delete(current.id)
        }
        // The song already playing logged again (a new steady tempo) is the same play, not another one.
        if (earlier.id == lastLogged?.id) dao.touch(earlier.id, now) else dao.countPlay(earlier.id, now)
        // More coverage of the song makes the next recognition easier, but only add it when sure:
        // a borderline match that was wrong would otherwise teach this entry a different song.
        val certain = lastMatchCertain
        lastLogged = LoggedSong(
            earlier.id, now, nameSource = earlier.nameSource, inRange = earlier.inRange, learning = certain,
            placeholderNo = earlier.placeholderNo.takeIf { earlier.title == null },
        )
        Monitor.update { it.copy(placeholderNo = earlier.placeholderNo.takeIf { earlier.title == null }, entryId = earlier.id) }
        // Heard again without a name and its clip is gone (or never taken): take a fresh one.
        if (earlier.title == null && earlier.clipPath == null) scheduleClip(earlier.id)
        printsStored = if (certain) dao.printCount(earlier.id) else MAX_PRINTS_PER_SONG
        if (certain) storeRecentPrints(earlier.id) else unsaved.clear()
    }

    /** Gives the song playing its name; it may merge into an entry of the same song (PLAN §5b). */
    private suspend fun nameLogged(song: LoggedSong, name: SongName) {
        val id = SongNamer.name(this, song.id, name.title, name.artist, name.label, name.source) ?: return
        lastLogged = song.copy(id = id, nameSource = name.source, placeholderNo = null)
        if (pendingClip?.first == song.id) pendingClip = null
        Monitor.update { it.copy(placeholderNo = null, title = name.title, artist = name.artist, entryId = id) }
        app.requestSync()
    }

    /**
     * After the server recognised what is playing ([findSameSong]): the logged entry learns its Library slug, and an
     * unnamed one gets the server's name for free. A `candidate` answer (one install so far) is not trusted for that.
     */
    private suspend fun applyServerName() {
        val server = lastServerSong ?: return
        lastServerSong = null
        val song = lastLogged ?: return
        app.database.beatLog().setSongSlug(song.id, server.song.slug)
        if (server.trusted && NameSource.shouldReplace(song.nameSource, NameSource.SERVER)) {
            nameLogged(song, SongName(server.song.title, server.song.artist, "BeatRadar Library", NameSource.SERVER))
        }
    }

    private data class ServerMatch(val song: RecognizedSong, val trusted: Boolean)

    /** Set by [findSameSong] when the server recognised the audio. */
    private var lastServerSong: ServerMatch? = null

    /** Set by [findSameSong]: the last match was clear-cut (same title, or a fingerprint well above the bar). */
    private var lastMatchCertain = false

    /** The log entry for what is playing now: same title, or an audio fingerprint that lines up. */
    private suspend fun findSameSong(title: String?, artist: String?, excludeId: Long): BeatLogEntry? {
        val dao = app.database.beatLog()
        lastMatchCertain = false
        lastServerSong = null
        if (title != null) dao.findByKey(SongKey.of(title, artist), excludeId)?.let { lastMatchCertain = true; return it }
        pullPrints()
        val query = recentPrints.toList()
        if (query.size < MIN_QUERY_PRINTS) return null
        // Ask the server's shared fingerprints first (PLAN §5a): they know every part of songs others have heard.
        // No answer within 1.5 s, offline or unknown: the phone's own fingerprints, as before.
        val server = runCatching { app.library.recognize(query) }.getOrNull()
        if (BuildConfig.DEBUG && server != null) Log.d(TAG, "server recognize: ${server.song?.slug} score=${server.score} ${server.state}")
        server?.song?.let { song ->
            val trusted = server.state == "trusted"
            lastServerSong = ServerMatch(song, trusted)
            val local = dao.findBySlug(song.slug)?.takeIf { it.id != excludeId }
                ?: dao.findByKey(SongKey.of(song.title, song.artist), excludeId)
            if (local != null) {
                // A candidate (one install so far) can stop an alert, but is not a certain match.
                lastMatchCertain = trusted
                return local
            }
        }
        val stored = query.map { it.hash }.distinct().chunked(500)
            .flatMap { dao.printsFor(it) }
            .filter { it.entryId != excludeId }
            .map { FingerprintMatcher.Stored(it.entryId, it.hash, it.frame) }
        val match = FingerprintMatcher.best(query, stored) ?: return null
        if (BuildConfig.DEBUG) Log.d(TAG, "fingerprint best entry=${match.entryId} score=${match.score} (query ${query.size})")
        if (match.score < FingerprintMatcher.MIN_SCORE) return null
        lastMatchCertain = match.score >= 2 * FingerprintMatcher.MIN_SCORE
        return dao.byId(match.entryId)
    }

    /** Moves new landmarks from the engine into [recentPrints] and [unsaved]. */
    private fun pullPrints() {
        val fresh = engine?.drainLandmarks().orEmpty()
        if (fresh.isEmpty()) return
        recentPrints.addAll(fresh)
        unsaved.addAll(fresh)
        val oldestKept = recentPrints.last().frame - QUERY_FRAMES
        while (recentPrints.first().frame < oldestKept) recentPrints.removeFirst()
    }

    /**
     * Saves landmarks older than [QUERY_SECONDS] to the song playing, whatever its tempo reads now: a misread
     * tempo or a quiet bridge is still the same song, and every part saved is a part that can be recognised.
     * Silence or a new title ends the song ([resetSong]); a new steady tempo logs a new one first.
     */
    private suspend fun saveSettledPrints() {
        if (unsaved.isEmpty()) return
        val cutoff = unsaved.last().frame - QUERY_FRAMES
        val settled = mutableListOf<Landmark>()
        while (unsaved.isNotEmpty() && unsaved.first().frame < cutoff) settled += unsaved.removeFirst()
        val song = lastLogged ?: return
        if (song.learning) storePrints(song.id, settled)
    }

    /** Saves the last [QUERY_SECONDS] (the audio that just identified the song) to [entryId]. */
    private suspend fun storeRecentPrints(entryId: Long) {
        unsaved.clear()
        storePrints(entryId, recentPrints.toList())
    }

    private suspend fun storePrints(entryId: Long, prints: List<Landmark>) {
        val room = MAX_PRINTS_PER_SONG - printsStored
        if (room <= 0 || prints.isEmpty()) return
        val batch = prints.take(room).map { Fingerprint(entryId = entryId, hash = it.hash, frame = it.frame) }
        app.database.beatLog().insertPrints(batch)
        printsStored += batch.size
    }

    private fun recordClip(pcm: ShortArray, count: Int) = synchronized(clipRing) {
        // 44.1 kHz -> 22.05 kHz by averaging pairs.
        var i = 0
        while (i + 1 < count) {
            clipRing[clipWrite] = ((pcm[i] + pcm[i + 1]) / 2).toShort()
            clipWrite = (clipWrite + 1) % clipRing.size
            if (clipFilled < clipRing.size) clipFilled++
            i += 2
        }
    }

    private suspend fun saveClip(entryId: Long) {
        val pcm = synchronized(clipRing) {
            if (clipFilled < ClipStore.SAMPLE_RATE * 5) return
            ShortArray(clipFilled) { clipRing[(clipWrite - clipFilled + it + clipRing.size) % clipRing.size] }
        }
        val path = withContext(Dispatchers.IO) { ClipStore.save(this@CaptureService, entryId, pcm) }
        val dao = app.database.beatLog()
        dao.setClip(entryId, path)
        ClipStore.trim(dao, settings.clipLimit)
    }

    /** With "All songs" on, an unnamed song gets its clip once 12 s of it were heard after it was logged. */
    private fun scheduleClip(entryId: Long) {
        val s = settings
        pendingClip = if (s.saveClips && s.clipAllSongs) entryId to SystemClock.elapsedRealtime() + ClipStore.SECONDS * 1_000L else null
    }

    private suspend fun savePendingClip() {
        val (id, dueAtMs) = pendingClip ?: return
        if (SystemClock.elapsedRealtime() < dueAtMs) return
        pendingClip = null
        val s = settings
        val song = lastLogged
        // The song changed or got a name meanwhile: nothing to keep.
        if (song == null || song.id != id || song.named || !s.saveClips || !s.clipAllSongs) return
        // An in-range song already got its clip at the match.
        if (app.database.beatLog().byId(id)?.clipPath != null) return
        saveClip(id)
    }

    private fun resetSong() {
        judge.reset()
        lastLogged = null
        pendingClip = null
        recentPrints.clear()
        unsaved.clear()
        Monitor.update { it.copy(title = null, artist = null, alreadyListed = false, placeholderNo = null, entryId = null) }
    }

    private suspend fun onTrackChanged(t: TrackInfo, firstSeen: Boolean) {
        val title = t.title ?: return
        if (!firstSeen) {
            engine?.requestReset()
            resetSong()
        } else {
            // First title we learn about (e.g. access was just granted): it names what is already playing.
            val last = lastLogged
            if (last != null && NameSource.shouldReplace(last.nameSource, NameSource.MEDIA_SESSION)) {
                nameLogged(last, SongName(title, t.artist, appLabel(t.packageName), NameSource.MEDIA_SESSION))
            }
        }
        Monitor.update { it.copy(title = title, artist = t.artist) }
    }

    private suspend fun onRecognition(r: Recognition) {
        // A clip being identified from the log owns this result (handled app-wide).
        if (SongNamer.isIdentifying(r.timeMs)) return
        val last = lastLogged
        val source = NameSource.fromRecognition(r.source)
        if (last != null && NameSource.shouldReplace(last.nameSource, source) && abs(r.timeMs - last.timeMs) <= RECOGNITION_WINDOW_MS) {
            nameLogged(last, SongName(r.title, r.artist, r.source, source))
        } else {
            pendingRecognition = r
        }
        Monitor.update { it.copy(title = r.title, artist = r.artist) }
    }

    private fun currentSongName(nowMs: Long): SongName? {
        val t = NowPlayingHub.track.value
        if (t != null && t.playing && !t.title.isNullOrBlank()) {
            return SongName(t.title, t.artist, appLabel(t.packageName), NameSource.MEDIA_SESSION)
        }
        val r = pendingRecognition?.takeIf { abs(nowMs - it.timeMs) <= RECOGNITION_WINDOW_MS } ?: return null
        pendingRecognition = null
        return SongName(r.title, r.artist, r.source, NameSource.fromRecognition(r.source))
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val TAG = "BeatRadar"
        const val ACTION_START = "com.boimin.beatradar.action.START"
        const val ACTION_PAUSE = "com.boimin.beatradar.action.PAUSE"
        const val ACTION_RESUME = "com.boimin.beatradar.action.RESUME"
        const val ACTION_STOP = "com.boimin.beatradar.action.STOP"

        private const val SAMPLE_RATE = 44_100
        private const val INPUT_LATENCY_MS = 40L
        /** Without a confident reading for this long, the screen shows "listening for a beat". */
        private const val NO_BEAT_AFTER_MS = 3_000L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 2_000L
        /** A Shazam result this close to a logged song is treated as that song's name. */
        private const val RECOGNITION_WINDOW_MS = 90_000L
        /** Recognition looks at this much recent audio. */
        private const val QUERY_SECONDS = 12.0
        private val QUERY_FRAMES = (QUERY_SECONDS * Fingerprinter.FRAMES_PER_SECOND).toInt()
        private const val MIN_QUERY_PRINTS = 20
        /** A whole song of up to ~5 minutes at 10–15 landmarks/s (~50 bytes each in the database). */
        private const val MAX_PRINTS_PER_SONG = 3_600
        private const val PRINT_FLUSH_MS = 3_000L

        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, CaptureService::class.java).setAction(ACTION_START),
        )

        fun send(context: Context, action: String) {
            context.startService(Intent(context, CaptureService::class.java).setAction(action))
        }
    }
}
