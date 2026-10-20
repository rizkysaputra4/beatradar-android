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
import com.boimin.beatradar.net.Milestone
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.media.app.NotificationCompat.MediaStyle

class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ongoing(state: MonitorState): Notification {
        val bpm = state.bpm?.roundToInt()
        val title = when {
            state.status == MonitorStatus.PAUSED -> "Paused"
            bpm == null -> "Listening for a beat…"
            state.matched -> "$bpm BPM · match!"
            state.matchProgress > 0f -> "$bpm BPM · in range ${(state.matchProgress * 10).toInt()}/10 s"
            else -> "$bpm BPM"
        }
        val song = listOfNotNull(state.title, state.artist).joinToString(" · ")
        val text = song.ifEmpty { "Listening through the microphone" }
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
                if (paused) "Resume" else "Pause",
                serviceAction(if (paused) CaptureService.ACTION_RESUME else CaptureService.ACTION_PAUSE),
            )
            .addAction(R.drawable.ic_stop, "Stop", serviceAction(CaptureService.ACTION_STOP))
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
            .build()
    }

    fun updateOngoing(state: MonitorState) = notify(ID_LISTENING, ongoing(state))

    fun match(bpm: Float, title: String?, artist: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_MATCH)
            .setSmallIcon(R.drawable.ic_stat_beat)
            .setColor(ContextCompat.getColor(context, R.color.brand_coral))
            .setContentTitle("${bpm.roundToInt()} BPM — in your range")
            .setContentText(listOfNotNull(title, artist).joinToString(" · ").ifEmpty { "Tap Identify to name this song" })
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
        if (title == null) {
            val shazam = PendingIntent.getActivity(
                context, REQUEST_SHAZAM, ShazamBridge.launchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_search, "Identify with Shazam", shazam)
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
                .setContentTitle("${m.title} reached ${NumberFormat.getIntegerInstance().format(m.milestone)} listeners")
                .setContentText("You brought this song in. ${m.artist} · now ${NumberFormat.getIntegerInstance().format(m.listeners)} listeners")
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
                .setContentTitle("Your week in tempo")
                .setContentText(
                    "${r.songs} songs" + (r.topBand?.let { ", mostly at $it–${it + 9} BPM" } ?: "") + ". See your recap and share it.",
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
                .setContentTitle("Listening stopped")
                .setContentText("Android ended audio capture. Tap to start again.")
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
                NotificationChannel(CHANNEL_LISTENING, "Listening", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the current BPM while BeatRadar listens"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MATCH, "BPM matches", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a song's tempo is inside your range"
                    // BeatRadar vibrates itself (works on silent); keep the channel quiet.
                    setSound(null, null)
                    enableVibration(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SONGS, "Songs you brought in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a song you first added reaches 10, 100 or 1,000 listeners"
                },
            )
        }
    }
}
