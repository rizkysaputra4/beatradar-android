package com.rizky.beatradar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.math.roundToInt

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val rangeEnabled: Boolean = true,
    val minBpm: Int = 120,
    val maxBpm: Int = 130,
    val vibrate: Boolean = true,
    val halfDoubleMatch: Boolean = false,
    /** Keep a short clip of unnamed songs so they can be played to Shazam later. Off by default. */
    val saveClips: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    /** True when [bpm] falls in the user's range (optionally counting half/double tempo). */
    fun matches(bpm: Float): Boolean {
        if (!rangeEnabled) return false
        val range = minBpm..maxBpm
        if (bpm.roundToInt() in range) return true
        return halfDoubleMatch && ((bpm * 2).roundToInt() in range || (bpm / 2).roundToInt() in range)
    }

    companion object {
        const val BPM_FLOOR = 60
        const val BPM_CEILING = 200
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val RANGE_ENABLED = booleanPreferencesKey("range_enabled")
        val MIN_BPM = intPreferencesKey("min_bpm")
        val MAX_BPM = intPreferencesKey("max_bpm")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val HALF_DOUBLE = booleanPreferencesKey("half_double")
        val SAVE_CLIPS = booleanPreferencesKey("save_clips")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.RANGE_ENABLED] = s.rangeEnabled
            prefs[Keys.MIN_BPM] = s.minBpm
            prefs[Keys.MAX_BPM] = s.maxBpm
            prefs[Keys.VIBRATE] = s.vibrate
            prefs[Keys.HALF_DOUBLE] = s.halfDoubleMatch
            prefs[Keys.SAVE_CLIPS] = s.saveClips
            prefs[Keys.THEME] = s.theme.name
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            rangeEnabled = this[Keys.RANGE_ENABLED] ?: d.rangeEnabled,
            minBpm = this[Keys.MIN_BPM] ?: d.minBpm,
            maxBpm = this[Keys.MAX_BPM] ?: d.maxBpm,
            vibrate = this[Keys.VIBRATE] ?: d.vibrate,
            halfDoubleMatch = this[Keys.HALF_DOUBLE] ?: d.halfDoubleMatch,
            saveClips = this[Keys.SAVE_CLIPS] ?: d.saveClips,
            theme = enumOrDefault(this[Keys.THEME], d.theme),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
