package ru.alox1d.wordnote.huawei.domain.repository

import ru.alox1d.wordnote.huawei.domain.model.ModelTask

interface ITaskRepository {

    fun getTask(timeStamp: Long): ModelTask?

    fun getTasks(selection: String?, selectionArgs: Array<String?>?, orderBy: String?): List<ModelTask>

    fun updateTask(task: ModelTask)

    fun saveTask(task: ModelTask)

    fun removeTask(timeStamp: Long)


    fun update(column: String, key: Long, value: String?)

    fun update(column: String, key: Long, value: Long)

    fun priority(timeStamp: Long, priority: Int)

    fun status(timeStamp: Long, status: Int)
}