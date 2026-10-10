package ru.alox1d.wordnote.huawei.domain.usecase

import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import toothpick.InjectConstructor

@InjectConstructor
class DeleteTaskUseCase(val taskRepository: ITaskRepository) {
    fun buildUseCase(timeStamp:Long){
        taskRepository.removeTask(timeStamp)
    }
}