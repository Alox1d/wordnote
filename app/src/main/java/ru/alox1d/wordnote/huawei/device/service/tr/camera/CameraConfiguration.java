package ru.alox1d.wordnote.huawei.device.service.tr.camera;

import android.hardware.Camera;

public class CameraConfiguration {

    public static final int CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK;
    public static final int CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;

    public static final int MAX_WIDTH = 960;
    public static final int MAX_HEIGHT = 720;
    protected static int cameraFacing = CameraConfiguration.CAMERA_FACING_BACK;

    private float fps = 20.0f;
    private int previewWidth = CameraConfiguration.MAX_WIDTH;
    private int previewHeight = CameraConfiguration.MAX_HEIGHT;
    private boolean isAutoFocus = true;
    private static final Object lock = new Object();

    public void setCameraFacing(int facing) {
        synchronized (lock) {
            if ((facing != CameraConfiguration.CAMERA_FACING_BACK) && (facing != CameraConfiguration.CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid camera: " + facing);
            }
            cameraFacing = facing;
        }
    }

    public float getFps() {
        return this.fps;
    }

    public void setFps(float fps) {
        this.fps = fps;
    }

    public int getPreviewWidth() {
        return this.previewWidth;
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    public int getPreviewHeight() {
        return this.previewHeight;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
    }

    public boolean isAutoFocus() {
        return this.isAutoFocus;
    }

    public static int getCameraFacing() {
        synchronized (lock) {
            return cameraFacing;
        }
    }
}
