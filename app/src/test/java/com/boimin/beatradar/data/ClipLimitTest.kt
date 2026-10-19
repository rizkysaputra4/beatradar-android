package com.boimin.beatradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipLimitTest {

    private fun clip(id: Long, timestamp: Long) = ClipRef(id, "/clips/$id.wav", timestamp)

    @Test fun keepsTheMostRecentlyHeard() {
        val clips = listOf(clip(1, 100), clip(2, 300), clip(3, 200), clip(4, 50))
        assertEquals(listOf(1L, 4L), ClipStore.overLimit(clips, 2).map { it.id })
    }

    @Test fun nothingToDropUnderTheLimit() {
        assertTrue(ClipStore.overLimit(listOf(clip(1, 1), clip(2, 2)), 30).isEmpty())
    }

    @Test fun lowerLimitDropsMore() {
        val clips = (1L..100L).map { clip(it, it * 10) }
        assertEquals(70, ClipStore.overLimit(clips, 30).size)
        assertEquals(90, ClipStore.overLimit(clips, 10).size)
        // The oldest go first.
        assertEquals((1L..90L).toList(), ClipStore.overLimit(clips, 10).map { it.id }.sorted())
    }

    @Test fun sameTimeNewerEntryWins() {
        assertEquals(listOf(1L), ClipStore.overLimit(listOf(clip(1, 5), clip(2, 5)), 1).map { it.id })
    }
}
