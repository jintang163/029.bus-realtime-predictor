package com.bus.predictor.stopboard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.bus.predictor.stopboard.StopBoardApplication;
import com.bus.predictor.stopboard.websocket.WebSocketClient;

public class StopBoardService extends Service {

    private static final String TAG = "StopBoardService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "stopboard_channel";

    private WebSocketClient webSocketClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "StopBoardService onCreate");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("电子站牌运行中"));

        webSocketClient = StopBoardApplication.getInstance().getWebSocketClient();
        if (webSocketClient != null) {
            String line = StopBoardApplication.getInstance().getLineCode();
            String station = StopBoardApplication.getInstance().getStationName();
            String direction = StopBoardApplication.getInstance().getDirection();
            webSocketClient.connect(line, station, direction);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case "ACTION_REFRESH":
                    if (webSocketClient != null) {
                        webSocketClient.sendRefresh();
                    }
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "StopBoardService onDestroy");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "电子站牌服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("电子站牌后台运行服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("智能公交电子站牌")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
