package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.model.ConnectionState;
import com.example.myapplication.model.DeviceState;
import com.example.myapplication.network.VehicleWebSocketClient;
import com.example.myapplication.ui.CameraView;
import com.example.myapplication.ui.ConnectionStatusView;
import com.example.myapplication.ui.SensorPanelView;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements VehicleWebSocketClient.Listener {
    private static final String DEFAULT_SERVER_URL = "ws://127.0.0.1:8080/app";

    private final DeviceState deviceState = new DeviceState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VehicleWebSocketClient webSocketClient;
    private ConnectionStatusView connectionStatusView;
    private CameraView cameraView;
    private SensorPanelView sensorPanelView;
    private LinearLayout settingsPanel;
    private Button settingsButton;
    private Button cameraToggleButton;
    private boolean cameraVisible = true;
    private int lastNotifiedBabyAlertLevel;

    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            cameraView.refreshState(deviceState);
            sensorPanelView.update(deviceState);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webSocketClient = new VehicleWebSocketClient(this);
        setContentView(buildContentView());
        webSocketClient.connect(DEFAULT_SERVER_URL);
        handler.post(statusTicker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(statusTicker);
        webSocketClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnectionState(ConnectionState state) {
        connectionStatusView.update(state);
    }

    @Override
    public void onJsonMessage(JSONObject json) {
        deviceState.applyJson(json);
        sensorPanelView.update(deviceState);
        if ("baby_alert".equals(json.optString("type"))) {
            notifyBabyAlertIfNeeded(deviceState.babyAlertLevel);
        }
    }

    @Override
    public void onBinaryFrame(byte[] bytes) {
        deviceState.markFrameReceived();
        cameraView.showFrame(bytes, deviceState.latestFrameInfo);
        sensorPanelView.update(deviceState);
    }

    @Override
    public void onError(String message) {
        connectionStatusView.setText("连接错误：" + message);
    }

    private LinearLayout buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        connectionStatusView = new ConnectionStatusView(this);
        root.addView(connectionStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(12, 12, 12, 8);

        settingsButton = new Button(this);
        settingsButton.setText("设置");
        settingsButton.setOnClickListener(view -> toggleSettingsPanel());
        actionBar.addView(settingsButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        cameraToggleButton = new Button(this);
        cameraToggleButton.setText("隐藏摄像头");
        cameraToggleButton.setOnClickListener(view -> toggleCameraView());
        actionBar.addView(cameraToggleButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actionBar);

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.HORIZONTAL);
        settingsPanel.setPadding(12, 0, 12, 8);
        settingsPanel.setVisibility(View.GONE);

        EditText serverUrl = new EditText(this);
        serverUrl.setSingleLine(true);
        serverUrl.setHint("服务器地址");
        serverUrl.setText(DEFAULT_SERVER_URL);
        Button connectButton = new Button(this);
        connectButton.setText("连接");
        connectButton.setOnClickListener(view -> {
            webSocketClient.disconnect();
            webSocketClient.connect(serverUrl.getText().toString().trim());
            settingsPanel.setVisibility(View.GONE);
            settingsButton.setText("设置");
        });
        settingsPanel.addView(serverUrl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        settingsPanel.addView(connectButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(settingsPanel);

        cameraView = new CameraView(this);
        root.addView(cameraView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.2f
        ));

        sensorPanelView = new SensorPanelView(this);
        root.addView(sensorPanelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private void toggleSettingsPanel() {
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        settingsButton.setText(show ? "收起设置" : "设置");
    }

    private void toggleCameraView() {
        cameraVisible = !cameraVisible;
        cameraView.setVisibility(cameraVisible ? View.VISIBLE : View.GONE);
        cameraToggleButton.setText(cameraVisible ? "隐藏摄像头" : "显示摄像头");
    }

    private void notifyBabyAlertIfNeeded(int level) {
        if (level <= 0) {
            lastNotifiedBabyAlertLevel = 0;
            return;
        }
        if (level <= lastNotifiedBabyAlertLevel) return;
        lastNotifiedBabyAlertLevel = level;

        Toast.makeText(this, babyAlertText(level), Toast.LENGTH_LONG).show();
        ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900);
        handler.postDelayed(toneGenerator::release, 1200);

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = level >= 3
                    ? new long[]{0, 500, 160, 500, 160, 700}
                    : level == 2
                    ? new long[]{0, 400, 150, 500}
                    : new long[]{0, 350};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    private String babyAlertText(int level) {
        if (level >= 3) return "三级提醒：车内有小孩/婴儿，温度和 CO2 均超阈值";
        if (level == 2) return "二级提醒：车内有小孩/婴儿，环境指标超阈值";
        return "一级提醒：车内发现小孩或婴儿";
    }
}
