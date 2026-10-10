package ru.alox1d.wordnote.huawei.di.module

import android.app.Activity
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.domain.service.fido.IFIDOService
import ru.alox1d.wordnote.huawei.domain.service.tr.ITextRecognitionService
import ru.alox1d.wordnote.huawei.domain.services.asr.HuaweiASRService
import ru.alox1d.wordnote.huawei.domain.services.fido.HuaweiFIDOService
import ru.alox1d.wordnote.huawei.domain.services.tr.HuaweiTextRecognitionService
import toothpick.config.Module
import toothpick.ktp.binding.bind

class ActivityModule(activity: Activity) : Module() {
    init {
        bind<Activity>().toInstance(activity)
        bind<IASRService>().toClass<HuaweiASRService>()
        bind<IFIDOService>().toClass<HuaweiFIDOService>()
        bind<ITextRecognitionService>().toClass<HuaweiTextRecognitionService>()
    }
}