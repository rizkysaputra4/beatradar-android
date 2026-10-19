package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.data.SyncState
import com.boimin.beatradar.data.UploadState
import com.boimin.beatradar.net.ItemResult
import com.boimin.beatradar.net.LogEntryOut
import com.boimin.beatradar.net.MeasurementIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    private fun entry(
        id: Long = 7,
        title: String? = "Halo",
        source: NameSource? = NameSource.MEDIA_SESSION,
        plays: Int = 1,
        updatedAt: Long = 1_000,
        sync: Int = SyncState.DIRTY,
        remote: String? = null,
    ) = BeatLogEntry(
        id = id, timestamp = 1_700_000_000_000, title = title, artist = "Beyoncé", sourceApp = "Spotify",
        bpm = 80f, confidence = 0.6f, inRange = true, nameSource = source, playCount = plays,
        updatedAt = updatedAt, syncState = sync, remoteInstallId = remote, remoteClientRef = remote?.let { "41" },
    )

    // Uploads

    @Test fun measurementCarriesTheWireFields() {
        val m = UploadRules.measurement(entry(), prints = "AAAA")!!
        assertEquals("7:1", m.clientRef)
        assertEquals("Halo", m.title)
        assertEquals("Beyoncé", m.artist)
        assertEquals("media_session", m.titleSource)
        assertEquals("2023-11-14T22:13:20Z", m.heardAt)
        assertEquals("AAAA", m.prints)
    }

    @Test fun eachPlayIsItsOwnMeasurement() {
        assertEquals("7:3", UploadRules.clientRef(entry(plays = 3)))
        assertEquals(7L to 3, UploadRules.parseClientRef("7:3"))
        assertNull(UploadRules.parseClientRef("7"))
        assertNull(UploadRules.parseClientRef("x:1"))
    }

    @Test fun unnamedAndPulledEntriesAreNotSent() {
        assertNull(UploadRules.measurement(entry(title = null), null))
        assertNull(UploadRules.measurement(entry(title = "  "), null))
        assertNull(UploadRules.measurement(entry(remote = "other-phone"), null))
    }

    @Test fun printsOnlyForReliableNames() {
        assertNull(UploadRules.measurement(entry(source = NameSource.MANUAL), "AAAA")!!.prints)
        assertNull(UploadRules.measurement(entry(source = NameSource.SERVER), "AAAA")!!.prints)
        assertEquals("AAAA", UploadRules.measurement(entry(source = NameSource.SHAZAM), "AAAA")!!.prints)
    }

    @Test fun titleSources() {
        assertEquals("shazam", UploadRules.titleSource(NameSource.SHAZAM))
        assertEquals("now_playing", UploadRules.titleSource(NameSource.NOW_PLAYING))
        assertEquals("server", UploadRules.titleSource(NameSource.SERVER))
        assertEquals("manual", UploadRules.titleSource(NameSource.MANUAL))
    }

    @Test fun legacyZeroConfidenceSendsTheFloor() {
        assertEquals(0.15f, UploadRules.measurement(entry().copy(confidence = 0f), null)!!.confidence)
    }

    @Test fun batchesOfAtMost100() {
        val items = List(250) { UploadRules.measurement(entry(id = it.toLong()), null)!! }
        assertEquals(listOf(100, 100, 50), UploadRules.batches(items).map { it.size })
    }

    @Test fun bigFingerprintsSplitBatchesBySize() {
        val big = "A".repeat(400_000)
        val items = List(10) { m(it, big) }
        val batches = UploadRules.batches(items)
        assertTrue(batches.all { b -> b.sumOf { it.prints!!.length } <= UploadRules.MAX_BATCH_BYTES })
        assertEquals(10, batches.sumOf { it.size })
        assertEquals(3, batches.first().size)
    }

    private fun m(i: Int, prints: String) = MeasurementIn("$i:1", "t", "a", "shazam", 120f, 0.5f, "2024-01-01T00:00:00Z", prints)

    @Test fun answersMapToStates() {
        fun r(status: String, reason: String? = null) = ItemResult("1:1", status, reason)
        assertEquals(UploadRules.Outcome.SENT, UploadRules.outcome(r("accepted")))
        assertEquals(UploadRules.Outcome.SENT, UploadRules.outcome(r("duplicate")))
        assertEquals(UploadRules.Outcome.SKIPPED, UploadRules.outcome(r("rejected", "low_confidence")))
        assertEquals(UploadRules.Outcome.RETRY_LATER, UploadRules.outcome(r("rejected", "daily_cap")))
        assertEquals(UploadState.SENT, UploadRules.state(UploadRules.Outcome.SENT))
        assertEquals(UploadState.SKIPPED, UploadRules.state(UploadRules.Outcome.SKIPPED))
        assertEquals(UploadState.PENDING, UploadRules.state(UploadRules.Outcome.RETRY_LATER))
    }

    // Log backup

    @Test fun ownEntriesPushWithoutInstallId() {
        val p = BackupRules.toPush(entry(updatedAt = 1_700_000_100_000))
        assertNull(p.installId)
        assertEquals("7", p.clientRef)
        assertEquals("2023-11-14T22:15:00Z", p.updatedAt)
        assertEquals(false, p.deleted)
    }

    @Test fun pulledEntriesKeepTheirOrigin() {
        val p = BackupRules.toPush(entry(remote = "phone-b"))
        assertEquals("phone-b", p.installId)
        assertEquals("41", p.clientRef)
    }

    @Test fun tombstonesPushAsDeleted() {
        assertTrue(BackupRules.toPush(entry(sync = SyncState.DELETED)).deleted)
    }

    @Test fun updatedAtNeverBeforeHeardAt() {
        // Rows migrated with updatedAt 0 still send a sensible time.
        assertEquals("2023-11-14T22:13:20Z", BackupRules.toPush(entry(updatedAt = 0)).updatedAt)
    }

    private fun remote(updatedAt: String, deleted: Boolean = false) = LogEntryOut(
        installId = "phone-b", clientRef = "41", title = "Halo", bpm = 80f, confidence = 0.5f, inRange = false,
        heardAt = "2024-01-01T00:00:00Z", updatedAt = updatedAt, deleted = deleted,
    )

    @Test fun newEntryFromAnotherPhoneIsInserted() {
        assertEquals(BackupRules.Action.Insert, BackupRules.decide(null, remote("2024-01-01T00:00:00Z"), own = false))
    }

    @Test fun deletedUnknownEntryIsSkipped() {
        assertEquals(BackupRules.Action.Skip, BackupRules.decide(null, remote("2024-01-01T00:00:00Z", deleted = true), own = false))
    }

    @Test fun ownEntryMissingHereStaysGone() {
        // Trimmed or deleted on this phone: the backup doesn't bring it back.
        assertEquals(BackupRules.Action.Skip, BackupRules.decide(null, remote("2030-01-01T00:00:00Z"), own = true))
    }

    @Test fun newestWins() {
        val local = entry(updatedAt = BackupRules.millis("2024-06-01T00:00:00Z"))
        assertEquals(BackupRules.Action.Update(7), BackupRules.decide(local, remote("2024-06-02T00:00:00Z"), own = false))
        assertEquals(BackupRules.Action.Skip, BackupRules.decide(local, remote("2024-05-01T00:00:00Z"), own = false))
        assertEquals(BackupRules.Action.Skip, BackupRules.decide(local, remote("2024-06-01T00:00:00Z"), own = false))
    }

    @Test fun newerDeleteRemovesTheLocalRow() {
        val local = entry(updatedAt = BackupRules.millis("2024-06-01T00:00:00Z"))
        assertEquals(BackupRules.Action.Delete(7), BackupRules.decide(local, remote("2024-06-02T00:00:00Z", deleted = true), own = true))
    }

    @Test fun badTimesReadAsZero() {
        assertEquals(0L, BackupRules.millis("yesterday"))
        assertEquals(1_704_067_200_123L, BackupRules.millis("2024-01-01T00:00:00.123Z"))
    }
}
