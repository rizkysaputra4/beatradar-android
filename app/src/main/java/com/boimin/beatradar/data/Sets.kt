package com.boimin.beatradar.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Sets: the user's own lists of songs by tempo ("Morning run 160–170", "Warm-up"), like playlists for a beat log.
 * BeatRadar plays nothing, so a set is a tempo plan: its songs sorted by BPM, with the ones in the current target
 * highlighted. Kept on this phone.
 */
@Entity(tableName = "song_set")
data class SongSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
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

@Dao
interface SetDao {
    @Query(
        """SELECT s.id, s.name, s.updatedAt, COUNT(x.slug) AS songs, MIN(x.bpm) AS minBpm, MAX(x.bpm) AS maxBpm
           FROM song_set s LEFT JOIN set_song x ON x.setId = s.id
           GROUP BY s.id ORDER BY s.updatedAt DESC""",
    )
    fun observeSets(): Flow<List<SetSummary>>

    @Query("SELECT * FROM set_song WHERE setId = :setId ORDER BY bpm ASC, title COLLATE NOCASE ASC")
    fun observeSongs(setId: Long): Flow<List<SetSong>>

    /** Sets the song is in, for the "Add to set" sheet. */
    @Query("SELECT setId FROM set_song WHERE slug = :slug")
    fun observeSetsWith(slug: String): Flow<List<Long>>

    @Insert
    suspend fun insertSet(set: SongSet): Long

    @Query("UPDATE song_set SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM song_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SetSong)

    @Query("DELETE FROM set_song WHERE setId = :setId AND slug = :slug")
    suspend fun deleteSong(setId: Long, slug: String)

    @Query("UPDATE song_set SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

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
