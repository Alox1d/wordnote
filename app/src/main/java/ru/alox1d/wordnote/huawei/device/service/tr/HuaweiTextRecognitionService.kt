package ru.alox1d.wordnote.huawei.domain.services.tr

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import ru.alox1d.wordnote.huawei.domain.service.tr.ITextRecognitionService
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiTextRecognitionService(val activity: Activity) : ITextRecognitionService {

    override fun checkPermissions(): Boolean {
        val permissionStorage = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val permissionCamera = Manifest.permission.CAMERA
        val resInt = activity.checkCallingOrSelfPermission(permissionStorage)
        val resAudio = activity.checkCallingOrSelfPermission(permissionCamera)
        return resInt == PackageManager.PERMISSION_GRANTED && resAudio == PackageManager.PERMISSION_GRANTED
    }

}