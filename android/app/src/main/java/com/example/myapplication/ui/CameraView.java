package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.myapplication.model.DeviceState;
import com.example.myapplication.model.FrameInfo;

public class CameraView extends FrameLayout {
    private final ImageView imageView;
    private final TextView overlay;

    public CameraView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        overlay = new TextView(context);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(14);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.argb(150, 0, 0, 0));
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        showStatus("等待视频画面");
    }

    public void showFrame(byte[] bytes, FrameInfo info) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            showStatus("JPEG 帧无效");
            return;
        }
        imageView.setImageBitmap(bitmap);
        String sizeText = info == null ? "" : "  #" + info.frameId + "  " + info.width + "x" + info.height;
        String abnormalText = info != null && info.abnormal ? "  大小异常" : "";
        showStatus("实时画面" + sizeText + abnormalText);
    }

    public void refreshState(DeviceState state) {
        if (state.isVideoStale()) {
            showStatus("视频未更新");
        }
    }

    private void showStatus(String text) {
        overlay.setText(text);
    }
}
