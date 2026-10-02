package com.example.myapplication.network;

import android.os.Handler;
import android.os.Looper;

import com.example.myapplication.model.ConnectionState;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class VehicleWebSocketClient {
    public interface Listener {
        void onConnectionState(ConnectionState state);

        void onJsonMessage(JSONObject json);

        void onBinaryFrame(byte[] bytes);

        void onError(String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private WebSocket webSocket;
    private String url;
    private boolean manuallyClosed;
    private int reconnectAttempt;
    private int connectionGeneration;

    public VehicleWebSocketClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(String url) {
        this.url = url;
        manuallyClosed = false;
        reconnectAttempt = 0;
        connectionGeneration += 1;
        open(ConnectionState.CONNECTING, connectionGeneration);
    }

    public void disconnect() {
        manuallyClosed = true;
        connectionGeneration += 1;
        if (webSocket != null) {
            webSocket.close(1000, "user closed");
            webSocket = null;
        }
        postConnectionState(ConnectionState.DISCONNECTED);
    }

    private void open(ConnectionState state, int generation) {
        postConnectionState(state);
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new ListenerAdapter(generation));
    }

    private void scheduleReconnect(int generation) {
        if (manuallyClosed || url == null || generation != connectionGeneration) return;
        reconnectAttempt += 1;
        long delayMs = Math.min(5_000L, reconnectAttempt == 1 ? 1_000L : reconnectAttempt == 2 ? 2_000L : 5_000L);
        postConnectionState(ConnectionState.RECONNECTING);
        mainHandler.postDelayed(() -> {
            if (!manuallyClosed && generation == connectionGeneration) {
                open(ConnectionState.RECONNECTING, generation);
            }
        }, delayMs);
    }

    private void postConnectionState(ConnectionState state) {
        mainHandler.post(() -> listener.onConnectionState(state));
    }

    private class ListenerAdapter extends WebSocketListener {
        private final int generation;

        ListenerAdapter(int generation) {
            this.generation = generation;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (generation != connectionGeneration) return;
            reconnectAttempt = 0;
            postConnectionState(ConnectionState.CONNECTED);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (generation != connectionGeneration) return;
            try {
                JSONObject json = new JSONObject(text);
                mainHandler.post(() -> listener.onJsonMessage(json));
            } catch (JSONException exception) {
                mainHandler.post(() -> listener.onError("JSON parse failed"));
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            if (generation != connectionGeneration) return;
            byte[] frameBytes = bytes.toByteArray();
            mainHandler.post(() -> listener.onBinaryFrame(frameBytes));
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (!manuallyClosed) scheduleReconnect(generation);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (generation != connectionGeneration) return;
            mainHandler.post(() -> listener.onError(t.getMessage() == null ? "WebSocket failed" : t.getMessage()));
            scheduleReconnect(generation);
        }
    }
}
