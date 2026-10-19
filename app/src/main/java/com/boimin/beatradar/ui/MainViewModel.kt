package com.boimin.beatradar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.app
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.data.CsvExport
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.meta.NowPlayingListener
import com.boimin.beatradar.meta.SongNamer
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.net.ApiException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = application.app.settings
    private val dao = application.app.database.beatLog()

    /** Null until loaded, so the splash screen can wait and the theme never flashes. */
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val monitor: StateFlow<MonitorState> = Monitor.state

    val query = MutableStateFlow("")
    val onlyInRange = MutableStateFlow(false)
    val onlyIdentified = MutableStateFlow(false)
    /** Only unnamed songs that still have a clip to identify. */
    val onlyUnnamed = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val log: StateFlow<List<BeatLogEntry>> = combine(query, onlyInRange, onlyIdentified, onlyUnnamed) { q, r, i, u -> Filter(q.trim(), r, i, u) }
        .flatMapLatest { f -> dao.observe(f.query, f.inRange, f.identified, f.unnamed) }
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

    private data class Filter(val query: String, val inRange: Boolean, val identified: Boolean, val unnamed: Boolean)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settings.value
            repo.update(transform)
            // A lower limit drops the oldest clips right away.
            val after = before?.let(transform) ?: return@launch
            if (after.clipLimit < before.clipLimit) ClipStore.trim(dao, after.clipLimit)
        }
    }

    /** The user typed a name: always applies, and merges with an entry of the same song. */
    fun rename(entry: BeatLogEntry, title: String, artist: String) {
        viewModelScope.launch {
            SongNamer.name(getApplication(), entry.id, title, artist, sourceLabel = null, source = NameSource.MANUAL, manual = true)
            getApplication<Application>().app.requestSync()
        }
    }

    /** Plays a saved clip once, so the user can recall which song it was. */
    fun playClip(entry: BeatLogEntry) {
        entry.clipPath?.let { runCatching { ClipStore.play(it, times = 1) } }
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

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val entries = dao.all()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { CsvExport.write(entries, it) }
            }
        }
    }

    fun hasNotificationAccess(): Boolean = NowPlayingListener.hasAccess(getApplication())
}
