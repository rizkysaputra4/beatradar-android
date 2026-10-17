package com.rizky.beatradar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rizky.beatradar.capture.CaptureService
import com.rizky.beatradar.data.BeatLogEntry
import com.rizky.beatradar.data.ClipStore
import com.rizky.beatradar.meta.SongNamer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rizky.beatradar.meta.ShazamBridge
import com.rizky.beatradar.ui.AppActions
import com.rizky.beatradar.ui.AppRoot
import com.rizky.beatradar.ui.MainViewModel
import com.rizky.beatradar.ui.theme.BeatRadarTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            startListening()
        } else {
            Toast.makeText(this, "BeatRadar needs audio permission to hear the beat", Toast.LENGTH_LONG).show()
        }
    }

    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportTo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge()

        val actions = AppActions(
            start = ::requestStart,
            stop = { CaptureService.send(this, CaptureService.ACTION_STOP) },
            pause = { CaptureService.send(this, CaptureService.ACTION_PAUSE) },
            resume = { CaptureService.send(this, CaptureService.ACTION_RESUME) },
            identify = { startActivity(ShazamBridge.launchIntent(this)) },
            identifyClip = ::identifyClip,
            openNotificationAccess = { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            exportLog = { exportCsv.launch("beatradar-log.csv") },
        )

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            settings?.let { s ->
                BeatRadarTheme(s.theme) {
                    AppRoot(vm = vm, settings = s, actions = actions)
                }
            }
        }
    }

    private fun requestStart() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(needed.toTypedArray())
    }

    private fun startListening() = CaptureService.start(this)

    /** Opens Shazam, then plays the saved clip through the speaker so Shazam can hear it. */
    private fun identifyClip(entry: BeatLogEntry) {
        val path = entry.clipPath ?: return
        SongNamer.startIdentify(entry.id)
        Toast.makeText(this, "Playing the clip for Shazam. Keep the volume up.", Toast.LENGTH_LONG).show()
        startActivity(ShazamBridge.launchIntent(this))
        lifecycleScope.launch {
            delay(1_800)
            runCatching { ClipStore.play(path) }
        }
    }
}
