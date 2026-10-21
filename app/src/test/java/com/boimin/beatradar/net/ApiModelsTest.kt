package com.boimin.beatradar.net

import com.boimin.beatradar.library.SongQuery
import com.boimin.beatradar.library.SongSort
import com.boimin.beatradar.library.compactCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bodies as the server sends them (shapes from docs/openapi.json), including fields the app doesn't use. */
class ApiModelsTest {

    @Test fun session() {
        val s = ApiJson.decodeFromString(
            Session.serializer(),
            """{"accessToken":"a","refreshToken":"r","expiresIn":900,"user":{"id":"8d3f…","email":"x@y.z","emailVerified":true,
               "nickname":"Indigo Drummer 4821","onLeaderboard":true,"hasPassword":false,"googleLinked":true,"extra":1}}""",
        )
        assertEquals(900, s.expiresIn)
        assertEquals("Indigo Drummer 4821", s.user.nickname)
        assertTrue(s.user.googleLinked)
    }

    @Test fun songPageAndDetail() {
        val page = ApiJson.decodeFromString(
            SongPage.serializer(),
            """{"items":[{"slug":"k3x9q2","title":"Halo","artist":"Beyoncé","bpm":79.5,"bpmAlt":159.0,"listeners":18,
               "confirmations":15,"loveCount":4,"artworkUrl":null,"lovedByMe":false}],"nextCursor":"bzozMA"}""",
        )
        assertEquals(1, page.items.size)
        assertEquals(159f, page.items[0].bpmAlt)
        assertEquals("bzozMA", page.nextCursor)

        val d = ApiJson.decodeFromString(
            SongDetail.serializer(),
            """{"slug":"k3x9q2","title":"Halo","artist":"Beyoncé","bpm":79.5,"listeners":18,"confirmations":15,"loveCount":4,
               "lovedByMe":true,"bpmSpread":1.5,"measurements":22,"links":[{"service":"spotify","url":"https://open.spotify.com/x","exact":true}],
               "broughtInBy":null,"createdAt":"2026-10-18T10:00:00Z"}""",
        )
        assertEquals(22, d.measurements)
        assertEquals("spotify", d.links.single().service)
        assertNull(d.broughtInBy)
        assertTrue(d.summary().lovedByMe)
    }

    @Test fun uploadResult() {
        val r = ApiJson.decodeFromString(
            UploadResult.serializer(),
            """{"results":[{"clientRef":"7:1","status":"accepted","songSlug":"k3x9q2","printsAccepted":true},
               {"clientRef":"8:1","status":"rejected","reason":"low_confidence","songSlug":null,"printsAccepted":false}]}""",
        )
        assertEquals("k3x9q2", r.results[0].songSlug)
        assertEquals("low_confidence", r.results[1].reason)
    }

    @Test fun rewardsAndBoard() {
        val r = ApiJson.decodeFromString(
            Rewards.serializer(),
            """{"beatsWeek":12,"beatsAll":1240,"rankWeek":null,"rankAll":7,"streak":3,"signedIn":true,"newBadges":["first_beat"],
               "badges":[{"code":"first_beat","name":"First Beat","description":"First counted measurement","tier":1,"maxTier":1,"count":1,"progress":1,"nextAt":null,"earnedAt":"2026-10-18T10:00:00Z"},
                         {"code":"tempo_keeper","name":"Tempo Keeper","description":"Accurate measurements","tier":0,"maxTier":3,"count":0,"progress":4,"nextAt":10}]}""",
        )
        assertEquals(listOf("first_beat"), r.newBadges)
        assertTrue(r.badges[0].earned)
        assertFalse(r.badges[1].earned)
        assertEquals(10L, r.badges[1].nextAt)

        val b = ApiJson.decodeFromString(
            BoardView.serializer(),
            """{"period":"week","entries":[{"rank":1,"nickname":"A","beats":50,"grooveMaster":2}],"me":null,"lastGrooveMaster":"A","refreshedAt":null}""",
        )
        assertEquals(2L, b.entries[0].grooveMaster)
        assertNull(b.me)
    }

    @Test fun logPage() {
        val p = ApiJson.decodeFromString(
            LogPage.serializer(),
            """{"entries":[{"installId":"0b9…","clientRef":"41","title":null,"artist":null,"bpm":128,"confidence":0.4,"inRange":true,
               "heardAt":"2026-10-18T10:00:00Z","updatedAt":"2026-10-18T10:05:00Z","deleted":false,"songSlug":null}],"cursor":"c1","hasMore":false}""",
        )
        assertNull(p.entries[0].title)
        assertEquals("c1", p.cursor)
    }

    @Test fun nullsAreLeftOutOfRequests() {
        val json = ApiJson.encodeToString(MeasurementIn.serializer(), MeasurementIn("1:1", "t", "a", "manual", 120f, 0.5f, "2026-01-01T00:00:00Z"))
        assertFalse(json.contains("prints"))
        val push = ApiJson.encodeToString(LogEntryIn.serializer(), LogEntryIn(clientRef = "1", bpm = 1f, confidence = 1f, inRange = false, heardAt = "h", updatedAt = "u"))
        assertFalse(push.contains("installId"))
        assertTrue(push.contains("\"deleted\":false"))
    }

    @Test fun errorBody() {
        val e = ApiClient.parseError(401, """{"error":{"code":"token_reused","message":"Refresh token was already used"}}""")
        assertEquals("token_reused", e.code)
        assertEquals(401, e.http)
        val plain = ApiClient.parseError(502, "<html>Bad gateway</html>")
        assertEquals("http_502", plain.code)
    }

    @Test fun songQueryParams() {
        val p = SongQuery(text = "  halo ", bpmMin = 120, bpmMax = 130, halfDouble = true, sort = SongSort.CONFIRMED, lovedOnly = false).params("c")
        assertEquals("halo", p["q"])
        assertEquals(120, p["bpmMin"])
        assertEquals(true, p["halfDouble"])
        assertEquals("confirmed", p["sort"])
        assertNull(p["loved"])
        assertEquals("c", p["cursor"])
        assertNull(SongQuery().params(null)["q"])
    }

    @Test fun counters() {
        assertEquals("950", compactCount(950))
        assertEquals("1k", compactCount(1_000))
        assertEquals("1.2k", compactCount(1_234))
        assertEquals("25k", compactCount(25_300))
        assertEquals("1.5M", compactCount(1_500_000))
    }
}
