package com.rizky.beatradar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rizky.beatradar.app
import com.rizky.beatradar.capture.Monitor
import com.rizky.beatradar.capture.MonitorState
import com.rizky.beatradar.data.BeatLogEntry
import com.rizky.beatradar.data.ClipStore
import com.rizky.beatradar.data.CsvExport
import com.rizky.beatradar.data.Settings
import com.rizky.beatradar.meta.NowPlayingListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val log: StateFlow<List<BeatLogEntry>> = combine(query, onlyInRange, onlyIdentified) { q, r, i -> Triple(q.trim(), r, i) }
        .flatMapLatest { (q, r, i) -> dao.observe(q, r, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun delete(entry: BeatLogEntry) {
        viewModelScope.launch { dao.delete(entry.id) }
    }

    fun restore(entry: BeatLogEntry) {
        viewModelScope.launch { dao.insert(entry) }
    }

    fun clearLog() {
        viewModelScope.launch {
            dao.allClips().forEach(ClipStore::delete)
            dao.clear()
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
