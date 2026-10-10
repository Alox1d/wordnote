package ru.alox1d.wordnote.huawei.di.module

import android.content.Context
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.service.ad.HuaweiAdsService
import ru.alox1d.wordnote.huawei.device.service.ad.IAdsService
import ru.alox1d.wordnote.huawei.device.service.drive.HuaweiDriveService
import ru.alox1d.wordnote.huawei.domain.repository.ITaskRepository
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import ru.alox1d.wordnote.huawei.domain.services.asr.HuaweiASRService
import ru.alox1d.wordnote.huawei.domain.usecase.*
import toothpick.config.Module
import toothpick.ktp.binding.bind

class MainModule(context: Context) : Module() {
    init {
        bind<Context>().toInstance(context)
        bind<IDriveService>().toClass<HuaweiDriveService>()
        bind(IDriveService::class.java).to(HuaweiDriveService::class.java)
        bind(IAdsService::class.java).to(HuaweiAdsService::class.java)
        bind<IAdsService>().toClass<HuaweiAdsService>()
        bind<IASRService>().toClass<HuaweiASRService>()

        val db = TaskDatabase.getInstance(context)
        bind(TaskDatabase::class.java)
        val taskRepository = TaskRepository(db.readableDatabase, db.writableDatabase)
        bind<ITaskRepository>().toInstance(taskRepository)
        bind(AddTaskUseCase::class)
        bind(UpdateTaskUseCase::class)
        bind(GetTasksUseCase::class)
        bind(GetTaskUseCase::class)
        bind(DeleteTaskUseCase::class)
    }
}