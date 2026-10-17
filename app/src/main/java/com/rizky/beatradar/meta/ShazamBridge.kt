package com.rizky.beatradar.meta

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.StatusBarNotification

/**
 * Free song naming via apps the user already has. Neither Shazam nor Pixel "Now Playing"
 * offers a result API, so results are read from their notifications. The parsing is
 * deliberately forgiving because those notification formats can change with any update.
 */
object ShazamBridge {
    const val SHAZAM_PACKAGE = "com.shazam.android"
    const val PIXEL_NOW_PLAYING_PACKAGE = "com.google.android.as"
    private const val ACTION_START_TAGGING = "com.shazam.android.intent.actions.START_TAGGING"

    fun isShazamInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(SHAZAM_PACKAGE) != null

    /** Starts listening in Shazam if it supports it, otherwise opens Shazam (or its store page). */
    fun launchIntent(context: Context): Intent {
        val pm = context.packageManager
        val tagging = Intent(ACTION_START_TAGGING).setPackage(SHAZAM_PACKAGE)
        val intent = when {
            tagging.resolveActivity(pm) != null -> tagging
            else -> pm.getLaunchIntentForPackage(SHAZAM_PACKAGE)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHAZAM_PACKAGE"))
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun parse(sbn: StatusBarNotification): Recognition? {
        val pkg = sbn.packageName
        if (pkg != SHAZAM_PACKAGE && pkg != PIXEL_NOW_PLAYING_PACKAGE) return null
        val n = sbn.notification
        // Ongoing ("Auto Shazam is on", "Listening…") and group summaries never carry a result.
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return if (pkg == SHAZAM_PACKAGE) parseShazam(title, text, sbn.postTime)
        else parseNowPlaying(title, text, sbn.postTime)
    }

    internal fun parseShazam(title: String?, text: String?, timeMs: Long): Recognition? {
        val song = title?.trim().orEmpty()
        if (song.isEmpty() || STATUS_WORDS.any { song.contains(it, ignoreCase = true) }) return null
        return Recognition(song, text?.trim()?.takeIf { it.isNotEmpty() }, "Shazam", timeMs)
    }

    /** Now Playing titles look like "Song by Artist". */
    internal fun parseNowPlaying(title: String?, text: String?, timeMs: Long): Recognition? {
        val raw = title?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("Now Playing", ignoreCase = true)) return null
        val split = raw.lastIndexOf(" by ")
        return if (split > 0) Recognition(raw.substring(0, split).trim(), raw.substring(split + 4).trim(), "Now Playing", timeMs)
        else Recognition(raw, text?.trim()?.takeIf { it.isNotEmpty() }, "Now Playing", timeMs)
    }

    private val STATUS_WORDS = listOf(
        "Shazam", "Listening", "No result", "Couldn't", "Could not", "Try again", "Offline", "Pending",
    )
}
