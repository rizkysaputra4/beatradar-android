package com.boimin.beatradar.sync

import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.data.SongSet
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.net.SetIn
import com.boimin.beatradar.net.SetOut
import java.time.Instant

/** Sets backup for signed-in users (like the log, PLAN §7a): newest change wins, deletes travel as tombstones. */
object SetBackup {
    const val MAX_PUSH = 50

    fun toPush(set: SongSet, songs: List<SetSong>): SetIn = SetIn(
        id = set.syncId,
        name = set.name,
        songs = if (set.syncState == SyncState.DELETED) emptyList() else songs.map { it.slug },
        createdAt = Instant.ofEpochMilli(set.createdAt).toString(),
        updatedAt = Instant.ofEpochMilli(set.updatedAt).toString(),
        deleted = set.syncState == SyncState.DELETED,
    )

    sealed interface Action {
        data object Skip : Action
        data object Insert : Action
        data class Update(val localId: Long) : Action
        data class Delete(val localId: Long) : Action
    }

    /** What to do with a set pulled from the account, given this phone's copy. */
    fun decide(local: SongSet?, remote: SetOut): Action {
        val remoteAt = BackupRules.millis(remote.updatedAt)
        return when {
            local == null -> if (remote.deleted) Action.Skip else Action.Insert
            // This phone's change is newer (or the same): it goes up on the next push instead.
            remoteAt <= local.updatedAt -> Action.Skip
            remote.deleted -> Action.Delete(local.id)
            else -> Action.Update(local.id)
        }
    }

    fun fromRemote(remote: SetOut): Pair<SongSet, List<SetSong>> {
        val at = BackupRules.millis(remote.updatedAt)
        val set = SongSet(
            name = remote.name,
            createdAt = BackupRules.millis(remote.createdAt),
            updatedAt = at,
            syncId = remote.id,
            syncState = SyncState.CLEAN,
        )
        val songs = remote.songs.map { SetSong(0, it.slug, it.title, it.artist, it.bpm, it.artworkUrl, at) }
        return set to songs
    }
}
