package ru.alox1d.wordnote.huawei

import com.example.toothpick.annotation.ApplicationScope
import toothpick.ktp.KTP
import toothpick.config.Module
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.domain.model.TaskModes
import android.graphics.Bitmap
import android.view.WindowManager
import com.github.irshulx.Editor
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.data.storage.AttachmentReferences
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorPrivacyTest {
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).setThrowInBindService(SecurityException("No remote HMS service in JVM tests"))
        PreferenceStorage.INSTANCE.init(context)
        PreferenceStorage.INSTANCE.putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, true)
        WordNoteApp.isHiddenNotesUnlocked = false
    }
    @After fun cleanup() {
        val context = RuntimeEnvironment.getApplication()
        TaskDatabase.getInstance(context).close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        File(context.filesDir, "attachments").deleteRecursively()
        WordNoteApp.isHiddenNotesUnlocked = false
    }

    @Test fun editorSavesImageOnlyToPrivateStorage() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val fragment = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            KTP.openScopes(ApplicationScope::class.java, fragment).installTestModules(object : Module() {
                init { bind(IASRService::class.java).toInstance(object : IASRService {
                    override fun setApiKey() {}
                    override fun startASR() {}
                    override fun checkPermissions() = false
                }) }
            })
            fragment.show(controller.get().supportFragmentManager, "AddingTaskDialogFragment")
            controller.get().supportFragmentManager.executePendingTransactions()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val dialog = fragment.requireDialog()
            dialog.findViewById<SwitchMaterial>(R.id.switchHidden).isChecked = true
            val editor = dialog.findViewById<Editor>(R.id.editor_dialogDescription)
            editor.insertImage(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
            val privateImages = File(controller.get().filesDir, "attachments").walkTopDown().filter { it.isFile }.toList()
            assertEquals(1, privateImages.size)
            val content = ModelTask("Synthetic", editor.contentAsSerialized, editor.contentAsHTML, 0, 1, 1, 42, 1)
            assertTrue(AttachmentReferences.paths(content).all { File(it).canonicalPath.startsWith(File(controller.get().filesDir, "attachments").canonicalPath + File.separator) })
        }
    }

    @Test fun privateEditorProtectsItsOwnDialogWindow() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val fragment = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            KTP.openScopes(ApplicationScope::class.java, fragment).installTestModules(object : Module() {
                init { bind(IASRService::class.java).toInstance(object : IASRService {
                    override fun setApiKey() {}
                    override fun startASR() {}
                    override fun checkPermissions() = false
                }) }
            })
            fragment.show(controller.get().supportFragmentManager, "AddingTaskDialogFragment")
            controller.get().supportFragmentManager.executePendingTransactions()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            fragment.requireDialog().findViewById<SwitchMaterial>(R.id.switchHidden).isChecked = true
            assertTrue(fragment.requireDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
}
