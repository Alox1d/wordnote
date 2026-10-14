package ru.alox1d.wordnote.huawei

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import com.github.irshulx.Editor
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.backup.BackupArchive
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupListValidationTest {
    private lateinit var context: Context
    private lateinit var database: TaskDatabase
    private lateinit var repository: TaskRepository
    private val scratch = mutableListOf<File>()

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = TaskDatabase.getInstance(context)
        database.writableDatabase.delete(TaskDatabase.TASKS_TABLE, null, null)
        repository = TaskRepository(database.readableDatabase, database.writableDatabase)
        repository.saveTask(note(101, """{"nodes":[]}""", "Keep this note"))
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        scratch.forEach { it.deleteRecursively() }
    }

    @Test fun missingUnorderedListChildrenAreRejectedBeforeReplacingNotes() = rejectList("ul", "")
    @Test fun missingOrderedListChildrenAreRejectedBeforeReplacingNotes() = rejectList("ol", "")
    @Test fun nullUnorderedListChildrenAreRejectedBeforeReplacingNotes() = rejectList("ul", ",\"childs\":null")
    @Test fun nullOrderedListChildrenAreRejectedBeforeReplacingNotes() = rejectList("ol", ",\"childs\":null")

    @Test fun explicitEmptyListsRestoreAndRenderFromLegacyDatabaseAndArchive() {
        val json = """{"nodes":[{"type":"ul","content":[],"childs":[]},{"type":"ol","content":[],"childs":[]}]}"""
        for (archive in listOf(false, true)) {
            BackupArchive(context).restore(candidate(json, archive))
            val restored = repository.getTask(202)!!
            assertEquals(json, restored.description)
            assertNull(repository.getTask(101))
            render(restored.description!!)
        }
    }

    @Test fun populatedListsKeepTheirTextAndStylesAcrossArchiveRoundTrip() {
        val json = """{"nodes":[{"type":"ul","content":[],"childs":[{"type":"UL_LI","content":["<b>Bullet</b>"],"contentStyles":["BOLD"],"textSettings":{"textColor":"#000000"}}]},{"type":"ol","content":[],"childs":[{"type":"OL_LI","content":["Number"],"contentStyles":[],"textSettings":{"textColor":"#000000"}}]}]}"""
        repository.removeTask(101)
        repository.saveTask(note(202, json, "<ul><li><b>Bullet</b></li></ul><ol><li>Number</li></ol>"))
        val archive = BackupArchive(context).create().also { scratch += it.parentFile!! }
        repository.removeTask(202)
        BackupArchive(context).restore(archive)
        val restored = repository.getTask(202)!!
        assertEquals(JsonParser().parse(json), JsonParser().parse(restored.description))
        assertEquals("<ul><li><b>Bullet</b></li></ul><ol><li>Number</li></ol>", restored.descriptionHTML)
        render(restored.description!!)
    }

    private fun rejectList(type: String, children: String) {
        val json = """{"nodes":[{"type":"$type","content":[]$children}]}"""
        for (archive in listOf(false, true)) {
            val source = candidate(json, archive)
            assertThrows("Malformed $type children must reject restore (archive=$archive)", IllegalArgumentException::class.java) {
                BackupArchive(context).restore(source)
            }
            assertEquals("Keep this note", repository.getTask(101)!!.descriptionHTML)
            assertNull(repository.getTask(202))
        }
    }

    private fun candidate(json: String, archive: Boolean): File {
        val source = temporary("synthetic-list", ".sqlite").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.execSQL("CREATE TABLE tasks_table (_id INTEGER PRIMARY KEY AUTOINCREMENT, task_title TEXT NOT NULL, task_description TEXT NOT NULL, task_descriptionHTML TEXT NOT NULL, task_date LONG, task_priority INTEGER, task_status INTEGER, task_time_stamp LONG, TASK_IS_HIDDEN INTEGER)")
            db.execSQL("INSERT INTO tasks_table VALUES(NULL, 'Synthetic list', ?, '', 0, 1, 1, 202, 0)", arrayOf(json))
            db.version = 4
        }
        if (!archive) return source
        val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        val manifest = """{"version":1,"databaseSha256":"$digest","attachments":[]}"""
        return temporary("synthetic-list", ".zip").also { result ->
            ZipOutputStream(result.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("database.sqlite")); source.inputStream().use { it.copyTo(out) }; out.closeEntry()
            }
        }
    }

    private fun render(json: String) {
        Robolectric.buildActivity(AppCompatActivity::class.java).apply { get().setTheme(R.style.Theme_WordNote) }.setup().use { controller ->
            val view = controller.get().layoutInflater.inflate(R.layout.model_task, null, false)
            val editor = view.findViewById<Editor>(R.id.editor_fragDescription)
            editor.setEditorImageLayout(R.layout.editor_image_view)
            editor.render(editor.getContentDeserialized(json))
        }
    }

    private fun note(id: Long, json: String, html: String) = ModelTask("Synthetic", json, html, 0, 1, 1, id, 0)
    private fun temporary(prefix: String, suffix: String) = File.createTempFile(prefix, suffix, context.cacheDir).also { scratch += it }
}
