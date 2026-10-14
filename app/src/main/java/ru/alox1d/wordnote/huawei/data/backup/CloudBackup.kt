package ru.alox1d.wordnote.huawei.data.backup

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget
import ru.alox1d.wordnote.huawei.ui.widgets.updateAppWidget
import java.io.IOException

class NoCloudBackupException : IOException("No cloud backup is available")

/** One snapshot/restore path for manual and scheduled backups. Drive errors must reach the caller. */
class CloudBackup(context: Context, private val drive: IDriveService) {
    private val context = context.applicationContext

    fun backup() = synchronized(transferLock) {
        // An unavailable cloud must not be mistaken for an empty cloud or create another backup.
        val existing = drive.queryFiles(BackupArchive.FILE_NAME)
        val snapshot = BackupArchive(context).create()
        try {
            if (existing == null) drive.uploadFile(snapshot) else drive.updateFile(snapshot, existing)
        } finally {
            // BackupArchive owns this unique private temporary directory.
            snapshot.parentFile?.deleteRecursively()
        }
    }

    fun restore(beforeCommit: () -> Unit = {}) = synchronized(transferLock) {
        val remote = drive.queryFiles(BackupArchive.FILE_NAME)
            ?: drive.queryFiles(TaskDatabase.DATABASE_NAME)
            ?: throw NoCloudBackupException()
        val downloaded = drive.downloadFile(remote) ?: throw IOException("Cloud download did not return a file")
        try {
            val database = TaskDatabase.getInstance(context)
            val repository = TaskRepository(database.readableDatabase, database.writableDatabase)
            val previous = repository.getTasks(null, null, null).map { it.timeStamp }
            // A corrupt ZIP is an error; legacy fallback is only for an absent ZIP.
            BackupArchive(context).restore(downloaded, beforeCommit)
            refreshRemindersAndWidgets(previous)
        } finally {
            downloaded.delete()
        }
    }

    companion object { private val transferLock = Any() }

    private fun refreshRemindersAndWidgets(previous: List<Long>) {
        // Restore has committed. OS permission/widget failures must not pretend it was rolled back.
        val alarms = AlarmHelper.instance
        alarms.init(context)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        previous.forEach { timestamp ->
            runCatching { alarms.removeAlarm(timestamp) }
            runCatching { notifications.cancel(timestamp.toInt()) }
        }
        runCatching {
            val database = TaskDatabase.getInstance(context)
            TaskRepository(database.readableDatabase, database.writableDatabase).getTasks(null, null, null)
        }.getOrDefault(emptyList()).filter {
            it.date > System.currentTimeMillis() &&
                (it.status == ModelTask.STATUS_CURRENT || it.status == ModelTask.STATUS_OVERDUE)
        }.forEach { task -> runCatching { alarms.setTaskAlarm(task) } }
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ButtonsWidget::class.java)).forEach { id ->
                updateAppWidget(context, manager, id)
            }
        }
    }
}
