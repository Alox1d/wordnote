package ru.alox1d.wordnote.huawei.di.module

import androidx.appcompat.app.AppCompatActivity
import ru.alox1d.wordnote.huawei.domain.service.fido.IFIDOService
import ru.alox1d.wordnote.huawei.domain.services.fido.HuaweiFIDOService
import toothpick.config.Module
import toothpick.ktp.binding.bind

class AppCompatActivityModule(appCompatActivity: AppCompatActivity) : Module() {
    init {
        bind<AppCompatActivity>().toInstance(appCompatActivity)
        bind<IFIDOService>().toClass<HuaweiFIDOService>()
    }
}