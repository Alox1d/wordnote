package ru.alox1d.wordnote.huawei

import android.Manifest
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.robolectric.Shadows.shadowOf
import android.widget.Button
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModernAndroidSmokeTest {
    @Before fun noRemoteHuaweiServiceInJvm() {
        // Robolectric cannot bind a real HMS process; device smoke checks cover that environment.
        shadowOf(RuntimeEnvironment.getApplication()).setThrowInBindService(SecurityException("No remote HMS service in JVM tests"))
        // Each Robolectric method has a fresh Application; clear the process singleton from the preceding method.
        val context = RuntimeEnvironment.getApplication()
        TaskDatabase.getInstance(context).close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
    }

    @Test fun launcherOpensWithoutHuaweiAccount() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            assertNotNull(controller.get().findViewById<android.view.View>(R.id.pager))
            assertNotNull(controller.get().findViewById<android.view.View>(R.id.fab))
        }
    }

    @Test fun addingButtonStaysReachableWhenNoAdIsConfigured() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val decor = controller.get().window.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1800, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1000, 1800)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val root = controller.get().findViewById<android.view.View>(R.id.content_frame)
            val fab = controller.get().findViewById<android.view.View>(R.id.fab)
            val location = IntArray(2)
            fab.getLocationOnScreen(location)
            assertTrue("Adding a note must remain reachable below the toolbar", location[1] > root.height / 2)
            assertTrue("Adding control must stay inside visible content", location[1] + fab.height <= decor.height)
        }
    }

    @Test fun launcherKeepsControlsInsideSystemBarsAndKeyboard() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val root = controller.get().findViewById<android.view.View>(R.id.content_frame)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 220))
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, insets)
            assertEquals(28, root.paddingTop)
            assertEquals(220, root.paddingBottom)
        }
    }

    @Test fun launcherRequestsNotificationPermissionOnModernAndroid() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                shadowOf(controller.get()).lastRequestedPermission.requestedPermissions)
        }
    }

    @Test fun backupScreenBindsItsControlsWithoutStartingCloudLogin() {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertTrue(activity.findViewById<Button>(R.id.btn_hms_backup).isEnabled)
            assertTrue(activity.findViewById<Button>(R.id.btn_hms_restore).isEnabled)
            assertNotNull(activity.findViewById<Spinner>(R.id.spinner_backup).adapter)
        }
    }

    @Test fun sqliteKeepsNoteTextFormattingAndVisibilityAcrossReopen() {
        val context = RuntimeEnvironment.getApplication()
        val helper = TaskDatabase.getInstance(context)
        val repository = TaskRepository(helper.readableDatabase, helper.writableDatabase)
        val task = ModelTask("Тест заметки", "Текст", "<b>Текст</b>", 0L,
            ModelTask.PRIORITY_NORMAL, ModelTask.STATUS_CURRENT, 42L, ModelTask.HIDDEN_TRUE)
        repository.saveTask(task)
        helper.close()
        val reopened = TaskDatabase.getInstance(context)
        try {
            val saved = TaskRepository(reopened.readableDatabase, reopened.writableDatabase).getTask(42L)!!
            assertEquals(task.title, saved.title)
            assertEquals(task.descriptionHTML, saved.descriptionHTML)
            assertEquals(ModelTask.HIDDEN_TRUE, saved.isHidden)
            val repo = TaskRepository(reopened.readableDatabase, reopened.writableDatabase)
            repo.status(42L, ModelTask.STATUS_DONE)
            assertEquals(ModelTask.STATUS_DONE, repo.getTask(42L)!!.status)
            repo.removeTask(42L)
            assertNull(repo.getTask(42L))
        } finally {
            reopened.close()
            context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        }
    }
}
