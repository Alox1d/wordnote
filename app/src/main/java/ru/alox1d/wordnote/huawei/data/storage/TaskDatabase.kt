package ru.alox1d.wordnote.huawei.data.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import ru.alox1d.wordnote.huawei.helpers.*
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import java.io.File
import java.io.IOException

class TaskDatabase private constructor(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    //    private val updateManager: DBUpdateManager
    private val dbFile: File?

    fun reopen(context: Context) {
        sInstance = TaskDatabase(context.applicationContext)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TASKS_TABLE_CREATE_SCRIPT)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE " + TASKS_TABLE)
        onCreate(db)
    }


    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.disableWriteAheadLogging()
    }

    fun importDB(file: File?) {
        try {
            Utils.FileUtils.copyFile(file, dbFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val DATABASE_VERSION = 4
        const val DATABASE_NAME = "wordnote_database"
        const val TASKS_TABLE = "tasks_table"
        private const val TASKS_TABLE_CREATE_SCRIPT = ("CREATE TABLE "
                + TASKS_TABLE + " (" + BaseColumns._ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, " + TASK_TITLE_COLUMN + " TEXT NOT NULL, " + TASK_DESCRIPTION_COLUMN
                + " TEXT NOT NULL, " + TASK_DESCRIPTION_HTML_COLUMN + " TEXT NOT NULL, "
                + TASK_DATE_COLUMN + " LONG, " + TASK_PRIORITY_COLUMN + " INTEGER, "
                + TASK_STATUS_COLUMN + " INTEGER, " + TASK_TIME_STAMP_COLUMN + " LONG, " + TASK_IS_HIDDEN_COLUMN + " INTEGER)")


        private lateinit var sInstance: TaskDatabase

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context?): TaskDatabase {

            // Use the application context, which will ensure that you
            // don't accidentally leak an Activity's context.
            // See this article for more information: http://bit.ly/6LRzfx
            sInstance = TaskDatabase(context?.applicationContext)
            return sInstance
        }
    }

    init {
//        taskRepository = TaskRepository(readableDatabase)
//        updateManager = DBUpdateManager(writableDatabase)
        dbFile = context?.getDatabasePath(DATABASE_NAME)
    }
}