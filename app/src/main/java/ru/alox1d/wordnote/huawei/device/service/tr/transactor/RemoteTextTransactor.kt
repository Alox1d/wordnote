package ru.alox1d.wordnote.huawei.domain.services.tr.transactor

import android.graphics.Bitmap
import android.os.Handler
import android.util.Log
import com.huawei.hmf.tasks.Task
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLRemoteTextSetting
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import ru.alox1d.wordnote.huawei.device.service.tr.callback.CouldInfoResultCallBack
import ru.alox1d.wordnote.huawei.device.service.tr.camera.FrameMetadata
import ru.alox1d.wordnote.huawei.helpers.util.Constant
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay

class RemoteTextTransactor(handler: Handler) : BaseTransactor<MLText?>() {
    private val detector: MLTextAnalyzer
    private var callBack: CouldInfoResultCallBack? = null
    private val handler: Handler
    override fun detectInImage(image: MLFrame?): Task<MLText?> {
        return detector.asyncAnalyseFrame(image)
    }

    fun addCouldTextResultCallBack(callBack: CouldInfoResultCallBack?) {
        this.callBack = callBack
    }

    override suspend fun onSuccess(
        originalCameraImage: Bitmap?,
        text: MLText?,
        frameMetadata: FrameMetadata?,
        graphicOverlay: GraphicOverlay?) {
        handler.sendEmptyMessage(Constant.GET_DATA_SUCCESS)
        graphicOverlay?.clear()
        callBack!!.onSuccessForText(originalCameraImage, text, graphicOverlay)
    }

    protected override fun onFailure(e: Exception?) {
        handler.sendEmptyMessage(Constant.GET_DATA_FAILED)
        Log.e(TAG, "Remote text detection failed: " + e?.message)
    }

    companion object {
        private const val TAG = "RemoteTextTransactor"
    }

    init {
        val languageList: MutableList<String?> = ArrayList<String?>()
        languageList.add("en")
        languageList.add("ru")
        val options = MLRemoteTextSetting.Factory().setBorderType(MLRemoteTextSetting.ARC)
                .setLanguageList(languageList)
                .create()
        detector = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(options)
        this.handler = handler
    }
}