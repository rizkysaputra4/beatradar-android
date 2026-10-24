package com.boimin.beatradar

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
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
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.meta.SongNamer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.boimin.beatradar.meta.ShazamBridge
import com.boimin.beatradar.ui.AppActions
import com.boimin.beatradar.ui.AppRoot
import com.boimin.beatradar.ui.MainViewModel
import com.boimin.beatradar.ui.theme.BeatRadarTheme
import com.boimin.beatradar.update.AppUpdate
import com.boimin.beatradar.update.UpdateAvailableDialog
import com.boimin.beatradar.update.UpdateNeed
import com.boimin.beatradar.update.UpdateRequiredScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val accountVm: AccountViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()
    private lateinit var coffee: CoffeeBilling
    private lateinit var haptics: Haptics

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            micPermission.value = true
            startListening()
        } else {
            micPermission.value = false
            Toast.makeText(this, str(R.string.main_activity_mic_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    /** Quick Setup's ask: the same permissions, without starting to listen (and no toast: the step says it). */
    private val setupPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        micPermission.value = grants[Manifest.permission.RECORD_AUDIO] == true
    }

    /** The mic permission: true granted, false refused here, null not asked yet (see [AppActions.micPermission]). */
    private val micPermission = MutableStateFlow<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        splash.setOnExitAnimationListener(::animateSplashExit)
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
            requestPermissions = ::requestPermissions,
            micPermission = micPermission,
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
            val update by AppUpdate.state.collectAsStateWithLifecycle()
            settings?.let { s ->
                BeatRadarTheme(s.theme) {
                    if (update.need == UpdateNeed.REQUIRED) {
                        UpdateRequiredScreen(update.message, onUpdate = ::openStore)
                    } else {
                        AppRoot(vm = vm, accountVm = accountVm, libraryVm = libraryVm, settings = s, actions = actions)
                        if (update.offerSuggestion) {
                            UpdateAvailableDialog(update.message, onUpdate = { AppUpdate.dismissSuggestion(); openStore() }, onLater = AppUpdate::dismissSuggestion)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Granted (or taken back) in the system settings meanwhile.
        if (hasMic()) micPermission.value = true else if (micPermission.value == true) micPermission.value = null
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
    /**
     * Lets the icon animation (API 31+) finish so the logo is never cut off half drawn, then the icon swells and fades as
     * the splash fades into the app. Below API 31 the icon is static and the start and duration are 0, so there is no wait.
     */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        val iconLeft = provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis - SystemClock.uptimeMillis()
        // The system may hand over a splash without an icon (e.g. opened from a link), and then iconView throws.
        val icon = runCatching { provider.iconView }.getOrNull()
        val animators = buildList {
            add(ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f))
            if (icon != null) {
                add(ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.25f))
                add(ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.25f))
            }
        }
        AnimatorSet().apply {
            playTogether(animators)
            startDelay = iconLeft.coerceIn(0L, SPLASH_MAX_WAIT_MS)
            duration = 260L
            interpolator = AccelerateInterpolator()
            doOnEnd { provider.remove() }
            start()
        }
    }

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
                Toast.makeText(this, str(R.string.main_activity_share_playlist_hint), Toast.LENGTH_LONG).show()
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
        val text = str(R.string.main_activity_share_song, song.title, song.artist, song.bpm.roundToInt(), "https://${BuildConfig.SHARE_HOST}/s/${song.slug}")
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
            .onFailure { Toast.makeText(this, str(R.string.main_activity_no_app_for_link), Toast.LENGTH_SHORT).show() }
    }

    /** BeatRadar's page in the Play Store app, or on the web without it. */
    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        runCatching { startActivity(market) }
            .onFailure { openUrl("https://play.google.com/store/apps/details?id=$packageName") }
    }

    /** Start: asks for the mic (and notifications) if needed, then starts listening. */
    private fun requestStart() {
        permissions.launch(neededPermissions())
    }

    /** Quick Setup: asks for the same permissions, once, without starting; the answer lands in [micPermission]. */
    private fun requestPermissions() {
        if (hasMic() && !needsNotificationPermission()) {
            micPermission.value = true
            return
        }
        setupPermissions.launch(neededPermissions())
    }

    private fun neededPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun hasMic(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun startListening() = CaptureService.start(this)

    /** Opens Shazam, then plays the saved clip through the speaker so Shazam can hear it. */
    private fun identifyClip(entry: BeatLogEntry) {
        val path = entry.clipPath ?: return
        SongNamer.startIdentify(entry.id)
        Toast.makeText(this, str(R.string.main_activity_playing_clip_for_shazam), Toast.LENGTH_LONG).show()
        startActivity(ShazamBridge.launchIntent(this))
        lifecycleScope.launch {
            delay(1_800)
            runCatching { ClipStore.play(path) }
        }
    }

    companion object {
        /** From the Sunday notification: open the weekly recap. */
        const val EXTRA_OPEN_RECAP = "open_recap"

        /** Longest the splash waits for its icon animation (700 ms, values-v31/themes.xml) before handing over. */
        private const val SPLASH_MAX_WAIT_MS = 700L
    }
}
