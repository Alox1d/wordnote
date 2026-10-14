package ru.alox1d.wordnote.huawei.ui.scenes.textrecognition;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.mlsdk.common.MLApplication;

import ru.alox1d.wordnote.huawei.R;

public class BaseTextRecognitionActivity extends AppCompatActivity {
    public static final String ML_TEXT = "ML_TEXT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Document images and recognition results remain private on every OCR entry point.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    /**
     * Set status bar immersion.
     */
    protected void setStatusBar() {
        // SDK 21/Android 5.0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = this.getWindow().getDecorView();
            int setting = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decorView.setSystemUiVisibility(setting);
            // Set the status bar to transparent.
            this.getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    protected void setApiKey() {
        AGConnectServicesConfig config = AGConnectServicesConfig.fromContext(getApplication());
        MLApplication.getInstance().setApiKey(config.getString(API_KEY));
    }

    public static final String API_KEY = "client/api_key";

    /**
     * Set the status bar font color to dark
     */
    public void setStatusBarFontColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = this.getWindow().getDecorView();
            int visibility = decorView.getSystemUiVisibility();
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(visibility);
        }
    }

    /**
     * Set status bar's color.
     *
     * @param activity Activity of page.
     * @param colorId  Color ID.
     */
    protected static void setStatusBarColor(Activity activity, int colorId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = activity.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(activity.getResources().getColor(colorId));
            }
        } catch (Exception e) {
            Log.e("BaseActivity", e.getMessage());
        }
    }

    protected void initTextSend(String text) {
        FloatingActionButton fab = findViewById(R.id.fab_accept);
        fab.setEnabled(true);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText(text);
            }
        });
    }

    protected void sendText(String text) {
        Intent i = new Intent();
        i.putExtra(ML_TEXT, text);
        setResult(Activity.RESULT_OK, i);
        finish();
    }
}
