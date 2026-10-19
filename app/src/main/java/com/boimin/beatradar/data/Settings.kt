package com.boimin.beatradar.data

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
    /** Keep a short clip of songs without a name, to play to Shazam later. Clips never leave the phone. */
    val saveClips: Boolean = true,
    /** Clips for every unnamed song, not only for in-range matches. */
    val clipAllSongs: Boolean = true,
    /** Clips are kept for this many of the most recently heard songs, one of [CLIP_LIMITS]. */
    val clipLimit: Int = 30,
    /** Send titles, tempos and (for reliably named songs) fingerprints to the shared Library. Never audio. */
    val contribute: Boolean = true,
    /** The one-time "what BeatRadar shares" notice was shown. */
    val sharingNoticeShown: Boolean = false,
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
        val CLIP_LIMITS = listOf(10, 30, 50, 100)
        /** A 12 s clip is about this big. */
        const val CLIP_MB = 0.5
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
        val CLIP_ALL_SONGS = booleanPreferencesKey("clip_all_songs")
        val CLIP_LIMIT = intPreferencesKey("clip_limit")
        val CONTRIBUTE = booleanPreferencesKey("contribute")
        val SHARING_NOTICE = booleanPreferencesKey("sharing_notice_shown")
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
            prefs[Keys.CLIP_ALL_SONGS] = s.clipAllSongs
            prefs[Keys.CLIP_LIMIT] = s.clipLimit
            prefs[Keys.CONTRIBUTE] = s.contribute
            prefs[Keys.SHARING_NOTICE] = s.sharingNoticeShown
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
            clipAllSongs = this[Keys.CLIP_ALL_SONGS] ?: d.clipAllSongs,
            clipLimit = this[Keys.CLIP_LIMIT]?.takeIf { it in Settings.CLIP_LIMITS } ?: d.clipLimit,
            contribute = this[Keys.CONTRIBUTE] ?: d.contribute,
            sharingNoticeShown = this[Keys.SHARING_NOTICE] ?: d.sharingNoticeShown,
            theme = enumOrDefault(this[Keys.THEME], d.theme),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
