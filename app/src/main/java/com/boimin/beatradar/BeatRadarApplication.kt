package com.boimin.beatradar

import android.app.Application
import android.content.Context
import com.boimin.beatradar.account.AccountRepository
import com.boimin.beatradar.data.AppDatabase
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.SettingsRepository
import com.boimin.beatradar.library.LibraryRepository
import com.boimin.beatradar.meta.NowPlayingHub
import com.boimin.beatradar.meta.SongNamer
import com.boimin.beatradar.notify.Notifier
import com.boimin.beatradar.notify.RecapWorker
import com.boimin.beatradar.sync.LibrarySync
import com.boimin.beatradar.sync.SyncWorker
import com.boimin.beatradar.sync.UnnamedRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BeatRadarApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val account: AccountRepository by lazy { AccountRepository(this) }
    val sync: LibrarySync by lazy { LibrarySync(this, database, account, settings) }
    val library: LibraryRepository by lazy { LibraryRepository(account) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // A Shazam result after "Identify" on a logged clip names that entry (and deletes its clip).
        scope.launch {
            NowPlayingHub.recognitions.collect { r ->
                SongNamer.consumePending(r)?.let { id ->
                    SongNamer.name(this@BeatRadarApplication, id, r.title, r.artist, r.source, NameSource.fromRecognition(r.source))
                    requestSync()
                }
            }
        }
        // Signing in uploads this phone's log to the account and pulls the rest down.
        scope.launch {
            account.signedIn.collect { me ->
                sync.onSignedIn(me.id)
                requestSync()
            }
        }
        requestSync()
        RecapWorker.schedule(this)
    }

    /** Uploads new measurements (and syncs the log backup when signed in) as soon as there is a network. */
    fun requestSync() = SyncWorker.request(this)

    /** A sync once an unnamed song that is playing now has finished, so its fingerprint can go up. */
    fun requestSyncAfterSong() = SyncWorker.requestLater(this, UnnamedRules.QUIET_MS + 60_000)
}

val Context.app: BeatRadarApplication get() = applicationContext as BeatRadarApplication
