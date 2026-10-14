package ru.alox1d.wordnote.huawei.device.service.drive

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.device.alarm.AlarmReceiver
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Persisted, network-constrained jobs on API 21+; one stable inexact alarm on API 19/20. */
class BackupScheduler(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    fun setInterval(interval: Int) {
        require(interval in 0..3)
        val previousInterval = interval()
        if (interval == 0) {
            // The running legacy service must observe Off before cancellation reaches its finally block.
            preferences.edit().putLong(PreferenceStorage.BACKUP_INTERVAL, 0L).remove(NEXT_DUE).apply()
            if (Build.VERSION.SDK_INT >= 21) {
                jobs().cancel(PERIODIC_JOB_ID)
                jobs().cancel(IMMEDIATE_JOB_ID)
            }
            cancelLegacyAlarm()
            context.stopService(Intent(context, BackupForegroundService::class.java))
        } else if (Build.VERSION.SDK_INT >= 21) {
            val info = job(PERIODIC_JOB_ID).setPeriodic(periodMillis(interval)).build()
            if (jobs().schedule(info) != JobScheduler.RESULT_SUCCESS) throw IOException("Backup scheduling failed")
            cancelLegacyAlarm()
            preferences.edit().remove(NEXT_DUE).apply()
        } else {
            cancelHistoricalAlarm()
            val retainedDue = if (previousInterval == interval) preferences.getLong(NEXT_DUE, -1L) else -1L
            scheduleLegacyAt(retainedDue.takeIf { it > 0 } ?: (System.currentTimeMillis() + periodMillis(interval)))
        }
        preferences.edit().putLong(PreferenceStorage.BACKUP_INTERVAL, interval.toLong()).apply()
    }

    fun restoreSchedule() {
        val interval = interval()
        if (interval == 0) { setInterval(0); return }
        if (Build.VERSION.SDK_INT >= 21) {
            val existing = jobs().allPendingJobs.firstOrNull { it.id == PERIODIC_JOB_ID }
            if (existing != null && existing.isPeriodic && existing.intervalMillis == periodMillis(interval) &&
                existing.networkType == JobInfo.NETWORK_TYPE_ANY && existing.isPersisted &&
                existing.service == ComponentName(context, BackupJobService::class.java)) {
                cancelLegacyAlarm()
                return
            }
            setInterval(interval)
        } else {
            cancelHistoricalAlarm()
            // Even an overdue alarm keeps its deadline; Android will deliver it promptly after restart.
            val due = preferences.getLong(NEXT_DUE, -1L).takeIf { it > 0 }
                ?: (System.currentTimeMillis() + periodMillis(interval))
            scheduleLegacyAt(due)
        }
    }

    fun enqueue() {
        if (!isEnabled()) return
        if (Build.VERSION.SDK_INT >= 21) {
            restoreSchedule()
            if (jobs().schedule(job(IMMEDIATE_JOB_ID).setMinimumLatency(0).build()) != JobScheduler.RESULT_SUCCESS) {
                throw IOException("Backup scheduling failed")
            }
        } else {
            // Arm the next occurrence before starting work, so process death cannot erase the schedule.
            scheduleLegacyAt(System.currentTimeMillis() + periodMillis(interval()))
            context.startService(Intent(context, BackupForegroundService::class.java))
        }
    }

    fun isEnabled() = interval() != 0

    internal fun afterLegacyAttempt(retry: Boolean) {
        if (!isEnabled()) return
        if (retry) scheduleLegacyAt(System.currentTimeMillis() + RETRY_MILLIS) else restoreSchedule()
    }

    private fun interval() = preferences.getLong(PreferenceStorage.BACKUP_INTERVAL, 0L).toInt().takeIf { it in 0..3 } ?: 0

    @RequiresApi(21)
    private fun jobs() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    @RequiresApi(21)
    private fun job(id: Int) = JobInfo.Builder(id, ComponentName(context, BackupJobService::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
        .setBackoffCriteria(RETRY_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)

    private fun scheduleLegacyAt(due: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.RTC_WAKEUP, due, legacyIntent())
        preferences.edit().putLong(NEXT_DUE, due).apply()
    }

    private fun legacyIntent() = PendingIntent.getBroadcast(context, LEGACY_REQUEST_ID,
        Intent(context, AlarmReceiver::class.java).setAction(LEGACY_ACTION).putExtra("backup", true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelLegacyAlarm() {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(legacyIntent())
        cancelHistoricalAlarm()
    }

    private fun cancelHistoricalAlarm() {
        val oldDate = preferences.getLong(PreferenceStorage.BACKUP_INITIAL_DATE, -1)
        if (oldDate != -1L) {
            val old = PendingIntent.getBroadcast(context, oldDate.toInt(), Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (old != null) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(old)
                old.cancel()
            }
            preferences.edit().remove(PreferenceStorage.BACKUP_INITIAL_DATE).apply()
        }
    }

    companion object {
        private const val PERIODIC_JOB_ID = 0x574e01
        private const val IMMEDIATE_JOB_ID = 0x574e02
        private const val LEGACY_REQUEST_ID = 0x574e03
        private const val LEGACY_ACTION = "ru.alox1d.wordnote.huawei.BACKUP"
        private const val NEXT_DUE = "backup_next_due"
        private val RETRY_MILLIS = TimeUnit.MINUTES.toMillis(30)
        private fun periodMillis(interval: Int) = TimeUnit.DAYS.toMillis(when (interval) { 1 -> 1; 2 -> 7; 3 -> 30; else -> 0 }.toLong())
    }
}
