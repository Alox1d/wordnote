package ru.alox1d.wordnote.huawei

import android.content.Context
import android.graphics.Bitmap
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.backup.BackupArchive
import ru.alox1d.wordnote.huawei.data.storage.AttachmentStore
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupArchiveTest {
    private lateinit var context: Context
    private lateinit var helper: TaskDatabase
    private lateinit var repo: TaskRepository
    private lateinit var store: AttachmentStore
    private val scratch = mutableListOf<File>()

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        helper = TaskDatabase.getInstance(context)
        helper.writableDatabase.delete(TaskDatabase.TASKS_TABLE, null, null)
        repo = TaskRepository(helper.readableDatabase, helper.writableDatabase)
        store = AttachmentStore(context)
    }
    @After fun tearDown() {
        helper.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        File(context.filesDir, "attachments").deleteRecursively()
        scratch.forEach { it.deleteRecursively() }
    }

    @Test fun restoresRenderableRichTextAndImagesOnAnEmptyInstallation() {
        val image = store.saveBitmap(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
        val originalBytes = image.readBytes()
        val description = """{"nodes":[{"type":"INPUT","content":["<b>bold</b>"],"contentStyles":["BOLD"],"textSettings":{"textColor":"#000000"}}, {"type":"img","content":["${image.path}"],"childs":[{"type":"IMG_SUB","content":["<p>caption</p>"],"contentStyles":[],"textSettings":{"textColor":"#5E5E5E"}}]}]}"""
        repo.saveTask(note(11, description, "<b>bold</b><img src='${image.path}'>"))
        val archive = BackupArchive(context).create().also { scratch += it.parentFile!! }
        ZipFile(archive).use { zip ->
            assertEquals(1, zip.entries().asSequence().count { it.name.startsWith("images/") })
            assertNotNull(zip.getEntry("manifest.json"))
        }
        image.delete()
        helper.writableDatabase.delete(TaskDatabase.TASKS_TABLE, null, null)
        BackupArchive(context).restore(archive)
        val restored = repo.getTask(11)!!
        val root = JsonParser().parse(restored.description).asJsonObject
        val path = root.getAsJsonArray("nodes")[1].asJsonObject.getAsJsonArray("content")[0].asString
        assertFalse(path == image.path)
        assertTrue(File(path).canonicalPath.startsWith(File(context.filesDir, "attachments").canonicalPath + File.separator))
        assertArrayEquals(originalBytes, File(path).readBytes())
        assertTrue(restored.descriptionHTML!!.contains(path))
        assertTrue(restored.descriptionHTML!!.contains("<b>bold</b>"))
        assertEquals(ModelTask.HIDDEN_TRUE, restored.isHidden)
        assertEquals(ModelTask.STATUS_DONE, restored.status)
        org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).apply {
            get().setTheme(R.style.Theme_WordNote)
        }.setup().use { controller ->
            val view = controller.get().layoutInflater.inflate(R.layout.model_task, null, false)
            val editor = view.findViewById<com.github.irshulx.Editor>(R.id.editor_fragDescription)
            editor.setEditorImageLayout(R.layout.editor_image_view)
            editor.render(editor.getContentDeserialized(restored.description))
            assertTrue(editor.childCount > 0)
        }
    }

    @Test fun refusesMissingImagesWithoutPublishingIncompleteSnapshot() {
        val path = File(context.filesDir, "attachments/missing.png").path
        repo.saveTask(note(11, imageJson(path), "<img src='$path'>"))
        rejected { BackupArchive(context).create() }
        assertNotNull(repo.getTask(11))
    }

    @Test fun notePathCannotCopyPreferencesIntoBackup() {
        val privateConfig = File(context.filesDir, "private-config.txt").apply { writeText("synthetic private material") }
        scratch += privateConfig
        repo.saveTask(note(11, imageJson(privateConfig.path), "<img src='${privateConfig.path}'>"))
        rejected { BackupArchive(context).create() }
        assertNotNull(repo.getTask(11))
    }

    @Test fun damagedArchivePreservesCurrentNotes() {
        repo.saveTask(note(11, "{\"nodes\":[]}", "Original"))
        val archive = BackupArchive(context).create().also { scratch += it.parentFile!! }
        val damaged = rewrite(archive, "database.sqlite") { byteArrayOf(1, 2, 3) }
        rejected { BackupArchive(context).restore(damaged) }
        assertEquals("Original", repo.getTask(11)!!.descriptionHTML)
    }

    @Test fun traversalEntryCannotWriteOutsideRestoreDirectory() {
        repo.saveTask(note(11, "{\"nodes\":[]}", "Original"))
        val archive = BackupArchive(context).create().also { scratch += it.parentFile!! }
        val hostile = temporary("hostile", ".zip")
        val escaped = File(context.cacheDir, "escaped-test.txt")
        escaped.delete()
        ZipOutputStream(hostile.outputStream()).use { out ->
            ZipFile(archive).use { zip -> zip.entries().asSequence().forEach {
                out.putNextEntry(ZipEntry(it.name)); zip.getInputStream(it).use { input -> input.copyTo(out) }; out.closeEntry()
            } }
            out.putNextEntry(ZipEntry("../escaped-test.txt")); out.write(byteArrayOf(1)); out.closeEntry()
        }
        rejected { BackupArchive(context).restore(hostile) }
        assertFalse(escaped.exists())
        assertEquals("Original", repo.getTask(11)!!.descriptionHTML)
    }

    @Test fun legacyDatabaseWithoutImagesStillRestores() {
        repo.saveTask(note(11, "{\"nodes\":[]}", "Legacy"))
        val legacy = temporary("legacy", ".sqlite").apply { delete() }
        helper.exportDB(legacy)
        repo.removeTask(11)
        BackupArchive(context).restore(legacy)
        assertEquals("Legacy", repo.getTask(11)!!.descriptionHTML)
    }

    @Test fun legacyMissingAttachmentIsRejectedBeforeReplacingNotes() {
        val missing = File(context.filesDir, "attachments/missing.png").path
        repo.saveTask(note(11, imageJson(missing), "<img src='$missing'>"))
        val legacy = temporary("legacy", ".sqlite").apply { delete() }
        helper.exportDB(legacy)
        repo.removeTask(11)
        repo.saveTask(note(22, "{\"nodes\":[]}", "Keep me"))
        rejected { BackupArchive(context).restore(legacy) }
        assertEquals("Keep me", repo.getTask(22)!!.descriptionHTML)
        assertNull(repo.getTask(11))
    }

    @Test fun failedCommitLeavesOriginalNotesAndRemovesNewImageFiles() {
        val image = store.saveBitmap(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
        repo.saveTask(note(11, imageJson(image.path), "<img src='${image.path}'>"))
        val archive = BackupArchive(context).create().also { scratch += it.parentFile!! }
        repo.removeTask(11)
        repo.saveTask(note(22, "{\"nodes\":[]}", "Keep me"))
        val before = File(context.filesDir, "attachments").walkTopDown().filter { it.isFile }.map { it.path }.toSet()
        helper.writableDatabase.execSQL("CREATE TRIGGER refuse_restore BEFORE INSERT ON tasks_table WHEN NEW.task_time_stamp=11 BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        rejected { BackupArchive(context).restore(archive) }
        assertEquals("Keep me", repo.getTask(22)!!.descriptionHTML)
        assertEquals(before, File(context.filesDir, "attachments").walkTopDown().filter { it.isFile }.map { it.path }.toSet())
    }

    @Test fun deletingOneNoteDoesNotDeleteAnImageUsedByAnother() {
        val image = store.saveBitmap(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
        repo.saveTask(note(11, imageJson(image.path), "<img src='${image.path}'>"))
        repo.saveTask(note(22, imageJson(image.path), "<img src='${image.path}'>"))
        repo.removeTask(11)
        assertFalse(store.deleteIfUnused(image.path))
        assertTrue(image.exists())
        repo.removeTask(22)
        assertTrue(store.deleteIfUnused(image.path))
        assertFalse(image.exists())
    }

    private fun note(id: Long, json: String, html: String) = ModelTask("Synthetic", json, html, 0,
        ModelTask.PRIORITY_NORMAL, ModelTask.STATUS_DONE, id, ModelTask.HIDDEN_TRUE)
    private fun imageJson(path: String) = """{"nodes":[{"type":"img","content":["$path"],"childs":[{"type":"IMG_SUB","content":["<p>caption</p>"],"contentStyles":[],"textSettings":{"textColor":"#5E5E5E"}}]}]}"""
    private fun temporary(prefix: String, suffix: String) = File.createTempFile(prefix, suffix, context.cacheDir).also { scratch += it }
    private fun rewrite(source: File, target: String, replacement: (ByteArray) -> ByteArray): File = temporary("modified", ".zip").also { result ->
        ZipOutputStream(result.outputStream()).use { out -> ZipFile(source).use { zip -> zip.entries().asSequence().forEach { entry ->
            out.putNextEntry(ZipEntry(entry.name)); val bytes = zip.getInputStream(entry).use { it.readBytes() }
            out.write(if (entry.name == target) replacement(bytes) else bytes); out.closeEntry()
        } } }
    }
    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("An incomplete or unsafe backup must report failure")
    }
}
