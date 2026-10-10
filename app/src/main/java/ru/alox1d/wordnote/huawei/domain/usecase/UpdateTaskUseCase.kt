package ru.alox1d.wordnote.huawei.domain.usecase

import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import toothpick.InjectConstructor

@InjectConstructor
class UpdateTaskUseCase(val taskRepository: ITaskRepository) {

    //    fun buildUseCase(column: String, key: Long, value: String?){
//        taskRepository.update(column, key, value)
//    }
//    fun buildUseCase(column: String, key: Long, value: Long){
//        taskRepository.update(column, key, value)
//    }
    fun buildUseCase(task: ModelTask) {
        taskRepository.updateTask(task)
    }

    fun buildUseCase(timeStamp: Long, status: Int) {
        taskRepository.status(timeStamp, status)
    }
}