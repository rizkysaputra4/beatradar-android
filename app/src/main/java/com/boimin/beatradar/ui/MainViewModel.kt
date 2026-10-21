package com.boimin.beatradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.app
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.sync.ClipRules
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.PrintUpload
import com.boimin.beatradar.data.Recap
import com.boimin.beatradar.data.RecapCalc
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.meta.NowPlayingListener
import com.boimin.beatradar.meta.SongNamer
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.net.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = application.app.settings
    private val dao = application.app.database.beatLog()


    /** Null until loaded, so the splash screen can wait and the theme never flashes. */
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val monitor: StateFlow<MonitorState> = Monitor.state

    val query = MutableStateFlow("")
    /** The log's BPM filter is on. */
    val onlyInRange = MutableStateFlow(false)
    /** The log's own BPM range; null follows the Listen tab's target range. */
    val logBpmRange = MutableStateFlow<IntRange?>(null)
    val onlyIdentified = MutableStateFlow(false)
    /** Only unnamed songs that still have a clip to identify. */
    val onlyUnnamed = MutableStateFlow(false)

    /**
     * The BPM filter uses the log's own range, or (until the user changes it there) the Listen tab's current target
     * range, never the one set when a song was logged.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val log: StateFlow<List<BeatLogEntry>> = combine(query, onlyIdentified, onlyUnnamed) { q, i, u -> Filter(q.trim(), i, u) }
        .flatMapLatest { f -> dao.observe(f.query, false, f.identified, f.unnamed) }
        .combine(combine(onlyInRange, settings, logBpmRange) { on, s, custom -> s?.takeIf { on }?.let { (custom ?: (it.minBpm..it.maxBpm)) to it.halfDoubleMatch } }) { entries, filter ->
            if (filter == null) entries else entries.filter { Settings.bpmIn(it.bpm, filter.first, filter.second) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The last three songs heard, for "Heard recently" on the Listen tab. */
    val recent: StateFlow<List<BeatLogEntry>> = dao.observe("", false, false, false).map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unnamedWithClip: StateFlow<Int> = dao.observeUnnamedWithClip()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Unnamed songs with a name suggested by the server (PLAN §5c). */
    val suggestedCount: StateFlow<Int> = dao.observeSuggested()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The log entry of the song playing now, so the Listen screen can show its suggestion. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentEntry: StateFlow<BeatLogEntry?> = Monitor.state.map { it.entryId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun messageShown() {
        _message.value = null
    }

    /** "Is this …?" → Yes: names the entry with source `server` (merge rules, clip deleted, upload queued). */
    fun acceptSuggestion(entry: BeatLogEntry) {
        val s = entry.suggestion ?: return
        viewModelScope.launch {
            try {
                if (getApplication<Application>().app.sync.acceptSuggestion(entry.id)) {
                    if (Monitor.state.value.entryId == entry.id) {
                        Monitor.update { it.copy(title = s.title, artist = s.artist, placeholderNo = null) }
                    }
                    getApplication<Application>().app.requestSync()
                } else {
                    _message.value = "That suggestion is no longer available"
                }
            } catch (e: ApiException) {
                _message.value = LibraryViewModel.describe(e)
            }
        }
    }

    /** "Is this …?" → No: never suggested again for this entry; the next match shows if there is one. */
    fun rejectSuggestion(entry: BeatLogEntry) {
        viewModelScope.launch {
            try {
                getApplication<Application>().app.sync.rejectSuggestion(entry.id)
            } catch (e: ApiException) {
                _message.value = LibraryViewModel.describe(e)
            }
        }
    }

    private data class Filter(val query: String, val identified: Boolean, val unnamed: Boolean)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settings.value
            repo.update(transform)
            // A lower limit drops the oldest clips right away.
            val after = before?.let(transform) ?: return@launch
            if (after.clipLimit < before.clipLimit) ClipStore.trim(dao, after.clipLimit)
            // Sharing clips turned on or off: send the consent and clips, or delete them on the server, now.
            if (ClipRules.sharing(after) != ClipRules.sharing(before)) getApplication<Application>().app.requestSync()
        }
    }

    /**
     * The user typed a name: always applies, and merges with an entry of the same song. An unnamed song whose
     * fingerprint went to the server also proposes the name for that unknown song (crowd names: an admin decides),
     * before the sync forgets the fingerprint.
     */
    fun rename(entry: BeatLogEntry, title: String, artist: String) {
        viewModelScope.launch {
            if (entry.title == null && entry.printUpload == PrintUpload.SENT && artist.isNotBlank()) {
                runCatching { getApplication<Application>().app.library.proposeName(entry.id.toString(), title, artist) }
            }
            SongNamer.name(getApplication(), entry.id, title, artist, sourceLabel = null, source = NameSource.MANUAL, manual = true)
            getApplication<Application>().app.requestSync()
        }
    }

    /** Plays a saved clip once, so the user can recall which song it was. */
    /** Plays an unnamed song's clip; tapping the one playing stops it. */
    fun playClip(entry: BeatLogEntry) {
        val path = entry.clipPath ?: return
        if (ClipStore.playing.value == path) ClipStore.stop() else runCatching { ClipStore.play(path, times = 1) }
    }

    private val signedIn: Boolean get() = getApplication<Application>().app.account.user.value != null

    /** Signed in, a delete is a tombstone so it reaches the user's other phones (PLAN §7a). */
    fun delete(entry: BeatLogEntry) {
        viewModelScope.launch {
            if (signedIn) dao.markDeleted(entry.id) else dao.delete(entry.id)
            getApplication<Application>().app.requestSync()
        }
    }

    /** Undo: back as a fresh change, so the backup undeletes it too. */
    fun restore(entry: BeatLogEntry) {
        viewModelScope.launch { dao.insert(entry.copy(syncState = SyncState.DIRTY, updatedAt = System.currentTimeMillis())) }
    }

    fun clearLog() {
        viewModelScope.launch {
            dao.allClips().forEach(ClipStore::delete)
            if (signedIn) dao.markAllDeleted() else dao.clear()
            getApplication<Application>().app.requestSync()
        }
    }

    /** The weekly recap sheet is open (from the Log tab or the Sunday notification). */
    val showRecap = MutableStateFlow(false)

    private val _recap = MutableStateFlow<Recap?>(null)
    val recap: StateFlow<Recap?> = _recap

    fun loadRecap() {
        viewModelScope.launch { _recap.value = RecapCalc.week(dao.all(), System.currentTimeMillis()) }
    }

    /** A song to open in the Library (from a share link). */
    val openSong = MutableStateFlow<String?>(null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Pull-to-refresh on the Log: uploads, then (signed in) pulls entries from the user's other phones. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            getApplication<Application>().app.sync.run()
            _refreshing.value = false
        }
    }

    /** Called once a delete can no longer be undone. */
    fun forgetClip(entry: BeatLogEntry) = ClipStore.delete(entry.clipPath)

    fun hasNotificationAccess(): Boolean = NowPlayingListener.hasAccess(getApplication())
}
