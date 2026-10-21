package com.boimin.beatradar.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.MainActivity
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.capture.MonitorStatus
import com.boimin.beatradar.meta.ShazamBridge
import com.boimin.beatradar.data.Recap
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.Milestone
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.media.app.NotificationCompat.MediaStyle

class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ongoing(state: MonitorState): Notification {
        val bpm = state.bpm?.roundToInt()
        val title = when {
            state.status == MonitorStatus.PAUSED -> context.str(R.string.notif_listening_paused)
            bpm == null -> context.str(R.string.notif_listening_waiting)
            state.matched -> context.str(R.string.notif_listening_bpm_match, bpm)
            state.matchProgress > 0f -> context.str(R.string.notif_listening_bpm_progress, bpm, (state.matchProgress * 10).toInt())
            else -> context.str(R.string.notif_listening_bpm, bpm)
        }
        val song = listOfNotNull(state.title, state.artist).joinToString(" · ")
        val text = song.ifEmpty { context.str(R.string.notif_listening_mic) }
        val paused = state.status == MonitorStatus.PAUSED
        return NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_beat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                context.str(if (paused) R.string.notif_action_resume else R.string.notif_action_pause),
                serviceAction(if (paused) CaptureService.ACTION_RESUME else CaptureService.ACTION_PAUSE),
            )
            .addAction(R.drawable.ic_stop, context.str(R.string.notif_action_stop), serviceAction(CaptureService.ACTION_STOP))
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
            .build()
    }

    fun updateOngoing(state: MonitorState) = notify(ID_LISTENING, ongoing(state))

    fun match(bpm: Float, title: String?, artist: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_MATCH)
            .setSmallIcon(R.drawable.ic_stat_beat)
            .setColor(ContextCompat.getColor(context, R.color.brand_coral))
            .setContentTitle(context.str(R.string.notif_match_title, bpm.roundToInt()))
            .setContentText(listOfNotNull(title, artist).joinToString(" · ").ifEmpty { context.str(R.string.notif_match_text_unnamed) })
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
        if (title == null) {
            val shazam = PendingIntent.getActivity(
                context, REQUEST_SHAZAM, ShazamBridge.launchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_search, context.str(R.string.notif_match_action_shazam), shazam)
        }
        notify(ID_MATCH, builder.build())
    }

    /** "You brought this song in": a song this user first measured reached [m] listeners. Tapping opens it. */
    fun milestone(m: Milestone) {
        val open = PendingIntent.getActivity(
            context, m.slug.hashCode(),
            Intent(Intent.ACTION_VIEW, "https://${BuildConfig.SHARE_HOST}/s/${m.slug}".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            ID_MILESTONE_BASE + (m.slug.hashCode() and 0xffff),
            NotificationCompat.Builder(context, CHANNEL_SONGS)
                .setSmallIcon(R.drawable.ic_stat_beat)
                .setColor(ContextCompat.getColor(context, R.color.brand_coral))
                .setContentTitle(context.strPlural(R.plurals.notif_milestone_title, m.milestone, m.title, NumberFormat.getIntegerInstance().format(m.milestone)))
                .setContentText(context.strPlural(R.plurals.notif_milestone_text, m.listeners, m.artist, NumberFormat.getIntegerInstance().format(m.listeners)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .build(),
        )
    }

    /** Sunday evening: "Your week in tempo". Tapping opens the recap. */
    fun recap(r: Recap) {
        val open = PendingIntent.getActivity(
            context, REQUEST_RECAP,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_RECAP, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            ID_RECAP,
            NotificationCompat.Builder(context, CHANNEL_SONGS)
                .setSmallIcon(R.drawable.ic_stat_beat)
                .setColor(ContextCompat.getColor(context, R.color.brand_coral))
                .setContentTitle(context.str(R.string.notif_recap_title))
                .setContentText(
                    r.topBand?.let { context.strPlural(R.plurals.notif_recap_text_band, r.songs, r.songs, it, it + 9) }
                        ?: context.strPlural(R.plurals.notif_recap_text, r.songs, r.songs),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun stoppedBySystem() {
        notify(
            ID_SYSTEM,
            NotificationCompat.Builder(context, CHANNEL_LISTENING)
                .setSmallIcon(R.drawable.ic_stat_beat)
                .setContentTitle(context.str(R.string.notif_stopped_title))
                .setContentText(context.str(R.string.notif_stopped_text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notify(id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            manager.notify(id, notification)
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, CaptureService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_LISTENING = "listening"
        const val CHANNEL_MATCH = "matches"
        const val CHANNEL_SONGS = "your_songs"
        const val ID_LISTENING = 1
        const val ID_MATCH = 2
        const val ID_SYSTEM = 3
        private const val ID_MILESTONE_BASE = 1_000
        const val ID_RECAP = 4
        private const val REQUEST_RECAP = 12
        private const val REQUEST_OPEN = 10
        private const val REQUEST_SHAZAM = 11

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LISTENING, context.str(R.string.notif_channel_listening_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.str(R.string.notif_channel_listening_description)
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MATCH, context.str(R.string.notif_channel_matches_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.str(R.string.notif_channel_matches_description)
                    // BeatRadar vibrates itself (works on silent); keep the channel quiet.
                    setSound(null, null)
                    enableVibration(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SONGS, context.str(R.string.notif_channel_songs_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.str(R.string.notif_channel_songs_description)
                },
            )
        }
    }
}
