package ru.alox1d.wordnote.huawei.ui.views.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import ru.alox1d.wordnote.huawei.device.service.tr.camera.CameraConfiguration;
import ru.alox1d.wordnote.huawei.ui.views.graphic.BaseGraphic;

public class GraphicOverlay extends View {
    private final Object lock = new Object();
    private int previewWidth;
    private int previewHeight;
    private float widthScaleValue = 1.0f;
    private float heightScaleValue = 1.0f;
    private int cameraFacing = CameraConfiguration.CAMERA_FACING_BACK;
    private final List<BaseGraphic> graphics = new ArrayList<>();

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void clear() {
        synchronized (this.lock) {
            this.graphics.clear();
        }
        this.postInvalidate();
    }

    public void addGraphic(BaseGraphic graphic) {
        synchronized (this.lock) {
            this.graphics.add(graphic);
        }
    }

    public void removeGraphic(BaseGraphic graphic) {
        synchronized (this.lock) {
            this.graphics.remove(graphic);
        }
        this.postInvalidate();
    }

    public void setCameraInfo(int width, int height, int facing) {
        synchronized (this.lock) {
            this.previewWidth = width;
            this.previewHeight = height;
            this.cameraFacing = facing;
        }
        this.postInvalidate();
    }

    public int getCameraFacing() {
        return this.cameraFacing;
    }

    public float getWidthScaleValue() {
        return this.widthScaleValue;
    }

    public float getHeightScaleValue() {
        return this.heightScaleValue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (this.lock) {
            if ((this.previewWidth != 0) && (this.previewHeight != 0)) {
                this.widthScaleValue = (float) canvas.getWidth() / (float) this.previewWidth;
                this.heightScaleValue = (float) canvas.getHeight() / (float) this.previewHeight;
            }
            for (BaseGraphic graphic : this.graphics) {
                graphic.draw(canvas);
            }
        }
    }
}
