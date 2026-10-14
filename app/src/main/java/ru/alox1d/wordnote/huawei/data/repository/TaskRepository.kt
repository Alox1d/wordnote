package ru.alox1d.wordnote.huawei.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase.Companion.TASKS_TABLE
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import ru.alox1d.wordnote.huawei.helpers.*
import toothpick.InjectConstructor

@InjectConstructor
class TaskRepository(
    private val readableDB: SQLiteDatabase,
    private val writableDB: SQLiteDatabase
) : ITaskRepository {
    override fun getTask(timeStamp: Long): ModelTask? {
        var modelTask: ModelTask? = null
        val cursor = readableDB.query(
            TaskDatabase.TASKS_TABLE,
            null,
            SELECTION_TIME_STAMP,
            arrayOf(java.lang.Long.toString(timeStamp)),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            val title = cursor.getString(cursor.getColumnIndexOrThrow(TASK_TITLE_COLUMN))
            val description = cursor.getString(cursor.getColumnIndexOrThrow(TASK_DESCRIPTION_COLUMN))
            val descriptionHTML =
                cursor.getString(cursor.getColumnIndexOrThrow(TASK_DESCRIPTION_HTML_COLUMN))
            val date = cursor.getLong(cursor.getColumnIndexOrThrow(TASK_DATE_COLUMN))
            val priority = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_PRIORITY_COLUMN))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_STATUS_COLUMN))
            val isHidden = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_IS_HIDDEN_COLUMN))
            modelTask = ModelTask(
                title,
                description,
                descriptionHTML,
                date,
                priority,
                status,
                timeStamp,
                isHidden
            )
        }
        cursor.close()
        return modelTask
    }

    override fun getTasks(
        selection: String?,
        selectionArgs: Array<String?>?,
        orderBy: String?
    ): List<ModelTask> {
        val tasks: MutableList<ModelTask> = ArrayList()
        val cursor = readableDB.query(
            TaskDatabase.TASKS_TABLE,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        )
        if (cursor.moveToFirst()) {
            do {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(TASK_TITLE_COLUMN))
                val description = cursor.getString(cursor.getColumnIndexOrThrow(TASK_DESCRIPTION_COLUMN))
                val descriptionHTML =
                    cursor.getString(cursor.getColumnIndexOrThrow(TASK_DESCRIPTION_HTML_COLUMN))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(TASK_DATE_COLUMN))
                val priority = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_PRIORITY_COLUMN))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_STATUS_COLUMN))
                val timeStamp = cursor.getLong(cursor.getColumnIndexOrThrow(TASK_TIME_STAMP_COLUMN))
                val isHidden = cursor.getInt(cursor.getColumnIndexOrThrow(TASK_IS_HIDDEN_COLUMN))
                val modelTask = ModelTask(
                    title,
                    description,
                    descriptionHTML,
                    date,
                    priority,
                    status,
                    timeStamp,
                    isHidden
                )
                tasks.add(modelTask)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return tasks
    }

    fun title(timeStamp: Long, title: String?) {
        update(TASK_TITLE_COLUMN, timeStamp, title)
    }

    fun description(timeStamp: Long, desc: String?) {
        update(TASK_DESCRIPTION_COLUMN, timeStamp, desc)
    }

    fun descriptionHTML(timeStamp: Long, desc: String?) {
        update(TASK_DESCRIPTION_HTML_COLUMN, timeStamp, desc)
    }

    fun date(timeStamp: Long, date: Long) {
        update(TASK_DATE_COLUMN, timeStamp, date)
    }

    override fun priority(timeStamp: Long, priority: Int) {
        update(TASK_PRIORITY_COLUMN, timeStamp, priority.toLong())
    }

    override fun status(timeStamp: Long, status: Int) {
        update(TASK_STATUS_COLUMN, timeStamp, status.toLong())
    }

    fun isHidden(timeStamp: Long, isHidden: Int) {
        update(TASK_IS_HIDDEN_COLUMN, timeStamp, isHidden.toLong())
    }

    override fun updateTask(task: ModelTask) {
        title(task.timeStamp, task.title)
        description(task.timeStamp, task.description)
        descriptionHTML(task.timeStamp, task.descriptionHTML)
        date(task.timeStamp, task.date)
        priority(task.timeStamp, task.priority)
        status(task.timeStamp, task.status)
        isHidden(task.timeStamp, task.isHidden)
    }

    override fun update(column: String, key: Long, value: String?) {
        val contentValues = ContentValues()
        contentValues.put(column, value)
        readableDB.update(
            TaskDatabase.TASKS_TABLE,
            contentValues,
            TASK_TIME_STAMP_COLUMN + " = " + key,
            null
        )
    }

    override fun update(column: String, key: Long, value: Long) {
        val contentValues = ContentValues()
        contentValues.put(column, value)
        readableDB.update(
            TaskDatabase.TASKS_TABLE,
            contentValues,
            TASK_TIME_STAMP_COLUMN + " = " + key,
            null
        )
    }

    override fun saveTask(task: ModelTask) {
        val newValues = ContentValues()
        newValues.put(TASK_TITLE_COLUMN, task.title)
        newValues.put(TASK_DESCRIPTION_COLUMN, task.description)
        newValues.put(TASK_DESCRIPTION_HTML_COLUMN, task.descriptionHTML)
        newValues.put(TASK_DATE_COLUMN, task.date)
        newValues.put(TASK_STATUS_COLUMN, task.status)
        newValues.put(TASK_PRIORITY_COLUMN, task.priority)
        newValues.put(TASK_TIME_STAMP_COLUMN, task.timeStamp)
        newValues.put(TASK_IS_HIDDEN_COLUMN, task.isHidden)
        writableDB.insert(TASKS_TABLE, null, newValues)
    }

    override fun removeTask(timeStamp: Long) {
        writableDB.delete(
            TaskDatabase.TASKS_TABLE,
            SELECTION_TIME_STAMP,
            arrayOf(java.lang.Long.toString(timeStamp))
        )
    }
}