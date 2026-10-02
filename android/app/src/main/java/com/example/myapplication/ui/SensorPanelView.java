package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.myapplication.model.DeviceState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SensorPanelView extends LinearLayout {
    private final TextView babyAlertStatus;
    private final TextView motionValue;
    private final TextView temperatureValue;
    private final TextView eco2Value;
    private final TextView tvocValue;
    private final TextView aqiValue;
    private final TextView humidityValue;
    private final TextView updateValue;

    public SensorPanelView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(18, 18, 18, 18);
        setBackgroundColor(Color.rgb(245, 247, 249));

        babyAlertStatus = new TextView(context);
        babyAlertStatus.setTextSize(16);
        babyAlertStatus.setTextColor(Color.WHITE);
        babyAlertStatus.setGravity(Gravity.CENTER);
        babyAlertStatus.setPadding(16, 12, 16, 12);
        LayoutParams alertParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(babyAlertStatus, alertParams);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = 14;
        addView(grid, gridParams);

        motionValue = addMetric(grid, "车辆状态", "--");
        temperatureValue = addMetric(grid, "DS18B20 温度", "--");
        eco2Value = addMetric(grid, "eCO2", "--");
        tvocValue = addMetric(grid, "TVOC", "--");
        aqiValue = addMetric(grid, "AQI", "--");
        humidityValue = addMetric(grid, "湿度", "--");
        updateValue = addMetric(grid, "更新时间", "--");
        update(new DeviceState());
    }

    public void update(DeviceState state) {
        babyAlertStatus.setText(formatBabyAlert(state));
        babyAlertStatus.setBackgroundColor(babyAlertColor(state.babyAlertLevel));

        motionValue.setText(state.moving == null ? "--" : state.moving ? "运动中" : "静止");
        motionValue.setTextColor(Boolean.TRUE.equals(state.moving) ? Color.rgb(191, 45, 45) : Color.rgb(33, 48, 64));
        temperatureValue.setText(state.celsius == null ? "--" : String.format(Locale.US, "%.2f C", state.celsius));
        eco2Value.setText(state.eco2Ppm == null ? "--" : String.format(Locale.US, "%d ppm", state.eco2Ppm));
        tvocValue.setText(state.tvocPpb == null ? "--" : String.format(Locale.US, "%d ppb", state.tvocPpb));
        aqiValue.setText(state.aqi == null ? "--" : String.valueOf(state.aqi));
        humidityValue.setText(state.humidityRh == null ? "--" : String.format(Locale.US, "%.2f %%", state.humidityRh));
        updateValue.setText(state.lastUpdateMs == 0 ? "--" : formatTime(state.lastUpdateMs) + (state.isDataStale() ? " 可能过期" : ""));
    }

    private TextView addMetric(GridLayout grid, String label, String value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(16, 14, 16, 14);
        card.setBackgroundColor(Color.WHITE);

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(95, 105, 115));
        labelView.setTextSize(13);
        card.addView(labelView);

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextColor(Color.rgb(33, 48, 64));
        valueView.setTextSize(20);
        valueView.setPadding(0, 8, 0, 0);
        card.addView(valueView);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(6, 6, 6, 6);
        grid.addView(card, params);
        return valueView;
    }

    private String formatTime(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms));
    }

    private String formatBabyAlert(DeviceState state) {
        if (!state.hasBaby || state.babyAlertLevel == 0) return "AI 检测：未发现小孩或婴儿";
        if (state.babyAlertLevel == 3) return "三级提醒：车内有小孩/婴儿，温度和 CO2 均超阈值";
        if (state.babyAlertLevel == 2) {
            String reason = state.babyTemperatureTriggered ? "温度超阈值" : "CO2 超阈值";
            return "二级提醒：车内有小孩/婴儿，" + reason;
        }
        return "一级提醒：车内发现小孩或婴儿";
    }

    private int babyAlertColor(int level) {
        if (level >= 3) return Color.rgb(150, 20, 20);
        if (level == 2) return Color.rgb(200, 100, 20);
        if (level == 1) return Color.rgb(70, 95, 150);
        return Color.rgb(95, 105, 115);
    }
}
