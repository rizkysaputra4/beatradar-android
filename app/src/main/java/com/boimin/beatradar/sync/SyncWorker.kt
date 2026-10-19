package com.boimin.beatradar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boimin.beatradar.app
import java.util.concurrent.TimeUnit

/** Runs [LibrarySync] when there is a network, retrying with exponential backoff. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (applicationContext.app.sync.run()) Result.success() else Result.retry()

    companion object {
        private const val NAME = "library-sync"

        /** Asks for a sync soon. A newer request replaces a waiting or running one (uploads are idempotent). */
        fun request(context: Context) {
            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, work)
        }

        /**
         * One sync after an unnamed song has surely finished (its fingerprint is complete then, PLAN §5c). Kept, not
         * replaced, so songs logged in a row still get a run [delayMs] after the first.
         */
        fun requestLater(context: Context, delayMs: Long) {
            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$NAME-later", ExistingWorkPolicy.KEEP, work)
        }
    }
}
