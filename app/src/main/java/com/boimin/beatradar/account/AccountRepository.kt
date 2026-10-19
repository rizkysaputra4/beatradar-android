package com.boimin.beatradar.account

import android.content.Context
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.net.ApiClient
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.ApiJson
import com.boimin.beatradar.net.Credentials
import com.boimin.beatradar.net.EmailOnly
import com.boimin.beatradar.net.GoogleSignIn
import com.boimin.beatradar.net.InstallToken
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.net.ProfileUpdate
import com.boimin.beatradar.net.RefreshRequest
import com.boimin.beatradar.net.RegisterInstall
import com.boimin.beatradar.net.Session
import com.boimin.beatradar.net.TokenSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** Why the account was signed out, so the UI knows whether to ask about the local log. */
enum class SignOutReason { USER, SESSION_LOST, DELETED }

/**
 * The phone's identity on the server (PLAN §3): an anonymous install, optionally signed in to an account.
 * Also the [TokenSource] for every API call: the user's access token when signed in (refreshed as needed),
 * otherwise the install token (registered on first use).
 */
class AccountRepository(context: Context) : TokenSource {
    private val store = SecureStore(context)
    private val lock = Mutex()

    val api = ApiClient(BuildConfig.API_BASE_URL, tokens = this)

    private val _user = MutableStateFlow(loadSession()?.user)
    /** The signed-in account, or null for a guest. */
    val user: StateFlow<Me?> = _user

    private val _signedOut = MutableSharedFlow<SignOutReason>(extraBufferCapacity = 4)
    val signedOut: SharedFlow<SignOutReason> = _signedOut

    private val _signedIn = MutableSharedFlow<Me>(extraBufferCapacity = 4)
    /** Fires after every successful sign-in (the log backup then uploads this phone's log). */
    val signedIn: SharedFlow<Me> = _signedIn

    val installId: String
        get() = store.get(KEY_INSTALL_ID) ?: newInstallIdentity().first

    /** Hash to bind a Play Integrity token to this install (from registration). */
    val integrityRequestHash: String? get() = store.get(KEY_INTEGRITY_HASH)

    // Tokens

    override suspend fun token(): String? = lock.withLock {
        val session = loadSession()
        if (session != null) {
            if (System.currentTimeMillis() < session.expiresAtMs - EXPIRY_MARGIN_MS) return@withLock session.accessToken
            return@withLock refreshLocked(session)?.accessToken ?: installTokenLocked()
        }
        installTokenLocked()
    }

    override suspend fun onUnauthorized(token: String): Boolean = lock.withLock {
        val session = loadSession()
        if (session != null && session.accessToken == token) {
            refreshLocked(session)
            return@withLock true
        }
        if (store.get(KEY_INSTALL_TOKEN) == token) store.put(KEY_INSTALL_TOKEN, null)
        registerLocked() != null
    }

    /** Registers this install now if it isn't yet (e.g. on first launch, so uploads can start). */
    suspend fun ensureInstall(): Boolean = lock.withLock { installTokenLocked() != null }

    private suspend fun installTokenLocked(): String? = store.get(KEY_INSTALL_TOKEN) ?: registerLocked()

    private suspend fun registerLocked(): String? {
        var (id, secret) = identity()
        repeat(2) {
            try {
                val t: InstallToken = api.post("v1/installs", RegisterInstall(id, secret, appVersion = BuildConfig.VERSION_NAME), auth = false)
                store.put(KEY_INSTALL_TOKEN, t.installToken)
                store.put(KEY_INTEGRITY_HASH, t.integrityRequestHash)
                return t.installToken
            } catch (e: ApiException) {
                android.util.Log.i("BeatRadar", "install registration failed: ${e.code} ${e.message}")
                // The id is taken with another secret (e.g. the secret was lost): start a fresh identity.
                if (e.code != "install_exists") return null
                newInstallIdentity().let { (newId, newSecret) -> id = newId; secret = newSecret }
            }
        }
        return null
    }

    private fun identity(): Pair<String, String> {
        val id = store.get(KEY_INSTALL_ID)
        val secret = store.get(KEY_INSTALL_SECRET)
        return if (id != null && secret != null) id to secret else newInstallIdentity()
    }

    private fun newInstallIdentity(): Pair<String, String> {
        val id = UUID.randomUUID().toString()
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        store.put(KEY_INSTALL_ID, id)
        store.put(KEY_INSTALL_SECRET, secret)
        store.put(KEY_INSTALL_TOKEN, null)
        return id to secret
    }

    /** Rotates the refresh token. A reused or unknown one means the session is gone: sign out locally. */
    private suspend fun refreshLocked(session: StoredSession): StoredSession? = try {
        val fresh: Session = api.post("v1/auth/refresh", RefreshRequest(session.refreshToken), auth = false)
        saveSession(fresh)
    } catch (e: ApiException) {
        if (!e.offline && e.http in 400..499) {
            clearSession()
            _signedOut.tryEmit(SignOutReason.SESSION_LOST)
        }
        null
    }

    // Account actions (all throw ApiException with the server's code on failure)

    /** Sends the verification email; the account can sign in once the address is verified. */
    suspend fun register(email: String, password: String) {
        api.post<Credentials, Unit>("v1/auth/register", Credentials(email.trim(), password))
    }

    suspend fun resendVerification(email: String) {
        api.post<EmailOnly, Unit>("v1/auth/resend-verification", EmailOnly(email.trim()), auth = false)
    }

    suspend fun forgotPassword(email: String) {
        api.post<EmailOnly, Unit>("v1/auth/forgot-password", EmailOnly(email.trim()), auth = false)
    }

    suspend fun login(email: String, password: String): Me =
        signedInWith(api.post("v1/auth/login", Credentials(email.trim(), password)))

    suspend fun signInWithGoogle(idToken: String): Me = signedInWith(api.post("v1/auth/google", GoogleSignIn(idToken)))

    private fun signedInWith(session: Session): Me {
        saveSession(session)
        _signedIn.tryEmit(session.user)
        return session.user
    }

    suspend fun refreshMe(): Me? {
        if (loadSession() == null) return null
        return runCatching { api.get<Me>("v1/me") }.getOrNull()?.also(::updateUser)
    }

    suspend fun updateProfile(nickname: String? = null, onLeaderboard: Boolean? = null): Me =
        api.patch<ProfileUpdate, Me>("v1/me/profile", ProfileUpdate(nickname, onLeaderboard)).also(::updateUser)

    /** Signs out this phone. Works offline too: the local session is dropped either way. */
    suspend fun logout() {
        runCatching { api.postEmpty<Unit>("v1/auth/logout") }
        lock.withLock { clearSession() }
        _signedOut.tryEmit(SignOutReason.USER)
    }

    suspend fun deleteAccount() {
        api.delete<Unit>("v1/me")
        lock.withLock { clearSession() }
        _signedOut.tryEmit(SignOutReason.DELETED)
    }

    private fun updateUser(me: Me) {
        val s = loadSession() ?: return
        store.put(KEY_SESSION, ApiJson.encodeToString(StoredSession.serializer(), s.copy(user = me)))
        _user.value = me
    }

    // Session storage

    @Serializable
    private data class StoredSession(val accessToken: String, val refreshToken: String, val expiresAtMs: Long, val user: Me)

    private fun loadSession(): StoredSession? =
        store.get(KEY_SESSION)?.let { runCatching { ApiJson.decodeFromString(StoredSession.serializer(), it) }.getOrNull() }

    private fun saveSession(s: Session): StoredSession {
        val stored = StoredSession(s.accessToken, s.refreshToken, System.currentTimeMillis() + s.expiresIn * 1_000, s.user)
        store.put(KEY_SESSION, ApiJson.encodeToString(StoredSession.serializer(), stored))
        _user.value = s.user
        return stored
    }

    private fun clearSession() {
        store.put(KEY_SESSION, null)
        _user.value = null
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_INSTALL_SECRET = "install_secret"
        const val KEY_INSTALL_TOKEN = "install_token"
        const val KEY_INTEGRITY_HASH = "integrity_hash"
        const val KEY_SESSION = "session"
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
