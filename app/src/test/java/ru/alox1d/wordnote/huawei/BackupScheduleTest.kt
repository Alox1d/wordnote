package ru.alox1d.wordnote.huawei

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.alarm.AlarmReceiver
import ru.alox1d.wordnote.huawei.device.alarm.AlarmSetter
import ru.alox1d.wordnote.huawei.device.service.drive.BackupScheduler
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupScheduleTest {
    private lateinit var context: Context
    private lateinit var jobs: JobScheduler

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        PreferenceStorage.INSTANCE.init(context)
        jobs = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobs.cancelAll()
    }
    @After fun cleanUp() {
        jobs.cancelAll()
        TaskDatabase.getInstance(context).close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
    }

    @Test fun everyIntervalSchedulesPersistedNetworkJobAndOffCancelsIt() {
        val scheduler = BackupScheduler(context)
        for ((interval, milliseconds) in listOf(1 to 86_400_000L, 2 to 604_800_000L, 3 to 2_592_000_000L)) {
            scheduler.setInterval(interval)
            val job = jobs.allPendingJobs.single { it.isPeriodic }
            assertEquals(milliseconds, job.intervalMillis)
            assertTrue(job.isPersisted)
            assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        }
        scheduler.setInterval(BackupActivity.OFF)
        assertTrue(jobs.allPendingJobs.isEmpty())
        assertEquals(0, PreferenceStorage.INSTANCE.getLong(PreferenceStorage.BACKUP_INTERVAL))
    }

    @Test fun bootRestoresWeeklyAndMonthlySchedules() {
        for (interval in listOf(2L, 3L)) {
            jobs.cancelAll()
            PreferenceStorage.INSTANCE.putLong(PreferenceStorage.BACKUP_INTERVAL, interval)
            AlarmSetter().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
            assertTrue(jobs.allPendingJobs.single().isPeriodic)
        }
    }

    @Test fun legacyBackupBroadcastEnqueuesWorkWithoutStartingForegroundServiceOnModernAndroid() {
        BackupScheduler(context).setInterval(BackupActivity.DAILY)
        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java).putExtra("backup", true))
        assertTrue(jobs.allPendingJobs.any { !it.isPeriodic && it.networkType == JobInfo.NETWORK_TYPE_ANY })
        assertNull(org.robolectric.Shadows.shadowOf(context as Application).nextStartedService)
    }

    @Test fun appRestartKeepsExistingPeriodicJobInsteadOfResettingItsWindow() {
        val scheduler = BackupScheduler(context)
        scheduler.setInterval(BackupActivity.DAILY)
        val original = jobs.allPendingJobs.single()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(3))
        BackupScheduler(context).restoreSchedule()
        assertSame("An app restart must retain the already scheduled job", original, jobs.allPendingJobs.single())
    }

    @Test fun legacyAppRestartKeepsTheExistingDueDateAfterTimeAdvances() {
        // Exercise our API19/20 compatibility branch using the sandbox's existing alarm service.
        // This does not substitute for an actual API19 device validation.
        val originalSdk = android.os.Build.VERSION.SDK_INT
        try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 19)
            val scheduler = BackupScheduler(context)
            scheduler.setInterval(BackupActivity.DAILY)
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val firstDue = org.robolectric.Shadows.shadowOf(alarms).nextScheduledAlarm!!.triggerAtTime
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(3))
            BackupScheduler(context).restoreSchedule()
            assertEquals(firstDue, org.robolectric.Shadows.shadowOf(alarms).nextScheduledAlarm!!.triggerAtTime)
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", originalSdk)
        }
    }

    @Test fun staleBackupAlarmDoesNothingAfterOff() {
        BackupScheduler(context).setInterval(BackupActivity.OFF)
        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java).putExtra("backup", true))
        assertTrue(jobs.allPendingJobs.isEmpty())
        assertNull(org.robolectric.Shadows.shadowOf(context as Application).nextStartedService)
    }
}
