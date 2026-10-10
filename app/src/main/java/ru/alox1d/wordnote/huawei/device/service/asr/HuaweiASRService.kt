package ru.alox1d.wordnote.huawei.domain.services.asr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.mlplugin.asr.MLAsrCaptureActivity
import com.huawei.hms.mlplugin.asr.MLAsrCaptureConstants
import com.huawei.hms.mlsdk.common.MLApplication
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiASRService(val activity: Activity) : IASRService {
    companion object {
        val API_KEY = "client/api_key"
    }

    init {
        setApiKey();
    }


    override fun setApiKey() {
        val config = AGConnectServicesConfig.fromContext(activity.applicationContext)
        MLApplication.getInstance().setApiKey(config.getString(API_KEY))
    }

    override fun startASR() {
        val intent = Intent(activity.applicationContext, MLAsrCaptureActivity::class.java)
                .putExtra(MLAsrCaptureConstants.LANGUAGE, "en-US")
                .putExtra(MLAsrCaptureConstants.FEATURE, MLAsrCaptureConstants.FEATURE_WORDFLUX)
        activity.startActivityForResult(intent, 100)
    }

    override fun checkPermissions(): Boolean {
        val permissionInt = Manifest.permission.INTERNET
        val permissionAudio = Manifest.permission.RECORD_AUDIO
        val resInt = activity.checkCallingOrSelfPermission(permissionInt)
        val resAudio = activity.checkCallingOrSelfPermission(permissionAudio)
        return resInt == PackageManager.PERMISSION_GRANTED && resAudio == PackageManager.PERMISSION_GRANTED
    }
}