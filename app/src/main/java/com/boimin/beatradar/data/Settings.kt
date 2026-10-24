package com.boimin.beatradar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boimin.beatradar.ui.setup.QuickSetupRules
import com.boimin.beatradar.ui.tour.QuickTourRules
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
    /** No lower limit ("up to [maxBpm]"). [minBpm] is kept, for when a lower limit is set again. */
    val minOpen: Boolean = false,
    /** No upper limit ("[minBpm] and up"). [maxBpm] is kept, for when an upper limit is set again. */
    val maxOpen: Boolean = false,
    val vibrate: Boolean = true,
    val halfDoubleMatch: Boolean = false,
    /** Keep a short clip of songs without a name, to play to Shazam later. Clips leave the phone only with [shareClips]. */
    val saveClips: Boolean = true,
    /** Clips for every unnamed song, not only for in-range matches. */
    val clipAllSongs: Boolean = true,
    /** Clips are kept for this many of the most recently heard songs, one of [CLIP_LIMITS]. */
    val clipLimit: Int = 30,
    /** Send titles, tempos and (for reliably named songs) fingerprints to the shared Library. Never audio. */
    val contribute: Boolean = true,
    /**
     * Help name unknown songs (PLAN §5d): send the 12 s clip of songs nobody could name, so an admin can listen and
     * name them for everyone. Off by default; needs [saveClips] (and [contribute], which sends their fingerprints).
     * Turning it off deletes this phone's clips on the server.
     */
    val shareClips: Boolean = false,
    /**
     * The first-run questions were answered: may BeatRadar contribute to the Library, and share clips of unknown songs?
     * (A new key: phones that only saw the earlier one-line notice are asked once too.)
     */
    val sharingConsentAsked: Boolean = false,
    /**
     * Quick Setup was finished or skipped (ui/setup). Phones from before it count as set up once they answered the
     * sharing questions ([QuickSetupRules.setupDone]), so only new installs see it.
     */
    val setupDone: Boolean = false,
    /**
     * The tabs (TourTab names) whose first-visit tip (ui/tour) was closed. Phones from before the tips count as having
     * seen them all once set up ([QuickTourRules.tipsSeen]), so only new installs get them.
     */
    val tipsSeen: Set<String> = emptySet(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** The app sets play in: "youtube" (the whole set as one queue) or "spotify" (a song at a time). */
    val playApp: String = "youtube",
) {
    /** True when [bpm] falls in the user's range and range alerts are on (they vibrate). */
    fun matches(bpm: Float): Boolean = rangeEnabled && inTarget(bpm)

    /**
     * True when [bpm] falls in the target range set on the Listen tab (optionally counting half/double tempo), whether
     * or not its alerts are on. The log's "In target" filter uses it, so it always follows the current range.
     */
    fun inTarget(bpm: Float): Boolean =
        bpmIn(bpm, (if (minOpen) 0 else minBpm)..(if (maxOpen) Int.MAX_VALUE else maxBpm), halfDoubleMatch)

    /** The target's lower limit, or null when it has none. */
    val targetLow: Int? get() = minBpm.takeUnless { minOpen }

    /** The target's upper limit, or null when it has none. */
    val targetHigh: Int? get() = maxBpm.takeUnless { maxOpen }

    /** The target as a closed range, an open end standing at the edge of what BeatRadar measures. */
    val targetRange: IntRange get() = (targetLow ?: BPM_FLOOR)..(targetHigh ?: BPM_CEILING)

    companion object {
        /** True when [bpm] falls in [range], optionally counting half and double tempo. */
        fun bpmIn(bpm: Float, range: IntRange, halfDouble: Boolean): Boolean {
            if (bpm.roundToInt() in range) return true
            return halfDouble && ((bpm * 2).roundToInt() in range || (bpm / 2).roundToInt() in range)
        }

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
        val MIN_OPEN = booleanPreferencesKey("min_open")
        val MAX_OPEN = booleanPreferencesKey("max_open")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val HALF_DOUBLE = booleanPreferencesKey("half_double")
        val SAVE_CLIPS = booleanPreferencesKey("save_clips")
        val CLIP_ALL_SONGS = booleanPreferencesKey("clip_all_songs")
        val CLIP_LIMIT = intPreferencesKey("clip_limit")
        val CONTRIBUTE = booleanPreferencesKey("contribute")
        val SHARE_CLIPS = booleanPreferencesKey("share_clips")
        val SHARING_CONSENT = booleanPreferencesKey("sharing_consent_asked")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val TIPS_SEEN = stringSetPreferencesKey("tips_seen")
        val THEME = stringPreferencesKey("theme")
        val PLAY_APP = stringPreferencesKey("play_app")
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
            // Both ends open is "any tempo", which the range alerts switch already covers.
            prefs[Keys.MIN_OPEN] = s.minOpen && !s.maxOpen
            prefs[Keys.MAX_OPEN] = s.maxOpen
            prefs[Keys.VIBRATE] = s.vibrate
            prefs[Keys.HALF_DOUBLE] = s.halfDoubleMatch
            prefs[Keys.SAVE_CLIPS] = s.saveClips
            prefs[Keys.CLIP_ALL_SONGS] = s.clipAllSongs
            prefs[Keys.CLIP_LIMIT] = s.clipLimit
            prefs[Keys.CONTRIBUTE] = s.contribute
            prefs[Keys.SHARE_CLIPS] = s.shareClips
            prefs[Keys.SHARING_CONSENT] = s.sharingConsentAsked
            prefs[Keys.SETUP_DONE] = s.setupDone
            prefs[Keys.TIPS_SEEN] = s.tipsSeen
            prefs[Keys.THEME] = s.theme.name
            prefs[Keys.PLAY_APP] = s.playApp
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        val setupDone = QuickSetupRules.setupDone(this[Keys.SETUP_DONE], this[Keys.SHARING_CONSENT] ?: d.sharingConsentAsked)
        return Settings(
            rangeEnabled = this[Keys.RANGE_ENABLED] ?: d.rangeEnabled,
            minBpm = this[Keys.MIN_BPM] ?: d.minBpm,
            maxBpm = this[Keys.MAX_BPM] ?: d.maxBpm,
            minOpen = this[Keys.MIN_OPEN] ?: d.minOpen,
            maxOpen = this[Keys.MAX_OPEN] ?: d.maxOpen,
            vibrate = this[Keys.VIBRATE] ?: d.vibrate,
            halfDoubleMatch = this[Keys.HALF_DOUBLE] ?: d.halfDoubleMatch,
            saveClips = this[Keys.SAVE_CLIPS] ?: d.saveClips,
            clipAllSongs = this[Keys.CLIP_ALL_SONGS] ?: d.clipAllSongs,
            clipLimit = this[Keys.CLIP_LIMIT]?.takeIf { it in Settings.CLIP_LIMITS } ?: d.clipLimit,
            contribute = this[Keys.CONTRIBUTE] ?: d.contribute,
            shareClips = this[Keys.SHARE_CLIPS] ?: d.shareClips,
            sharingConsentAsked = this[Keys.SHARING_CONSENT] ?: d.sharingConsentAsked,
            setupDone = setupDone,
            tipsSeen = QuickTourRules.tipsSeen(this[Keys.TIPS_SEEN], setupDone),
            theme = enumOrDefault(this[Keys.THEME], d.theme),
            playApp = this[Keys.PLAY_APP]?.takeIf { it == "youtube" || it == "spotify" } ?: d.playApp,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
