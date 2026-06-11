package com.bus.predictor.stopboard;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bus.predictor.stopboard.tts.TtsManager;
import com.bus.predictor.stopboard.websocket.WebSocketClient;

public class StopBoardApplication extends Application {

    private static final String TAG = "StopBoardApplication";

    private static StopBoardApplication instance;

    private WebSocketClient webSocketClient;
    private TtsManager ttsManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String boardDeviceId;
    private String lineCode;
    private String stationName;
    private String direction;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "StopBoardApplication onCreate");

        boardDeviceId = BuildConfig.BOARD_DEVICE_ID;
        lineCode = BuildConfig.DEFAULT_LINE_CODE;
        stationName = BuildConfig.DEFAULT_STATION;
        direction = BuildConfig.DEFAULT_DIRECTION;

        initManagers();
    }

    private void initManagers() {
        webSocketClient = new WebSocketClient(this);
        ttsManager = new TtsManager(this);

        webSocketClient.setEtaUpdateListener(eta -> {
            if (ttsManager != null) {
                ttsManager.onEtaUpdate(eta);
            }
        });
    }

    public static StopBoardApplication getInstance() {
        return instance;
    }

    public WebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    public TtsManager getTtsManager() {
        return ttsManager;
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

    public String getBoardDeviceId() {
        return boardDeviceId;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void connectWebSocket() {
        if (webSocketClient != null) {
            webSocketClient.connect(lineCode, stationName, direction);
        }
    }

    public void disconnectWebSocket() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (webSocketClient != null) {
            webSocketClient.release();
        }
        if (ttsManager != null) {
            ttsManager.release();
        }
    }
}
