package ru.alox1d.wordnote.huawei.helpers.util;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;

public class CommonUtils {
    private static final String TAG = "CommonUtils";
    /**
     * Handle ByteBuffer
     *
     * @param src High resolution ByteBuffer
     * @param dst Low resolution ByteBuffer
     * @param srcWidth High resolution wide
     * @param srcHeight High resolution height
     * @param dstWidth Low resolution wide
     * @param dstHeight Low resolution height
     */
    public static void handleByteBuffer(ByteBuffer src, ByteBuffer dst,
                                        int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        int sw = srcWidth;
        int sh = srcHeight;
        int dw = dstWidth;
        int dh = dstHeight;
        int y;
        int x;
        int srcY;
        int srcX;
        int srcIndex;
        int xrIntFloat = (sw << 16) / dw + 1;
        int yrIntFloat = (sh << 16) / dh + 1;

        int dstUv = dh * dw;
        int srcUv = sh * sw;
        int dstUvYScanLine = 0;
        int srcUvYScanLine = 0;
        int dstYSlice = 0;
        int srcYSlice;
        int sp;
        int dp;

        for (y = 0; y < (dh & ~7); ++y) {
            srcY = (y * yrIntFloat) >> 16;
            srcYSlice = srcY * sw;

            if ((y & 1) == 0) {
                dstUvYScanLine = dstUv + (y / 2) * dw;
                srcUvYScanLine = srcUv + (srcY / 2) * sw;
            }

            for (x = 0; x < (dw & ~7); ++x) {
                srcX = (x * xrIntFloat) >> 16;
                try {
                    dst.put((x + dstYSlice), src.get(srcYSlice + srcX));
                } catch (Exception e) {
                    Log.d(TAG, "nv12_Resize Exception1" + e.getMessage());
                }

                if ((y & 1) == 0) {
                    if ((x & 1) == 0) {
                        srcIndex = (srcX / 2) * 2;
                        sp = dstUvYScanLine + x;
                        dp = srcUvYScanLine + srcIndex;
                        try {
                            dst.put(sp, src.get(dp));
                        } catch (Exception e) {
                            Log.d(TAG, "nv12_Resize Exception2" + e.getMessage());
                        }
                        ++sp;
                        ++dp;
                        try {
                            dst.put(sp, src.get(dp));
                        } catch (Exception e) {
                            Log.d(TAG, "nv12_Resize Exception3" + e.getMessage());
                        }
                    }
                }
            }
            dstYSlice += dw;
        }
    }

    public static float dp2px(Context context, float dipValue) {
        return dipValue * context.getResources().getDisplayMetrics().density + 0.5f;
    }
}
