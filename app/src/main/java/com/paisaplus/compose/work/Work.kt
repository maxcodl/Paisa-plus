package com.paisaplus.compose.work

import com.paisaplus.compose.str

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paisaplus.compose.MainActivity
import com.paisaplus.compose.R
import com.paisaplus.compose.Routes
import com.paisaplus.compose.data.AutoPoster
import com.paisaplus.compose.data.BackupManager
import com.paisaplus.compose.data.PaisaDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Posts due loan installments + recurring occurrences, then reschedules reminders. */
@HiltWorker
class AutoPostWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val poster: AutoPoster,
    private val db: PaisaDatabase,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (poster.run() > 0) com.paisaplus.compose.widget.BalanceWidget.refresh(applicationContext)
        Reminders.reschedule(applicationContext, db)
        return Result.success()
    }
}

/** Daily copy into the user-picked folder; retries later if the folder is unavailable. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupManager,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        runCatching { backup.autoBackup() }.fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
}

/** recurring_service.dart reschedule: one pending notification per remind rule, 9:00 on its next date. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Reminders.show(applicationContext, inputData.getLong("id", 0), inputData.getString("name").orEmpty(), inputData.getString("amount").orEmpty())
        return Result.success()
    }
}

object AutoPost {
    /** Daily job plus a run now (app start). KEEP: a run already queued/running isn't duplicated. */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork("autopost-daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoPostWorker>(1, TimeUnit.DAYS).build())
        runNow(ctx)
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork("autopost-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoPostWorker>().build())
    }
}

object AutoBackup {
    /** Daily; the worker is a no-op until a folder is chosen. */
    fun schedule(ctx: Context) = WorkManager.getInstance(ctx)
        .enqueueUniquePeriodicWork("autobackup-daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build())

    fun runNow(ctx: Context) = WorkManager.getInstance(ctx)
        .enqueueUniqueWork("autobackup-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
}

object Reminders {
    private const val TAG = "remind"
    private const val CHANNEL = "recurring"

    suspend fun reschedule(ctx: Context, db: PaisaDatabase) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(TAG)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        db.recurring().list().filter { it.remind }.forEach { r ->
            val at = Instant.ofEpochMilli(r.next).atZone(zone).toLocalDate().atTime(9, 0).atZone(zone).toInstant()
            if (at.isBefore(now)) return@forEach
            wm.enqueueUniqueWork(
                "remind-${r.id}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .addTag(TAG)
                    .setInitialDelay(Duration.between(now, at))
                    .setInputData(workDataOf("id" to r.id, "name" to r.name, "amount" to r.amount.toString()))
                    .build(),
            )
        }
    }

    fun show(ctx: Context, id: Long, name: String, amount: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.recurring_transactions), NotificationManager.IMPORTANCE_HIGH))
        nm.notify(
            id.toInt(),
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_paisa)
                .setContentTitle(str(R.string.upcoming_x, name))
                .setContentText(str(R.string.x_due_today, amount))
                .setContentIntent(PendingIntent.getActivity(
                    ctx, id.toInt(),
                    Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, Routes.RECURRING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
                .setAutoCancel(true)
                .build(),
        )
    }
}

