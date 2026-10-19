package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.NameSuggestion
import com.boimin.beatradar.data.PrintUpload
import com.boimin.beatradar.net.ApiJson
import com.boimin.beatradar.net.RejectResult
import com.boimin.beatradar.net.Suggestion
import com.boimin.beatradar.net.SuggestionList
import com.boimin.beatradar.net.UnnamedIn
import com.boimin.beatradar.net.UnnamedItemResult
import com.boimin.beatradar.net.UnnamedResult
import com.boimin.beatradar.ui.unnamedLineText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnnamedRulesTest {

    private fun entry(id: Long = 42, title: String? = null, bpm: Float = 124f, remote: String? = null) = BeatLogEntry(
        id = id, timestamp = 0, title = title, artist = null, sourceApp = null, bpm = bpm, confidence = 0.5f,
        inRange = false, placeholderNo = 3, playCount = 4, remoteInstallId = remote,
    )

    @Test fun uploadUsesTheLocalIdWithoutPlayCount() {
        val u = UnnamedRules.upload(entry(), "AAAAAAAAAAA=")!!
        assertEquals("42", u.clientRef)
        assertEquals(124f, u.bpm)
        assertEquals("AAAAAAAAAAA=", u.prints)
    }

    @Test fun onlyUnnamedOwnEntriesWithPrints() {
        assertNull(UnnamedRules.upload(entry(title = "Halo"), "AAAA"))
        assertNull(UnnamedRules.upload(entry(remote = "phone-b"), "AAAA"))
        assertNull(UnnamedRules.upload(entry(), ""))
    }

    @Test fun oddTempoIsLeftOut() {
        assertNull(UnnamedRules.upload(entry(bpm = 0f), "AAAA")!!.bpm)
        assertFalse(ApiJson.encodeToString(UnnamedIn.serializer(), UnnamedRules.upload(entry(bpm = 0f), "AAAA")!!).contains("bpm"))
    }

    @Test fun batchesOfAtMost50AndByteLimited() {
        val small = List(120) { UnnamedIn("$it", "AAAA") }
        assertEquals(listOf(50, 50, 20), UnnamedRules.batches(small).map { it.size })
        val big = List(40) { UnnamedIn("$it", "A".repeat(85_000)) }
        val batches = UnnamedRules.batches(big)
        assertTrue(batches.all { b -> b.sumOf { it.prints.length } <= UnnamedRules.MAX_BATCH_BYTES })
        assertEquals(40, batches.sumOf { it.size })
    }

    @Test fun answersMapToUploadStates() {
        fun r(status: String, reason: String? = null) = UnnamedItemResult("1", status, reason)
        assertEquals(PrintUpload.SENT, UnnamedRules.state(r("stored")))
        assertEquals(PrintUpload.SENT, UnnamedRules.state(r("suggested")))
        assertEquals(PrintUpload.SENT, UnnamedRules.state(r("duplicate")))
        assertEquals(PrintUpload.SKIPPED, UnnamedRules.state(r("rejected", "too_many_landmarks")))
        assertNull("daily cap: stay pending, try tomorrow", UnnamedRules.state(r("rejected", "daily_cap")))
    }

    private fun s(ref: String, title: String = "Halo") = Suggestion(ref, "k3x9q2", title, "Beyoncé", 79.5f, 21, "trusted")

    @Test fun suggestionsShowOnlyOnStillUnnamedEntries() {
        val plan = UnnamedRules.plan(listOf(s("1"), s("2"), s("3"), s("x")), stillUnnamed = setOf(1L, 3L))
        assertEquals(setOf(1L, 3L), plan.show.keys)
        assertEquals(listOf(2L), plan.forget)
    }

    @Test fun emptyListClearsEverything() {
        val plan = UnnamedRules.plan(emptyList(), setOf(1L))
        assertTrue(plan.show.isEmpty())
        assertTrue(plan.forget.isEmpty())
    }

    @Test fun localCopyAndQuestion() {
        val local = UnnamedRules.toLocal(s("1"))
        assertEquals(NameSuggestion("k3x9q2", "Halo", "Beyoncé", 21, "trusted"), local)
        assertEquals("Is this Halo – Beyoncé?", UnnamedRules.question(local))
        assertEquals("Is this Halo?", UnnamedRules.question(local.copy(artist = "")))
    }

    @Test fun serverBodies() {
        val r = ApiJson.decodeFromString(
            UnnamedResult.serializer(),
            """{"results":[{"clientRef":"42","status":"suggested","reason":null,"suggestion":{"clientRef":"42","slug":"k3x9q2",
               "title":"Halo","artist":"Beyoncé","bpm":79.5,"score":21,"state":"trusted"}},{"clientRef":"43","status":"stored"}]}""",
        )
        assertEquals("Halo", r.results[0].suggestion!!.title)
        assertNull(r.results[1].suggestion)
        val list = ApiJson.decodeFromString(SuggestionList.serializer(), """{"items":[{"clientRef":"7","slug":"a","title":"T","artist":"A","score":16,"state":"candidate"}]}""")
        assertNull(list.items[0].bpm)
        assertNull(ApiJson.decodeFromString(RejectResult.serializer(), """{"next":null}""").next)
        assertNull(ApiJson.decodeFromString(RejectResult.serializer(), "{}").next)
        assertEquals("b", ApiJson.decodeFromString(RejectResult.serializer(), """{"next":{"clientRef":"7","slug":"b","title":"T2","artist":"A","score":17,"state":"trusted"}}""").next!!.slug)
    }

    @Test fun unnamedLine() {
        assertEquals("1 song without a name · Identify", unnamedLineText(1, 0))
        assertEquals("3 songs without a name · 1 suggested name · Identify", unnamedLineText(3, 1))
        assertEquals("3 songs without a name · 2 suggested names · Identify", unnamedLineText(3, 2))
    }
}
