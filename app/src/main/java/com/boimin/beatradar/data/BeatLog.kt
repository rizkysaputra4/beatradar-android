package com.boimin.beatradar.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
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

@Entity(tableName = "beat_log", indices = [Index("timestamp"), Index("matchKey")])
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
    /** "Song #N" while the entry has no name. Only meaningful on this phone. */
    val placeholderNo: Int? = null,
    /** How many separate plays of this song were heard. */
    @ColumnInfo(defaultValue = "1") val playCount: Int = 1,
    val nameSource: NameSource? = null,
    /** [SongKey] of the name, so differently written names of one song find each other. */
    val matchKey: String? = null,
    /** The app playing on this phone when the song was heard, or null for a speaker nearby. */
    val phoneApp: String? = null,
    /** Library upload of this play ([UploadState]). */
    @ColumnInfo(defaultValue = "0") val uploadState: Int = UploadState.NONE,
    /** The Library song this entry is, once the server knows it. */
    val songSlug: String? = null,
    /** Log backup state ([SyncState]); [SyncState.DELETED] rows are tombstones waiting to be pushed. */
    @ColumnInfo(defaultValue = "1") val syncState: Int = SyncState.DIRTY,
    /** Last change, for "newest wins" in the log backup (epoch ms). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** Set for entries pulled from the user's other phones: their install id and log id there. */
    val remoteInstallId: String? = null,
    val remoteClientRef: String? = null,
    /** Upload of an unnamed entry's fingerprint so the crowd can name it later (PLAN §5c, [PrintUpload]). */
    @ColumnInfo(defaultValue = "0") val printUpload: Int = PrintUpload.NONE,
    /** A name the server suggests for this unnamed entry, waiting for the user's yes or no. */
    @Embedded(prefix = "sug") val suggestion: NameSuggestion? = null,
) {
    val label: String get() = title ?: placeholderNo?.let { "Song #$it" } ?: "Unknown song"
}

/** "Is this <title> – <artist>?" from the server (PLAN §5c). [state]: `candidate` or `trusted`. */
data class NameSuggestion(val slug: String, val title: String, val artist: String, val score: Int, val state: String)

/** Upload of an unnamed entry's fingerprint (PLAN §5c). */
object PrintUpload {
    const val NONE = 0
    /** The play is over and its landmarks wait to be sent. */
    const val PENDING = 1
    /** The server has it (stored or already suggested). */
    const val SENT = 2
    /** Refused, or not to be sent (contribution off): never retried. */
    const val SKIPPED = 3
}

/**
 * Entries whose unnamed fingerprint the server should forget (`DELETE /v1/unnamed/{id}`): filled by triggers when a
 * sent entry gets a name, becomes a tombstone or is deleted, so no code path can forget to.
 */
@Entity(tableName = "unnamed_forget")
data class UnnamedForget(@PrimaryKey val entryId: Long)

/** Upload of an entry's measurement to the Library (PLAN §7). */
object UploadState {
    const val NONE = 0
    /** Named and waiting to be sent. */
    const val PENDING = 1
    const val SENT = 2
    /** Refused by the server, or not to be sent (contribution off): never retried. */
    const val SKIPPED = 3
}

/** Log backup state of an entry (PLAN §7a). */
object SyncState {
    const val CLEAN = 0
    const val DIRTY = 1
    const val DELETED = 2
}

/** Epoch milliseconds in SQL, for `updatedAt`. */
private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
/** Marks a row changed for the log backup. */
private const val CHANGED = "syncState = CASE WHEN syncState = 2 THEN 2 ELSE 1 END, updatedAt = $NOW_MS"

/** An entry that holds a saved clip. */
data class ClipRef(val id: Long, val clipPath: String, val timestamp: Long)

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
             AND (:onlyUnnamed = 0 OR (title IS NULL AND (clipPath IS NOT NULL OR sugslug IS NOT NULL)))
             AND syncState != 2
           ORDER BY timestamp DESC"""
    )
    fun observe(query: String, onlyInRange: Boolean, onlyIdentified: Boolean, onlyUnnamed: Boolean): Flow<List<BeatLogEntry>>

    /** Unnamed songs that can still be identified: from their clip, or with a suggested name. */
    @Query("SELECT COUNT(*) FROM beat_log WHERE title IS NULL AND (clipPath IS NOT NULL OR sugslug IS NOT NULL) AND syncState != 2")
    fun observeUnnamedWithClip(): Flow<Int>

    @Query("SELECT * FROM beat_log WHERE id = :id")
    suspend fun byId(id: Long): BeatLogEntry?

    /** The oldest other entry with the same [SongKey]. */
    @Query("SELECT * FROM beat_log WHERE matchKey = :key AND id != :excludeId AND syncState != 2 ORDER BY id LIMIT 1")
    suspend fun findByKey(key: String, excludeId: Long): BeatLogEntry?

    @Query("SELECT MAX(placeholderNo) FROM beat_log")
    suspend fun maxPlaceholderNo(): Int?

    /** A new name: changed for the backup, and (on the phone that heard it) queued for the Library. */
    @Query(
        """UPDATE beat_log SET title = :title, artist = :artist, sourceApp = :sourceApp, nameSource = :source, matchKey = :key,
             uploadState = CASE WHEN remoteInstallId IS NULL THEN 1 ELSE uploadState END, $CHANGED
           WHERE id = :id"""
    )
    suspend fun setName(id: Long, title: String, artist: String?, sourceApp: String?, source: NameSource, key: String)

    /** Folds a duplicate's plays into [keepId]: newest time, plays added up, in range if either was. */
    @Query(
        """UPDATE beat_log SET
             timestamp = MAX(timestamp, :timestamp),
             playCount = playCount + :plays,
             inRange = (inRange OR :inRange),
             uploadState = CASE WHEN title IS NOT NULL AND remoteInstallId IS NULL THEN 1 ELSE uploadState END,
             $CHANGED
           WHERE id = :keepId"""
    )
    suspend fun absorb(keepId: Long, timestamp: Long, plays: Int, inRange: Boolean)

    @Query("UPDATE fingerprint SET entryId = :toId WHERE entryId = :fromId")
    suspend fun movePrints(fromId: Long, toId: Long)

    /** Another play of a song already in the log. */
    @Query(
        """UPDATE beat_log SET timestamp = :timestamp, playCount = playCount + 1,
             uploadState = CASE WHEN title IS NOT NULL AND remoteInstallId IS NULL THEN 1 ELSE uploadState END, $CHANGED
           WHERE id = :id"""
    )
    suspend fun countPlay(id: Long, timestamp: Long)

    @Insert
    suspend fun insertPrints(prints: List<Fingerprint>)

    @Query("SELECT entryId, hash, frame FROM fingerprint WHERE hash IN (:hashes)")
    suspend fun printsFor(hashes: List<Int>): List<StoredPrint>

    @Query("SELECT COUNT(*) FROM fingerprint WHERE entryId = :id")
    suspend fun printCount(id: Long): Int

    /**
     * Keeps fingerprints only for the [keep] most recently heard songs (~150 KB each). Older entries are still
     * recognised by title, and get fingerprints again when heard again.
     */
    @Query("DELETE FROM fingerprint WHERE entryId IN (SELECT id FROM beat_log ORDER BY timestamp DESC LIMIT -1 OFFSET :keep)")
    suspend fun trimPrints(keep: Int = MAX_FINGERPRINTED)

    @Query("UPDATE beat_log SET clipPath = :path WHERE id = :id")
    suspend fun setClip(id: Long, path: String?)

    @Query("SELECT clipPath FROM beat_log WHERE clipPath IS NOT NULL")
    suspend fun allClips(): List<String>

    @Query("SELECT id, clipPath, timestamp FROM beat_log WHERE clipPath IS NOT NULL")
    suspend fun clipRefs(): List<ClipRef>

    @Query("SELECT * FROM beat_log WHERE syncState != 2 ORDER BY timestamp DESC")
    suspend fun all(): List<BeatLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BeatLogEntry): Long

    @Query("UPDATE beat_log SET inRange = :inRange, $CHANGED WHERE id = :id")
    suspend fun updateInRange(id: Long, inRange: Boolean)

    @Query("UPDATE beat_log SET timestamp = :timestamp, $CHANGED WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("DELETE FROM beat_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM beat_log")
    suspend fun clear()

    /** Local only: trimmed entries stay in the account's backup and are never pushed as deletes. */
    @Query("DELETE FROM beat_log WHERE id NOT IN (SELECT id FROM beat_log ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trim(keep: Int = MAX_ENTRIES)

    // Library uploads

    @Query("SELECT * FROM beat_log WHERE uploadState = 1 AND syncState != 2 ORDER BY timestamp LIMIT :limit")
    suspend fun pendingUploads(limit: Int): List<BeatLogEntry>

    /** Sent (or refused): unless another play was counted meanwhile, which is a new measurement. */
    @Query("UPDATE beat_log SET uploadState = :state, songSlug = COALESCE(:slug, songSlug) WHERE id = :id AND playCount = :playCount")
    suspend fun setUploaded(id: Long, playCount: Int, state: Int, slug: String?)

    /** Contribution switched off: what's queued is never sent. */
    @Query("UPDATE beat_log SET uploadState = 3 WHERE uploadState = 1")
    suspend fun skipPendingUploads()

    @Query("SELECT entryId, hash, frame FROM fingerprint WHERE entryId = :id ORDER BY frame LIMIT :limit")
    suspend fun printsOf(id: Long, limit: Int): List<StoredPrint>

    @Query("DELETE FROM fingerprint WHERE entryId = :id")
    suspend fun deletePrints(id: Long)

    @Query("UPDATE beat_log SET songSlug = :slug WHERE id = :id")
    suspend fun setSongSlug(id: Long, slug: String)

    @Query("SELECT * FROM beat_log WHERE songSlug = :slug AND syncState != 2 ORDER BY id LIMIT 1")
    suspend fun findBySlug(slug: String): BeatLogEntry?

    // Unnamed fingerprints and suggestions (PLAN §5c)

    /**
     * An unnamed song whose play is over (not heard for [quietMs]) and that has fingerprints: its landmarks can go up.
     * Covers new songs, songs logged before this existed, and plays cut short by the app being closed.
     */
    @Query(
        """UPDATE beat_log SET printUpload = 1
           WHERE printUpload = 0 AND title IS NULL AND syncState != 2 AND remoteInstallId IS NULL
             AND timestamp < :before AND id IN (SELECT DISTINCT entryId FROM fingerprint)"""
    )
    suspend fun markUnnamedPrintsPending(before: Long)

    @Query("SELECT * FROM beat_log WHERE printUpload = 1 AND title IS NULL AND syncState != 2 ORDER BY timestamp LIMIT :limit")
    suspend fun pendingUnnamedPrints(limit: Int): List<BeatLogEntry>

    @Query("UPDATE beat_log SET printUpload = :state WHERE id = :id")
    suspend fun setPrintUpload(id: Long, state: Int)

    /** Contribution switched off: queued fingerprints are never sent. */
    @Query("UPDATE beat_log SET printUpload = 3 WHERE printUpload = 1")
    suspend fun skipPendingPrints()

    @Query(
        """UPDATE beat_log SET sugslug = :slug, sugtitle = :title, sugartist = :artist, sugscore = :score, sugstate = :state
           WHERE id = :id AND title IS NULL"""
    )
    suspend fun setSuggestion(id: Long, slug: String, title: String, artist: String, score: Int, state: String)

    @Query("UPDATE beat_log SET sugslug = NULL, sugtitle = NULL, sugartist = NULL, sugscore = NULL, sugstate = NULL WHERE id = :id")
    suspend fun clearSuggestion(id: Long)

    /** The server's list is the truth: suggestions it no longer has (answered elsewhere, expired) go away. */
    @Query(
        """UPDATE beat_log SET sugslug = NULL, sugtitle = NULL, sugartist = NULL, sugscore = NULL, sugstate = NULL
           WHERE sugslug IS NOT NULL AND id NOT IN (:keep)"""
    )
    suspend fun clearSuggestionsExcept(keep: List<Long>)

    @Query("SELECT id FROM beat_log WHERE id IN (:ids) AND title IS NULL AND syncState != 2")
    suspend fun unnamedAmong(ids: List<Long>): List<Long>

    @Query("SELECT COUNT(*) FROM beat_log WHERE title IS NULL AND sugslug IS NOT NULL AND syncState != 2")
    fun observeSuggested(): Flow<Int>

    @Query("SELECT * FROM beat_log WHERE id = :id")
    fun observeById(id: Long): Flow<BeatLogEntry?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun forgetUnnamed(items: List<UnnamedForget>)

    @Query("SELECT entryId FROM unnamed_forget LIMIT :limit")
    suspend fun unnamedToForget(limit: Int): List<Long>

    @Query("DELETE FROM unnamed_forget WHERE entryId = :id")
    suspend fun forgotten(id: Long)

    // Log backup

    /** A delete while signed in: a tombstone the backup pushes before the row is removed. */
    @Query("UPDATE beat_log SET syncState = 2, updatedAt = $NOW_MS, clipPath = NULL WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("UPDATE beat_log SET syncState = 2, updatedAt = $NOW_MS, clipPath = NULL WHERE syncState != 2")
    suspend fun markAllDeleted()

    /** After signing in, the whole log goes up to the account. */
    @Query("UPDATE beat_log SET syncState = 1 WHERE syncState = 0")
    suspend fun markAllDirty()

    @Query("SELECT * FROM beat_log WHERE syncState != 0 ORDER BY updatedAt LIMIT :limit")
    suspend fun changedForBackup(limit: Int): List<BeatLogEntry>

    /** Pushed: clean unless it changed again meanwhile. */
    @Query("UPDATE beat_log SET syncState = 0 WHERE id = :id AND updatedAt = :updatedAt AND syncState = 1")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM beat_log WHERE id = :id AND syncState = 2 AND updatedAt = :updatedAt")
    suspend fun dropTombstone(id: Long, updatedAt: Long)

    @Query("SELECT * FROM beat_log WHERE remoteInstallId = :installId AND remoteClientRef = :clientRef LIMIT 1")
    suspend fun byRemote(installId: String, clientRef: String): BeatLogEntry?

    /** Applies a newer version from the backup without marking it changed again. */
    @Query(
        """UPDATE beat_log SET title = :title, artist = :artist, bpm = :bpm, confidence = :confidence, inRange = :inRange,
             timestamp = :timestamp, matchKey = :matchKey, songSlug = COALESCE(:slug, songSlug),
             nameSource = CASE WHEN :title IS NULL THEN NULL WHEN title IS :title THEN nameSource ELSE 'MANUAL' END,
             syncState = 0, updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun applyRemote(
        id: Long, title: String?, artist: String?, bpm: Float, confidence: Float, inRange: Boolean,
        timestamp: Long, matchKey: String?, slug: String?, updatedAt: Long,
    )

    companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_FINGERPRINTED = 200
    }
}

@Database(entities = [BeatLogEntry::class, Fingerprint::class, UnnamedForget::class], version = 5, exportSchema = false)
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

        /**
         * v3: labels (PLAN §5b). Unnamed entries get "Song #N" numbers in the order they were heard; named ones
         * get their match key and a best-guess name source. Keeps every existing log entry.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `placeholderNo` INTEGER")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `nameSource` TEXT")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `matchKey` TEXT")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `phoneApp` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_beat_log_matchKey` ON `beat_log` (`matchKey`)")
                var next = 1
                db.query("SELECT id, title, artist, sourceApp FROM beat_log ORDER BY timestamp ASC, id ASC").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val title = if (c.isNull(1)) null else c.getString(1)
                        if (title == null) {
                            db.execSQL("UPDATE beat_log SET placeholderNo = ? WHERE id = ?", arrayOf<Any>(next++, id))
                        } else {
                            val artist = if (c.isNull(2)) null else c.getString(2)
                            val source = NameSource.fromLegacyLabel(if (c.isNull(3)) null else c.getString(3))
                            db.execSQL(
                                "UPDATE beat_log SET matchKey = ?, nameSource = ? WHERE id = ?",
                                arrayOf<Any>(SongKey.of(title, artist), source.name, id),
                            )
                        }
                    }
                }
            }
        }

        /**
         * v4: Library upload and log backup state (PLAN §7, §7a). Named entries are queued for upload, and every entry
         * is marked changed so it reaches the account's backup after signing in. Keeps every existing log entry.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `uploadState` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `songSlug` TEXT")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `remoteInstallId` TEXT")
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `remoteClientRef` TEXT")
                db.execSQL("UPDATE beat_log SET updatedAt = timestamp, uploadState = CASE WHEN title IS NOT NULL THEN 1 ELSE 0 END")
            }
        }

        /** Triggers that queue `DELETE /v1/unnamed/{id}` for sent fingerprints whose entry got a name or went away. */
        private val FORGET_TRIGGERS = listOf(
            """CREATE TRIGGER IF NOT EXISTS unnamed_forget_on_name AFTER UPDATE OF title ON beat_log
               WHEN OLD.printUpload = 2 AND NEW.title IS NOT NULL
               BEGIN INSERT OR IGNORE INTO unnamed_forget (entryId) VALUES (OLD.id); END""",
            """CREATE TRIGGER IF NOT EXISTS unnamed_forget_on_tombstone AFTER UPDATE OF syncState ON beat_log
               WHEN OLD.printUpload = 2 AND NEW.syncState = 2
               BEGIN INSERT OR IGNORE INTO unnamed_forget (entryId) VALUES (OLD.id); END""",
            """CREATE TRIGGER IF NOT EXISTS unnamed_forget_on_delete AFTER DELETE ON beat_log
               WHEN OLD.printUpload = 2
               BEGIN INSERT OR IGNORE INTO unnamed_forget (entryId) VALUES (OLD.id); END""",
        )

        /**
         * v5: unnamed fingerprints and name suggestions (PLAN §5c). Unnamed entries that have fingerprints are queued
         * for upload. Keeps every existing log entry.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `beat_log` ADD COLUMN `printUpload` INTEGER NOT NULL DEFAULT 0")
                for (col in listOf("sugslug TEXT", "sugtitle TEXT", "sugartist TEXT", "sugscore INTEGER", "sugstate TEXT")) {
                    db.execSQL("ALTER TABLE `beat_log` ADD COLUMN $col")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `unnamed_forget` (`entryId` INTEGER NOT NULL, PRIMARY KEY(`entryId`))")
                FORGET_TRIGGERS.forEach(db::execSQL)
                db.execSQL(
                    "UPDATE beat_log SET printUpload = 1 WHERE title IS NULL AND remoteInstallId IS NULL " +
                        "AND id IN (SELECT DISTINCT entryId FROM fingerprint)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "beatradar.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    // Room doesn't know triggers: fresh installs get them here, upgrades in MIGRATION_4_5.
                    override fun onCreate(db: SupportSQLiteDatabase) = FORGET_TRIGGERS.forEach(db::execSQL)
                })
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
