package ru.alox1d.wordnote.huawei.helpers.util;

import static java.io.File.separator;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.alox1d.wordnote.huawei.BuildConfig;


public class Utils {

    public static void appendLog(String text)
    {
        if(!BuildConfig.DEBUG) return;
        File logFile = new File("sdcard/WordNote_Log.txt");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            long time = Calendar.getInstance().getTimeInMillis();
            buf.append(text + " - " + getFullDateWithSecs(time));
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static String getDate(long date) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");
        return dateFormat.format(date);

    }

    public static String getTime(long time) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        return timeFormat.format(time);
    }

    public static String getFullDate(long date) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat fullDateFormat = new SimpleDateFormat("dd.MM.yy HH:mm");
        return fullDateFormat.format(date);
    }

    public static String getFullDateWithSecs(long date) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat fullDateFormat = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
        return fullDateFormat.format(date);
    }

    public static String colorHex(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return String.format(Locale.getDefault(), "#%02X%02X%02X", r, g, b);
    }

    public static Map<Integer, String> getHeadingTypeface() {
        Map<Integer, String> typefaceMap = new HashMap<>();
        typefaceMap.put(Typeface.NORMAL, "fonts/RobotoCondensed-Bold.ttf");
        typefaceMap.put(Typeface.BOLD, "fonts/RobotoCondensed-Bold.ttf");
        typefaceMap.put(Typeface.ITALIC, "fonts/RobotoCondensed-BoldItalic.ttf");
        typefaceMap.put(Typeface.BOLD_ITALIC, "fonts/RobotoCondensed-BoldItalic.ttf");
        return typefaceMap;
    }

    public static Map<Integer, String> getContentface() {
        Map<Integer, String> typefaceMap = new HashMap<>();
        typefaceMap.put(Typeface.NORMAL, "fonts/RobotoCondensed-Regular.ttf");
        typefaceMap.put(Typeface.BOLD, "fonts/RobotoCondensed-Bold.ttf");
        typefaceMap.put(Typeface.ITALIC, "fonts/RobotoCondensed-Italic.ttf");
        typefaceMap.put(Typeface.BOLD_ITALIC, "fonts/RobotoCondensed-BoldItalic.ttf");
        return typefaceMap;
    }

    @ColorInt
    public static int getThemeColor
            (
                    @NonNull final Context context,
                    @AttrRes final int attributeColor
            ) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attributeColor, value, true);
        return value.data;
    }

    public static class ViewGroupUtils {

        public static ViewGroup getParent(View view) {
            return (ViewGroup) view.getParent();
        }

        public static void removeView(View view) {
            ViewGroup parent = getParent(view);
            if (parent != null) {
                parent.removeView(view);
            }
        }

        public static void replaceView(View currentView, View newView) {
            ViewGroup parent = getParent(currentView);
            if (parent == null) {
                return;
            }
            final int index = parent.indexOfChild(currentView);
            removeView(currentView);
            removeView(newView);
            parent.addView(newView, index);
        }
    }

    public static class FileUtils {
        public static String readRawTextFile(Context ctx, int resId)
        {
            InputStream inputStream = ctx.getResources().openRawResource(resId);

            InputStreamReader inputreader = new InputStreamReader(inputStream);
            BufferedReader buffreader = new BufferedReader(inputreader);
            String line;
            StringBuilder text = new StringBuilder();

            try {
                while (( line = buffreader.readLine()) != null) {
                    text.append(line);
                    text.append('\n');
                }
            } catch (IOException e) {
                return null;
            }
            return text.toString();
        }
        public static File saveToFile(Context context, String fileName, Object object) throws IOException {
            File fileObject = new java.io.File(context.getExternalFilesDir(null), fileName);
            // file does not exist, create it
            if (!fileObject.exists()) fileObject.createNewFile();
            FileOutputStream fos = new FileOutputStream(fileObject);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(object);
            os.close();
            fos.close();
            return fileObject;
        }

        public static <T> T loadFromFile(File file) throws IOException, ClassNotFoundException {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream is = new ObjectInputStream(fis);
            T simpleClass = (T) is.readObject();
            is.close();
            fis.close();
            return simpleClass;
        }

        /**
         * Creates the specified <code>toFile</code> as a byte for byte copy of the
         * <code>fromFile</code>. If <code>toFile</code> already exists, then it
         * will be replaced with a copy of <code>fromFile</code>. The name and path
         * of <code>toFile</code> will be that of <code>toFile</code>.<br/>
         * <br/>
         * <i> Note: <code>fromFile</code> and <code>toFile</code> will be closed by
         * this function.</i>
         *
         * @param fromFile - FileInputStream for the file to copy from.
         * @param toFile   - FileInputStream for the file to copy to.
         */
        public static void copyFile(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
            FileChannel fromChannel = null;
            FileChannel toChannel = null;
            try {
                fromChannel = fromFile.getChannel();
                toChannel = toFile.getChannel();
                fromChannel.transferTo(0, fromChannel.size(), toChannel);
            } finally {
                try {
                    if (fromChannel != null) {
                        fromChannel.close();
                    }
                } finally {
                    if (toChannel != null) {
                        toChannel.close();
                    }
                }
            }
        }

        public static void copyFile(File from, File to) throws IOException {
            File data = Environment.getDataDirectory();
            FileChannel source = null;
            FileChannel destination = null;
            String backupDBPath = to.getAbsolutePath();
            File backup = new File(data, backupDBPath);

            source = new FileInputStream(from).getChannel();
            destination = new FileOutputStream(to).getChannel();
            destination.transferFrom(source, 0, source.size());
            source.close();
            destination.close();
        }

        public static void copyInputStreamToFile(InputStream in, File file) {
            try {
                OutputStream out = new FileOutputStream(file);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /** Creates parent directories if necessary. Then returns file */
        public static File fileWithDirectoryAssurance(String directory, String filename) {
            File dir = new File(directory);
            if (!dir.exists()) dir.mkdirs();
            return new File(directory + "/" + filename);
        }
    }

    public static class ImageUtils {
        public static Uri getImageUri(Context context, Bitmap image) {
            return Uri.fromFile(new ru.alox1d.wordnote.huawei.data.storage.AttachmentStore(context).saveBitmap(image));
        }

        public static String getRealPathFromURI(Uri uri, Context context) {
            if (uri == null) return null;
            if ("file".equals(uri.getScheme())) return uri.getPath();
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) return null;
                int index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                return index < 0 ? null : cursor.getString(index);
            }
        }

        public static boolean deleteImg(Context context, String path) {
            return new ru.alox1d.wordnote.huawei.data.storage.AttachmentStore(context).deleteIfUnused(path);
        }

        public static Uri saveImage(Bitmap image, Context context, String ignoredFolderName) throws FileNotFoundException {
            return getImageUri(context, image);
        }
    }

    public static final class EscapedWriter
            extends Writer {

        private static final char[] hex = {
                '0', '1', '2', '3',
                '4', '5', '6', '7',
                '8', '9', 'a', 'b',
                'c', 'd', 'e', 'f'
        };

        private final Writer writer;

        // I/O components are usually implemented in not thread-safe manner
        // so we can save some time on constructing a single UTF-16 escape
        private final char[] escape = {'\\', 'u', 0, 0, 0, 0};

        public EscapedWriter(final Writer writer) {
            this.writer = writer;
        }

        // This implementation is not very efficient and is open for enhancements:
        // * constructing a single "normalized" buffer character array so that it could be passed to the downstream writer
        //   rather than writing characters one by one
        // * etc...
        @Override
        public void write(final char[] buffer, final int offset, final int length)
                throws IOException {
            for (int i = offset; i < length; i++) {
                final int ch = buffer[i];
                if (ch < 128) {
                    writer.write(ch);
                } else {
                    escape[2] = hex[(ch & 0xF000) >> 12];
                    escape[3] = hex[(ch & 0x0F00) >> 8];
                    escape[4] = hex[(ch & 0x00F0) >> 4];
                    escape[5] = hex[ch & 0x000F];
                    writer.write(escape);
                }
            }
        }

        @Override
        public void flush()
                throws IOException {
            writer.flush();
        }

        @Override
        public void close()
                throws IOException {
            writer.close();
        }

        // Some java.io.Writer subclasses may use java.lang.Object.toString() to materialize their accumulated state by design
        // so it has to be overridden and forwarded as well
        @Override
        public String toString() {
            return writer.toString();
        }

    }
    public static final class Animations{
        public static boolean toggleArrow(View view, boolean isExpanded) {

            if (isExpanded) {
                view.animate().setDuration(200).rotation(0);
                return true;
            } else {
                view.animate().setDuration(200).rotation(180);
                return false;
            }
        }
        public static void expand(View view) {
            Animation animation = expandAction(view);
            view.startAnimation(animation);
        }

        private static Animation expandAction(final View view) {

            view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            final int actualheight = view.getMeasuredHeight();

            view.getLayoutParams().height = 0;
            view.setVisibility(View.VISIBLE);

            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {

                    view.getLayoutParams().height = interpolatedTime == 1
                            ? ViewGroup.LayoutParams.WRAP_CONTENT
                            : (int) (actualheight * interpolatedTime);
                    view.requestLayout();
                }
            };


            animation.setDuration((long) (actualheight / view.getContext().getResources().getDisplayMetrics().density));

            view.startAnimation(animation);

            return animation;


        }

        public static void collapse(final View view) {

            final int actualHeight = view.getMeasuredHeight();

            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {

                    if (interpolatedTime == 1) {
                        view.setVisibility(View.GONE);
                    } else {
                        view.getLayoutParams().height = actualHeight - (int) (actualHeight * interpolatedTime);
                        view.requestLayout();

                    }
                }
            };

            animation.setDuration((long) (actualHeight/ view.getContext().getResources().getDisplayMetrics().density));
            view.startAnimation(animation);
        }
    }
}
