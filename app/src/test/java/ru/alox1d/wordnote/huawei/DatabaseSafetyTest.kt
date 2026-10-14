package ru.alox1d.wordnote.huawei

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseSafetyTest {
    private lateinit var context: Context
    private lateinit var helper: TaskDatabase
    private lateinit var repo: TaskRepository
    private val candidates = mutableListOf<File>()

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        helper = TaskDatabase.getInstance(context)
        helper.writableDatabase.delete(TaskDatabase.TASKS_TABLE, null, null)
        repo = TaskRepository(helper.readableDatabase, helper.writableDatabase)
        repo.saveTask(ModelTask("Original", "{\"nodes\":[]}", "<b>Original</b>", 0,
            ModelTask.PRIORITY_NORMAL, ModelTask.STATUS_CURRENT, 101, ModelTask.HIDDEN_TRUE))
    }

    @After fun tearDown() {
        runCatching { helper.close() }
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        candidates.forEach { it.delete() }
    }

    @Test fun corruptBackupIsRejectedBeforeTouchingCurrentDatabase() {
        val candidate = temporary().apply { writeText("not sqlite") }
        rejected { helper.importDB(candidate) }
        assertEquals("Original", repo.getTask(101)!!.title)
        assertEquals(1, repo.getTasks(null, null, null).size)
    }

    @Test fun validRestoreIsVisibleToAlreadyOpenRepository() {
        helper.importDB(candidate())
        assertNull(repo.getTask(101))
        val restored = repo.getTask(202)!!
        assertEquals("Restored", restored.title)
        assertEquals("<b>Restored</b>", restored.descriptionHTML)
        assertEquals(ModelTask.HIDDEN_TRUE, restored.isHidden)
        assertEquals(ModelTask.STATUS_DONE, restored.status)
    }

    @Test fun missingColumnIsRejectedWithoutReplacingNotes() {
        val file = temporary()
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE tasks_table (_id INTEGER PRIMARY KEY, task_title TEXT)")
            it.version = 4
        }
        rejected { helper.importDB(file) }
        assertEquals("Original", repo.getTask(101)!!.title)
    }

    @Test fun futureSchemaIsRejectedWithoutReplacingNotes() {
        val file = candidate()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 99 }
        rejected { helper.importDB(file) }
        assertEquals("Original", repo.getTask(101)!!.title)
    }

    @Test fun duplicateNoteIdentifiersAreRejectedBeforeDeletingCurrentRows() {
        val file = candidate()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("INSERT INTO tasks_table SELECT NULL, task_title, task_description, task_descriptionHTML, task_date, task_priority, task_status, task_time_stamp, TASK_IS_HIDDEN FROM tasks_table")
        }
        rejected { helper.importDB(file) }
        assertEquals("Original", repo.getTask(101)!!.title)
    }

    @Test fun failedInsertRollsBackTheWholeRestore() {
        helper.writableDatabase.execSQL("CREATE TRIGGER refuse_restore BEFORE INSERT ON tasks_table WHEN NEW.task_time_stamp=202 BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        rejected { helper.importDB(candidate()) }
        assertEquals("Original", repo.getTask(101)!!.title)
        assertNull(repo.getTask(202))
    }

    @Test fun upgradingCompatibleVersionThreePreservesNotes() {
        helper.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(TaskDatabase.DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 3 }
        helper = TaskDatabase.getInstance(context)
        repo = TaskRepository(helper.readableDatabase, helper.writableDatabase)
        assertEquals("Original", repo.getTask(101)!!.title)
        assertEquals(4, helper.readableDatabase.version)
    }

    @Test fun editingANotePublishesOneAtomicRowForBackupSnapshots() {
        helper.writableDatabase.execSQL("CREATE TABLE update_audit (title TEXT, body TEXT, hidden INTEGER)")
        helper.writableDatabase.execSQL("CREATE TRIGGER audit_update AFTER UPDATE ON tasks_table BEGIN INSERT INTO update_audit VALUES(NEW.task_title, NEW.task_description, NEW.TASK_IS_HIDDEN); END")
        repo.updateTask(ModelTask("Updated", "New body", "<i>New body</i>", 0,
            ModelTask.PRIORITY_HIGH, ModelTask.STATUS_DONE, 101, ModelTask.HIDDEN_FALSE))
        helper.readableDatabase.rawQuery("SELECT title, body, hidden FROM update_audit", null).use {
            assertEquals("A backup must never observe intermediate field updates", 1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("Updated", it.getString(0))
            assertEquals("New body", it.getString(1))
            assertEquals(ModelTask.HIDDEN_FALSE, it.getInt(2))
        }
    }

    @Test fun invalidEditorContentsAreRejectedBeforeReplacingNotes() {
        val broken = listOf("not-json", "[]", "null", "{\"nodes\":[{}]}",
            "{\"nodes\":[{\"type\":\"img\",\"content\":[\"https://example.invalid/image\"],\"childs\":[]}]}",
            "{\"nodes\":[{\"type\":\"INPUT\",\"content\":[\"text\"],\"contentStyles\":[\"unknown\"],\"textSettings\":{\"textColor\":\"#000000\"}}]}")
        val invalidMaps = listOf("1,", ",", "not-a-number,2", "1,2,3")
        (broken + invalidMaps.map { "{\"nodes\":[{\"type\":\"map\",\"content\":[\"$it\",\"caption\"]}]}" }).forEach { json ->
            val file = candidate()
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE tasks_table SET task_description=?", arrayOf(json))
            }
            rejected { helper.importDB(file) }
            assertEquals("Original", repo.getTask(101)!!.title)
            assertNull(repo.getTask(202))
        }
    }

    @Test fun cancelledRestoreRollsBackBeforeItsCommit() {
        val incoming = TaskDatabase.readBackup(candidate())
        org.junit.Assert.assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            helper.replaceTasks(incoming) { throw kotlinx.coroutines.CancellationException("Synthetic cancellation") }
        }
        assertEquals("Original", repo.getTask(101)!!.title)
        assertNull(repo.getTask(202))
    }

    @Test fun oversizedIntegerMetadataCannotWrapPrivateNotesIntoPublicNotes() {
        listOf("TASK_IS_HIDDEN" to 4294967296L, "task_priority" to 4294967297L,
            "task_status" to 4294967296L).forEach { (column, invalid) ->
            val backup = candidate()
            SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE tasks_table SET $column=?", arrayOf(invalid))
            }
            rejected { helper.importDB(backup) }
            assertEquals("Original", repo.getTask(101)!!.title)
            assertNull(repo.getTask(202))
        }
    }

    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Invalid or incomplete restore must report failure")
    }

    private fun temporary(): File = File.createTempFile("synthetic-backup-", ".sqlite", context.cacheDir).also {
        it.delete()
        candidates += it
    }

    private fun candidate(): File = temporary().also { file ->
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE tasks_table (_id INTEGER PRIMARY KEY AUTOINCREMENT, task_title TEXT NOT NULL, task_description TEXT NOT NULL, task_descriptionHTML TEXT NOT NULL, task_date LONG, task_priority INTEGER, task_status INTEGER, task_time_stamp LONG, TASK_IS_HIDDEN INTEGER)")
            it.execSQL("INSERT INTO tasks_table VALUES(NULL, 'Restored', '{\"nodes\":[]}', '<b>Restored</b>', 0, 1, 2, 202, 1)")
            it.version = 4
        }
    }
}
