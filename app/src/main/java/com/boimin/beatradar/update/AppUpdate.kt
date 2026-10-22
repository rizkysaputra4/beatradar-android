package com.boimin.beatradar.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.net.ApiClient
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.AppVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UpdateState(
    val need: UpdateNeed = UpdateNeed.NONE,
    /** Show the "Update available" dialog (a suggested update not dismissed yet). */
    val offerSuggestion: Boolean = false,
    /** The server's note for the update screen, e.g. what the update fixes. */
    val message: String? = null,
)

/**
 * Forced and suggested updates (MVP-AUDIT A1). Every request carries this build's version code (`X-App-Version`, see
 * [ApiClient]); the server answers 426 when it's below the minimum, and [check] asks at start which versions it wants.
 * The answer is kept, so an outdated app stays behind the update screen when it opens offline.
 */
object AppUpdate {
    private const val PREFS = "app_update"
    private const val KEY_MIN = "min"
    private const val KEY_LATEST = "latest"
    private const val KEY_MESSAGE = "message"
    private const val KEY_DISMISSED = "dismissed_latest"

    private val current = BuildConfig.VERSION_CODE
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** Loads what the last check said, before the first screen is drawn. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        publish(p.getInt(KEY_MIN, 0), p.getInt(KEY_LATEST, 0), p.getString(KEY_MESSAGE, null))
    }

    /**
     * Asks the server which versions it wants. With a token when there is one, so the server learns this install's
     * version; without when getting one fails (an outdated app can't register). Offline, the kept answer stays.
     */
    suspend fun check(api: ApiClient) {
        val info = try {
            api.get<AppVersionInfo>("v1/app/version")
        } catch (e: ApiException) {
            if (e.offline) return
            runCatching { api.get<AppVersionInfo>("v1/app/version", auth = false) }.getOrNull() ?: return
        }
        save(info.minVersionCode, info.latestVersionCode, info.message)
    }

    /** The server refused a request with 426 `update_required`. */
    fun onUpdateRequired() {
        val p = prefs
        val min = UpdateRules.minAfterRefusal(current, p?.getInt(KEY_MIN, 0) ?: 0)
        save(min, maxOf(min, p?.getInt(KEY_LATEST, 0) ?: 0), p?.getString(KEY_MESSAGE, null))
    }

    /** "Later" on a suggested update: not offered again until a newer version is out. */
    fun dismissSuggestion() {
        val p = prefs ?: return
        p.edit { putInt(KEY_DISMISSED, p.getInt(KEY_LATEST, 0)) }
        _state.value = _state.value.copy(offerSuggestion = false)
    }

    private fun save(min: Int, latest: Int, message: String?) {
        prefs?.edit {
            putInt(KEY_MIN, min)
            putInt(KEY_LATEST, latest)
            if (message == null) remove(KEY_MESSAGE) else putString(KEY_MESSAGE, message)
        }
        publish(min, latest, message)
    }

    private fun publish(min: Int, latest: Int, message: String?) {
        val need = UpdateRules.need(current, min, latest)
        val dismissed = prefs?.getInt(KEY_DISMISSED, 0) ?: 0
        _state.value = UpdateState(need, UpdateRules.offerSuggestion(need, latest, dismissed), message?.takeIf { it.isNotBlank() })
    }
}
