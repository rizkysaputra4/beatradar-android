package com.boimin.beatradar.dsp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matcher test vectors shared with the server (`beatradar-backend/testdata/fingerprint_vectors.json`, PLAN §5a):
 * the phone and the server must pick the same song with the same score, or recognition would drift apart.
 */
class FingerprintVectorsTest {

    private val cases: List<JsonObject> by lazy {
        val text = javaClass.classLoader!!.getResource("fingerprint_vectors.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }
    }

    @Test fun vectorsArePresent() {
        assertTrue("expected at least 5 shared cases", cases.size >= 5)
    }

    @Test fun sameSongAndScoreAsTheServer() {
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val query = (case["query"] as JsonArray).map { val a = it.jsonArray; Landmark(a[0].jsonPrimitive.int, a[1].jsonPrimitive.int) }
            val stored = (case["stored"] as JsonArray).map {
                val a = it.jsonArray
                FingerprintMatcher.Stored(a[0].jsonPrimitive.int.toLong(), a[1].jsonPrimitive.int, a[2].jsonPrimitive.int)
            }
            val expectedSong = case["expected_song"]!!.jsonPrimitive.intOrNull?.toLong()
            val expectedScore = case["expected_score"]!!.jsonPrimitive.int

            val best = FingerprintMatcher.best(query, stored)
            val score = best?.score ?: 0
            assertEquals("score for '$name'", expectedScore, score)
            if (expectedSong != null) {
                assertEquals("song for '$name'", expectedSong, best?.entryId)
            } else {
                // No song: nothing lines up at all.
                assertTrue("'$name' should not match (got ${best?.entryId} score $score)", score < FingerprintMatcher.MIN_SCORE)
            }
        }
    }
}
