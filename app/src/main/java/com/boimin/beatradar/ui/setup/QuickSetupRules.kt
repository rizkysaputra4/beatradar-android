package com.boimin.beatradar.ui.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings

/**
 * What BeatRadar is for, as Quick Setup's first step offers it: a target range with its alerts, and whether half or
 * double tempo counts (a runner's 170 steps fit an 85 BPM song). [FREE] has no target: alerts off.
 */
enum class SetupPreset(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val min: Int?,
    val max: Int?,
    val halfDouble: Boolean,
) {
    RUN(R.string.app_setup_preset_run, R.drawable.ic_fire, 160, 180, halfDouble = true),
    BRISK_WALK(R.string.app_setup_preset_walk, R.drawable.ic_play, 110, 125, halfDouble = false),
    CYCLING(R.string.app_setup_preset_cycling, R.drawable.ic_radar, 80, 100, halfDouble = true),
    DJ(R.string.app_setup_preset_dj, R.drawable.ic_speaker, 120, 130, halfDouble = true),
    FREE(R.string.app_setup_preset_free, R.drawable.ic_note, null, null, halfDouble = false),
}

/** Quick Setup's pure rules (docs/plans/03-quick-setup.md): presets into [Settings], consent, and who sees it. */
object QuickSetupRules {
    /** Steps: what for, permissions, the Library, the account. */
    const val STEPS = 4

    /**
     * Whether Quick Setup is done, from the stored `setup_done` ([stored], null when never written). A phone from before
     * Quick Setup has no such key: it counts as set up once it answered the sharing questions, so upgrades never see it.
     */
    fun setupDone(stored: Boolean?, sharingConsentAsked: Boolean): Boolean = stored ?: sharingConsentAsked

    /** [preset]'s target range, alerts and half/double tempo; everything else stays. */
    fun apply(s: Settings, preset: SetupPreset): Settings =
        if (preset.min == null || preset.max == null) {
            s.copy(rangeEnabled = false, halfDoubleMatch = preset.halfDouble)
        } else {
            s.copy(
                rangeEnabled = true,
                minBpm = preset.min,
                maxBpm = preset.max,
                minOpen = false,
                maxOpen = false,
                halfDoubleMatch = preset.halfDouble,
            )
        }

    /** "Set my own": the range from the editor (an end may be open), alerts on; half/double stays as it was. */
    fun custom(s: Settings, low: Int?, high: Int?): Settings =
        s.copy(rangeEnabled = true, minBpm = low ?: s.minBpm, maxBpm = high ?: s.maxBpm, minOpen = low == null, maxOpen = high == null)

    /** The preset [s] already matches, so running Quick Setup again shows the current choice; null for none. */
    fun presetOf(s: Settings): SetupPreset? = SetupPreset.entries.firstOrNull { p ->
        if (p.min == null) {
            !s.rangeEnabled && !s.halfDoubleMatch
        } else {
            s.rangeEnabled && s.targetLow == p.min && s.targetHigh == p.max && s.halfDoubleMatch == p.halfDouble
        }
    }

    /**
     * The Library step's answer, written as the first-run questions always were: clips can only be shared if they're
     * kept on the phone, and clips need [contribute] (which sends their fingerprints).
     */
    fun consent(s: Settings, contribute: Boolean, shareClips: Boolean): Settings {
        val clips = contribute && shareClips
        return s.copy(sharingConsentAsked = true, contribute = contribute, shareClips = clips, saveClips = s.saveClips || clips)
    }

    /** Finished or skipped: it doesn't show again (Settings → "Run Quick Setup again" brings it back). */
    fun finish(s: Settings): Settings = s.copy(setupDone = true)

    /** Settings → "Run Quick Setup again": every choice stays, the steps show it. */
    fun again(s: Settings): Settings = s.copy(setupDone = false)

    /** Nicknames like the server gives new accounts ("Indigo Drummer 4821"), for the account step's preview. */
    fun sampleNickname(seed: Int): String {
        val colour = NICK_COLOURS[Math.floorMod(seed, NICK_COLOURS.size)]
        val role = NICK_ROLES[Math.floorMod(seed / NICK_COLOURS.size, NICK_ROLES.size)]
        return "$colour $role ${1000 + Math.floorMod(seed, 9000)}"
    }

    private val NICK_COLOURS = listOf("Indigo", "Violet", "Coral", "Mint", "Amber", "Cobalt")
    private val NICK_ROLES = listOf("Drummer", "Runner", "Rider", "Groover", "Dancer")
}
