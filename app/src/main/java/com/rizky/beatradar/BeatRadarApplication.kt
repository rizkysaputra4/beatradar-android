package com.rizky.beatradar

import android.app.Application
import android.content.Context
import com.rizky.beatradar.data.AppDatabase
import com.rizky.beatradar.data.SettingsRepository
import com.rizky.beatradar.meta.NowPlayingHub
import com.rizky.beatradar.meta.SongNamer
import com.rizky.beatradar.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BeatRadarApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // A Shazam result after "Identify" on a logged clip names that entry (and deletes its clip).
        scope.launch {
            NowPlayingHub.recognitions.collect { r ->
                SongNamer.consumePending(r)?.let { id -> SongNamer.name(this@BeatRadarApplication, id, r.title, r.artist, r.source) }
            }
        }
    }
}

val Context.app: BeatRadarApplication get() = applicationContext as BeatRadarApplication
