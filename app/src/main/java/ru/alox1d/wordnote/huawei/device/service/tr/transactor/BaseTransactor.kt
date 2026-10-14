package ru.alox1d.wordnote.huawei.domain.services.tr.transactor

import android.graphics.*
import android.util.Log
import com.huawei.hmf.tasks.Task
import com.huawei.hms.mlsdk.common.MLFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.alox1d.wordnote.huawei.device.service.tr.camera.CameraConfiguration
import ru.alox1d.wordnote.huawei.device.service.tr.camera.FrameMetadata
import ru.alox1d.wordnote.huawei.device.service.tr.transactor.ImageTransactor
import ru.alox1d.wordnote.huawei.helpers.util.BitmapUtils
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

abstract class BaseTransactor<T> : ImageTransactor {

    companion object{
        val TAG = this::class.java.name
        /**
         * Convert nv21 format byte buffer to bitmap
         *
         * @param data data
         * @param metadata metadata
         * @return Bitmap object
         */
        fun getBitmap(data: ByteBuffer?, metadata: FrameMetadata?): Bitmap? {
            data!!.rewind()
            val imageBuffer = ByteArray(data.limit())
            data[imageBuffer, 0, imageBuffer.size]
            try {
                val yuvImage = YuvImage(
                    imageBuffer, ImageFormat.NV21, metadata!!.width,
                    metadata.height, null
                )
                val stream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, metadata.width, metadata.height), 80, stream)
                val bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
                stream.close()
                return BitmapUtils.rotateBitmap(bitmap, metadata.rotation, metadata.cameraFacing)
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Error: " + e.message)
            }
            return null
        }
    }

    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var stopped = false

    // To keep the latest images and its metadata.
    private var latestImage: ByteBuffer? = null
    private var latestImageMetaData: FrameMetadata? = null

    // To keep the images and metadata in process.
    private var transactingImage: ByteBuffer? = null
    private var transactingMetaData: FrameMetadata? = null
    @Synchronized
    override fun process(data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay) {
        if (stopped) return
        latestImage = data
        latestImageMetaData = frameMetadata
        if (transactingImage == null && transactingMetaData == null) {
            processLatestImage(graphicOverlay)
        }
    }

    @Synchronized
    override fun process(bitmap: Bitmap, graphicOverlay: GraphicOverlay) {
        if (stopped) return
        val frame = MLFrame.Creator().setBitmap(bitmap).create()
        detectInVisionImage(bitmap, frame, null, graphicOverlay)
    }

    @Synchronized
    private fun processLatestImage(graphicOverlay: GraphicOverlay) {
        if (stopped) return
        transactingImage = latestImage
        transactingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (transactingImage != null && transactingMetaData != null) {
            val width: Int
            val height: Int
            width = transactingMetaData!!.width
            height = transactingMetaData!!.height
            val metadata = MLFrame.Property.Creator()
                    .setFormatType(ImageFormat.NV21)
                    .setWidth(width)
                    .setHeight(height)
                    .setQuadrant(transactingMetaData!!.rotation)
                    .create()
            val bitmap = getBitmap(transactingImage, transactingMetaData)
            detectInVisionImage(
                    bitmap, MLFrame.fromByteBuffer(transactingImage, metadata), transactingMetaData, graphicOverlay)
        }
    }

    private fun detectInVisionImage(bitmap: Bitmap?, image: MLFrame, metadata: FrameMetadata?,
                                    graphicOverlay: GraphicOverlay) {
        detectInImage(image)
                .addOnSuccessListener { results ->
                    callbackScope.launch {
                        try {
                            if (!stopped && (metadata == null || metadata.cameraFacing == CameraConfiguration.getCameraFacing())) {
                                this@BaseTransactor.onSuccess(bitmap, results, metadata, graphicOverlay)
                            }
                        } finally {
                            if (metadata != null) processLatestImage(graphicOverlay)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    callbackScope.launch {
                        try {
                            if (!stopped) this@BaseTransactor.onFailure(e)
                        } finally {
                            if (metadata != null) processLatestImage(graphicOverlay)
                        }
                    }
                }
    }

    @Synchronized
    override fun stop() {
        stopped = true
        callbackScope.cancel()
        latestImage = null
        latestImageMetaData = null
        transactingImage = null
        transactingMetaData = null
    }

    /**
     * detect
     *
     * @param image image
     * @return Task that encapsulate results
     */
    protected abstract fun detectInImage(image: MLFrame?): Task<T>

    /**
     * Callback that executes with a successful detection result.
     *
     * @param originalCameraImage hold the original image from camera, used to draw the background
     * image.
     * @param results             results
     * @param frameMetadata       metadata
     * @param graphicOverlay      graphicOverlay
     */
    protected abstract suspend fun onSuccess(
            originalCameraImage: Bitmap?,
            results: T,
            frameMetadata: FrameMetadata?, graphicOverlay: GraphicOverlay?)

    /**
     * Callback that executes with a failed detection result.
     *
     * @param e exception
     */
    protected abstract fun onFailure(e: Exception?)
}