package ru.alox1d.wordnote.huawei.device.alarm

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.WordNoteApp.Companion.isActivityVisible
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.device.service.drive.BackupForegroundService.Companion.startService
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")
        val timeStamp = intent.getLongExtra("time_stamp", 0)
        val color = intent.getIntExtra("color", 0)
        val isBackup = intent.getBooleanExtra("backup", false)
        if (isBackup) {
            initBackup(context, timeStamp)
            return
        }
        var resultIntent = Intent(context, TasksActivity::class.java)
        if (isActivityVisible) {
            resultIntent = intent
        }
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        resultIntent.putExtra("time_stamp", timeStamp)
        val pendingIntent = PendingIntent.getActivity(context, timeStamp.toInt(), resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val NOTIFICATION_CHANNEL_ID = "my_channel_01"
        val NOTIFICATION_CHANNEL_NAME: CharSequence = context.getString(R.string.notification_channel_name)
        val notificationManager = context
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance)
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.enableVibration(true)
            notificationChannel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(context.resources.getString(R.string.app_name))
        builder.setContentText(title)
        builder.color = context.resources.getColor(color)
        builder.setSmallIcon(R.drawable.ic_check_white_48dp)
        builder.setDefaults(Notification.DEFAULT_ALL)
        builder.setContentIntent(pendingIntent)
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(timeStamp.toInt(), notification)
        }
    }

    private fun initBackup(context: Context, timeStamp: Long) {
        PreferenceStorage.INSTANCE.init(context)
        val interval = PreferenceStorage.INSTANCE.getLong(PreferenceStorage.BACKUP_INTERVAL).toInt()
        AlarmHelper.instance.init(context)
        var thirtySecondsFromNow: Long = -1
        when (interval) {
            BackupActivity.DAILY -> {
                thirtySecondsFromNow = System.currentTimeMillis() + 24 * 60 * 60 * 1000
            }
            BackupActivity.WEEKLY -> { // TODO
            }
            BackupActivity.MONTHLY -> { // TODO
            }
        }
        AlarmHelper.instance.setBackupAlarm(thirtySecondsFromNow)

        startService(context, timeStamp)
        return
    }
}