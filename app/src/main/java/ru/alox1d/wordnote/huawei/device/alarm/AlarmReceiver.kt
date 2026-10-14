package ru.alox1d.wordnote.huawei.device.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.service.drive.BackupScheduler
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("backup", false)) {
            BackupScheduler(context).enqueue()
            return
        }
        val timestamp = intent.getLongExtra("time_stamp", -1)
        val task = runCatching {
            val database = TaskDatabase.getInstance(context)
            TaskRepository(database.readableDatabase, database.writableDatabase).getTask(timestamp)
        }.getOrNull() ?: return
        if (task.status != ModelTask.STATUS_CURRENT && task.status != ModelTask.STATUS_OVERDUE) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val resultIntent = Intent(context, TasksActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("time_stamp", timestamp)
        val pendingIntent = PendingIntent.getActivity(context, timestamp.toInt(), resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val genericText = context.getString(R.string.reminder_private_text)
        val text = if (task.isHidden != ModelTask.HIDDEN_FALSE) genericText else task.title
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name)).setContentText(genericText)
            .setSmallIcon(R.drawable.ic_check_white_48dp).build()
        val color = task.priorityColor.takeIf { it != 0 } ?: R.color.priority_normal
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name)).setContentText(text)
            .setColor(ContextCompat.getColor(context, color)).setSmallIcon(R.drawable.ic_check_white_48dp)
            .setDefaults(Notification.DEFAULT_ALL).setAutoCancel(true).setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
        manager.notify(timestamp.toInt(), notification)
    }

    companion object { private const val CHANNEL_ID = "my_channel_01" }
}
