package ru.alox1d.wordnote.huawei.helpers.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera.CameraInfo;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import com.huawei.hms.mlsdk.common.MLFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final long MAX_IMAGE_BYTES = 32L * 1024 * 1024;

    public static Bitmap rotateBitmap(Bitmap bitmap, int rotation, int facing) {
        Matrix matrix = new Matrix();
        int rotationDegree = 0;
        if (rotation == MLFrame.SCREEN_SECOND_QUADRANT) {
            rotationDegree = 90;
        } else if (rotation == MLFrame.SCREEN_THIRD_QUADRANT) {
            rotationDegree = 180;
        } else if (rotation == MLFrame.SCREEN_FOURTH_QUADRANT) {
            rotationDegree = 270;
        }
        matrix.postRotate(rotationDegree);
        if (facing != CameraInfo.CAMERA_FACING_BACK) {
            matrix.postScale(-1.0f, 1.0f);
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static void recycleBitmap(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }

    public static Bitmap loadFromPath(Activity activity, Uri uri, int width, int height) {
        if (uri == null || width <= 0 || height <= 0) {
            return null;
        }
        File imageFile = null;
        try {
            // Providers may expose only a URI. A private temporary copy also lets API 19-23
            // read EXIF, whose platform InputStream constructor is available only on API 24+.
            imageFile = File.createTempFile("wordnote-ocr-", ".image", activity.getCacheDir());
            try (InputStream input = activity.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(imageFile)) {
                if (input == null) {
                    return null;
                }
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (total + count > MAX_IMAGE_BYTES) {
                        return null;
                    }
                    output.write(buffer, 0, count);
                    total += count;
                }
            }

            String path = imageFile.getPath();
            int rotation = getRotationAngle(path);
            int decodeWidth = rotation == 90 || rotation == 270 ? height : width;
            int decodeHeight = rotation == 90 || rotation == 270 ? width : height;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            options.inSampleSize = calculateInSampleSize(options, decodeWidth, decodeHeight);
            options.inJustDecodeBounds = false;
            Bitmap decoded = BitmapFactory.decodeFile(path, options);
            if (decoded == null) {
                return null;
            }
            Bitmap scaled = zoomImage(decoded, decodeWidth, decodeHeight);
            if (scaled != decoded) {
                decoded.recycle();
            }
            Bitmap rotated = rotateBitmap(scaled, rotation);
            if (rotated != scaled) {
                scaled.recycle();
            }
            return rotated;
        } catch (IOException | SecurityException error) {
            Log.w(TAG, "Unable to read selected image");
            return null;
        } finally {
            if (imageFile != null) {
                imageFile.delete();
            }
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            // Calculate height and required height scale.
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            // Calculate width and required width scale.
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            // Take the larger of the values.
            inSampleSize = heightRatio > widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    // Scale pictures to screen width.
    private static Bitmap zoomImage(Bitmap imageBitmap, int targetWidth, int maxHeight) {
        float scaleFactor =
                Math.max(
                        (float) imageBitmap.getWidth() / (float) targetWidth,
                        (float) imageBitmap.getHeight() / (float) maxHeight);
        Bitmap resizedBitmap =
                Bitmap.createScaledBitmap(
                        imageBitmap,
                        (int) (imageBitmap.getWidth() / scaleFactor),
                        (int) (imageBitmap.getHeight() / scaleFactor),
                        true);

        return resizedBitmap;
    }

    /**
     * Get the rotation angle of the photo.
     *
     * @param path photo path.
     * @return angle.
     */
    public static int getRotationAngle(String path) {
        int rotation = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to get rotation: " + e.getMessage());
        }
        return rotation;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap result = null;
        try {
            result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Failed to rotate bitmap: " + e.getMessage());
        }
        if (result == null) {
            return bitmap;
        }
        return result;
    }
}

