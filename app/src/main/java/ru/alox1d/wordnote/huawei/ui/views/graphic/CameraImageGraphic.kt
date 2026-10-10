package ru.alox1d.wordnote.huawei.ui.views.graphic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay

class CameraImageGraphic : BaseGraphic {
    private val bitmap: Bitmap
    private var isFill = true

    constructor(overlay: GraphicOverlay?, bitmap: Bitmap) : super(overlay) {
        this.bitmap = bitmap
    }

    constructor(overlay: GraphicOverlay?, bitmap: Bitmap, isFill: Boolean) : super(overlay) {
        this.bitmap = bitmap
        this.isFill = isFill
    }

    override fun draw(canvas: Canvas) {
        val width: Int
        val height: Int
        if (isFill) {
            width = canvas.width
            height = canvas.height
        } else {
            width = bitmap.width
            height = bitmap.height
        }
        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
    }
}