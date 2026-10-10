package ru.alox1d.wordnote.huawei.device.service.tr.callback;

import android.graphics.Bitmap;

import com.huawei.hms.mlsdk.document.MLDocument;
import com.huawei.hms.mlsdk.text.MLText;

import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay;

public interface CouldInfoResultCallBack {
    /**
     * Successful callback function of text recognition
     * @param originalCameraImage Original image
     * @param text recognition result
     * @param graphicOverlay Layers showing results
     */
    void onSuccessForText(Bitmap originalCameraImage, MLText text, GraphicOverlay graphicOverlay);

    /**
     * Successful callback function of document recognition
     * @param originalCameraImage Original image
     * @param text recognition result
     * @param graphicOverlay Layers showing results
     */
    void onSuccessForDoc(Bitmap originalCameraImage, MLDocument text, GraphicOverlay graphicOverlay);
}
