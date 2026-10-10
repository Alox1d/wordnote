package ru.alox1d.wordnote.huawei.ui.scenes.textrecognition

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.ActivityCompat
import com.huawei.hms.mlsdk.text.MLText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.device.service.tr.camera.CameraConfiguration
import ru.alox1d.wordnote.huawei.device.service.tr.camera.LensEngine
import ru.alox1d.wordnote.huawei.device.service.tr.camera.LensEnginePreview
import ru.alox1d.wordnote.huawei.domain.services.tr.processor.LocalDataProcessor
import ru.alox1d.wordnote.huawei.domain.services.tr.transactor.BaseTransactor.Companion.getBitmap
import ru.alox1d.wordnote.huawei.domain.services.tr.transactor.LocalTextTransactor
import ru.alox1d.wordnote.huawei.domain.services.tr.transactor.TextLineContainer
import ru.alox1d.wordnote.huawei.helpers.util.Constant
import ru.alox1d.wordnote.huawei.helpers.util.SharedPreferencesUtil
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.AddPictureDialog.ClickListener
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay
import ru.alox1d.wordnote.huawei.ui.views.overlay.ZoomImageView
import java.io.IOException
import java.lang.ref.WeakReference

class TextRecognitionActivity : BaseTextRecognitionActivity(), ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener {
    private var lensEngine: LensEngine? = null
    private var preview: LensEnginePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private lateinit var takePicture: ImageButton
    private lateinit var imageSwitch: ImageButton
    private lateinit var zoomImageLayout: RelativeLayout
    private lateinit var textFrame: FrameLayout
    private lateinit var btnLanguage: ImageButton
    private lateinit var zoomImageView: ZoomImageView
    private lateinit var zoomImageClose: ImageButton
    var cameraConfiguration: CameraConfiguration? = null
    private var facing = CameraConfiguration.CAMERA_FACING_BACK
    private var mCamera: Camera? = null
    private var isLandScape = false
    private var bitmap: Bitmap? = null
    private var bitmapCopy: Bitmap? = null
    private var localTextTransactor: LocalTextTransactor? = null
    private val mHandler: Handler = MsgHandler(this)
    private var languageDialog: Dialog? = null
    private var addPictureDialog: AddPictureDialog? = null
    private lateinit var textCN: TextView
    private lateinit var textEN: TextView
    private lateinit var textJN: TextView
    private lateinit var textKN: TextView
    private lateinit var textLN: TextView
    private lateinit var textRU: TextView
    private var textType = Constant.POSITION_CN
    private var isInitialization = false

    private class MsgHandler(mainActivity: TextRecognitionActivity) : Handler() {
        var mMainActivityWeakReference: WeakReference<TextRecognitionActivity>
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val mainActivity = mMainActivityWeakReference.get() ?: return
            Log.d(TAG, "msg what :" + msg.what)
            if (msg.what == Constant.SHOW_TAKE_PHOTO_BUTTON) {
                mainActivity.setVisible()
            } else if (msg.what == Constant.HIDE_TAKE_PHOTO_BUTTON) {
                mainActivity.setGone()
            }
        }

        init {
            mMainActivityWeakReference = WeakReference(mainActivity)
        }
    }

    private fun setVisible() {
        if (takePicture.visibility == View.GONE) {
            takePicture.visibility = View.VISIBLE
        }
    }

    private fun setGone() {
        if (takePicture.visibility == View.VISIBLE) {
            takePicture.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_text_recognition)
        setApiKey()
        if (savedInstanceState != null) {
            facing = savedInstanceState.getInt(Constant.CAMERA_FACING)
        }
        preview = findViewById(R.id.live_preview)
        graphicOverlay = findViewById(R.id.live_overlay)
        cameraConfiguration = CameraConfiguration()
        cameraConfiguration!!.setCameraFacing(facing)
        initViews()
        isLandScape = this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        createLensEngine()
        setStatusBar()
        //initAttention();
    }

    private fun initAttention() {
        val toast = Toast.makeText(this, R.string.offline_text_recognition, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.TOP, 0, 0)
        toast.show()
    }

    private fun initViews() {
        takePicture = findViewById(R.id.takePicture)
        takePicture.setOnClickListener(this)
        imageSwitch = findViewById(R.id.text_imageSwitch)
        imageSwitch.setOnClickListener(this)
        zoomImageLayout = findViewById(R.id.zoomImageLayout)
        textFrame = findViewById(R.id.fl_text)
        btnLanguage = findViewById(R.id.language_setting)
        zoomImageView = findViewById(R.id.take_picture_overlay)
        zoomImageClose = findViewById(R.id.zoomImageClose)
        zoomImageClose.setOnClickListener(this)
        findViewById<View>(R.id.back).setOnClickListener(this)
        findViewById<View>(R.id.tv_offline_text_recognition).setOnClickListener(this)
        findViewById<View>(R.id.language_setting).setOnClickListener(this)
        createLanguageDialog()
        createAddPictureDialog()
    }

    override fun onClick(view: View) {
        if (view.id == R.id.takePicture) {
            takePicture()
            btnLanguage.visibility = View.INVISIBLE
        } else if (view.id == R.id.zoomImageClose) {
            returnToTakePicture()
        } else if (view.id == R.id.text_imageSwitch) {
            showAddPictureDialog()
        } else if (view.id == R.id.language_setting) {
            showLanguageDialog()
        } else if (view.id == R.id.simple_cn) {
            SharedPreferencesUtil.getInstance(this)
                    .putStringValue(Constant.POSITION_KEY, Constant.POSITION_CN)
            languageDialog!!.dismiss()
            restartLensEngine(Constant.POSITION_CN)
        } else if (view.id == R.id.english) {
            SharedPreferencesUtil.getInstance(this)
                    .putStringValue(Constant.POSITION_KEY, Constant.POSITION_EN)
            languageDialog!!.dismiss()
            preview!!.release()
            restartLensEngine(Constant.POSITION_EN)
        } else if (view.id == R.id.russian) {
            SharedPreferencesUtil.getInstance(this)
                .putStringValue(Constant.POSITION_KEY, Constant.POSITION_RU)
            languageDialog!!.dismiss()
            preview!!.release()
            restartLensEngine(Constant.POSITION_RU)
        } else if (view.id == R.id.japanese) {
            SharedPreferencesUtil.getInstance(this)
                    .putStringValue(Constant.POSITION_KEY, Constant.POSITION_JA)
            languageDialog!!.dismiss()
            preview!!.release()
            restartLensEngine(Constant.POSITION_JA)
        } else if (view.id == R.id.korean) {
            SharedPreferencesUtil.getInstance(this)
                    .putStringValue(Constant.POSITION_KEY, Constant.POSITION_KO)
            languageDialog!!.dismiss()
            preview!!.release()
            restartLensEngine(Constant.POSITION_KO)
        } else if (view.id == R.id.latin) {
            SharedPreferencesUtil.getInstance(this)
                    .putStringValue(Constant.POSITION_KEY, Constant.POSITION_LA)
            languageDialog!!.dismiss()
            preview!!.release()
            restartLensEngine(Constant.POSITION_LA)
        } else if (view.id == R.id.back) {
            releaseLensEngine()
            finish()
        }
    }

    protected fun returnToTakePicture() {
        zoomImageLayout.visibility = View.GONE
        textFrame.visibility = View.VISIBLE
        btnLanguage.visibility = View.VISIBLE
        takePicture.visibility = View.VISIBLE
        recycleBitmap()
    }

    private fun restartLensEngine(type: String) {
        if (textType == type) {
            return
        }
        lensEngine!!.release()
        lensEngine = null
        createLensEngine()
        startLensEngine()
        if (lensEngine == null || lensEngine!!.getCamera() == null) {
            return
        }
        mCamera = lensEngine!!.getCamera()
        try {
            mCamera?.setPreviewDisplay(preview!!.surfaceHolder)
        } catch (e: IOException) {
            Log.d(TAG, "initViews IOException")
        }
    }

    override fun onBackPressed() {
        if (zoomImageLayout.visibility == View.VISIBLE) {
            returnToTakePicture()
        } else {
            super.onBackPressed()
            releaseLensEngine()
        }
    }

    private fun createLanguageDialog() {
        languageDialog = Dialog(this, R.style.AddPictureDialog)
        val view = View.inflate(this, R.layout.dialog_language_setting, null)
        // Set up a custom layout
        languageDialog!!.setContentView(view)
        textCN = view.findViewById(R.id.simple_cn)
        textCN.setOnClickListener(this)
        textEN = view.findViewById(R.id.english)
        textEN.setOnClickListener(this)
        textJN = view.findViewById(R.id.japanese)
        textJN.setOnClickListener(this)
        textKN = view.findViewById(R.id.korean)
        textKN.setOnClickListener(this)
        textLN = view.findViewById(R.id.latin)
        textLN.setOnClickListener(this)
        textRU = view.findViewById(R.id.russian)
        textRU.setOnClickListener(this)
        languageDialog!!.setCanceledOnTouchOutside(true)
        // Set the size of the dialog
        val dialogWindow = languageDialog!!.window
        val layoutParams = dialogWindow!!.attributes
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.BOTTOM
        dialogWindow.attributes = layoutParams
    }

    private fun showLanguageDialog() {
        initDialogViews()
        languageDialog!!.show()
    }

    private fun createAddPictureDialog() {
        addPictureDialog = AddPictureDialog(this, AddPictureDialog.TYPE_NORMAL)
        val intent = Intent(this@TextRecognitionActivity, RemoteDetectionActivity::class.java)
        intent.putExtra(Constant.MODEL_TYPE, Constant.CLOUD_TEXT_DETECTION)
        addPictureDialog!!.setClickListener(object : ClickListener {
            override fun takePicture() {
                lensEngine!!.release()
                isInitialization = false
                intent.putExtra(Constant.ADD_PICTURE_TYPE, Constant.TYPE_TAKE_PHOTO)
                this@TextRecognitionActivity.startActivityForResult(intent, RemoteDetectionActivity.REQUEST)
            }

            override fun selectImage() {
                intent.putExtra(Constant.ADD_PICTURE_TYPE, Constant.TYPE_SELECT_IMAGE)
                this@TextRecognitionActivity.startActivityForResult(intent, RemoteDetectionActivity.REQUEST)
            }

            override fun doExtend() {}
        })
    }

    private fun showAddPictureDialog() {
        addPictureDialog!!.show()
    }

    private fun initDialogViews() {
        val position = SharedPreferencesUtil.getInstance(this).getStringValue(Constant.POSITION_KEY)
        textType = position
        textCN.isSelected = false
        textEN.isSelected = false
        textJN.isSelected = false
        textLN.isSelected = false
        textKN.isSelected = false
        textRU.isSelected = false
        when (position) {
            Constant.POSITION_CN -> textCN.isSelected = true
            Constant.POSITION_EN -> textEN.isSelected = true
            Constant.POSITION_LA -> textLN.isSelected = true
            Constant.POSITION_JA -> textJN.isSelected = true
            Constant.POSITION_KO -> textKN.isSelected = true
            Constant.POSITION_RU -> textRU.isSelected = true
            else -> {}
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(Constant.CAMERA_FACING, facing)
        super.onSaveInstanceState(outState)
    }

    private fun createLensEngine() {
        if (lensEngine == null) {
            lensEngine = LensEngine(this, cameraConfiguration, graphicOverlay)
        }
        try {
            localTextTransactor = LocalTextTransactor(mHandler, this)
            lensEngine!!.setMachineLearningFrameTransactor(localTextTransactor)
            isInitialization = true
        } catch (e: Exception) {
            Toast.makeText(
                    this,
                    "Can not create image transactor: " + e.message,
                    Toast.LENGTH_LONG)
                    .show()
        }
    }

    private fun startLensEngine() {
        if (lensEngine != null) {
            try {
                preview!!.start(lensEngine, false)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start lensEngine.", e)
                lensEngine!!.release()
                lensEngine = null
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (!isInitialization) {
            createLensEngine()
        }
        startLensEngine()
    }

    override fun onStop() {
        super.onStop()
        preview!!.stop()
    }

    private fun releaseLensEngine() {
        if (lensEngine != null) {
            lensEngine!!.release()
            lensEngine = null
        }
        recycleBitmap()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLensEngine()
    }

    private fun recycleBitmap() {
        if (bitmap != null && !bitmap!!.isRecycled) {
            bitmap!!.recycle()
            bitmap = null
        }
        if (bitmapCopy != null && !bitmapCopy!!.isRecycled) {
            bitmapCopy!!.recycle()
            bitmapCopy = null
        }
    }

    private fun takePicture() {
        zoomImageLayout.visibility = View.VISIBLE
        textFrame.visibility = View.INVISIBLE
        takePicture.visibility = View.INVISIBLE
        val localDataProcessor = LocalDataProcessor(this)
        localDataProcessor.isLandScape = isLandScape
        bitmap = getBitmap(localTextTransactor!!.transactingImage, localTextTransactor!!.transactingMetaData)
        var previewWidth = localDataProcessor.getMaxWidthOfImage(localTextTransactor!!.transactingMetaData!!)!!.toFloat()
        var previewHeight = localDataProcessor.getMaxHeightOfImage(localTextTransactor!!.transactingMetaData!!)!!.toFloat()
        if (isLandScape) {
            previewWidth = localDataProcessor.getMaxHeightOfImage(localTextTransactor!!.transactingMetaData!!)!!.toFloat()
            previewHeight = localDataProcessor.getMaxWidthOfImage(localTextTransactor!!.transactingMetaData!!)!!.toFloat()
        }
        bitmapCopy = Bitmap.createBitmap(bitmap!!).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmapCopy!!)
        val min = Math.min(previewWidth, previewHeight)
        val max = Math.max(previewWidth, previewHeight)
        if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            localDataProcessor.setCameraInfo(graphicOverlay!!, canvas, min, max)
        } else {
            localDataProcessor.setCameraInfo(graphicOverlay!!, canvas, max, min)
        }
        // block the current thread while waiting for condition
        // this is required to use a suspend function
        val result = runBlocking {
            Log.i(TAG, "waiting for result started")
            waitForCondition(1000, 100)
        }
        val blocks: MutableList<MLText.Block> = ArrayList()
        blocks.addAll(localTextTransactor!!.lastResults!!.blocks)
        val resultingText = removeTextNotInFrame(blocks)
        localDataProcessor.drawHmsMLVisionText(canvas, blocks)
        zoomImageView.setImageBitmap(bitmapCopy)
        initTextSend(resultingText)
    }

    private fun removeTextNotInFrame(blocks: List<MLText.Block>): String {
        val lastNotInFrameBlocks: Map<Int, List<TextLineContainer>> = localTextTransactor!!.lastNotInFrameLines
        var value = ""
        val originalBlocks = localTextTransactor!!.lastResults!!.blocks
        for (i in originalBlocks.indices.reversed()) {
            val originalLines = originalBlocks[i].contents
            val lines = blocks[i].contents
            var found = false
            for ((key, textLineContainers) in lastNotInFrameBlocks) {
                if (key == i && textLineContainers != null) {
                    for ((_, text) in textLineContainers) {
                        if (lines.remove(text)) {
                            found = true
                        }
                    }
                }
            }
        }
        for (i in blocks.indices.reversed()) {
            val lines = blocks[i].contents
            for (j in lines.indices.reversed()) {
                try {
                    value = if (value == "") StringBuilder(value).insert(0, lines[j].stringValue).toString() else {
                        StringBuilder(value).insert(0, lines[j].stringValue + "\n").toString()
                    }
                } catch (ignored: IndexOutOfBoundsException) {
                }
            }
        }
        return value
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RemoteDetectionActivity.REQUEST && resultCode == RESULT_OK) {
            sendText(data!!.extras!!.getString(ML_TEXT))
        } else {
            Log.w(TAG, "Image recognition without result.")
        }
    }

    companion object {
        private const val TAG = "TextRecognitionActivity"
        const val REQUEST = 123
    }

    // waits until condition is ready and return true when it is; otherwise returns false
// tailrec function gets rolled out into normal loop under the hood
    tailrec suspend fun waitForCondition(maxDelay: Long, checkPeriod: Long): Boolean {
        if (maxDelay < 0) return false
        if (localTextTransactor?.isSuccessRecognition!!) return true
        delay(checkPeriod)
        return waitForCondition(maxDelay - checkPeriod, checkPeriod)
    }
}