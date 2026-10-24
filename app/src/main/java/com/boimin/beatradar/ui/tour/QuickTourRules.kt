package com.boimin.beatradar.ui.tour

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings

/** The tab a tour step shows; the same order, labels and icons as the tab bar (AppRoot's TAB_*). */
enum class TourTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    LISTEN(R.string.app_tab_listen, R.drawable.ic_graphic_eq),
    LOG(R.string.app_tab_log, R.drawable.ic_history),
    LIBRARY(R.string.app_tab_library, R.drawable.ic_library_music),
    SETS(R.string.app_tab_sets, R.drawable.ic_queue),
}

/** What a tour step points at, marked in the screens with [tourTarget]. */
enum class TourTarget { DIAL, TARGET_CHIP, LOG_SEARCH, LIBRARY_SEARCH, SETS_ACTIONS, HEADER_BEATS, HEADER_PROFILE, TAB_LISTEN, TAB_LOG, TAB_LIBRARY, TAB_SETS }

/**
 * One step: the tab it shows, what it spotlights ([target], else that tab's button in the tab bar; null: none, the
 * card in the middle) and its text. A step with [goTo] points at the next tab's button and says what's there; only
 * its button (or a tap on that tab) moves to the tab, so the tour never switches tabs on its own.
 */
enum class TourStep(
    val tab: TourTab,
    val target: TourTarget?,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val goTo: TourTab? = null,
) {
    WELCOME(TourTab.LISTEN, null, R.string.app_tour_welcome_title, R.string.app_tour_welcome_body),
    DIAL(TourTab.LISTEN, TourTarget.DIAL, R.string.app_tour_dial_title, R.string.app_tour_dial_body),
    TARGET(TourTab.LISTEN, TourTarget.TARGET_CHIP, R.string.app_tour_target_title, R.string.app_tour_target_body),
    GO_LOG(TourTab.LISTEN, TourTarget.TAB_LOG, R.string.app_tour_next_tab, R.string.app_tour_go_log_body, goTo = TourTab.LOG),
    LOG(TourTab.LOG, TourTarget.LOG_SEARCH, R.string.app_tour_log_title, R.string.app_tour_log_body),
    GO_LIBRARY(TourTab.LOG, TourTarget.TAB_LIBRARY, R.string.app_tour_next_tab, R.string.app_tour_go_library_body, goTo = TourTab.LIBRARY),
    LIBRARY(TourTab.LIBRARY, TourTarget.LIBRARY_SEARCH, R.string.app_tour_library_title, R.string.app_tour_library_body),
    GO_SETS(TourTab.LIBRARY, TourTarget.TAB_SETS, R.string.app_tour_next_tab, R.string.app_tour_go_sets_body, goTo = TourTab.SETS),
    SETS(TourTab.SETS, TourTarget.SETS_ACTIONS, R.string.app_tour_sets_title, R.string.app_tour_sets_body),
    // On Sets still: the header is on every tab, so the tour doesn't jump back to point at it.
    BEATS(TourTab.SETS, TourTarget.HEADER_BEATS, R.string.app_tour_beats_title, R.string.app_tour_beats_body),
    PROFILE(TourTab.SETS, TourTarget.HEADER_PROFILE, R.string.app_tour_profile_title, R.string.app_tour_profile_body),
}

/**
 * The quick tour's pure rules. After Quick Setup nothing tours: the app is ready to listen. Instead each of Log, Library
 * and Sets shows its tip ([tipsFor]) the first time it opens. The full tour runs only from Settings → "Take the quick
 * tour"; any step can close it (Skip, or Back on the first step).
 */
object QuickTourRules {
    val STEPS: List<TourStep> = TourStep.entries

    /**
     * The tabs with a first-visit tip. Listen's is a hint in the page under the target chip (ListenScreen), the one thing
     * there that isn't obvious, so the dial stays free right after Quick Setup; the others are a coach mark ([tipsFor]).
     */
    val TIP_TABS: List<TourTab> = TourTab.entries

    /**
     * The coach marks [tab]'s tip shows, in order; none for Listen (its hint is in the page, see [showsListenHint]). Sets,
     * the last tab, also points at the header: the beats chip (leaderboard) and the profile button (Settings).
     */
    fun tipsFor(tab: TourTab): List<TourStep> = when (tab) {
        TourTab.LISTEN -> emptyList()
        TourTab.LOG -> listOf(TourStep.LOG)
        TourTab.LIBRARY -> listOf(TourStep.LIBRARY)
        TourTab.SETS -> listOf(TourStep.SETS, TourStep.BEATS, TourStep.PROFILE)
    }

    /**
     * The tabs whose tip was seen, from the stored `tips_seen` ([stored], null when never written). Every settings write
     * stores it, so a new install has an empty set from Quick Setup on. A phone from before the tips has no such key:
     * once set up, it counts as having seen them all, so upgrades never get them uninvited.
     */
    fun tipsSeen(stored: Set<String>?, setupDone: Boolean): Set<String> =
        stored ?: if (setupDone) TIP_TABS.map { it.name }.toSet() else emptySet()

    /** [tab]'s coach mark shows: after Quick Setup, the first time the tab is open. */
    fun showsTip(s: Settings, tab: TourTab): Boolean = s.setupDone && tipsFor(tab).isNotEmpty() && tab.name !in s.tipsSeen

    /** Listen's hint under the target chip shows: after Quick Setup, until it's closed or the chip is tapped. */
    fun showsListenHint(s: Settings): Boolean = s.setupDone && TourTab.LISTEN.name !in s.tipsSeen

    /** [tab]'s tip was closed: it doesn't show again. */
    fun tipSeen(s: Settings, tab: TourTab): Settings = s.copy(tipsSeen = s.tipsSeen + tab.name)

    /** The whole tour was walked: the tabs' tips would only repeat it. */
    fun allTipsSeen(s: Settings): Settings = s.copy(tipsSeen = s.tipsSeen + TIP_TABS.map { it.name })

    /** The tab button that stands in when a step's own target isn't on screen (e.g. a set is open on the Sets tab). */
    fun fallback(tab: TourTab): TourTarget = when (tab) {
        TourTab.LISTEN -> TourTarget.TAB_LISTEN
        TourTab.LOG -> TourTarget.TAB_LOG
        TourTab.LIBRARY -> TourTarget.TAB_LIBRARY
        TourTab.SETS -> TourTarget.TAB_SETS
    }

    /** The step at [index], kept in range (a saved index survives a change in the steps). */
    fun step(index: Int): TourStep = STEPS[index.coerceIn(0, STEPS.lastIndex)]

    fun isLast(index: Int): Boolean = index >= STEPS.lastIndex
}
