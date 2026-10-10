package ru.alox1d.wordnote.huawei.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_ADD
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_RECOGNIZE_CAMERA
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_RECOGNIZE_VOICE


class ButtonsWidget : AppWidgetProvider() {

    companion object {
        const val WIDGET_REQUEST_CODE = "WIDGET_REQUEST_CODE_ADD"
        const val WIDGET_REQUEST_CODE_ADD = 1
        const val WIDGET_REQUEST_CODE_RECOGNIZE_VOICE = 2
        const val WIDGET_REQUEST_CODE_RECOGNIZE_CAMERA = 3
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }


    override fun onReceive(context: Context?, intent: Intent?) {
        val widgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context!!, ButtonsWidget::class.java)
        val appWidgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, widgetManager, appWidgetId)
        }
        super.onReceive(context, intent)
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // Utils.appendLog("updateAppWidget started")  TODO Удобное сохранение логов при дебаге в Android 10,11,12?
    val views = RemoteViews(context.packageName, R.layout.widget_buttons)
    val i = Intent(context, TasksActivity::class.java)
    i.putExtra(WIDGET_REQUEST_CODE, WIDGET_REQUEST_CODE_ADD)
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    i.setAction("WIDGET_ADD");

    val pendingIntent1 = PendingIntent.getActivity(
        context,
        1,
        i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_add, pendingIntent1)
    val i2 = Intent(context, TasksActivity::class.java)
    i2.putExtra(WIDGET_REQUEST_CODE, WIDGET_REQUEST_CODE_RECOGNIZE_VOICE)
    i2.setAction("WIDGET_VOICE");
    i2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    val pendingIntent2 = PendingIntent.getActivity(
        context,
        2,
        i2,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_recognize_voice, pendingIntent2)

    val i3 = Intent(context, TasksActivity::class.java)

    i3.putExtra(WIDGET_REQUEST_CODE, WIDGET_REQUEST_CODE_RECOGNIZE_CAMERA)
    i3.setAction("WIDGET_CAMERA");
    i3.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    val pendingIntent3 = PendingIntent.getActivity(
        context,
        3,
        i3,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_recognize_image, pendingIntent3)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
    // Utils.appendLog("updateAppWidget ended")
}
