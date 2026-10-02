package com.example.myapplication.model;

import org.json.JSONObject;

public class DeviceState {
    public boolean deviceOnline;
    public Boolean moving;
    public Double celsius;
    public Integer eco2Ppm;
    public Integer tvocPpb;
    public Integer aqi;
    public Double humidityRh;
    public long lastUpdateMs;
    public long lastFrameMs;
    public boolean hasBaby;
    public int babyAlertLevel;
    public boolean babyTemperatureTriggered;
    public boolean babyCo2Triggered;
    public long babyAlertAtMs;
    public FrameInfo latestFrameInfo;

    public void applyJson(JSONObject json) {
        String type = json.optString("type", "");
        long serverTsMs = json.optLong("server_ts_ms", System.currentTimeMillis());
        lastUpdateMs = Math.max(lastUpdateMs, serverTsMs);

        switch (type) {
            case "device_status":
                deviceOnline = json.optBoolean("online", false);
                break;
            case "motion":
                moving = json.optBoolean("moving");
                break;
            case "temperature":
                celsius = json.has("celsius") ? json.optDouble("celsius") : null;
                break;
            case "co2":
                eco2Ppm = json.has("eco2_ppm") ? json.optInt("eco2_ppm") : null;
                tvocPpb = json.has("tvoc_ppb") ? json.optInt("tvoc_ppb") : null;
                aqi = json.has("aqi") ? json.optInt("aqi") : null;
                humidityRh = json.has("humidity_rh") ? json.optDouble("humidity_rh") : null;
                break;
            case "frame_meta":
                latestFrameInfo = parseFrameInfo(json, serverTsMs);
                break;
            case "baby_alert":
                hasBaby = json.optBoolean("has_baby", false);
                babyAlertLevel = json.optInt("level", 0);
                babyTemperatureTriggered = json.optBoolean("temperature_triggered", false);
                babyCo2Triggered = json.optBoolean("co2_triggered", false);
                babyAlertAtMs = serverTsMs;
                break;
            default:
                break;
        }
    }

    public void markFrameReceived() {
        lastFrameMs = System.currentTimeMillis();
    }

    public boolean isDataStale() {
        return lastUpdateMs > 0 && System.currentTimeMillis() - lastUpdateMs > 10_000;
    }

    public boolean isVideoStale() {
        return lastFrameMs == 0 || System.currentTimeMillis() - lastFrameMs > 5_000;
    }

    private FrameInfo parseFrameInfo(JSONObject json, long serverTsMs) {
        FrameInfo info = new FrameInfo();
        info.frameId = json.optLong("frame_id", 0);
        info.width = json.optInt("width", 0);
        info.height = json.optInt("height", 0);
        info.size = json.optInt("size", 0);
        info.abnormal = json.optBoolean("abnormal", false);
        info.serverTsMs = serverTsMs;
        return info;
    }
}
