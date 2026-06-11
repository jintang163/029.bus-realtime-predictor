package com.bus.predictor.stopboard.websocket;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bus.predictor.stopboard.BuildConfig;
import com.bus.predictor.stopboard.model.EtaResponse;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;

public class WebSocketClient {

    private static final String TAG = "WebSocketClient";

    private static final long HEARTBEAT_INTERVAL = 30000;
    private static final long RECONNECT_BASE_DELAY = 2000;
    private static final long RECONNECT_MAX_DELAY = 30000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final Context context;
    private final Handler handler;
    private final Gson gson;

    private WebSocketClientImpl client;
    private String lineCode;
    private String stationName;
    private String direction;

    private int reconnectAttempts = 0;
    private boolean manualDisconnect = false;
    private boolean connected = false;

    private EtaUpdateListener etaUpdateListener;
    private ConnectionStateListener connectionStateListener;

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED
    }

    public interface EtaUpdateListener {
        void onEtaUpdate(EtaResponse eta);
    }

    public interface ConnectionStateListener {
        void onStateChanged(ConnectionState state, String message);
    }

    public WebSocketClient(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    public void setEtaUpdateListener(EtaUpdateListener listener) {
        this.etaUpdateListener = listener;
    }

    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.connectionStateListener = listener;
    }

    public synchronized void connect(String lineCode, String stationName, String direction) {
        this.lineCode = lineCode;
        this.stationName = stationName;
        this.direction = direction;
        this.manualDisconnect = false;

        if (isConnected()) {
            Log.i(TAG, "Already connected, resubscribing");
            sendSubscribe();
            return;
        }

        notifyStateChange(ConnectionState.CONNECTING, "正在连接...");
        connectInternal();
    }

    private void connectInternal() {
        try {
            if (client != null) {
                client.close();
                client = null;
            }

            String wsUrl = BuildConfig.WS_BASE_URL;
            URI uri = new URI(wsUrl);

            client = new WebSocketClientImpl(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.i(TAG, "WebSocket connected");
                    connected = true;
                    reconnectAttempts = 0;
                    notifyStateChange(ConnectionState.CONNECTED, "已连接");
                    startHeartbeat();
                    sendSubscribe();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    super.onMessage(bytes);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);
                    connected = false;
                    stopHeartbeat();

                    if (!manualDisconnect) {
                        scheduleReconnect();
                    } else {
                        notifyStateChange(ConnectionState.DISCONNECTED, "已断开");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                    connected = false;
                    notifyStateChange(ConnectionState.FAILED, "连接错误: " + ex.getMessage());
                }
            };

            client.setConnectionLostTimeout(15);
            client.connect();

        } catch (Exception e) {
            Log.e(TAG, "Failed to create WebSocket", e);
            notifyStateChange(ConnectionState.FAILED, "连接失败: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void sendSubscribe() {
        if (!isConnected() || lineCode == null || stationName == null) return;

        String authDeviceId = BuildConfig.BOARD_DEVICE_ID;

        String authMsg = String.format(
                "{\"action\":\"auth\",\"deviceId\":\"%s\",\"deviceType\":\"stopboard\"}",
                authDeviceId
        );
        send(authMsg);

        String subscribeMsg = String.format(
                "{\"action\":\"subscribe\",\"line\":\"%s\",\"station\":\"%s\",\"direction\":\"%s\"}",
                lineCode, stationName, direction
        );
        send(subscribeMsg);
    }

    public void sendRefresh() {
        send("{\"action\":\"refresh\"}");
    }

    public void send(String message) {
        if (client != null && isConnected()) {
            try {
                client.send(message);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }
    }

    private void handleMessage(String message) {
        try {
            WsMessage msg = gson.fromJson(message, WsMessage.class);
            if (msg == null || msg.type == null) return;

            switch (msg.type) {
                case "eta":
                    handleEtaMessage(msg);
                    break;
                case "welcome":
                    Log.d(TAG, "Server welcome message");
                    break;
                case "error":
                    Log.w(TAG, "Server error: " + msg.message);
                    break;
                case "pong":
                    Log.d(TAG, "Heartbeat pong received");
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse message: " + message, e);
        }
    }

    private void handleEtaMessage(WsMessage msg) {
        try {
            EtaResponse eta = gson.fromJson(gson.toJson(msg.data), EtaResponse.class);
            if (eta != null && etaUpdateListener != null) {
                handler.post(() -> etaUpdateListener.onEtaUpdate(eta));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse ETA data", e);
        }
    }

    private void scheduleReconnect() {
        if (manualDisconnect) return;

        reconnectAttempts++;
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached");
            notifyStateChange(ConnectionState.FAILED, "连接失败，请检查网络");
            reconnectAttempts = 5;
        }

        long delay = Math.min(
                RECONNECT_BASE_DELAY * (long) Math.pow(2, reconnectAttempts - 1),
                RECONNECT_MAX_DELAY
        );

        notifyStateChange(ConnectionState.RECONNECTING,
                "正在重连... (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");

        Log.i(TAG, "Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");

        handler.postDelayed(reconnectRunnable, delay);
    }

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!manualDisconnect) {
                connectInternal();
            }
        }
    };

    public synchronized void disconnect() {
        manualDisconnect = true;
        stopHeartbeat();
        handler.removeCallbacks(reconnectRunnable);

        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
            client = null;
        }

        connected = false;
        notifyStateChange(ConnectionState.DISCONNECTED, "已断开");
    }

    public synchronized void release() {
        disconnect();
        etaUpdateListener = null;
        connectionStateListener = null;
    }

    public boolean isConnected() {
        return connected && client != null && client.isOpen();
    }

    public ConnectionState getState() {
        if (isConnected()) return ConnectionState.CONNECTED;
        if (reconnectAttempts > 0) return ConnectionState.RECONNECTING;
        return ConnectionState.DISCONNECTED;
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected()) {
                send("ping");
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        }
    };

    private void startHeartbeat() {
        stopHeartbeat();
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    private void stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
    }

    private void notifyStateChange(ConnectionState state, String message) {
        if (connectionStateListener != null) {
            handler.post(() -> connectionStateListener.onStateChanged(state, message));
        }
    }

    private static abstract class WebSocketClientImpl extends WebSocketClient {
        public WebSocketClientImpl(URI serverUri) {
            super(serverUri);
        }
    }

    private static class WsMessage {
        String type;
        String message;
        Object data;
        long timestamp;
    }
}
