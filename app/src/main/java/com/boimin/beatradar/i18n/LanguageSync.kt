package com.boimin.beatradar.i18n

import android.content.Context
import android.content.res.Resources
import androidx.core.content.edit
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.net.ApiClient
import com.boimin.beatradar.net.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

internal val I18nJson = Json { ignoreUnknownKeys = true }

/** A language the server has a pack for. */
@Serializable
data class LanguageInfo(val code: String, val name: String, val nativeName: String, val version: Int)

/** One download of a pack: the chunks asked for, and the list of all its chunks. */
@Serializable
private data class PackPart(val code: String, val nativeName: String, val version: Int, val chunks: List<String>, val strings: Map<String, String>)

/**
 * Picks the app's language and keeps its pack current. The language is the phone's, unless the user picked one in
 * Settings ([choose]). English is built in; for any other language the server has, its pack is downloaded and kept (only
 * that one); a language the server doesn't have yet shows in English. Runs at every start, so a new pack version or a
 * pack for the phone's language that appeared since is picked up.
 *
 * A pack comes in chunks, one per string-name prefix (`listen_…` is chunk `listen`). The [CORE] chunks (what the first
 * screens, notifications and the widget say) come in one small download and the app switches at once; the rest follow
 * one chunk at a time in the background, in [ORDER], and their strings show in English until they arrive. A chunk that
 * didn't arrive (offline, app closed) is fetched at the next start.
 */
object LanguageSync {
    private const val PREFS = "language"
    private const val KEY_CHOICE = "choice"
    const val ENGLISH = "en"

    /** Downloaded before the switch: the Listen tab and its tempo range, the app frame (Quick Setup too), notifications,
     * widget and tile, this picker. */
    private val CORE = listOf("app", "main", "listen", "tempo", "language", "notif", "widget", "tile", "capture", "data")
    /** The rest, most used first; chunks not named here come last. */
    private val ORDER = listOf("log", "library", "song", "settings", "sets", "rewards", "profile", "badge", "account", "practice", "playlist", "recap", "shared", "shazam", "billing")

    private val api by lazy { ApiClient(BuildConfig.API_BASE_URL) }
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fillJob: Job? = null

    private val _busy = MutableStateFlow(false)
    /** The core of a pack is downloading (the switch waits for it). */
    val busy: StateFlow<Boolean> = _busy

    private val _filling = MutableStateFlow(false)
    /** The rest of the pack in use is downloading in the background. */
    val filling: StateFlow<Boolean> = _filling

    /** The language picked in Settings, or null to follow the phone. */
    fun choice(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOICE, null)

    /** The phone's language as a BCP 47 tag (e.g. `id`, `pt-BR`). */
    fun systemTag(): String = Resources.getSystem().configuration.locales[0].toLanguageTag()

    /** The languages the server has, for the picker (English isn't listed: it's built in). */
    suspend fun available(): List<LanguageInfo> = api.get<List<LanguageInfo>>("v1/languages", auth = false).also { lastList = it }

    /** The list from the last [available] call, so the Language sheet opens at its full size instead of growing. */
    @Volatile
    var lastList: List<LanguageInfo>? = null
        private set

    /**
     * Picks [code] (null = follow the phone) and switches to it once its core strings are here. Throws [ApiException]
     * when they can't be downloaded (e.g. offline); the choice is kept only once it worked, so the app never sits on a
     * language it can't show.
     */
    suspend fun choose(context: Context, code: String?) {
        apply(context, code ?: systemTag(), strict = code != null)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { if (code == null) remove(KEY_CHOICE) else putString(KEY_CHOICE, code) }
    }

    /** At start, and when the phone's language changes: brings the kept pack in line with the language to show. */
    suspend fun refresh(context: Context) {
        runCatching { apply(context, choice(context) ?: systemTag(), strict = false) }
    }

    /**
     * Shows [tag]: English needs nothing; otherwise the server's closest pack (exact tag, then the language alone). A
     * kept pack of the same version only fetches its missing chunks; otherwise the core is downloaded and used, and the
     * rest follows. Offline, a kept pack for that language stays. [strict]: a language the server doesn't have is an
     * error (the user asked for it) rather than English.
     */
    private suspend fun apply(context: Context, tag: String, strict: Boolean) = lock.withLock {
        val app = context.applicationContext
        val wanted = Locale.forLanguageTag(tag)
        if (wanted.language == ENGLISH) {
            fillJob?.cancel()
            Strings.use(app, null)
            return@withLock
        }
        val kept = Strings.pack.value
        val list = try {
            available()
        } catch (e: ApiException) {
            // Offline: keep showing a pack of this language; otherwise English until the next start.
            if (kept != null && Locale.forLanguageTag(kept.code).language == wanted.language) return@withLock
            if (strict) throw e
            fillJob?.cancel()
            Strings.use(app, null)
            return@withLock
        }
        val match = list.firstOrNull { it.code.equals(tag, ignoreCase = true) }
            ?: list.firstOrNull { Locale.forLanguageTag(it.code).language == wanted.language }
        if (match == null) {
            if (strict) throw ApiException(404, "not_found", "This language isn't available yet")
            fillJob?.cancel()
            Strings.use(app, null)
            return@withLock
        }
        if (kept != null && kept.code == match.code && kept.version == match.version) {
            fillIn(app)
            return@withLock
        }
        fillJob?.cancel()
        _busy.value = true
        try {
            val core = part(match.code, CORE)
            Strings.use(app, LanguagePack(core.code, core.nativeName, core.version, core.strings, core.chunks, CORE.toSet()))
        } finally {
            _busy.value = false
        }
        fillIn(app)
    }

    /** Fetches the kept pack's missing chunks one at a time, saving after each, so a closed app resumes where it was. */
    private fun fillIn(app: Context) {
        val start = Strings.pack.value ?: return
        if (start.missing.isEmpty() || fillJob?.isActive == true) return
        fillJob = scope.launch {
            _filling.value = true
            try {
                fillQueue(app, start)
            } finally {
                _filling.value = false
            }
        }
    }

    private suspend fun fillQueue(app: Context, start: LanguagePack) {
        val queue = start.missing.sortedBy { ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        for (chunk in queue) {
            val part = runCatching { part(start.code, listOf(chunk)) }.getOrNull() ?: return
            lock.withLock {
                val now = Strings.pack.value ?: return
                // Switched language, or the pack changed on the server: the next start fetches the new one.
                if (now.code != start.code || part.version != now.version) return
                Strings.use(app, now.copy(strings = now.strings + part.strings, loaded = now.loaded + chunk))
            }
        }
    }

    private suspend fun part(code: String, chunks: List<String>): PackPart =
        api.get("v1/languages/$code", query = mapOf("chunks" to chunks.joinToString(",")), auth = false)
}
