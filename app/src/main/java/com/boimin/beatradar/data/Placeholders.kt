package com.boimin.beatradar.data

import android.content.Context

/** Hands out "Song #N" numbers. A number is never reused, even after its entry is deleted. */
object Placeholders {
    private const val PREFS = "labels"
    private const val KEY_LAST = "last_placeholder_no"

    @Synchronized
    fun next(context: Context, highestInLog: Int?): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = maxOf(prefs.getInt(KEY_LAST, 0), highestInLog ?: 0) + 1
        prefs.edit().putInt(KEY_LAST, n).apply()
        return n
    }
}
