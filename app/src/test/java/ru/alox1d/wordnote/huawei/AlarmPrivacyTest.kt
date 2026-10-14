package ru.alox1d.wordnote.huawei

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.alarm.AlarmReceiver
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlarmPrivacyTest {
    private lateinit var context: Context
    private lateinit var database: TaskDatabase
    private lateinit var repository: TaskRepository
    private lateinit var notifications: NotificationManager

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        database = TaskDatabase.getInstance(context)
        repository = TaskRepository(database.readableDatabase, database.writableDatabase)
        notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WordNoteApp.activityPaused()
    }

    @After fun cleanUp() {
        WordNoteApp.activityPaused()
        notifications.cancelAll()
        database.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
    }

    @Test fun hiddenReminderDoesNotExposeNoteTitleEvenWhenUnlocked() {
        WordNoteApp.isHiddenNotesUnlocked = true
        for (hidden in listOf(1, 3)) {
            val task = note(hidden = hidden)
            repository.saveTask(task)
            AlarmReceiver().onReceive(context, reminder(task.timeStamp, task.title!!))
            val posted = shadowOf(notifications).getNotification(task.timeStamp.toInt())
            assertNotNull(posted)
            assertFalse(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("private artificial title"))
            assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
            repository.removeTask(task.timeStamp)
        }
    }

    @Test fun reminderReadsCurrentTitleInsteadOfSpoofedIntentTitle() {
        val task = note(hidden = 0)
        task.title = "Current artificial title"
        repository.saveTask(task)
        AlarmReceiver().onReceive(context, reminder(task.timeStamp, "Stale or injected title"))
        assertEquals(task.title, shadowOf(notifications).getNotification(task.timeStamp.toInt())
            .extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun missingAndCompletedNotesDoNotNotify() {
        AlarmReceiver().onReceive(context, reminder(500, "Deleted private title"))
        val task = note(hidden = 0)
        task.status = ModelTask.STATUS_DONE
        repository.saveTask(task)
        AlarmReceiver().onReceive(context, reminder(task.timeStamp, task.title!!))
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test fun reminderAlwaysOpensExplicitTasksActivityWhileAppIsVisible() {
        val task = note(hidden = 0)
        repository.saveTask(task)
        WordNoteApp.activityResumed()
        AlarmReceiver().onReceive(context, reminder(task.timeStamp, task.title!!))
        val pending = shadowOf(notifications).getNotification(task.timeStamp.toInt()).contentIntent
        val target = shadowOf(pending).savedIntent
        assertEquals(TasksActivity::class.java.name, target.component!!.className)
        assertEquals(task.timeStamp, target.getLongExtra("time_stamp", -1))
    }

    private fun note(hidden: Int) = ModelTask("private artificial title", "{\"nodes\":[]}", "", 0,
        ModelTask.PRIORITY_NORMAL, ModelTask.STATUS_CURRENT, 900L + hidden, hidden)

    private fun reminder(timestamp: Long, title: String) = Intent(context, AlarmReceiver::class.java)
        .putExtra("time_stamp", timestamp).putExtra("title", title).putExtra("color", R.color.priority_normal)
}
