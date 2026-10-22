package com.boimin.beatradar.update

enum class UpdateNeed { NONE, SUGGESTED, REQUIRED }

/** Whether this build must or may update, from the versions the server asks for (`GET /v1/app/version`). */
object UpdateRules {
    /** 0 means "no requirement" for both [min] and [latest]. */
    fun need(current: Int, min: Int, latest: Int): UpdateNeed = when {
        current < min -> UpdateNeed.REQUIRED
        current < latest -> UpdateNeed.SUGGESTED
        else -> UpdateNeed.NONE
    }

    /** A suggested update is offered once per new version: "Later" quiets it until a newer one is out. */
    fun offerSuggestion(need: UpdateNeed, latest: Int, dismissedLatest: Int): Boolean =
        need == UpdateNeed.SUGGESTED && latest > dismissedLatest

    /**
     * After a 426 the server didn't say which version it wants; anything newer than this build will do until the next
     * check tells.
     */
    fun minAfterRefusal(current: Int, knownMin: Int): Int = maxOf(knownMin, current + 1)
}
