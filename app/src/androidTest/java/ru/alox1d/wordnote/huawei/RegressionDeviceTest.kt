package ru.alox1d.wordnote.huawei

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.irshulx.Editor
import com.github.irshulx.models.EditorTextStyle
import com.google.android.material.switchmaterial.SwitchMaterial
import com.huawei.hmf.tasks.Task
import com.huawei.hmf.tasks.TaskCompletionSource
import com.huawei.hms.mlsdk.common.MLFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.service.tr.camera.FrameMetadata
import ru.alox1d.wordnote.huawei.domain.services.tr.transactor.BaseTransactor
import ru.alox1d.wordnote.huawei.helpers.util.Constant
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.RemoteDetectionActivity
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.TextRecognitionActivity
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs on an isolated Android 36 emulator using unsaved synthetic drafts and documents. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class RegressionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun toolbarBoldThenItalicSurvivesSerializedRestore() = withDraft { _, dialog, editor ->
        main {
            editor.removeAllViews()
            editor.render("<p>Formatted paragraph</p>")
            editor.activeView = inputs(editor).single().apply { requestFocus() }
            assertTrue(dialog.findViewById<View>(R.id.action_bold)!!.performClick())
            assertTrue(dialog.findViewById<View>(R.id.action_Italic)!!.performClick())
        }
        repeat(3) { pass ->
            instrumentation.waitForIdleSync()
            main {
                assertTrue("Combined toolbar style missing on pass $pass",
                    editor.content.nodes.single().contentStyles.contains(EditorTextStyle.BOLDITALIC))
                assertEquals(Typeface.BOLD_ITALIC, inputs(editor).single().typeface.style)
                assertEquals("Formatted paragraph", inputs(editor).single().text.toString())
                if (pass < 2) editor.render(editor.getContentDeserialized(editor.contentAsSerialized))
            }
        }
    }

    @Test fun clipboardMultilinePastePreservesLiteralMarkupAfterRestore() = withDraft { _, _, editor ->
        val expected = listOf("<first> &amp;", "<tag> &amp; literal", "last &lt; value")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            main {
                editor.removeAllViews()
                editor.render("<p></p>")
                val input = inputs(editor).single().apply { requestFocus(); setSelection(0) }
                editor.activeView = input
                clipboard.setPrimaryClip(ClipData.newPlainText("Synthetic multiline text", expected.joinToString("\n")))
                assertTrue("Android paste action was not handled", input.onTextContextMenuItem(android.R.id.paste))
            }
            await("Pasted paragraphs were not created") { main { editor.childCount == expected.size } }
            repeat(2) { pass ->
                main {
                    assertEquals("Literal clipboard text changed on pass $pass", expected,
                        inputs(editor).map { it.text.toString() })
                    if (pass == 0) editor.render(editor.getContentDeserialized(editor.contentAsSerialized))
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            main { clipboard.clearPrimaryClip() }
        }
    }

    @Test fun privateLinkWindowIsSecureAndHiddenAfterHomeAndReturn() = withDraft { activity, dialog, editor ->
        main {
            inputs(editor).single().setText("Synthetic private draft")
            dialog.findViewById<SwitchMaterial>(R.id.switchHidden)!!.isChecked = true
            assertTrue("Private draft window is not secure", secure(dialog.window!!.attributes))
            assertTrue(dialog.findViewById<View>(R.id.action_insert_link)!!.performClick())
        }
        await("Link input window did not appear") { main { linkRoot() != null } }
        val childRoot = main { requireNotNull(linkRoot()) }
        val paused = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val application = activity.application
        val observer = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(candidate: Activity) { if (candidate === activity) paused.countDown() }
            override fun onActivityResumed(candidate: Activity) { if (candidate === activity) resumed.countDown() }
            override fun onActivityCreated(candidate: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(candidate: Activity) = Unit
            override fun onActivityStopped(candidate: Activity) = Unit
            override fun onActivitySaveInstanceState(candidate: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(candidate: Activity) = Unit
        }
        main { application.registerActivityLifecycleCallbacks(observer) }
        try {
            main {
                requireNotNull(findUrlInput(childRoot)).setText("https://example.invalid/synthetic-private-path")
                assertTrue("Private URL window is not secure",
                    secure(childRoot.layoutParams as WindowManager.LayoutParams))
            }
            pressKey(KeyEvent.KEYCODE_HOME)
            assertTrue("Home did not pause the editor", paused.await(10, TimeUnit.SECONDS))
            main {
                assertFalse("Private session stayed unlocked", WordNoteApp.isHiddenNotesUnlocked)
                assertFalse("Private editor stayed visible in background", dialog.isShowing)
            }
            context.startActivity(Intent(context, TasksActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            assertTrue("Editor did not resume", resumed.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            main {
                assertFalse("Private session unlocked on return", WordNoteApp.isHiddenNotesUnlocked)
                assertFalse("Private editor reappeared while locked", dialog.isShowing)
                assertFalse("Private URL window reappeared while locked", childRoot.isShown)
                assertEquals("Unsaved private draft was lost", "Synthetic private draft",
                    inputs(editor).single().text.toString())
            }
        } finally {
            main {
                application.unregisterActivityLifecycleCallbacks(observer)
                // The negative URL button closes only this synthetic auxiliary window.
                childRoot.findViewById<View>(android.R.id.button2)?.performClick()
            }
        }
    }

    @Test fun realCameraAndImagePickerOcrWindowsProtectDocuments() {
        val camera = launch(TextRecognitionActivity::class.java)
        try {
            main { assertTrue("Camera OCR window is not secure", secure(camera.window.attributes)) }
        } finally {
            main { camera.finish() }
            instrumentation.waitForIdleSync()
        }
        val image = launch(RemoteDetectionActivity::class.java, Intent(context, RemoteDetectionActivity::class.java)
            .putExtra(Constant.MODEL_TYPE, Constant.CLOUD_TEXT_DETECTION)
            .putExtra(Constant.ADD_PICTURE_TYPE, Constant.TYPE_SELECT_IMAGE))
        try {
            main { assertTrue("Image OCR window is not secure", secure(image.window.attributes)) }
        } finally {
            // Cancel the external image picker without providing document data.
            pressKey(KeyEvent.KEYCODE_BACK)
            main { image.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun malformedBackupListsAreRejectedByRealSqliteValidation() {
        val candidate = File.createTempFile("synthetic-list-", ".sqlite", context.cacheDir)
        try {
            assertTrue(candidate.delete())
            SQLiteDatabase.openOrCreateDatabase(candidate, null).use { db ->
                db.execSQL("CREATE TABLE tasks_table (_id INTEGER PRIMARY KEY AUTOINCREMENT, task_title TEXT NOT NULL, task_description TEXT NOT NULL, task_descriptionHTML TEXT NOT NULL, task_date LONG, task_priority INTEGER, task_status INTEGER, task_time_stamp LONG, TASK_IS_HIDDEN INTEGER)")
                db.execSQL("INSERT INTO tasks_table VALUES(NULL, 'Synthetic list', ?, '', 0, 1, 1, 202, 0)",
                    arrayOf("""{"nodes":[]}"""))
                db.version = 4
            }
            for (type in listOf("ul", "ol")) {
                val valid = """{"nodes":[{"type":"$type","content":[],"childs":[]}]}"""
                updateDescription(candidate, valid)
                assertEquals(valid, TaskDatabase.readBackup(candidate).single().description)
                for (children in listOf("", ",\"childs\":null")) {
                    updateDescription(candidate, """{"nodes":[{"type":"$type","content":[]$children}]}""")
                    try {
                        TaskDatabase.readBackup(candidate)
                        fail("Backup validation accepted missing/null $type children")
                    } catch (_: IllegalArgumentException) {
                        // Expected validation rejection before any restore operation.
                    }
                }
            }
        } finally {
            SQLiteDatabase.deleteDatabase(candidate)
        }
    }

    @Test fun analyzerFailureAllowsNextNv21FrameAndStopIgnoresLaterFrames() {
        val probe = Probe()
        val overlay = main { GraphicOverlay(context, null) }
        val metadata = FrameMetadata.Builder().setWidth(4).setHeight(4).setRotation(0).setCameraFacing(0).build()
        try {
            main {
                probe.process(frame(100), metadata, overlay)
                assertEquals(1, probe.requests.size)
                probe.requests.single().setException(IllegalStateException("Synthetic transient analyzer failure"))
            }
            await("Analyzer failure callback did not complete") { main { probe.failures == 1 } }
            main { probe.process(frame(110), metadata, overlay) }
            await("Analyzer did not receive the next camera frame") { main { probe.requests.size == 2 } }
            main {
                assertNotNull("NV21 analysis lost its frame", probe.frames[1])
                probe.stop()
                probe.requests[1].setException(IllegalStateException("Synthetic stopped analyzer failure"))
                probe.process(frame(120), metadata, overlay)
            }
            instrumentation.waitForIdleSync()
            main {
                assertEquals("Stopped analyzer received a future frame", 2, probe.requests.size)
                assertEquals("Stopped analyzer delivered a stale callback", 1, probe.failures)
            }
        } finally {
            main { probe.stop() }
        }
    }

    private fun withDraft(block: (TasksActivity, AlertDialog, Editor) -> Unit) {
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val existed = preferences.contains(PreferenceStorage.SPLASH_IS_INVISIBLE)
        val original = preferences.getBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, false)
        preferences.edit().putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, true).commit()
        var activity: TasksActivity? = null
        var fragment: AddingTaskDialogFragment? = null
        try {
            val owner = launch(TasksActivity::class.java)
            activity = owner
            main {
                assertTrue(owner.findViewById<View>(R.id.fab).performClick())
                owner.supportFragmentManager.executePendingTransactions()
                fragment = owner.supportFragmentManager.fragments.filterIsInstance<AddingTaskDialogFragment>().single()
            }
            await("Draft editor did not appear") { main { fragment?.dialog?.isShowing == true } }
            val dialog = main { fragment!!.requireDialog() as AlertDialog }
            val editor = main { dialog.findViewById<Editor>(R.id.editor_dialogDescription)!! }
            block(owner, dialog, editor)
        } finally {
            main {
                fragment?.dismissAllowingStateLoss()
                activity?.supportFragmentManager?.executePendingTransactions()
                activity?.finish()
            }
            instrumentation.waitForIdleSync()
            val edit = preferences.edit()
            if (existed) edit.putBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE, original)
            else edit.remove(PreferenceStorage.SPLASH_IS_INVISIBLE)
            edit.commit()
        }
    }

    private fun <T : Activity> launch(type: Class<T>, intent: Intent = Intent(context, type)): T =
        type.cast(instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))!!

    private fun inputs(editor: Editor): List<EditText> =
        (0 until editor.childCount).map { editor.getChildAt(it) as EditText }

    private fun secure(attributes: WindowManager.LayoutParams) =
        attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun linkRoot(): View? = WindowInspector.getGlobalWindowViews()
        .firstOrNull { findUrlInput(it) != null && it.isShown }

    private fun findUrlInput(view: View): EditText? {
        if (view is EditText && view.hint?.toString() == "type the URL here") return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findUrlInput(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun pressKey(key: Int) {
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, key), true)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, key), true)
    }

    private fun updateDescription(file: File, json: String) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE tasks_table SET task_description=?", arrayOf(json))
        }
    }

    private fun frame(value: Int) = ByteBuffer.wrap(ByteArray(24) { value.toByte() })

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) fail(message)
            SystemClock.sleep(50)
        }
    }

    private fun <T> main(block: () -> T): T {
        var value: T? = null
        var error: Throwable? = null
        instrumentation.runOnMainSync {
            try { value = block() } catch (failure: Throwable) { error = failure }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private class Probe : BaseTransactor<String>() {
        val requests = mutableListOf<TaskCompletionSource<String>>()
        val frames = mutableListOf<ByteBuffer?>()
        var failures = 0
        override fun detectInImage(image: MLFrame?): Task<String> {
            frames += image?.byteBuffer
            return TaskCompletionSource<String>().also { requests += it }.task
        }
        override suspend fun onSuccess(originalCameraImage: Bitmap?, results: String,
            frameMetadata: FrameMetadata?, graphicOverlay: GraphicOverlay?) = Unit
        override fun onFailure(e: Exception?) { failures++ }
    }
}
