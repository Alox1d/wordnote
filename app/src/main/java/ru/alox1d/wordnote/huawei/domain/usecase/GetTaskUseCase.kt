package ru.alox1d.wordnote.huawei.domain.usecase

import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import toothpick.InjectConstructor

@InjectConstructor
class GetTaskUseCase(val taskRepository: ITaskRepository) {
    fun buildUseCase(timeStamp:Long):ModelTask?{
       return taskRepository.getTask(timeStamp = timeStamp)
    }
}