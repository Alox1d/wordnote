package ru.alox1d.wordnote.huawei.domain.services.tr.transactor

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.util.Log
import com.huawei.hmf.tasks.Task
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLLocalTextSetting
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import ru.alox1d.wordnote.huawei.device.service.tr.camera.FrameMetadata
import ru.alox1d.wordnote.huawei.helpers.util.Constant
import ru.alox1d.wordnote.huawei.helpers.util.SharedPreferencesUtil
import ru.alox1d.wordnote.huawei.ui.views.graphic.BaseGraphic
import ru.alox1d.wordnote.huawei.ui.views.graphic.CameraImageGraphic
import ru.alox1d.wordnote.huawei.ui.views.graphic.LocalTextGraphic
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class LocalTextTransactor(handler: Handler, private val mContext: Context) : BaseTransactor<MLText?>() {
    private val detector: MLTextAnalyzer
    var lastResults: MLText? = null
        private set
    val notInFrameLines = mutableListOf<Int>()
    var lastNotInFrameLines = mutableMapOf<Int, MutableList<TextLineContainer>>()
    var lastInFrameLines = mutableMapOf<Int, MutableList<TextLineContainer>>()
    var isSuccessRecognition = false

    var transactingMetaData: FrameMetadata? = null

    var transactingImage: ByteBuffer? = null

    private val mHandler: Handler
    var mCount = 0
    private val language: String
        private get() {
            val position = SharedPreferencesUtil.getInstance(mContext).getStringValue(Constant.POSITION_KEY)
            Log.d(TAG, "position: $position")
            var language = ""
            when (position) {
                Constant.POSITION_CN -> language = "zh"
                Constant.POSITION_EN, Constant.POSITION_LA -> language = "en"
                Constant.POSITION_JA, Constant.POSITION_KO -> language = "ja"
                Constant.POSITION_RU -> language = "ru"
                else -> {
                    language = if (Constant.IS_CHINESE) {
                        "zh"
                    } else {
                        val localLang = Locale.getDefault().language
                        if (localLang.lowercase(Locale.ROOT) == "russian"
                            || localLang.lowercase(Locale.ROOT) == "ru"
                        ) "ru" else "en"
                    }
                    Log.d(TAG, "default value!")
                }
            }
            return language
        }

    override fun stop() {
        super.stop()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG,
                    "Exception thrown while trying to close text transactor: " + e.message)
        }
    }

    override fun detectInImage(image: MLFrame?): Task<MLText?> {
        this.transactingImage = image?.byteBuffer
        // crop bitmap
//        val dstBmp:Bitmap
//            dstBmp = Bitmap.createBitmap(
//                    image.previewBitmap,
//                    image.previewBitmap.width/2-(250f.px.toInt()/2),
//                    image.previewBitmap.height/2-(250f.px.toInt()/2),
//                    250f.px.toInt(),
//                    250f.px.toInt()
//            );
//        val frame = MLFrame.Creator().setBitmap(dstBmp).create()

        return detector.asyncAnalyseFrame(image)
    }

    override suspend fun onSuccess(
            originalCameraImage: Bitmap?,
            results: MLText?,
            frameMetadata: FrameMetadata?,
            graphicOverlay: GraphicOverlay?) {
        lastResults = results
        this.transactingMetaData = frameMetadata
        lastNotInFrameLines = mutableMapOf()
        isSuccessRecognition = false
        graphicOverlay?.clear()
        val blocks = results?.blocks
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && originalCameraImage != null) {
            val imageGraphic = CameraImageGraphic(graphicOverlay, originalCameraImage)
            graphicOverlay?.addGraphic(imageGraphic)
        }
        if (blocks!!.size > 0) {
            mCount = 0
            mHandler.sendEmptyMessage(Constant.SHOW_TAKE_PHOTO_BUTTON)
        } else {
            mCount++
            if (mCount > 1) {
                mHandler.sendEmptyMessage(Constant.HIDE_TAKE_PHOTO_BUTTON)
            }
        }
        for (i in blocks.indices) {
            val lines: MutableList<MLText.TextLine> = blocks[i].contents
            for (j in lines.indices) {
                // Display by line, without displaying empty lines.
                var removed = false
                if (lines[j].stringValue != null && lines[j].stringValue.trim().isNotEmpty()) {
                    val textGraphic: BaseGraphic = LocalTextGraphic(mContext, graphicOverlay,
                            lines[j],
                    onTextNotInFrame = {
                        if (lastNotInFrameLines[i] == null) {
                            lastNotInFrameLines[i] = mutableListOf()
                        }
                        lastNotInFrameLines[i]!!.add(TextLineContainer(j,lines[j]))
                    })
                    graphicOverlay?.addGraphic(textGraphic)
                }
            }
//            notInFrameLines.sortByDescending {
//                it
//            }
        }
        graphicOverlay?.postInvalidate()
        isSuccessRecognition = true
    }

    override fun onFailure(e: Exception?) {
        Log.e(TAG, "Text detection failed: " + e?.message)
    }

    companion object {
        private const val TAG = "TextRecProc"
    }

    init {
//        var lang = Locale.getDefault().language
//        if (!(lang == "ru" || lang == "RU")) lang = "RU"
        val language = language
        Log.d(TAG, "language:$language")
        val options = MLLocalTextSetting.Factory()
                .setOCRMode(MLLocalTextSetting.OCR_TRACKING_MODE)
                .setLanguage(language)
                .create()
        detector = MLAnalyzerFactory.getInstance().getLocalTextAnalyzer(options)
        mHandler = handler
    }


}

val Float.dp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)
val Float.px: Float
    get() = (this * Resources.getSystem().displayMetrics.density)

data class TextLineContainer(val index:Int, val text:MLText.TextLine)