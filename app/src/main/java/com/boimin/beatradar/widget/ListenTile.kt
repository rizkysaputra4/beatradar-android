package com.boimin.beatradar.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.boimin.beatradar.MainActivity
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.capture.MonitorStatus
import kotlin.math.roundToInt

/** Quick Settings tile: start or stop listening from the notification shade, with the live BPM as its subtitle. */
class ListenTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (Monitor.state.value.active) {
            CaptureService.send(this, CaptureService.ACTION_STOP)
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            openApp()
        } else {
            // A tile tap counts as the user starting it; if the system still refuses the microphone service, the app opens.
            runCatching { CaptureService.start(this) }.onFailure {
                Log.w("BeatRadar", "tile could not start listening", it)
                openApp()
            }
        }
        render()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = Monitor.state.value
        tile.state = if (s.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_beat)
        tile.subtitle = when {
            s.status == MonitorStatus.LISTENING && s.bpm != null -> "${s.bpm.roundToInt()} BPM"
            s.status == MonitorStatus.LISTENING -> "Listening"
            s.status == MonitorStatus.PAUSED -> "Paused"
            else -> "Off"
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile (it calls [onStartListening] if the shade is open). */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ListenTile::class.java)) }
        }
    }
}
