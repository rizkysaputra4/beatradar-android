package com.rizky.beatradar.capture

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
import com.rizky.beatradar.BuildConfig
import com.rizky.beatradar.app
import com.rizky.beatradar.data.BeatLogEntry
import com.rizky.beatradar.data.ClipStore
import com.rizky.beatradar.data.Fingerprint
import com.rizky.beatradar.data.Settings
import com.rizky.beatradar.dsp.BeatEngine
import com.rizky.beatradar.dsp.BeatUpdate
import com.rizky.beatradar.dsp.FingerprintMatcher
import com.rizky.beatradar.dsp.Fingerprinter
import com.rizky.beatradar.dsp.Landmark
import com.rizky.beatradar.dsp.SongJudge
import com.rizky.beatradar.meta.NowPlayingHub
import com.rizky.beatradar.meta.Recognition
import com.rizky.beatradar.meta.SongNamer
import com.rizky.beatradar.meta.TrackInfo
import com.rizky.beatradar.notify.Haptics
import com.rizky.beatradar.notify.Notifier
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

    /** The song currently playing, once it is in the log. [bpm] is the tempo its fingerprints belong to. */
    private data class LoggedSong(val id: Long, val timeMs: Long, val named: Boolean, val inRange: Boolean, val bpm: Float)
    private data class SongName(val title: String, val artist: String?, val source: String?)

    private var lastLogged: LoggedSong? = null
    private var pendingRecognition: Recognition? = null

    /** Fingerprint landmarks of the last [QUERY_SECONDS] seconds, to recognise what is playing now. */
    private val recentPrints = ArrayDeque<Landmark>()
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
        scope.launch(serial) {
            while (true) {
                delay(PRINT_FLUSH_MS)
                pullPrints(store = true)
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
        captureJob?.cancel()
        Monitor.update { MonitorState(status = MonitorStatus.LISTENING, phoneApp = it.phoneApp) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, Notifier.ID_LISTENING, notifier.ongoing(Monitor.state.value), type)
        foreground = true
        LiveSignal.clear()
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
        val buffer = ShortArray(SAMPLE_RATE / 20) // 50 ms reads keep the beat animation tight
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
                LiveSignal.push(now, e.takeOnsetLevel())
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

        override fun onSilenceChanged(silent: Boolean) {
            if (silent) scope.launch(serial) {
                resetSong()
                Monitor.update { it.copy(bpm = null, lastBeatAtMs = null, matchProgress = 0f, matched = false) }
            }
        }
    }

    private suspend fun onBeatUpdate(u: BeatUpdate) {
        val s = settings
        val verdict = judge.onBeat(u.bpm, u.confidence, u.timeMs, s.rangeEnabled, s::matches)
        val confident = u.confidence >= BeatEngine.MIN_CONFIDENCE
        if (confident) lastConfidentAtMs = u.timeMs
        val hearing = confident || u.timeMs - lastConfidentAtMs < NO_BEAT_AFTER_MS
        Monitor.update {
            it.copy(
                bpm = if (hearing) u.bpm else null,
                lastBeatAtMs = if (hearing) u.lastBeatAtMs else null,
                matchProgress = verdict.progress,
                matched = verdict.matched,
                alreadyListed = verdict.matched && it.alreadyListed,
            )
        }
        for (event in verdict.events) {
            when (event) {
                is SongJudge.Event.SongLogged -> logSong(event.bpm)
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
            adoptEarlier(earlier, current, bpm)
            Monitor.update { it.copy(alreadyListed = true) }
            return
        }
        current?.let {
            dao.updateInRange(it.id, true)
            lastLogged = it.copy(inRange = true)
        }
        Monitor.update { it.copy(alreadyListed = false) }
        if (s.vibrate) haptics.matchFound()
        notifier.match(bpm, st.title, st.artist)
    }

    /** A new steady song. If it is one we already know (title or fingerprint), keep using that entry. */
    private suspend fun logSong(bpm: Float) {
        val now = System.currentTimeMillis()
        val name = currentSongName(now)
        if (name != null) Monitor.update { it.copy(title = name.title, artist = name.artist) }
        val dao = app.database.beatLog()
        val earlier = findSameSong(name?.title, name?.artist, excludeId = -1)
        if (earlier != null) {
            adoptEarlier(earlier, current = null, bpm = bpm)
            if (earlier.title == null && name != null) {
                SongNamer.name(this, earlier.id, name.title, name.artist, name.source)
                lastLogged = lastLogged?.copy(named = true)
            } else if (earlier.title != null) {
                Monitor.update { it.copy(title = earlier.title, artist = earlier.artist) }
            }
            return
        }
        val id = dao.insert(
            BeatLogEntry(
                timestamp = now, title = name?.title, artist = name?.artist, sourceApp = name?.source,
                bpm = bpm, confidence = 0f, inRange = false,
            ),
        )
        dao.trim()
        lastLogged = LoggedSong(id, now, named = name != null, inRange = false, bpm = bpm)
        printsStored = 0
        storePrints(id, recentPrints.toList())
        if (name == null && settings.saveClips) saveClip(id)
    }

    /** Continue with an entry already in the log: bump it to now and drop the duplicate this play created. */
    private suspend fun adoptEarlier(earlier: BeatLogEntry, current: LoggedSong?, bpm: Float) {
        val dao = app.database.beatLog()
        val now = System.currentTimeMillis()
        if (current != null && current.id != earlier.id) {
            dao.byId(current.id)?.clipPath?.let(ClipStore::delete)
            dao.delete(current.id)
        }
        dao.touch(earlier.id, now)
        lastLogged = LoggedSong(earlier.id, now, named = earlier.title != null, inRange = earlier.inRange, bpm = bpm)
        // More coverage of the song makes the next recognition easier.
        printsStored = dao.printCount(earlier.id)
        storePrints(earlier.id, recentPrints.toList())
    }

    /** The log entry for what is playing now: same title, or an audio fingerprint that lines up. */
    private suspend fun findSameSong(title: String?, artist: String?, excludeId: Long): BeatLogEntry? {
        val dao = app.database.beatLog()
        if (title != null) dao.findByName(title, artist, excludeId)?.let { return it }
        pullPrints(store = false)
        val query = recentPrints.toList()
        if (query.size < MIN_QUERY_PRINTS) return null
        val stored = query.map { it.hash }.distinct().chunked(500)
            .flatMap { dao.printsFor(it) }
            .filter { it.entryId != excludeId }
            .map { FingerprintMatcher.Stored(it.entryId, it.hash, it.frame) }
        val match = FingerprintMatcher.best(query, stored) ?: return null
        if (BuildConfig.DEBUG) Log.d(TAG, "fingerprint best entry=${match.entryId} score=${match.score} (query ${query.size})")
        return if (match.score >= FingerprintMatcher.MIN_SCORE) dao.byId(match.entryId) else null
    }

    /** Moves new landmarks from the engine into [recentPrints]; optionally saves them for the current song. */
    private suspend fun pullPrints(store: Boolean) {
        val fresh = engine?.drainLandmarks().orEmpty()
        if (fresh.isEmpty()) return
        recentPrints.addAll(fresh)
        val newest = recentPrints.last().frame
        val oldestKept = newest - (QUERY_SECONDS * Fingerprinter.FRAMES_PER_SECOND).toInt()
        while (recentPrints.first().frame < oldestKept) recentPrints.removeFirst()
        if (!store) return
        val song = lastLogged ?: return
        // Only while the tempo still matches the logged song: a new song may have started.
        val bpm = Monitor.state.value.bpm ?: return
        if (abs(bpm - song.bpm) > 4 && abs(bpm * 2 - song.bpm) > 6 && abs(bpm - song.bpm * 2) > 6) return
        storePrints(song.id, fresh)
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
        app.database.beatLog().setClip(entryId, path)
    }

    private fun resetSong() {
        judge.reset()
        lastLogged = null
        recentPrints.clear()
        Monitor.update { it.copy(title = null, artist = null, alreadyListed = false) }
    }

    private suspend fun onTrackChanged(t: TrackInfo, firstSeen: Boolean) {
        val title = t.title ?: return
        if (!firstSeen) {
            engine?.requestReset()
            resetSong()
        } else {
            // First title we learn about (e.g. access was just granted): it names what is already playing.
            val last = lastLogged
            if (last != null && !last.named) {
                SongNamer.name(this, last.id, title, t.artist, appLabel(t.packageName))
                lastLogged = last.copy(named = true)
            }
        }
        Monitor.update { it.copy(title = title, artist = t.artist) }
    }

    private suspend fun onRecognition(r: Recognition) {
        // A clip being identified from the log owns this result (handled app-wide).
        if (SongNamer.isIdentifying(r.timeMs)) return
        val last = lastLogged
        if (last != null && !last.named && abs(r.timeMs - last.timeMs) <= RECOGNITION_WINDOW_MS) {
            SongNamer.name(this, last.id, r.title, r.artist, r.source)
            lastLogged = last.copy(named = true)
        } else {
            pendingRecognition = r
        }
        Monitor.update { it.copy(title = r.title, artist = r.artist) }
    }

    private fun currentSongName(nowMs: Long): SongName? {
        val t = NowPlayingHub.track.value
        if (t != null && t.playing && !t.title.isNullOrBlank()) return SongName(t.title, t.artist, appLabel(t.packageName))
        val r = pendingRecognition?.takeIf { abs(nowMs - it.timeMs) <= RECOGNITION_WINDOW_MS } ?: return null
        pendingRecognition = null
        return SongName(r.title, r.artist, r.source)
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val TAG = "BeatRadar"
        const val ACTION_START = "com.rizky.beatradar.action.START"
        const val ACTION_PAUSE = "com.rizky.beatradar.action.PAUSE"
        const val ACTION_RESUME = "com.rizky.beatradar.action.RESUME"
        const val ACTION_STOP = "com.rizky.beatradar.action.STOP"

        private const val SAMPLE_RATE = 44_100
        private const val INPUT_LATENCY_MS = 40L
        /** Without a confident reading for this long, the screen shows "listening for a beat". */
        private const val NO_BEAT_AFTER_MS = 3_000L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 2_000L
        /** A Shazam result this close to a logged song is treated as that song's name. */
        private const val RECOGNITION_WINDOW_MS = 90_000L
        /** Recognition looks at this much recent audio. */
        private const val QUERY_SECONDS = 12.0
        private const val MIN_QUERY_PRINTS = 20
        /** About 4 minutes of song at ~10 landmarks/s. */
        private const val MAX_PRINTS_PER_SONG = 2_500
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
