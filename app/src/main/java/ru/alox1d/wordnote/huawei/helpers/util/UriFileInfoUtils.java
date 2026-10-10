package ru.alox1d.wordnote.huawei.helpers.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

/**
 * Uri file info utils.
 *
 */
public class UriFileInfoUtils {
    private static final String TAG = "UriFileInfoUtils";

    private static final String MEDIA_AUTHORITY = "media";

    public static String getFileNameWithSuffix(Context context, Uri uri) {
        String path = getFilePath(context, uri);
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        int start = path.lastIndexOf("/");
        if (start != -1) {
            return path.substring(start + 1);
        } else {
            return "";
        }
    }

    public static String getFilePath(Context context, Uri uri) {
        String filePath = null;
        final String scheme = uri.getScheme();
        if (uri == null) {
            Log.e(TAG, "uri is empty");
            return null;
        }
        if (context == null) {
            Log.e(TAG, "context is null");
            return null;
        }
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            return uri.getPath();
        }
        if (!uri.getAuthority().equals(MEDIA_AUTHORITY)) {
            return uri.getEncodedPath();
        }
        Cursor cursor = null;
        final String column = MediaStore.Images.Media.DATA;
        try {
            CursorLoader loader = new CursorLoader(context, uri, null, null, null, null);
            cursor = loader.loadInBackground();
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(columnIndex);
        } finally {
            if(cursor != null){
                cursor.close();
            }
        }
    }

    public static long getDuration(Context context, Uri uri) {
        long mediaPlayerDuration = 0L;
        if (context == null || uri == null) {
            return 0;
        }
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepare();
            mediaPlayerDuration = mediaPlayer.getDuration();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
        }
        return mediaPlayerDuration;
    }
}