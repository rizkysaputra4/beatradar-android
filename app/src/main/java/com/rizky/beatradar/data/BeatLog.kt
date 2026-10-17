package com.rizky.beatradar.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

@Entity(tableName = "beat_log", indices = [Index("timestamp")])
data class BeatLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val title: String?,
    val artist: String?,
    val sourceApp: String?,
    val bpm: Float,
    val confidence: Float,
    val inRange: Boolean,
    /** A saved audio clip of the song for later identification, until it gets a name. */
    val clipPath: String? = null,
)

/** Audio fingerprint landmarks of a logged song, so the same song is recognised from any speaker. */
@Entity(
    tableName = "fingerprint",
    foreignKeys = [ForeignKey(entity = BeatLogEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("hash"), Index("entryId")],
)
data class Fingerprint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val hash: Int,
    val frame: Int,
)

data class StoredPrint(val entryId: Long, val hash: Int, val frame: Int)

@Dao
interface BeatLogDao {
    @Query(
        """SELECT * FROM beat_log
           WHERE (:query = '' OR title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%')
             AND (:onlyInRange = 0 OR inRange = 1)
             AND (:onlyIdentified = 0 OR title IS NOT NULL)
           ORDER BY timestamp DESC"""
    )
    fun observe(query: String, onlyInRange: Boolean, onlyIdentified: Boolean): Flow<List<BeatLogEntry>>

    @Query("SELECT * FROM beat_log WHERE id = :id")
    suspend fun byId(id: Long): BeatLogEntry?

    @Query("SELECT * FROM beat_log WHERE title = :title AND artist IS :artist AND id != :excludeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun findByName(title: String, artist: String?, excludeId: Long): BeatLogEntry?

    @Insert
    suspend fun insertPrints(prints: List<Fingerprint>)

    @Query("SELECT entryId, hash, frame FROM fingerprint WHERE hash IN (:hashes)")
    suspend fun printsFor(hashes: List<Int>): List<StoredPrint>

    @Query("SELECT COUNT(*) FROM fingerprint WHERE entryId = :id")
    suspend fun printCount(id: Long): Int

    @Query("UPDATE beat_log SET clipPath = :path WHERE id = :id")
    suspend fun setClip(id: Long, path: String?)

    @Query("SELECT clipPath FROM beat_log WHERE clipPath IS NOT NULL")
    suspend fun allClips(): List<String>

    @Query("SELECT * FROM beat_log ORDER BY timestamp DESC")
    suspend fun all(): List<BeatLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BeatLogEntry): Long

    @Query("UPDATE beat_log SET title = :title, artist = :artist, sourceApp = :sourceApp WHERE id = :id")
    suspend fun updateName(id: Long, title: String?, artist: String?, sourceApp: String?)

    @Query("UPDATE beat_log SET inRange = :inRange WHERE id = :id")
    suspend fun updateInRange(id: Long, inRange: Boolean)

    @Query("UPDATE beat_log SET timestamp = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("DELETE FROM beat_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM beat_log")
    suspend fun clear()

    @Query("DELETE FROM beat_log WHERE id NOT IN (SELECT id FROM beat_log ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trim(keep: Int = MAX_ENTRIES)

    companion object {
        const val MAX_ENTRIES = 1_000
    }
}

@Database(entities = [BeatLogEntry::class, Fingerprint::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun beatLog(): BeatLogDao

    companion object {
        /** v2: song fingerprints and saved clips. Keeps every existing log entry. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `clipPath` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fingerprint` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`entryId` INTEGER NOT NULL, `hash` INTEGER NOT NULL, `frame` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`entryId`) REFERENCES `beat_log`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingerprint_hash` ON `fingerprint` (`hash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingerprint_entryId` ON `fingerprint` (`entryId`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "beatradar.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

object CsvExport {
    fun write(entries: List<BeatLogEntry>, out: OutputStream) {
        out.bufferedWriter().use { w ->
            w.appendLine("timestamp,title,artist,source_app,bpm,confidence,in_range")
            for (e in entries) {
                w.appendLine(
                    listOf(
                        java.time.Instant.ofEpochMilli(e.timestamp).toString(),
                        e.title.orEmpty(), e.artist.orEmpty(), e.sourceApp.orEmpty(),
                        "%.1f".format(java.util.Locale.US, e.bpm),
                        "%.2f".format(java.util.Locale.US, e.confidence),
                        e.inRange.toString(),
                    ).joinToString(",") { escape(it) }
                )
            }
        }
    }

    internal fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
