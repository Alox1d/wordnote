package ru.alox1d.wordnote.huawei.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.*
import java.io.File

class TaskDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) = db.execSQL(TASKS_TABLE_CREATE_SCRIPT)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1 until DATABASE_VERSION && newVersion == DATABASE_VERSION) { "Unsupported database version" }
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($TASKS_TABLE)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        // Keep legacy notes. Refuse an unknown schema instead of dropping user data.
        require(columns.containsAll(listOf(TASK_TITLE_COLUMN, TASK_DESCRIPTION_COLUMN, TASK_TIME_STAMP_COLUMN))) { "Unsupported database schema" }
        val additions = mapOf(
            TASK_DESCRIPTION_HTML_COLUMN to "TEXT NOT NULL DEFAULT ''",
            TASK_DATE_COLUMN to "LONG NOT NULL DEFAULT 0",
            TASK_PRIORITY_COLUMN to "INTEGER NOT NULL DEFAULT 1",
            TASK_STATUS_COLUMN to "INTEGER NOT NULL DEFAULT 1",
            TASK_IS_HIDDEN_COLUMN to "INTEGER NOT NULL DEFAULT 0"
        )
        additions.forEach { (name, declaration) ->
            if (name !in columns) db.execSQL("ALTER TABLE $TASKS_TABLE ADD COLUMN $name $declaration")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.disableWriteAheadLogging()
    }

    /** Validate everything before changing the live connection used by repositories. */
    fun importDB(file: File?) = replaceTasks(readBackup(requireNotNull(file)))

    fun replaceTasks(tasks: List<ModelTask>, beforeCommit: () -> Unit = {}) {
        validateTasks(tasks)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TASKS_TABLE, null, null)
            tasks.forEach { db.insertOrThrow(TASKS_TABLE, null, values(it)) }
            // A cancelled lifecycle must roll back even if cancellation arrived during insertion.
            beforeCommit()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Logical snapshot: never stream the live SQLite file while it is being written. */
    fun exportDB(destination: File) {
        require(!destination.exists()) { "Snapshot destination already exists" }
        val source = readableDatabase
        source.beginTransaction()
        try {
            val tasks = readTasks(source)
            SQLiteDatabase.openOrCreateDatabase(destination, null).use { snapshot ->
                onCreate(snapshot)
                snapshot.version = DATABASE_VERSION
                snapshot.beginTransaction()
                try {
                    tasks.forEach { snapshot.insertOrThrow(TASKS_TABLE, null, values(it)) }
                    snapshot.setTransactionSuccessful()
                } finally {
                    snapshot.endTransaction()
                }
            }
            source.setTransactionSuccessful()
        } catch (error: Exception) {
            SQLiteDatabase.deleteDatabase(destination)
            throw error
        } finally {
            source.endTransaction()
        }
    }

    override fun close() {
        synchronized(TaskDatabase::class.java) {
            super.close()
            if (sInstance === this) sInstance = null
        }
    }

    companion object {
        const val DATABASE_VERSION = 4
        const val DATABASE_NAME = "wordnote_database"
        const val TASKS_TABLE = "tasks_table"
        private const val MAX_NOTES = 100_000
        private val COLUMNS = arrayOf(TASK_TITLE_COLUMN, TASK_DESCRIPTION_COLUMN, TASK_DESCRIPTION_HTML_COLUMN,
            TASK_DATE_COLUMN, TASK_PRIORITY_COLUMN, TASK_STATUS_COLUMN, TASK_TIME_STAMP_COLUMN, TASK_IS_HIDDEN_COLUMN)
        private const val TASKS_TABLE_CREATE_SCRIPT = "CREATE TABLE $TASKS_TABLE (${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT, $TASK_TITLE_COLUMN TEXT NOT NULL, $TASK_DESCRIPTION_COLUMN TEXT NOT NULL, $TASK_DESCRIPTION_HTML_COLUMN TEXT NOT NULL, $TASK_DATE_COLUMN LONG, $TASK_PRIORITY_COLUMN INTEGER, $TASK_STATUS_COLUMN INTEGER, $TASK_TIME_STAMP_COLUMN LONG, $TASK_IS_HIDDEN_COLUMN INTEGER)"
        private var sInstance: TaskDatabase? = null

        @JvmStatic @Synchronized fun getInstance(context: Context?): TaskDatabase =
            sInstance ?: TaskDatabase(requireNotNull(context).applicationContext).also { sInstance = it }

        fun readBackup(file: File): List<ModelTask> {
            require(file.isFile && file.length() in 100..(64L * 1024 * 1024)) { "Invalid backup database" }
            return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                require(db.version in 1..DATABASE_VERSION) { "Unsupported backup version" }
                db.rawQuery("PRAGMA integrity_check", null).use {
                    require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "Damaged backup database" }
                }
                db.rawQuery("SELECT type FROM sqlite_master WHERE name=?", arrayOf(TASKS_TABLE)).use {
                    require(it.moveToFirst() && it.getString(0) == "table") { "Invalid backup schema" }
                }
                readTasks(db).also(::validateTasks)
            }
        }

        fun writeBackup(destination: File, tasks: List<ModelTask>) {
            validateTasks(tasks)
            require(!destination.exists()) { "Snapshot destination already exists" }
            try {
                SQLiteDatabase.openOrCreateDatabase(destination, null).use { db ->
                    db.execSQL(TASKS_TABLE_CREATE_SCRIPT)
                    db.version = DATABASE_VERSION
                    db.beginTransaction()
                    try {
                        tasks.forEach { db.insertOrThrow(TASKS_TABLE, null, values(it)) }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
            } catch (error: Exception) {
                SQLiteDatabase.deleteDatabase(destination)
                throw error
            }
        }

        private fun readTasks(db: SQLiteDatabase): List<ModelTask> {
            val tasks = ArrayList<ModelTask>()
            db.query(TASKS_TABLE, COLUMNS, null, null, null, null, null).use { c ->
                require(c.count <= MAX_NOTES) { "Backup contains too many notes" }
                while (c.moveToNext()) {
                    for (i in 0..2) require(c.getType(i) == Cursor.FIELD_TYPE_STRING) { "Invalid note text" }
                    for (i in 3..7) require(c.getType(i) == Cursor.FIELD_TYPE_INTEGER) { "Invalid note metadata" }
                    // SQLite integers are 64-bit. Validate before narrowing so a large private
                    // flag cannot wrap to zero and turn a restored note into a public one.
                    val priority = c.getLong(4)
                    val status = c.getLong(5)
                    val hidden = c.getLong(7)
                    require(priority in 0L..2L && status in 0L..2L && hidden in setOf(0L, 1L, 3L)) { "Invalid note metadata" }
                    tasks += ModelTask(c.getString(0), c.getString(1), c.getString(2), c.getLong(3),
                        priority.toInt(), status.toInt(), c.getLong(6), hidden.toInt())
                }
            }
            return tasks
        }

        private fun validateTasks(tasks: List<ModelTask>) {
            require(tasks.size <= MAX_NOTES) { "Backup contains too many notes" }
            val identifiers = HashSet<Long>()
            tasks.forEach {
                require(it.title != null && it.description != null && it.descriptionHTML != null && it.date >= 0 &&
                    it.priority in 0..2 && it.status in 0..2 && it.timeStamp > 0 && identifiers.add(it.timeStamp) &&
                    it.isHidden in setOf(0, 1, 3)) { "Invalid note in backup" }
                AttachmentReferences.validate(it)
            }
        }

        private fun values(task: ModelTask) = ContentValues().apply {
            put(TASK_TITLE_COLUMN, task.title)
            put(TASK_DESCRIPTION_COLUMN, task.description)
            put(TASK_DESCRIPTION_HTML_COLUMN, task.descriptionHTML)
            put(TASK_DATE_COLUMN, task.date)
            put(TASK_PRIORITY_COLUMN, task.priority)
            put(TASK_STATUS_COLUMN, task.status)
            put(TASK_TIME_STAMP_COLUMN, task.timeStamp)
            put(TASK_IS_HIDDEN_COLUMN, if (task.isHidden == ModelTask.HIDDEN_FALSE) 0 else 1)
        }
    }
}
