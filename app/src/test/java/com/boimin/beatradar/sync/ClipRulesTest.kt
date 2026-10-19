package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.sync.ClipRules.ConsentStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipRulesTest {

    private fun entry(id: Long, title: String? = null, clip: String? = "/clips/$id.wav", remote: String? = null) = BeatLogEntry(
        id = id, timestamp = 0, title = title, artist = null, sourceApp = null, bpm = 120f, confidence = 0.5f,
        inRange = false, placeholderNo = 1, playCount = 1, remoteInstallId = remote, clipPath = clip,
    )

    @Test fun offByDefaultAndNeedsClipsAndContribute() {
        assertFalse(ClipRules.sharing(Settings()))
        assertTrue(ClipRules.sharing(Settings(shareClips = true)))
        assertFalse(ClipRules.sharing(Settings(shareClips = true, saveClips = false)))
        assertFalse(ClipRules.sharing(Settings(shareClips = true, contribute = false)))
    }

    @Test fun consentIsSentOnceAndWithdrawnOnce() {
        assertEquals(ConsentStep.SEND, ClipRules.consentStep(sharing = true, consentSent = false))
        assertEquals(ConsentStep.NONE, ClipRules.consentStep(sharing = true, consentSent = true))
        assertEquals(ConsentStep.WITHDRAW, ClipRules.consentStep(sharing = false, consentSent = true))
        assertEquals(ConsentStep.NONE, ClipRules.consentStep(sharing = false, consentSent = false))
    }

    @Test fun onlyStillUnnamedLocalEntriesWithTheirClipGoUp() {
        val entries = listOf(
            entry(1),
            entry(2, title = "Named meanwhile"),
            entry(3, clip = null),
            entry(4, remote = "other-phone"),
            entry(5, clip = "/clips/gone.wav"),
            entry(6),
        )
        val up = ClipRules.toUpload(listOf("6", "1", "2", "3", "4", "5", "99", "x", "1"), entries) { it != "/clips/gone.wav" }
        assertEquals(listOf(6L to "/clips/6.wav", 1L to "/clips/1.wav"), up)
    }
}
