package com.boimin.beatradar.account

import android.content.Context
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.net.IntegrityRequest
import com.boimin.beatradar.net.IntegrityResult
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Play Integrity (PLAN §7b): only installs that pass earn beats. Strictly best-effort: without Play services
 * (emulators, custom ROMs) or before the Cloud project is set up, it quietly does nothing and is tried again later.
 */
object IntegrityCheck {
    private const val PREFS = "integrity"
    private const val KEY_DONE_FOR = "done_for_install"

    suspend fun runOnce(context: Context, account: AccountRepository) {
        if (BuildConfig.CLOUD_PROJECT_NUMBER == 0L) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val install = account.installId
        if (prefs.getString(KEY_DONE_FOR, null) == install) return
        if (!account.ensureInstall()) return
        val hash = account.integrityRequestHash ?: return
        val token = withTimeoutOrNull(20_000) { requestToken(context, hash) } ?: return
        val result = runCatching {
            account.api.post<IntegrityRequest, IntegrityResult>("v1/installs/$install/integrity", IntegrityRequest(token))
        }.getOrNull() ?: return
        // Answered either way: a failed verdict is final for this install, no point asking again.
        prefs.edit().putString(KEY_DONE_FOR, install).apply()
        if (!result.integrityOk) android.util.Log.i("BeatRadar", "Play Integrity: this install doesn't earn beats")
    }

    private suspend fun requestToken(context: Context, requestHash: String): String? = runCatching {
        val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
        val provider = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT_NUMBER).build(),
        ).await() ?: return null
        provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).await()?.token()
    }.getOrNull()

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }
}
