package ru.alox1d.wordnote.huawei.domain.service.asr

interface IASRService {

    fun setApiKey()

    fun startASR()

    fun checkPermissions(): Boolean
}