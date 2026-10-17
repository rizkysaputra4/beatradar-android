package com.rizky.beatradar.meta

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Optional (the user grants "Notification access"). Provides song titles from music apps'
 * media sessions and from Shazam / Pixel Now Playing notifications. Nothing leaves the phone.
 */
class NowPlayingListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val controllers = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { bind(it.orEmpty()) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = ComponentName(this, NowPlayingListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, handler)
            bind(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        controllers.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllers.clear()
        NowPlayingHub.track.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        ShazamBridge.parse(sbn)?.let { NowPlayingHub.recognitions.tryEmit(it) }
    }

    private fun bind(active: List<MediaController>) {
        val tokens = active.filter { it.packageName != packageName }.associateBy { it.sessionToken }
        controllers.keys.filter { it !in tokens }.forEach { token ->
            controllers.remove(token)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }
        for ((token, controller) in tokens) {
            if (token in controllers) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onSessionDestroyed() {
                    controllers.remove(token)?.let { (c, cb) -> c.unregisterCallback(cb) }
                    publish()
                }
            }
            controller.registerCallback(callback, handler)
            controllers[token] = controller to callback
        }
        publish()
    }

    private fun publish() {
        val all = controllers.values.map { it.first }
        val current = all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
        NowPlayingHub.track.value = current?.let { c ->
            val md = c.metadata
            TrackInfo(
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                packageName = c.packageName,
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
    }

    companion object {
        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
