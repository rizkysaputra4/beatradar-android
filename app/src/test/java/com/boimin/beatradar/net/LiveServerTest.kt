package com.boimin.beatradar.net

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.NameSource
import com.boimin.beatradar.dsp.Landmark
import com.boimin.beatradar.library.SongQuery
import com.boimin.beatradar.sync.BackupRules
import com.boimin.beatradar.sync.UploadRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

/**
 * End to end against a running server, with the app's own client, DTOs and upload/backup rules. Opt-in:
 * BEATRADAR_E2E_URL=http://localhost:8080/ (and BEATRADAR_E2E_LOG=<server log file> for the account part, where the
 * dev server logs its emails). Skipped otherwise.
 */
class LiveServerTest {
    private val base: String? = System.getenv("BEATRADAR_E2E_URL")
    private val log: String? = System.getenv("BEATRADAR_E2E_LOG")

    /** Minimal stand-in for AccountRepository (which needs the Android Keystore). */
    private class Tokens(val api: () -> ApiClient) : TokenSource {
        var install: String? = null
        var access: String? = null
        val id = UUID.randomUUID().toString()
        val secret = UUID.randomUUID().toString() + UUID.randomUUID()
        override suspend fun token(): String? = access ?: install ?: register()
        override suspend fun onUnauthorized(token: String): Boolean = false
        suspend fun register(): String {
            val t: InstallToken = api().post("v1/installs", RegisterInstall(id, secret, appVersion = "e2e"), auth = false)
            install = t.installToken
            return t.installToken
        }
    }

    private fun song(seed: Int) = List(1_500) { Landmark(Random(seed * 100_000 + it).nextInt(1, 2_000_000), it / 2) }

    @Test fun libraryFlow() = runBlocking {
        assumeTrue("set BEATRADAR_E2E_URL to run", base != null)
        lateinit var api: ApiClient
        val tokens = Tokens { api }
        api = ApiClient(base!!, tokens)

        // Re-registering with the same id + secret gives a new token; another secret is refused.
        val first = tokens.register()
        assertTrue(first.isNotBlank())
        val other = runCatching { api.post<RegisterInstall, InstallToken>("v1/installs", RegisterInstall(tokens.id, "x".repeat(32)), auth = false) }
        assertEquals("install_exists", (other.exceptionOrNull() as ApiException).code)

        val tag = UUID.randomUUID().toString().take(8)
        val title = "E2E Song $tag"
        val prints = song(tag.hashCode())
        val entry = BeatLogEntry(
            id = 1, timestamp = System.currentTimeMillis() - 60_000, title = title, artist = "E2E Artist", sourceApp = null,
            bpm = 128.4f, confidence = 0.7f, inRange = true, nameSource = NameSource.SHAZAM,
        )
        val items = listOf(
            UploadRules.measurement(entry, Landmarks.pack(prints))!!,
            UploadRules.measurement(entry.copy(id = 2, title = "E2E Other $tag"), null)!!,
            UploadRules.measurement(entry.copy(id = 3, confidence = 0.01f), null)!!,
        )
        val up: UploadResult = api.post("v1/measurements", MeasurementBatch(items))
        assertEquals(listOf("accepted", "accepted", "rejected"), up.results.map { it.status })
        assertEquals(listOf(UploadRules.Outcome.SENT, UploadRules.Outcome.SENT, UploadRules.Outcome.SKIPPED), up.results.map(UploadRules::outcome))
        assertTrue("prints accepted", up.results[0].printsAccepted)
        val slug = up.results[0].songSlug!!
        // Retrying the same batch is harmless.
        val again: UploadResult = api.post("v1/measurements", MeasurementBatch(items.take(1)))
        assertEquals("duplicate", again.results[0].status)

        // The detail and search reflect the upload once the consensus job ran.
        var detail: SongDetail? = null
        repeat(20) {
            detail = api.get<SongDetail>("v1/songs/$slug").takeIf { it.listeners >= 1 }
            if (detail != null) return@repeat
            delay(500)
        }
        assertNotNull("consensus ran", detail)
        assertEquals(title, detail!!.title)
        assertEquals(128.4f, detail!!.bpm, 0.5f)
        val page: SongPage = api.get("v1/songs", SongQuery(text = tag, bpmMin = 120, bpmMax = 130).params(null))
        assertTrue(page.items.any { it.slug == slug })

        // Recognition from a later part of the song (the merge job builds the reference).
        var rec: RecognizeOut? = null
        repeat(20) {
            rec = api.post<RecognizeIn, RecognizeOut>("v1/recognize", RecognizeIn(Landmarks.pack(prints.drop(600).take(300))))
            if (rec?.song != null) return@repeat
            delay(500)
        }
        assertEquals(slug, rec!!.song?.slug)
        assertEquals("candidate", rec!!.state)

        // Guests can't love; rewards answer for the install.
        val love = runCatching { api.put<LoveState>("v1/songs/$slug/love") }.exceptionOrNull() as ApiException
        assertTrue(love.http == 401 || love.http == 403)
        val rewards: Rewards = api.get("v1/me/rewards")
        assertTrue(!rewards.signedIn)
        val board: BoardView = api.get("v1/leaderboard", mapOf("period" to "week"))
        assertEquals("week", board.period)
    }

    @Test fun accountFlow() = runBlocking {
        assumeTrue("set BEATRADAR_E2E_URL and BEATRADAR_E2E_LOG to run", base != null && log != null)
        lateinit var api: ApiClient
        val tokens = Tokens { api }
        api = ApiClient(base!!, tokens)
        tokens.register()

        val email = "e2e-${UUID.randomUUID().toString().take(8)}@example.com"
        val password = "correct horse battery ${Random.nextInt()}"
        // Any password, no verification: registering signs in at once, and the address is then taken.
        val registered: Session = rl { api.post("v1/auth/register", Credentials(email, password)) }
        assertEquals(email, registered.user.email)
        val taken = runCatching { rl { api.post<Credentials, Session>("v1/auth/register", Credentials(email, "x")) } }.exceptionOrNull() as ApiException
        assertEquals("email_taken", taken.code)
        val bad = runCatching { rl { api.post<Credentials, Session>("v1/auth/login", Credentials(email, "wrong password!")) } }.exceptionOrNull() as ApiException
        assertEquals("invalid_credentials", bad.code)

        val session: Session = rl { api.post("v1/auth/login", Credentials(email, password)) }
        assertEquals(email, session.user.email)
        assertNotNull("a nickname is generated", session.user.nickname)
        tokens.access = session.accessToken

        // Rename, hide/show, rewards as a user.
        val me: Me = api.patch("v1/me/profile", ProfileUpdate(nickname = "E2E ${Random.nextInt(1000, 9999)}"))
        assertTrue(me.nickname!!.startsWith("E2E"))
        assertTrue(api.get<Rewards>("v1/me/rewards").signedIn)

        // Log backup: push two (one deleted), pull them back.
        val e = BeatLogEntry(
            id = 11, timestamp = System.currentTimeMillis() - 5_000, title = "Backup song", artist = "A", sourceApp = null,
            bpm = 100f, confidence = 0.5f, inRange = false, updatedAt = System.currentTimeMillis(),
        )
        val pushed: PushResult = api.post("v1/me/log", LogPush(listOf(BackupRules.toPush(e), BackupRules.toPush(e.copy(id = 12, syncState = 2)))))
        assertEquals(2, pushed.applied)
        val pulled: LogPage = api.get("v1/me/log")
        assertEquals(setOf("11", "12"), pulled.entries.map { it.clientRef }.toSet())
        assertEquals(tokens.id, pulled.entries.first().installId)
        assertTrue(pulled.entries.first { it.clientRef == "12" }.deleted)
        // Pulled on this phone, they are its own entries: nothing to insert again.
        assertEquals(BackupRules.Action.Skip, BackupRules.decide(e, pulled.entries.first { it.clientRef == "11" }, own = true))

        // Refresh rotates; presenting the old refresh token again is caught.
        val rotated: Session = rl { api.post("v1/auth/refresh", RefreshRequest(session.refreshToken), auth = false) }
        val reuse = runCatching { rl { api.post<RefreshRequest, Session>("v1/auth/refresh", RefreshRequest(session.refreshToken), auth = false) } }.exceptionOrNull() as ApiException
        assertEquals("token_reused", reuse.code)
        assertEquals(401, reuse.http)
        // The reuse revoked the whole family, so the rotated token is dead too.
        val dead = runCatching { rl { api.post<RefreshRequest, Session>("v1/auth/refresh", RefreshRequest(rotated.refreshToken), auth = false) } }.exceptionOrNull() as ApiException
        assertEquals(401, dead.http)

        // Delete the account (access token still valid for its 15 minutes).
        tokens.access = rotated.accessToken
        api.delete<Unit>("v1/me")
        tokens.access = null
        val gone = runCatching { rl { api.post<Credentials, Session>("v1/auth/login", Credentials(email, password)) } }.exceptionOrNull() as ApiException
        assertEquals("invalid_credentials", gone.code)
    }

    /** Auth endpoints allow a burst of 10 per IP, then one every 3 s: wait instead of failing. */
    private suspend fun <T> rl(block: suspend () -> T): T {
        repeat(6) {
            try {
                return block()
            } catch (e: ApiException) {
                if (e.http != 429) throw e
                delay(3_500)
            }
        }
        return block()
    }
}
