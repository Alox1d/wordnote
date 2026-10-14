package ru.alox1d.wordnote.huawei.device.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.alox1d.wordnote.huawei.domain.model.ModelTask

class AlarmHelper {
    private var context: Context? = null
    private var alarmManager: AlarmManager? = null
    fun init(context: Context) {
        this.context = context.applicationContext
        alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun setTaskAlarm(task: ModelTask) {
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("time_stamp", task.timeStamp)
        val pendingIntent = PendingIntent.getBroadcast(context!!.applicationContext,
                task.timeStamp.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager!!.set(AlarmManager.RTC_WAKEUP, task.date, pendingIntent)
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