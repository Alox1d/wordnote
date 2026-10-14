package ru.alox1d.wordnote.huawei

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.toothpick.annotation.ApplicationScope
import com.github.irshulx.Editor
import com.google.android.material.textfield.TextInputLayout
import com.huawei.hms.mlplugin.asr.MLAsrCaptureConstants
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
import org.robolectric.util.ReflectionHelpers
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.model.TaskModes
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.domain.services.tr.HuaweiTextRecognitionService
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.EditTaskDialogFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.TaskDialog
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.TextRecognitionActivity
import toothpick.config.Module
import toothpick.ktp.KTP
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCompatibilityTest {
    @Before fun prepare() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).setThrowInBindService(SecurityException("No remote HMS in JVM tests"))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        PreferenceStorage.INSTANCE.init(app)
        PreferenceStorage.INSTANCE.putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, true)
    }
    @After fun cleanup() {
        val app = RuntimeEnvironment.getApplication()
        TaskDatabase.getInstance(app).close()
        app.deleteDatabase(TaskDatabase.DATABASE_NAME)
    }
    private fun open(state: Bundle? = null) = Robolectric.buildActivity(TasksActivity::class.java).also { c ->
        c.get().supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreAttached(fm: FragmentManager, f: Fragment, context: Context) {
                if (f is TaskDialog) KTP.openScopes(ApplicationScope::class.java, f).installTestModules(object : Module() {
                    init { bind(IASRService::class.java).toInstance(object : IASRService {
                        override fun setApiKey() = Unit
                        override fun startASR() = Unit
                        override fun checkPermissions() = true
                    }) }
                })
            }
        }, true)
        if (state == null) c.setup() else c.create(state).start().restoreInstanceState(state).resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun show(a: TasksActivity, f: TaskDialog): AlertDialog {
        f.show(a.supportFragmentManager, "compat-editor")
        a.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        return f.requireDialog() as AlertDialog
    }
    @Test fun cameraWorksWithoutObsoleteStoragePermission() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        open().use { c -> assertTrue(HuaweiTextRecognitionService(c.get()).checkPermissions()) }
    }
    @Test fun cameraGrantContinuesTheOriginalRecognitionRequest() {
        open().use { c ->
            val f = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            val d = show(c.get(), f)
            d.findViewById<android.view.View>(R.id.ib_taskTitleRecognizeImage)!!.performClick()
            f.onRequestPermissionsResult(2101, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
            val launched = shadowOf(c.get()).nextStartedActivity
            assertEquals(TextRecognitionActivity::class.java.name, launched?.component?.className)
        }
    }
    @Test fun addingDraftSurvivesRecreation() = checkDraftRestoration(false)
    @Test fun editedDraftSurvivesRecreation() = checkDraftRestoration(true)
    private fun checkDraftRestoration(editing: Boolean) {
        open().use { c ->
            val f = if (editing) EditTaskDialogFragment.newInstance(ModelTask("Original", "{\"nodes\":[]}", "", 0, 0, ModelTask.STATUS_CURRENT, 42, 0))
                else AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            val d = show(c.get(), f)
            d.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.setText("Unsaved title")
            val editor = d.findViewById<Editor>(R.id.editor_dialogDescription)!!
            editor.clearAllContents(); editor.render("<p>Unsaved first paragraph</p><p><b>Second paragraph</b></p>")
            val expectedContent = editor.contentAsSerialized
            val time = 2000000000000L
            ReflectionHelpers.getField<Calendar>(f, "calendar").timeInMillis = time
            d.findViewById<TextInputLayout>(R.id.tilDialogTaskDate)!!.editText!!.setText("2033-05-18")
            d.findViewById<TextInputLayout>(R.id.tilDialogTaskTime)!!.editText!!.setText("03:33")
            d.findViewById<Spinner>(R.id.spDialogTaskPriority)!!.setSelection(2)
            shadowOf(Looper.getMainLooper()).idle()
            val state = Bundle(); c.pause().saveInstanceState(state).stop()
            open(state).use { replacement ->
                val restored = replacement.get().supportFragmentManager.findFragmentByTag("compat-editor") as TaskDialog
                val rd = restored.requireDialog() as AlertDialog
                assertEquals("Unsaved title", rd.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.text.toString())
                assertEquals(expectedContent, rd.findViewById<Editor>(R.id.editor_dialogDescription)!!.contentAsSerialized)
                assertEquals(time, ReflectionHelpers.getField<Calendar>(restored, "calendar").timeInMillis)
                assertEquals("2033-05-18", rd.findViewById<TextInputLayout>(R.id.tilDialogTaskDate)!!.editText!!.text.toString())
                assertEquals("03:33", rd.findViewById<TextInputLayout>(R.id.tilDialogTaskTime)!!.editText!!.text.toString())
                assertEquals(2, rd.findViewById<Spinner>(R.id.spDialogTaskPriority)!!.selectedItemPosition)
            }
        }
    }
    @Test fun voiceResultReturnsToRestoredDraftWithoutOldActivityCallback() {
        open().use { c ->
            val f = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            val d = show(c.get(), f)
            d.findViewById<android.view.View>(R.id.ib_taskTitleRecognizeVoice)!!.performClick()
            val state = Bundle(); c.pause().saveInstanceState(state).stop()
            open(state).use { replacement ->
                TasksActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }.invoke(replacement.get(), 100, MLAsrCaptureConstants.ASR_SUCCESS,
                    Intent().putExtra(MLAsrCaptureConstants.ASR_RESULT, "Restored voice title"))
                val restored = replacement.get().supportFragmentManager.findFragmentByTag("compat-editor") as TaskDialog
                assertEquals("Restored voice title", restored.requireDialog().findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.text.toString())
            }
        }
    }
    @Test fun bodyVoiceRecognitionHandlesAnErasedDraftAsPlainText() = checkBodyRecognition(true)
    @Test fun bodyImageRecognitionHandlesAnErasedDraftAsPlainText() = checkBodyRecognition(false)
    private fun checkBodyRecognition(voice: Boolean) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        open().use { c ->
            val f = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
            val d = show(c.get(), f)
            d.findViewById<android.view.View>(R.id.action_erase)!!.performClick()
            d.findViewById<android.view.View>(if (voice) R.id.action_recognize_voice else R.id.action_recognize_image)!!.performClick()
            val recognized = "Recognized <tag> & text"
            if (voice) f.onActivityResult(100, MLAsrCaptureConstants.ASR_SUCCESS, Intent().putExtra(MLAsrCaptureConstants.ASR_RESULT, recognized))
            else f.onActivityResult(TextRecognitionActivity.REQUEST, android.app.Activity.RESULT_OK,
                Intent().putExtra(ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.BaseTextRecognitionActivity.ML_TEXT, recognized))
            fun text(view: android.view.View): String = when (view) {
                is android.widget.TextView -> view.text.toString()
                is android.view.ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
                else -> ""
            }
            assertTrue(text(d.findViewById<Editor>(R.id.editor_dialogDescription)!!).contains(recognized))
        }
    }

}
