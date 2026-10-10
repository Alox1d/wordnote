package ru.alox1d.wordnote.huawei.device.alarm

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.toothpick.annotation.ApplicationScope
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase.Companion.getInstance
import ru.alox1d.wordnote.huawei.di.module.MainModule
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.usecase.GetTasksUseCase
import ru.alox1d.wordnote.huawei.helpers.SELECTION_STATUS
import ru.alox1d.wordnote.huawei.helpers.TASK_DATE_COLUMN
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget
import ru.alox1d.wordnote.huawei.ui.widgets.updateAppWidget
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject


class AlarmSetter : BroadcastReceiver() {
    private val mGetTasksUseCase: GetTasksUseCase by inject()
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.e("Alarm", "Alarm set2")
//                appendLog("Alarm set2")
                setDI(context)
                val dbHelper = getInstance(context)
                AlarmHelper.instance.init(context)
                val alarmHelper = AlarmHelper.instance
                val tasks: MutableList<ModelTask> = ArrayList()
                tasks.addAll(mGetTasksUseCase.buildUseCase(SELECTION_STATUS + " OR "
                        + SELECTION_STATUS, arrayOf(Integer.toString(ModelTask.STATUS_CURRENT),
                        Integer.toString(ModelTask.STATUS_OVERDUE)), TASK_DATE_COLUMN))
                for (task in tasks) {
                    if (task.date != 0L) {
                        alarmHelper.setTaskAlarm(task)
                    }
                }
                PreferenceStorage.INSTANCE.init(context.applicationContext)
                val preferenceHelper = PreferenceStorage.INSTANCE
                val date = preferenceHelper.getLong(PreferenceStorage.BACKUP_INITIAL_DATE)
                if (date != -1L
                        && preferenceHelper.getLong(PreferenceStorage.BACKUP_INTERVAL) == BackupActivity.DAILY.toLong())
                    alarmHelper.setBackupAlarm(date)

                val widgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, ButtonsWidget::class.java)
                val appWidgetIds = widgetManager.getAppWidgetIds(widgetComponent)
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, widgetManager, appWidgetId)
                }
            }
        }
    }
    private fun setDI(context: Context) {
        KTP.openScopes(ApplicationScope::class.java, this)
            .installModules(
                MainModule(context),
            )
            .inject(this)
    }
}