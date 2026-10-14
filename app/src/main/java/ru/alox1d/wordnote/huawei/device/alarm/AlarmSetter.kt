package ru.alox1d.wordnote.huawei.device.alarm

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.service.drive.BackupScheduler
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget
import ru.alox1d.wordnote.huawei.ui.widgets.updateAppWidget

class AlarmSetter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        runCatching { BackupScheduler(context).restoreSchedule() }
        AlarmHelper.instance.init(context)
        runCatching {
            val database = TaskDatabase.getInstance(context)
            TaskRepository(database.readableDatabase, database.writableDatabase).getTasks(null, null, null)
        }.getOrDefault(emptyList()).filter {
            it.date > System.currentTimeMillis() &&
                (it.status == ModelTask.STATUS_CURRENT || it.status == ModelTask.STATUS_OVERDUE)
        }.forEach { task -> runCatching { AlarmHelper.instance.setTaskAlarm(task) } }
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ButtonsWidget::class.java)).forEach { id ->
                updateAppWidget(context, manager, id)
            }
        }
    }
}
