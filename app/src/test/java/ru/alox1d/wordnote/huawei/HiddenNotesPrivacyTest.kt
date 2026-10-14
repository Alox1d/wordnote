package ru.alox1d.wordnote.huawei

import android.view.WindowManager
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
import androidx.appcompat.app.AlertDialog
import android.content.DialogInterface
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.irshulx.Editor
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.CurrentTaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.TaskDialog
import androidx.appcompat.widget.PopupMenu
import com.example.toothpick.annotation.ApplicationScope
import java.util.IdentityHashMap
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.EditTaskDialogFragment
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnCallback
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnPrompt
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.service.fido.IFIDOService
import ru.alox1d.wordnote.huawei.domain.services.fido.HuaweiFIDOService
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TaskFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import toothpick.config.Module
import toothpick.ktp.KTP

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiddenNotesPrivacyTest {
    private lateinit var database: TaskDatabase
    private lateinit var repository: TaskRepository
    private val authenticationByActivity = IdentityHashMap<TasksActivity, ControlledAuthentication>()

    @Before fun prepareSyntheticNotes() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).setThrowInBindService(SecurityException("No remote HMS service in JVM tests"))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WordNoteApp.isHiddenNotesUnlocked = false
        PreferenceStorage.INSTANCE.init(context)
        PreferenceStorage.INSTANCE.putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, true)
        database = TaskDatabase.getInstance(context)
        database.writableDatabase.delete(TaskDatabase.TASKS_TABLE, null, null)
        repository = TaskRepository(database.readableDatabase, database.writableDatabase)
        repository.saveTask(note(1, "match public", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_FALSE))
        repository.saveTask(note(2, "match private", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_TRUE))
        repository.saveTask(note(3, "other private", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_TRUE))
        repository.saveTask(note(4, "match completed", ModelTask.STATUS_DONE, ModelTask.HIDDEN_FALSE))
        repository.saveTask(note(5, "match completed private", ModelTask.STATUS_DONE, ModelTask.HIDDEN_TRUE))
        // Old/imported data may contain the transient UI value. It grants no authorization.
        repository.saveTask(note(6, "legacy private", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_TRUE_UNLOCKED))
        repository.saveTask(note(7, "legacy completed private", ModelTask.STATUS_DONE, ModelTask.HIDDEN_TRUE_UNLOCKED))
    }

    @After fun cleanSyntheticNotes() {
        WordNoteApp.isHiddenNotesUnlocked = false
        database.close()
        RuntimeEnvironment.getApplication().deleteDatabase(TaskDatabase.DATABASE_NAME)
    }

    @Test fun failedAuthenticationKeepsHiddenNotesLocked() {
        openActivity().use { controller ->
            val auth = installAuthentication(controller.get())
            toggleHidden(controller.get())
            auth.finish(false)
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
            assertEquals(listOf(1L), ids(controller.get().currentTaskFragment))
        }
    }

    @Test fun cancelledAuthenticationDoesNotRevealNotes() {
        openActivity().use { controller ->
            val auth = installAuthentication(controller.get())
            toggleHidden(controller.get())
            // The external prompt reports cancellation as an unsuccessful terminal result.
            auth.finish(false)
            assertEquals(listOf(1L), ids(controller.get().currentTaskFragment))
            assertEquals(listOf(4L), ids(controller.get().doneTaskFragment))
        }
    }

    @Test fun lockedCurrentListRejectsLegacyUnlockedFlag() {
        openActivity().use { controller ->
            assertEquals(listOf(1L), ids(controller.get().currentTaskFragment))
        }
    }

    @Test fun lockedCompletedListHidesPrivateNotesOnLoadAndSearch() {
        openActivity().use { controller ->
            val done = controller.get().doneTaskFragment
            assertEquals(listOf(4L), ids(done))
            done.findTasks("private")
            assertTrue(ids(done).isEmpty())
            done.findTasks("match")
            assertEquals(listOf(4L), ids(done))
        }
    }

    @Test fun lockedCurrentSearchDoesNotRevealPrivateNotes() {
        openActivity().use { controller ->
            val current = controller.get().currentTaskFragment
            current.findTasks("private")
            assertTrue(ids(current).isEmpty())
            current.findTasks("match")
            assertEquals(listOf(1L), ids(current))
        }
    }

    @Test fun successfulAuthenticationRevealsBothListsAndRelockingHidesBoth() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            assertTrue(WordNoteApp.isHiddenNotesUnlocked)
            assertEquals(listOf(1L, 2L, 3L, 6L), ids(activity.currentTaskFragment).sorted())
            assertEquals(listOf(4L, 5L, 7L), ids(activity.doneTaskFragment).sorted())
            toggleHidden(activity)
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
            assertEquals(listOf(1L), ids(activity.currentTaskFragment))
            assertEquals(listOf(4L), ids(activity.doneTaskFragment))
        }
    }

    @Test fun unlockAfterRepeatedReloadDoesNotDuplicatePrivateNotes() {
        openActivity().use { controller ->
            val activity = controller.get()
            activity.currentTaskFragment.addTaskFromDB()
            activity.currentTaskFragment.addTaskFromDB()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            assertEquals(listOf(1L, 2L, 3L, 6L), ids(activity.currentTaskFragment).sorted())
        }
    }

    @Test fun unlockAndRelockPreserveTheActiveSearchInBothLists() {
        openActivity().use { controller ->
            val activity = controller.get()
            activity.currentTaskFragment.findTasks("match")
            activity.doneTaskFragment.findTasks("match")
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            assertEquals(listOf(1L, 2L), ids(activity.currentTaskFragment).sorted())
            assertEquals(listOf(4L, 5L), ids(activity.doneTaskFragment).sorted())
            toggleHidden(activity)
            assertEquals(listOf(1L), ids(activity.currentTaskFragment))
            assertEquals(listOf(4L), ids(activity.doneTaskFragment))
        }
    }

    @Test fun backgroundingLocksBothListsBeforeReturning() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            controller.pause().stop()
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
            assertEquals(listOf(1L), ids(activity.currentTaskFragment))
            assertEquals(listOf(4L), ids(activity.doneTaskFragment))
            controller.start().resume().visible()
            assertEquals(listOf(1L), ids(activity.currentTaskFragment))
            assertEquals(listOf(4L), ids(activity.doneTaskFragment))
        }
    }

    @Test fun successfulAuthenticationCanReturnThroughPauseResume() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            controller.pause().stop()
            auth.finish(true)
            // A device-credential screen can pause the activity; do not expose data behind it.
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
            controller.start().resume().visible()
            assertTrue(WordNoteApp.isHiddenNotesUnlocked)
            assertEquals(listOf(1L, 2L, 3L, 6L), ids(activity.currentTaskFragment).sorted())
        }
    }

    @Test fun recreatingActivityLocksAnUnlockedSession() {
        openActivity().use { controller ->
            val auth = installAuthentication(controller.get())
            toggleHidden(controller.get())
            auth.finish(true)
            controller.recreate()
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
        }
    }

    @Test fun callbackFromDestroyedActivityCannotUnlockReplacement() {
        openActivity().use { controller ->
            val auth = installAuthentication(controller.get())
            toggleHidden(controller.get())
            controller.recreate()
            auth.finish(true)
            assertFalse(WordNoteApp.isHiddenNotesUnlocked)
        }
    }

    @Test
    fun biometricCancellationReportsFailureToTheCaller() {
        openActivity().use { controller ->
            val service = HuaweiFIDOService(controller.get())
            val results = mutableListOf<Boolean>()
            val callback = pendingBiometricCallback(service) { results.add(it) }
            callback.onAuthError(BioAuthnPrompt.ERROR_USER_CANCELED, "cancelled")
            assertEquals(listOf(false), results)
        }
    }

    @Test
    fun terminalBiometricErrorCompletesTheRequestOnlyOnce() {
        openActivity().use { controller ->
            val service = HuaweiFIDOService(controller.get())
            val results = mutableListOf<Boolean>()
            val callback = pendingBiometricCallback(service) { results.add(it) }
            callback.onAuthError(BioAuthnPrompt.ERROR_HW_UNAVAILABLE, "unavailable")
            callback.onAuthError(BioAuthnPrompt.ERROR_CANCELED, "cancelled")
            assertEquals(listOf(false), results)
        }
    }

    @Test fun unlockingProtectsSensitiveWindowThroughRelocking() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            controller.pause().stop()
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    @Test fun privateEditorRetainsItsDraftBehindLockUntilReauthenticated() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            activity.currentTaskFragment.showTaskEditDialog(repository.getTask(2L))
            activity.supportFragmentManager.executePendingTransactions()
            val editor = activity.supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
            val title = editor.requireDialog().findViewById<TextInputLayout>(R.id.til_DialogTaskTitle).editText!!
            title.setText("unsaved synthetic draft")
            assertTrue(editor.requireDialog().isShowing)
            controller.pause().stop()
            assertFalse(editor.requireDialog().isShowing)
            controller.start().resume().visible()
            assertFalse(editor.requireDialog().isShowing)
            // Opening another note recovers the retained draft after the ordinary auth.
            activity.currentTaskFragment.showTaskEditDialog(repository.getTask(1L))
            activity.supportFragmentManager.executePendingTransactions()
            assertEquals(1, activity.supportFragmentManager.fragments.filterIsInstance<EditTaskDialogFragment>().size)
            auth.finish(true)
            assertTrue(editor.requireDialog().isShowing)
            assertEquals("unsaved synthetic draft", title.text.toString())
        }
    }

    @Test fun newlyPrivateDraftRemainsSensitiveAfterUncheckingHidden() {
        openActivity().use { controller ->
            val activity = controller.get()
            val draft = AddingTaskDialogFragment.newInstance(ru.alox1d.wordnote.huawei.domain.model.TaskModes.NONE)
            draft.show(activity.supportFragmentManager, "AddingTaskDialogFragment")
            activity.supportFragmentManager.executePendingTransactions()
            val hidden = draft.requireDialog().findViewById<SwitchMaterial>(R.id.switchHidden)
            hidden.isChecked = true
            hidden.isChecked = false
            assertTrue(draft.requireDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            controller.pause().stop()
            assertFalse(draft.requireDialog().isShowing)
            controller.start().resume().visible()
            assertFalse(draft.requireDialog().isShowing)
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            assertTrue(draft.requireDialog().isShowing)
        }
    }

    @Test fun restoredPrivateEditorStaysHiddenUntilAuthentication() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            activity.currentTaskFragment.showTaskEditDialog(repository.getTask(2L))
            activity.supportFragmentManager.executePendingTransactions()
            val state = Bundle()
            controller.pause().saveInstanceState(state).stop()
            openActivity(state).use { replacement ->
                val restored = replacement.get().supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
                assertFalse(WordNoteApp.isHiddenNotesUnlocked)
                assertFalse(restored.requireDialog().isShowing)
                assertTrue(restored.requireDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertSame(replacement.get().supportFragmentManager.fragments.first { it is ru.alox1d.wordnote.huawei.ui.scenes.tasks.CurrentTaskFragment }, replacement.get().currentTaskFragment)
            }
        }
    }

    @Test fun lockedListsRemoveAlreadyPublishedPrivateNotificationText() {
        val notifications = RuntimeEnvironment.getApplication()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel("synthetic", "Synthetic", NotificationManager.IMPORTANCE_DEFAULT))
        notifications.notify(2, Notification.Builder(RuntimeEnvironment.getApplication(), "synthetic")
            .setContentTitle("Previously public synthetic title").setSmallIcon(R.drawable.ic_check_white_48dp).build())
        assertNotNull(shadowOf(notifications).getNotification(2))
        openActivity().use {
            assertNull(shadowOf(notifications).getNotification(2))
        }
    }

    @Test fun makingAnExistingNotePrivateRemovesItsPublishedNotification() {
        openActivity().use { controller ->
            val activity = controller.get()
            val notifications = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifications.createNotificationChannel(NotificationChannel("synthetic", "Synthetic", NotificationManager.IMPORTANCE_DEFAULT))
            notifications.notify(1, Notification.Builder(activity, "synthetic")
                .setContentTitle("Previously public synthetic title").setSmallIcon(R.drawable.ic_check_white_48dp).build())
            activity.currentTaskFragment.showTaskEditDialog(repository.getTask(1L))
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
            val dialog = fragment.requireDialog() as AlertDialog
            dialog.findViewById<SwitchMaterial>(R.id.switchHidden)!!.isChecked = true
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ModelTask.HIDDEN_TRUE, repository.getTask(1L)!!.isHidden)
            assertNull(shadowOf(notifications).getNotification(1))
        }
    }

    @Test fun restoredPrivateEditorSavesAfterReauthentication() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            activity.currentTaskFragment.showTaskEditDialog(repository.getTask(2L))
            activity.supportFragmentManager.executePendingTransactions()
            val state = Bundle()
            controller.pause().saveInstanceState(state).stop()
            openActivity(state).use { replacement ->
                val restoredActivity = replacement.get()
                val restoredAuth = installAuthentication(restoredActivity)
                toggleHidden(restoredActivity)
                restoredAuth.finish(true)
                val fragment = restoredActivity.supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                dialog.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.setText("Saved restored synthetic edit")
                shadowOf(Looper.getMainLooper()).idle()
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Saved restored synthetic edit", repository.getTask(2L)!!.title)
                assertEquals(ModelTask.HIDDEN_TRUE, repository.getTask(2L)!!.isHidden)
            }
        }
    }

    @Test fun restoredPrivateAddingDraftPersistsExactlyOnce() {
        openActivity().use { controller ->
            val activity = controller.get()
            activity.findViewById<android.view.View>(R.id.fab).performClick()
            activity.supportFragmentManager.executePendingTransactions()
            val adding = activity.supportFragmentManager.findFragmentByTag("AddingTaskDialogFragment") as AddingTaskDialogFragment
            adding.requireDialog().findViewById<SwitchMaterial>(R.id.switchHidden).isChecked = true
            val state = Bundle()
            controller.pause().saveInstanceState(state).stop()
            openActivity(state).use { replacement ->
                val restoredActivity = replacement.get()
                val auth = installAuthentication(restoredActivity)
                toggleHidden(restoredActivity)
                auth.finish(true)
                val fragment = restoredActivity.supportFragmentManager.findFragmentByTag("AddingTaskDialogFragment") as AddingTaskDialogFragment
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                dialog.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.setText("Saved restored synthetic draft")
                dialog.findViewById<SwitchMaterial>(R.id.switchHidden)!!.isChecked = true
                shadowOf(Looper.getMainLooper()).idle()
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val saved = repository.getTasks(null, null, null).filter { it.title == "Saved restored synthetic draft" }
                assertEquals(1, saved.size)
                assertEquals(ModelTask.HIDDEN_TRUE, saved.single().isHidden)
            }
        }
    }

    @Test fun completedPrivateNoteKeepsItsStatusWhenEditedAndRestored() {
        openActivity().use { controller ->
            val activity = controller.get()
            val auth = installAuthentication(activity)
            toggleHidden(activity)
            auth.finish(true)
            activity.doneTaskFragment.showTaskEditDialog(repository.getTask(5L))
            activity.supportFragmentManager.executePendingTransactions()
            val first = activity.supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
            val firstDialog = first.requireDialog() as AlertDialog
            firstDialog.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.setText("Edited completed synthetic note")
            shadowOf(Looper.getMainLooper()).idle()
            firstDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ModelTask.STATUS_DONE, repository.getTask(5L)!!.status)
            activity.doneTaskFragment.showTaskEditDialog(repository.getTask(5L))
            activity.supportFragmentManager.executePendingTransactions()
            val state = Bundle()
            controller.pause().saveInstanceState(state).stop()
            openActivity(state).use { replacement ->
                val restoredActivity = replacement.get()
                toggleHidden(restoredActivity)
                installAuthentication(restoredActivity).finish(true)
                val restored = restoredActivity.supportFragmentManager.findFragmentByTag("EditTaskDialogFragment") as EditTaskDialogFragment
                val dialog = restored.requireDialog() as AlertDialog
                dialog.findViewById<TextInputLayout>(R.id.til_DialogTaskTitle)!!.editText!!.setText("Restored completed synthetic note")
                shadowOf(Looper.getMainLooper()).idle()
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Restored completed synthetic note", repository.getTask(5L)!!.title)
                assertEquals(ModelTask.STATUS_DONE, repository.getTask(5L)!!.status)
                assertEquals(ModelTask.HIDDEN_TRUE, repository.getTask(5L)!!.isHidden)
            }
        }
    }

    @Test fun recycledPublicEmptyRowClearsThePreviousPrivateDescription() {
        openActivity().use { controller ->
            val activity = controller.get()
            toggleHidden(activity)
            installAuthentication(activity).finish(true)
            val hidden = note(20L, "Private synthetic row", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_TRUE)
            hidden.description = """{"nodes":[{"type":"INPUT","content":["Synthetic private body"],"contentStyles":[],"textSettings":{"textColor":""},"childs":null,"macroSettings":null}]}"""
            val empty = note(21L, "Public synthetic row", ModelTask.STATUS_CURRENT, ModelTask.HIDDEN_FALSE)
            val adapter = CurrentTaskAdapter()
            adapter.items = arrayListOf(hidden)
            val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0)) as TaskAdapter.TaskViewHolder
            adapter.onBindViewHolder(holder, 0)
            adapter.expand(0, false, holder)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(View.VISIBLE, holder.containerDescription.visibility)
            assertTrue(descendantText(holder.description).contains("Synthetic private body"))
            toggleHidden(activity)
            adapter.items = arrayListOf(empty)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(View.GONE, holder.containerDescription.visibility)
            assertEquals(View.GONE, holder.ivArrow.visibility)
            assertFalse(descendantText(holder.description).contains("Synthetic private body"))
        }
    }

    private fun descendantText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString(" ") { descendantText(view.getChildAt(it)) }
        else -> ""
    }

    private fun openActivity(savedState: Bundle? = null): ActivityController<TasksActivity> {
        val controller = Robolectric.buildActivity(TasksActivity::class.java)
        val activity = controller.get()
        val authentication = ControlledAuthentication()
        authenticationByActivity[activity] = authentication
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreAttached(manager: FragmentManager, fragment: Fragment, context: Context) {
                if (fragment is TaskDialog) {
                    KTP.openScopes(ApplicationScope::class.java, fragment).installTestModules(object : Module() {
                        init { bind(IASRService::class.java).toInstance(object : IASRService {
                            override fun setApiKey() = Unit
                            override fun startASR() = Unit
                            override fun checkPermissions() = false
                        }) }
                    })
                }
            }
        }, true)
        // KTP removes delegate registrations after its first eager injection. Install
        // the external dependency before onCreate injects the real activity.
        KTP.openScopes(ApplicationScope::class.java, activity).installTestModules(object : Module() {
            init { bind(IFIDOService::class.java).toInstance(authentication) }
        })
        return if (savedState == null) controller.setup()
        else controller.create(savedState).start().restoreInstanceState(savedState).resume().visible()
    }

    private fun installAuthentication(activity: TasksActivity): ControlledAuthentication =
        checkNotNull(authenticationByActivity[activity])

    private fun toggleHidden(activity: TasksActivity) {
        val menu = PopupMenu(activity, activity.findViewById(R.id.toolbar)).menu
        activity.menuInflater.inflate(R.menu.menu_main, menu)
        activity.onOptionsItemSelected(menu.findItem(R.id.action_hidden_notes))
    }

    private fun ids(fragment: TaskFragment): List<Long> =
        fragment.tasks.orEmpty().filterIsInstance<ModelTask>().map { it.timeStamp }

    private fun note(id: Long, title: String, status: Int, hidden: Int) =
        ModelTask(title, "{\"nodes\":[]}", "<p>synthetic fixture</p>", 0L,
            ModelTask.PRIORITY_NORMAL, status, id, hidden)

    private fun pendingBiometricCallback(service: HuaweiFIDOService, result: (Boolean) -> Unit): BioAuthnCallback {
        // HMS optimized constructors cannot be Robolectric-instrumented. Use the real
        // callback boundary, with a synthetic pending request and no hardware prompt.
        ReflectionHelpers.setField(service, "callback", result)
        val promptAdapter: Any = ReflectionHelpers.getField(service.createBioAuthnPrompt(), "delegatee")
        val promptContext: Any = ReflectionHelpers.getField(promptAdapter, "bioAuthnContext")
        return ReflectionHelpers.getField(promptContext, "callback")
    }

    private class ControlledAuthentication : IFIDOService {
        private var pending: ((Boolean) -> Unit)? = null
        override fun createBioAuthnPrompt(): BioAuthnPrompt =
            error("The external authentication prompt is controlled by this fixture")
        override fun fingerAuthenticateWithoutCryptoObject(callback: (Boolean) -> Unit) {
            check(pending == null)
            pending = callback
        }
        override fun showResult(msg: String) = Unit
        fun finish(result: Boolean) {
            val callback = checkNotNull(pending)
            pending = null
            callback(result)
        }
    }
}
