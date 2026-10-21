package com.boimin.beatradar

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import com.boimin.beatradar.account.AccountViewModel
import com.boimin.beatradar.billing.CoffeeBilling
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.library.PlayLinks
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.notify.Haptics
import kotlin.math.roundToInt
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.meta.SongNamer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.boimin.beatradar.meta.ShazamBridge
import com.boimin.beatradar.ui.AppActions
import com.boimin.beatradar.ui.AppRoot
import com.boimin.beatradar.ui.MainViewModel
import com.boimin.beatradar.ui.theme.BeatRadarTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val accountVm: AccountViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()
    private lateinit var coffee: CoffeeBilling
    private lateinit var haptics: Haptics

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            startListening()
        } else {
            Toast.makeText(this, "BeatRadar needs audio permission to hear the beat", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        splash.setOnExitAnimationListener { splashScreenViewProvider ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenViewProvider.view,
                View.ALPHA,
                1f,
                0f
            ).apply {
                duration = 200L
                interpolator = AccelerateDecelerateInterpolator()
                doOnEnd { splashScreenViewProvider.remove() }
            }
            fadeOut.start()
        }
        enableEdgeToEdge()
        coffee = CoffeeBilling(this, onPurchased = { product, token ->
            app.library.claimSupporter(product, token)
            accountVm.loadRewards()
        }).also { it.start() }
        haptics = Haptics(this)
        // Not again after a rotation: the link was handled when the activity first started.
        if (savedInstanceState == null) handleLink(intent)

        val actions = AppActions(
            start = ::requestStart,
            stop = { CaptureService.send(this, CaptureService.ACTION_STOP) },
            pause = { CaptureService.send(this, CaptureService.ACTION_PAUSE) },
            resume = { CaptureService.send(this, CaptureService.ACTION_RESUME) },
            identify = { startActivity(ShazamBridge.launchIntent(this)) },
            identifyClip = ::identifyClip,
            openNotificationAccess = { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            shareSong = ::shareSong,
            shareText = ::shareText,
            openUrl = ::openUrl,
            coffee = coffee.state,
            buyCoffee = { coffee.buy(this, it) },
            coffeeThanksShown = coffee::thanksShown,
            celebrateHaptic = haptics::light,
        )

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            settings?.let { s ->
                BeatRadarTheme(s.theme) {
                    AppRoot(vm = vm, accountVm = accountVm, libraryVm = libraryVm, settings = s, actions = actions)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onDestroy() {
        coffee.close()
        super.onDestroy()
    }

    /**
     * `https://<share host>/s/<slug>` (App Links) opens that song in the Library; a playlist link shared from Spotify or
     * YouTube Music opens "BPM of a playlist".
     */
    private fun handleLink(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RECAP, false) == true) {
            vm.showRecap.value = true
            return
        }
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (PlayLinks.looksLikePlaylist(text)) {
                libraryVm.sharedPlaylistLink.value = text
            } else {
                Toast.makeText(this, "Share a Spotify or YouTube playlist to see its BPMs", Toast.LENGTH_LONG).show()
            }
            return
        }
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.host != BuildConfig.SHARE_HOST) return
        val segments = uri.pathSegments
        if (segments.size == 2 && segments[0] == "s") vm.openSong.value = segments[1]
        // A shared set: view it, and copy it into My sets.
        if (segments.size == 2 && segments[0] == "set") libraryVm.sharedSetLink.value = segments[1]
    }

    private fun shareSong(song: SongSummary) {
        val text = "${song.title} – ${song.artist} · ${song.bpm.roundToInt()} BPM on BeatRadar https://${BuildConfig.SHARE_HOST}/s/${song.slug}"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    /** Spotify / YouTube / Apple Music links open their app when installed (they claim these URLs), else the browser. */
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show() }
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

    companion object {
        /** From the Sunday notification: open the weekly recap. */
        const val EXTRA_OPEN_RECAP = "open_recap"
    }
}
