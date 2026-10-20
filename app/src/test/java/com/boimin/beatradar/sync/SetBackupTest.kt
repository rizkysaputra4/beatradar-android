package com.boimin.beatradar.sync

import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.data.SongSet
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.net.SetOut
import com.boimin.beatradar.net.SongSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SetBackupTest {
    private val t = 1_800_000_000_000L
    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
    private val local = SongSet(id = 7, name = "Run", createdAt = t, updatedAt = t, syncId = "abc", syncState = SyncState.CLEAN)
    private fun remote(at: Long, deleted: Boolean = false) =
        SetOut("abc", "Run 2", listOf(SongSummary("s1", "Levels", "Avicii", 126f)), iso(t), iso(at), deleted)

    @Test fun newerRemoteWins() {
        assertEquals(SetBackup.Action.Update(7), SetBackup.decide(local, remote(t + 1000)))
        assertEquals(SetBackup.Action.Delete(7), SetBackup.decide(local, remote(t + 1000, deleted = true)))
    }

    @Test fun olderOrSameRemoteIsSkipped() {
        assertEquals(SetBackup.Action.Skip, SetBackup.decide(local, remote(t)))
        assertEquals(SetBackup.Action.Skip, SetBackup.decide(local, remote(t - 1)))
    }

    @Test fun unknownSetsArriveUnlessDeleted() {
        assertEquals(SetBackup.Action.Insert, SetBackup.decide(null, remote(t)))
        assertEquals(SetBackup.Action.Skip, SetBackup.decide(null, remote(t, deleted = true)))
    }

    @Test fun tombstonesGoUpWithoutSongs() {
        val songs = listOf(SetSong(7, "s1", "Levels", "Avicii", 126f, null, t))
        assertEquals(listOf("s1"), SetBackup.toPush(local.copy(syncState = SyncState.DIRTY), songs).songs)
        val gone = SetBackup.toPush(local.copy(syncState = SyncState.DELETED), songs)
        assertTrue(gone.deleted && gone.songs.isEmpty())
    }

    @Test fun pulledSetsAreClean() {
        val (set, songs) = SetBackup.fromRemote(remote(t + 5))
        assertEquals("abc", set.syncId)
        assertEquals(SyncState.CLEAN, set.syncState)
        assertEquals("Levels", songs.single().title)
    }
}
