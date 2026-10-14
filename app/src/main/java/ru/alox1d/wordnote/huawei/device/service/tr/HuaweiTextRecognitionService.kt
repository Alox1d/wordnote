package ru.alox1d.wordnote.huawei.domain.services.tr

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import ru.alox1d.wordnote.huawei.domain.service.tr.ITextRecognitionService
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiTextRecognitionService(val activity: Activity) : ITextRecognitionService {

    override fun checkPermissions(): Boolean {
        return activity.checkCallingOrSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

}