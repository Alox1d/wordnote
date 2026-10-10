
package ru.alox1d.wordnote.huawei.ui.views.graphic

import android.content.Context
import android.graphics.*
import com.huawei.hms.mlsdk.text.MLText
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay

class LocalTextGraphic(val mContext: Context, overlay: GraphicOverlay?, private val text: MLText.Base, private val onTextNotInFrame: (() -> Unit)) : BaseGraphic(overlay) {
    private val rectPaint: Paint
    private val textPaint: Paint
    override fun draw(canvas: Canvas) {

        // Draw text boundaries accurately based on boundary points.
        val points = text.vertexes
        if (points != null && points.size == 4) {
            for (i in points.indices) {
                points[i].x = translateX(points[i].x.toFloat()).toInt()
                points[i].y = translateY(points[i].y.toFloat()).toInt()
            }
            if (!isTextInFrame(canvas, points)) {
                onTextNotInFrame()
                return
            }
            val pts = floatArrayOf(points[0].x.toFloat(), points[0].y.toFloat(), points[1].x.toFloat(), points[1].y.toFloat(),
                    points[1].x.toFloat(), points[1].y.toFloat(), points[2].x.toFloat(), points[2].y.toFloat(),
                    points[2].x.toFloat(), points[2].y.toFloat(), points[3].x.toFloat(), points[3].y.toFloat(),
                    points[3].x.toFloat(), points[3].y.toFloat(), points[0].x.toFloat(), points[0].y.toFloat())
            val averageHeight = (points[3].y - points[0].y + (points[2].y - points[1].y)) / 2.0f
            val textSize = averageHeight * 0.7f
            val offset = averageHeight / 4
            textPaint.textSize = textSize
            canvas.drawLines(pts, rectPaint)
            val path = Path()
            path.moveTo(points[3].x.toFloat(), points[3].y - offset)
            path.lineTo(points[2].x.toFloat(), points[2].y - offset)
            canvas.drawLines(pts, rectPaint)
            canvas.drawTextOnPath(text.stringValue, path, 0f, 0f, textPaint)
        } else {
            onTextNotInFrame()
            return
        }
    }

    private fun isTextInFrame(canvas: Canvas, points: Array<Point>): Boolean {
        val mCanvasCenterWidth = canvas.width / 2.0f
        val mCanvasCenterHeight = (canvas.height / 2.0f) - 50
        val halfFrameWidth = mContext.resources.getDimension(R.dimen.text_recognition_frame_width) / 2.0f
        val halfFrameHeight = mContext.resources.getDimension(R.dimen.text_recognition_frame_height) / 2.0f
        val mLeftBorder = mCanvasCenterWidth - halfFrameWidth
        val mRightBorder = mCanvasCenterWidth + halfFrameWidth
        val mBottomBorder = mCanvasCenterHeight + halfFrameHeight
        val mTopBorder = mCanvasCenterHeight - halfFrameHeight
        if (points[0].x < mLeftBorder || points[0].y < mTopBorder
                || points[1].x > mRightBorder || points[1].y < mTopBorder
                || points[2].x > mRightBorder || points[2].y > mBottomBorder
                || points[3].x < mLeftBorder || points[3].y > mBottomBorder) {
            return false
        }
        return true
    }

    companion object {
        private const val TEXT_COLOR = Color.WHITE
        private const val TEXT_SIZE = 45.0f
        private const val STROKE_WIDTH = 4.0f
    }

    init {
        rectPaint = Paint()
        rectPaint.color = TEXT_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH
        textPaint = Paint()
        textPaint.color = TEXT_COLOR
        textPaint.textSize = TEXT_SIZE
    }
}
