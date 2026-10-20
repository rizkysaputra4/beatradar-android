package com.boimin.beatradar.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.boimin.beatradar.MainActivity
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.capture.MonitorStatus
import kotlin.math.roundToInt

/**
 * Home-screen widget: the live BPM and song, and a button that starts or stops listening without opening the app.
 * [CaptureService] pushes updates (at most once a second) while it runs.
 */
class BeatWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, Monitor.state.value)
    }

    companion object {
        fun update(context: Context, state: MonitorState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BeatWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, state))
        }

        private fun views(context: Context, state: MonitorState): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_beat)
            val listening = state.status == MonitorStatus.LISTENING
            v.setTextViewText(R.id.widget_bpm, state.bpm?.takeIf { listening }?.let { "${it.roundToInt()} BPM" } ?: "—")
            v.setTextViewText(
                R.id.widget_status,
                when {
                    state.matched -> "In your range"
                    listening && state.title != null -> listOfNotNull(state.title, state.artist).joinToString(" · ")
                    listening -> "Listening…"
                    state.status == MonitorStatus.PAUSED -> "Paused"
                    else -> "Tap ▶ to listen"
                },
            )
            v.setImageViewResource(R.id.widget_toggle, if (state.active) R.drawable.ic_stop else R.drawable.ic_play)
            v.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent(context, state.active))
            v.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            return v
        }

        /**
         * Starting listening needs the microphone permission; without it the app opens to ask. Android lets a widget tap
         * start the microphone service in the background; if a system refuses, the service falls back to the app.
         */
        private fun toggleIntent(context: Context, active: Boolean): PendingIntent {
            val canListen = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            return when {
                active -> PendingIntent.getService(
                    context, 21, Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                canListen -> PendingIntent.getForegroundService(
                    context, 22, Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_START),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                else -> PendingIntent.getActivity(context, 23, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }
    }
}
