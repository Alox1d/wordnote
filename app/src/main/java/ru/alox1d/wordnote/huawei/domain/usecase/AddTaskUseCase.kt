package ru.alox1d.wordnote.huawei.domain.usecase

import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import toothpick.InjectConstructor

@InjectConstructor
class AddTaskUseCase(val taskRepository: ITaskRepository) {
    fun buildUseCase(task:ModelTask){
        taskRepository.saveTask(task)
    }
}