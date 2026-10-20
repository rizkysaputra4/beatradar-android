package com.boimin.beatradar.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Sets: the user's own lists of songs by tempo ("Morning run 160–170", "Warm-up"), like playlists for a beat log.
 * BeatRadar plays nothing, so a set is a tempo plan: its songs sorted by BPM, with the ones in the current target
 * highlighted. Kept on this phone, and backed up to the account when signed in.
 */
@Entity(tableName = "song_set", indices = [Index(value = ["syncId"], unique = true)])
data class SongSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** The set's id on every phone and on the server. */
    @ColumnInfo(defaultValue = "") val syncId: String = UUID.randomUUID().toString(),
    /** Backup state ([SyncState]); [SyncState.DELETED] sets are tombstones waiting to be pushed. */
    @ColumnInfo(defaultValue = "1") val syncState: Int = SyncState.DIRTY,
    /** The share link's slug once shared, so sharing again updates the same link. */
    val shareSlug: String? = null,
)

/** A Library song in a set, copied so the set shows without a connection. */
@Entity(
    tableName = "set_song",
    primaryKeys = ["setId", "slug"],
    foreignKeys = [ForeignKey(entity = SongSet::class, parentColumns = ["id"], childColumns = ["setId"], onDelete = ForeignKey.CASCADE)],
)
data class SetSong(
    val setId: Long,
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float,
    val artworkUrl: String? = null,
    val addedAt: Long,
)

/** A set with what its list card shows. */
data class SetSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val songs: Int,
    val minBpm: Float?,
    val maxBpm: Float?,
)

/** Marks a set changed for the backup (a tombstone stays one). */
private const val SET_CHANGED = "syncState = CASE WHEN syncState = 2 THEN 2 ELSE 1 END"

@Dao
interface SetDao {
    @Query(
        """SELECT s.id, s.name, s.updatedAt, COUNT(x.slug) AS songs, MIN(x.bpm) AS minBpm, MAX(x.bpm) AS maxBpm
           FROM song_set s LEFT JOIN set_song x ON x.setId = s.id
           WHERE s.syncState != 2
           GROUP BY s.id ORDER BY s.updatedAt DESC""",
    )
    fun observeSets(): Flow<List<SetSummary>>

    @Query("SELECT * FROM set_song WHERE setId = :setId ORDER BY bpm ASC, title COLLATE NOCASE ASC")
    fun observeSongs(setId: Long): Flow<List<SetSong>>

    @Query("SELECT * FROM set_song WHERE setId = :setId ORDER BY bpm ASC, title COLLATE NOCASE ASC")
    suspend fun songs(setId: Long): List<SetSong>

    /** Sets the song is in, for the "Add to set" sheet. */
    @Query("SELECT x.setId FROM set_song x JOIN song_set s ON s.id = x.setId WHERE x.slug = :slug AND s.syncState != 2")
    fun observeSetsWith(slug: String): Flow<List<Long>>

    @Query("SELECT * FROM song_set WHERE id = :id")
    suspend fun byId(id: Long): SongSet?

    @Query("SELECT * FROM song_set WHERE syncId = :syncId")
    suspend fun bySyncId(syncId: String): SongSet?

    @Insert
    suspend fun insertSet(set: SongSet): Long

    @Query("UPDATE song_set SET name = :name, updatedAt = :now, $SET_CHANGED WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM song_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    /** Signed in, a delete is a tombstone so it reaches the user's other phones. */
    @Query("UPDATE song_set SET syncState = 2, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SetSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SetSong>)

    @Query("DELETE FROM set_song WHERE setId = :setId AND slug = :slug")
    suspend fun deleteSong(setId: Long, slug: String)

    @Query("DELETE FROM set_song WHERE setId = :setId")
    suspend fun deleteSongs(setId: Long)

    @Query("UPDATE song_set SET updatedAt = :now, $SET_CHANGED WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE song_set SET shareSlug = :slug WHERE id = :id")
    suspend fun setShareSlug(id: Long, slug: String)

    @Transaction
    suspend fun add(song: SetSong) {
        insertSong(song)
        touch(song.setId, song.addedAt)
    }

    @Transaction
    suspend fun remove(setId: Long, slug: String, now: Long) {
        deleteSong(setId, slug)
        touch(setId, now)
    }

    // Backup

    @Query("SELECT * FROM song_set WHERE syncState != 0 LIMIT :limit")
    suspend fun changedForBackup(limit: Int): List<SongSet>

    /** Clean only if nothing changed while the push was on its way. */
    @Query("UPDATE song_set SET syncState = 0 WHERE id = :id AND updatedAt = :updatedAt AND syncState = 1")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM song_set WHERE id = :id AND updatedAt = :updatedAt AND syncState = 2")
    suspend fun dropTombstone(id: Long, updatedAt: Long)

    /** After signing in, every set goes up to the account. */
    @Query("UPDATE song_set SET $SET_CHANGED")
    suspend fun markAllDirty()

    @Insert
    suspend fun insertSetRaw(set: SongSet): Long

    @Query("UPDATE song_set SET name = :name, updatedAt = :updatedAt, syncState = 0 WHERE id = :id")
    suspend fun applyRemote(id: Long, name: String, updatedAt: Long)

    /** A newer version from the backup, without marking it changed again. */
    @Transaction
    suspend fun applyRemoteSet(local: SongSet?, remote: SongSet, songs: List<SetSong>) {
        val id = if (local == null) {
            insertSetRaw(remote.copy(id = 0, syncState = SyncState.CLEAN))
        } else {
            applyRemote(local.id, remote.name, remote.updatedAt)
            local.id
        }
        deleteSongs(id)
        insertSongs(songs.map { it.copy(setId = id) })
    }
}

object SetRules {
    const val MAX_NAME = 40

    /** A trimmed name, or null when there's nothing left. */
    fun cleanName(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).ifEmpty { null }

    /** "118–128 BPM", "124 BPM", or "No songs yet". */
    fun span(min: Float?, max: Float?): String = when {
        min == null || max == null -> "No songs yet"
        Math.round(min) == Math.round(max) -> "${Math.round(min)} BPM"
        else -> "${Math.round(min)}–${Math.round(max)} BPM"
    }
}
