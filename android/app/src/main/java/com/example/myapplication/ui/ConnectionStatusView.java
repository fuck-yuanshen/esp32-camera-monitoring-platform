package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

import com.example.myapplication.model.ConnectionState;

public class ConnectionStatusView extends TextView {
    public ConnectionStatusView(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setTextColor(Color.WHITE);
        setTextSize(15);
        setPadding(20, 12, 20, 12);
        update(ConnectionState.DISCONNECTED);
    }

    public void update(ConnectionState state) {
        switch (state) {
            case CONNECTED:
                setText("服务器已连接");
                setBackgroundColor(Color.rgb(25, 118, 75));
                break;
            case CONNECTING:
                setText("正在连接服务器");
                setBackgroundColor(Color.rgb(57, 91, 140));
                break;
            case RECONNECTING:
                setText("连接断开，正在重连");
                setBackgroundColor(Color.rgb(173, 111, 32));
                break;
            case DISCONNECTED:
            default:
                setText("服务器未连接");
                setBackgroundColor(Color.rgb(128, 42, 42));
                break;
        }
    }
}
