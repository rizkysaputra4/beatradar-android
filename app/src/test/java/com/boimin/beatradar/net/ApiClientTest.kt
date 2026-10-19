package com.boimin.beatradar.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private class Tokens(var current: String = "old") : TokenSource {
        var refreshed = 0
        override suspend fun token() = current
        override suspend fun onUnauthorized(token: String): Boolean {
            refreshed++
            current = "new"
            return true
        }
    }

    private fun client(tokens: TokenSource? = null) = ApiClient(server.url("/").toString(), tokens)

    @Test fun sendsBearerAndQuery() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"items":[],"nextCursor":null}""").build())
        val page: SongPage = client(Tokens()).get("v1/songs", mapOf("q" to "halo ü", "bpmMin" to 120, "cursor" to null))
        assertTrue(page.items.isEmpty())
        val req = server.takeRequest()
        assertEquals("Bearer old", req.headers["Authorization"])
        assertEquals("/v1/songs?q=halo%20%C3%BC&bpmMin=120", req.url.encodedPath + "?" + req.url.encodedQuery)
    }

    @Test fun retriesOnceWithANewTokenAfter401() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"code":"invalid_token","message":"expired"}}""").build())
        server.enqueue(MockResponse.Builder().body("""{"loved":true,"loveCount":5}""").build())
        val tokens = Tokens()
        val love: LoveState = client(tokens).put("v1/songs/abc/love")
        assertEquals(5, love.loveCount)
        assertEquals(1, tokens.refreshed)
        server.takeRequest()
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
    }

    @Test fun serverErrorsCarryTheirCode() = runBlocking {
        server.enqueue(MockResponse.Builder().code(409).body("""{"error":{"code":"email_taken","message":"Taken"}}""").build())
        try {
            client().post<Credentials, Unit>("v1/auth/register", Credentials("a@b.c", "password1"), auth = false)
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals("email_taken", e.code)
            assertEquals(409, e.http)
        }
    }

    @Test fun emptyBodiesReadAsUnit() = runBlocking {
        server.enqueue(MockResponse.Builder().code(202).build())
        client().post<EmailOnly, Unit>("v1/auth/forgot-password", EmailOnly("a@b.c"), auth = false)
        assertEquals("""{"email":"a@b.c"}""", server.takeRequest().body!!.utf8())
    }

    @Test fun noServerIsOffline() = runBlocking {
        val url = server.url("/").toString()
        server.close()
        try {
            ApiClient(url).get<SongPage>("v1/songs", auth = false)
            fail("expected an error")
        } catch (e: ApiException) {
            assertTrue(e.offline)
        }
    }
}
