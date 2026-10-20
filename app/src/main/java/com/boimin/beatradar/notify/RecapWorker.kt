package com.boimin.beatradar.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boimin.beatradar.app
import com.boimin.beatradar.data.RecapCalc
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

/**
 * The Sunday evening "Your week in tempo" notification. Runs every few hours; tells once per week, from Sunday 18:00,
 * and only when the week had enough songs to be worth a card.
 */
class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now()
        if (!due(now)) return Result.success()
        val prefs = applicationContext.getSharedPreferences("recap", Context.MODE_PRIVATE)
        val week = "${now.get(IsoFields.WEEK_BASED_YEAR)}-${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        if (prefs.getString(KEY_SENT, null) == week) return Result.success()
        val recap = RecapCalc.week(applicationContext.app.database.beatLog().all(), System.currentTimeMillis())
        if (recap.worthSharing) Notifier(applicationContext).recap(recap)
        prefs.edit().putString(KEY_SENT, week).apply()
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-recap"
        private const val KEY_SENT = "sent_week"

        fun due(now: ZonedDateTime): Boolean = now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<RecapWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
