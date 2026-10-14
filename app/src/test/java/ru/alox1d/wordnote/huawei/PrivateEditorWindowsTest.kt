package ru.alox1d.wordnote.huawei

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.toothpick.annotation.ApplicationScope
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
import org.robolectric.shadows.ShadowDialog
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.TaskModes
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import toothpick.config.Module
import toothpick.ktp.KTP

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateEditorWindowsTest {
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).setThrowInBindService(SecurityException("No remote service in synthetic audit"))
        PreferenceStorage.INSTANCE.init(context)
        PreferenceStorage.INSTANCE.putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, true)
        WordNoteApp.activityPaused()
    }

    @After fun cleanup() {
        val context = RuntimeEnvironment.getApplication()
        TaskDatabase.getInstance(context).close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        WordNoteApp.activityPaused()
    }

    @Test fun privateLinkInputMustProtectItsOwnWindow() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val parent = openPrivateDraft(controller.get()).second
            val child = openLinkInput(parent)
            assertTrue("Private editor itself is secure", secure(parent))
            assertTrue("Auxiliary private URL input lacks FLAG_SECURE", secure(child))
        }
    }

    @Test fun privateLinkInputMustBeMaskedAfterBackgroundAndReturn() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val parent = openPrivateDraft(controller.get()).second
            val child = openLinkInput(parent)
            assertTrue(child.isShowing)
            controller.pause().stop()
            assertFalse("Session must lock", WordNoteApp.isHiddenNotesUnlocked)
            assertFalse("Parent private editor masks its draft", parent.isShowing)
            controller.start().resume().visible()
            assertFalse("Parent remains masked before reauthentication", parent.isShowing)
            assertFalse("Auxiliary private URL input stays visible while the draft is locked", child.isShowing)
        }
    }

    @Test fun privateLinkDraftReappearsOnlyAfterUnlockWithItsValue() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val (fragment, parent) = openPrivateDraft(controller.get())
            val child = openLinkInput(parent)
            controller.pause().stop().start().resume().visible()
            assertFalse(parent.isShowing)
            assertFalse(child.isShowing)
            WordNoteApp.isHiddenNotesUnlocked = true
            assertTrue(fragment.refreshHiddenNotesVisibility())
            assertTrue(parent.isShowing)
            assertTrue(child.isShowing)
            assertEquals("https://example.invalid/synthetic-private-path",
                findEditText(child.window!!.decorView)!!.text.toString())
        }
    }

    @Test fun dismissingParentAlsoClosesAuxiliaryInput() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val (fragment, parent) = openPrivateDraft(controller.get())
            val child = openLinkInput(parent)
            fragment.dismiss()
            controller.get().supportFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse("A closed editor must not leave an input window behind", child.isShowing)
        }
    }

    @Test fun privateTimePickerUsesEditorProtectionAndLocksOnReturn() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val (_, parent) = openPrivateDraft(controller.get())
            parent.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilDialogTaskTime)!!.editText!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val child = requireNotNull(ShadowDialog.getLatestDialog())
            assertNotSame(parent, child)
            assertTrue("Private reminder time input must be secure", secure(child))
            controller.pause().stop().start().resume().visible()
            assertFalse("Reminder time input must lock with its private editor", child.isShowing)
        }
    }

    @Test fun privateDatePickerUsesEditorProtectionAndLocksOnReturn() {
        Robolectric.buildActivity(TasksActivity::class.java).setup().use { controller ->
            val (fragment, parent) = openPrivateDraft(controller.get())
            parent.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilDialogTaskDate)!!.editText!!.performClick()
            controller.get().supportFragmentManager.executePendingTransactions()
            fragment.childFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            val child = requireNotNull(ShadowDialog.getLatestDialog())
            assertNotSame(parent, child)
            assertTrue("Private reminder date input must be secure", secure(child))
            controller.pause().stop().start().resume().visible()
            assertFalse("Reminder date input must lock with its private editor", child.isShowing)
        }
    }

    private fun openPrivateDraft(activity: TasksActivity): Pair<AddingTaskDialogFragment, AlertDialog> {
        val fragment = AddingTaskDialogFragment.newInstance(TaskModes.NONE)
        KTP.openScopes(ApplicationScope::class.java, fragment).installTestModules(object : Module() {
            init { bind(IASRService::class.java).toInstance(object : IASRService {
                override fun setApiKey() = Unit
                override fun startASR() = Unit
                override fun checkPermissions() = false
            }) }
        })
        fragment.show(activity.supportFragmentManager, "SyntheticPrivateDraft")
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        val parent = (fragment.requireDialog() as AlertDialog).also {
            it.findViewById<SwitchMaterial>(R.id.switchHidden)!!.isChecked = true
        }
        return fragment to parent
    }

    private fun openLinkInput(parent: AlertDialog): android.app.Dialog {
        parent.findViewById<View>(R.id.action_insert_link)!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(ShadowDialog.getLatestDialog()).also {
            assertNotSame(parent, it)
            requireNotNull(findEditText(it.window!!.decorView)).setText("https://example.invalid/synthetic-private-path")
        }
    }

    private fun secure(dialog: android.app.Dialog) =
        dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun findEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findEditText(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
