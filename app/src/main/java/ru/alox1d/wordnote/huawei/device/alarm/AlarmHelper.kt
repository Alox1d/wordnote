package ru.alox1d.wordnote.huawei.device.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import ru.alox1d.wordnote.huawei.domain.model.ModelTask

class AlarmHelper {
    private var context: Context? = null
    private var alarmManager: AlarmManager? = null
    fun init(context: Context) {
        this.context = context
        alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun setTaskAlarm(task: ModelTask) {
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("title", task.title)
        intent.putExtra("time_stamp", task.timeStamp)
        intent.putExtra("color", task.priorityColor)
        val pendingIntent = PendingIntent.getBroadcast(context!!.applicationContext,
                task.timeStamp.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager!!.set(AlarmManager.RTC_WAKEUP, task.date, pendingIntent)
    }

    fun setBackupAlarm(date: Long) {

        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("title", "Backup Success")
        intent.putExtra("time_stamp", date)
        intent.putExtra("color", Color.BLACK)
        intent.putExtra("backup", true)
        val alarmIntent = PendingIntent.getBroadcast(context!!.applicationContext, date.toInt(), intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager!!.set(AlarmManager.RTC_WAKEUP, date, alarmIntent)
    }

    fun removeAlarm(timeStamp: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, timeStamp.toInt(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager!!.cancel(pendingIntent)
    }

    companion object {
        val instance: AlarmHelper = AlarmHelper()
    }
}