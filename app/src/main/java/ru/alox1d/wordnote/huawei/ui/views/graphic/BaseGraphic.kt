package ru.alox1d.wordnote.huawei.ui.views.graphic

import android.graphics.Canvas
import com.huawei.hms.mlsdk.common.LensEngine
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay

abstract class BaseGraphic(val graphicOverlay: GraphicOverlay?) {
    /**
     * Draw results
     * @param canvas canvas
     */
    abstract fun draw(canvas: Canvas)
    fun scaleX(x: Float): Float {
        if (graphicOverlay != null) {
            return x * graphicOverlay.widthScaleValue
        }
        return 0f
    }

    fun scaleY(y: Float): Float {
        if (graphicOverlay != null) {
            return y * graphicOverlay.heightScaleValue
        }
        return 0f
    }

    fun translateX(x: Float): Float {
        return if (graphicOverlay?.cameraFacing == LensEngine.FRONT_LENS) {
            graphicOverlay.width - scaleX(x)
        } else {
            scaleX(x)
        }

    }

    fun translateY(y: Float): Float {
        return scaleY(y)
    }
}