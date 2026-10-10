package ru.alox1d.wordnote.huawei.domain.usecase

import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import toothpick.InjectConstructor

@InjectConstructor
class GetTasksUseCase(val taskRepository: ITaskRepository) {
    fun buildUseCase(selection: String?, selectionArgs: Array<String?>?, orderBy: String?): List<ModelTask>{
        return taskRepository.getTasks(selection, selectionArgs, orderBy)
    }
}