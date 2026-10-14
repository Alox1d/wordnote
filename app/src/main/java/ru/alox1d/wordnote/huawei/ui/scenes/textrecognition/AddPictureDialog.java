package ru.alox1d.wordnote.huawei.ui.scenes.textrecognition;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import ru.alox1d.wordnote.huawei.R;


public class AddPictureDialog extends Dialog implements View.OnClickListener {
    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_CUSTOM = 2;
    private TextView tvTakePicture;
    private TextView tvSelectImage;
    private TextView tvExtend;
    private Context context;
    private ClickListener clickListener;
    private int type;

    public interface ClickListener {
        /**
         * Take picture
         */
        void takePicture();

        /**
         * Select picture from local
         */
        void selectImage();

        /**
         * Extension method
         */
        void doExtend();
    }

    public AddPictureDialog(Context context, int type) {
        super(context, R.style.AddPictureDialog);
        this.context = context;
        this.type = type;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.initViews();
    }

    private void initViews() {
        LayoutInflater inflater = LayoutInflater.from(this.context);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_add_picture, null);
        this.setContentView(view);

        this.tvTakePicture = view.findViewById(R.id.take_photo);
        this.tvSelectImage = view.findViewById(R.id.select_image);
        this.tvExtend = view.findViewById(R.id.extend);
        if (type == TYPE_CUSTOM) {
            this.tvExtend.setText(R.string.video_frame);
        }
        this.tvTakePicture.setOnClickListener(this);
        this.tvSelectImage.setOnClickListener(this);
        this.tvExtend.setOnClickListener(this);

        this.setCanceledOnTouchOutside(true);
        Window dialogWindow = this.getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams layoutParams = dialogWindow.getAttributes();
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM;
            dialogWindow.setAttributes(layoutParams);
        }
    }

    public void setClickListener(ClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @Override
    public void onClick(View v) {
        this.dismiss();
        if(this.clickListener == null){
            return;
        }
        if (v.getId() == R.id.take_photo) {
            this.clickListener.takePicture();
        } else if (v.getId() == R.id.select_image) {
            this.clickListener.selectImage();
        } else if (v.getId() == R.id.extend) {
            this.clickListener.doExtend();
        }
    }
}

