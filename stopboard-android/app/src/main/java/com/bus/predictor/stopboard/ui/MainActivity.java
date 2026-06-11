package com.bus.predictor.stopboard.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bus.predictor.stopboard.R;
import com.bus.predictor.stopboard.StopBoardApplication;
import com.bus.predictor.stopboard.model.EtaResponse;
import com.bus.predictor.stopboard.sensor.BrightnessManager;
import com.bus.predictor.stopboard.service.StopBoardService;
import com.bus.predictor.stopboard.tts.TtsManager;
import com.bus.predictor.stopboard.websocket.WebSocketClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private StopBoardApplication app;
    private WebSocketClient webSocketClient;
    private TtsManager ttsManager;
    private BrightnessManager brightnessManager;

    private TextView tvLineCode;
    private TextView tvStationName;
    private TextView tvTime;
    private TextView tvDate;
    private TextView tvStatus;
    private TextView tvStatusDot;

    private List<VehicleViewHolder> vehicleHolders;
    private EtaResponse currentEta;

    private Handler handler;
    private Runnable clockRunnable;
    private Runnable countdownRunnable;

    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA);

    private static class VehicleViewHolder {
        TextView tvRank;
        TextView tvMinutes;
        TextView tvUnit;
        TextView tvCountdown;
        TextView tvStations;
        TextView tvPlate;
        TextView tvCrowd;
        View progressBar;
        View progressFill;

        VehicleViewHolder(View root) {
            tvRank = root.findViewById(R.id.tv_vehicle_rank);
            tvMinutes = root.findViewById(R.id.tv_vehicle_minutes);
            tvUnit = root.findViewById(R.id.tv_vehicle_unit);
            tvCountdown = root.findViewById(R.id.tv_vehicle_countdown);
            tvStations = root.findViewById(R.id.tv_vehicle_stations);
            tvPlate = root.findViewById(R.id.tv_vehicle_plate);
            tvCrowd = root.findViewById(R.id.tv_vehicle_crowd);
            progressBar = root.findViewById(R.id.progress_bar);
            progressFill = root.findViewById(R.id.progress_fill);
        }

        void setVisible(boolean visible) {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE;
            tvRank.setVisibility(visibility);
            tvMinutes.setVisibility(visibility);
            tvUnit.setVisibility(visibility);
            tvCountdown.setVisibility(visibility);
            tvStations.setVisibility(visibility);
            tvPlate.setVisibility(visibility);
            tvCrowd.setVisibility(visibility);
            progressBar.setVisibility(visibility);
            progressFill.setVisibility(visibility);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        app = StopBoardApplication.getInstance();
        webSocketClient = app.getWebSocketClient();
        ttsManager = app.getTtsManager();

        handler = new Handler(Looper.getMainLooper());

        initViews();
        initBrightness();
        setupWebSocket();
        startClock();
        startService();
    }

    private void initViews() {
        tvLineCode = findViewById(R.id.tv_line_code);
        tvStationName = findViewById(R.id.tv_station_name);
        tvTime = findViewById(R.id.tv_time);
        tvDate = findViewById(R.id.tv_date);
        tvStatus = findViewById(R.id.tv_status);
        tvStatusDot = findViewById(R.id.tv_status_dot);

        vehicleHolders = new ArrayList<>();
        vehicleHolders.add(new VehicleViewHolder(findViewById(R.id.vehicle_1)));
        vehicleHolders.add(new VehicleViewHolder(findViewById(R.id.vehicle_2)));
        vehicleHolders.add(new VehicleViewHolder(findViewById(R.id.vehicle_3)));

        for (VehicleViewHolder vh : vehicleHolders) {
            vh.setVisible(false);
        }

        tvLineCode.setText(app.getLineCode() + "路");
        tvStationName.setText(app.getStationName());
    }

    private void initBrightness() {
        brightnessManager = new BrightnessManager(this);
        brightnessManager.attachWindow(getWindow());
        brightnessManager.setBrightnessListener((brightness, lux) -> {
            Log.d(TAG, "Brightness: " + brightness + ", Lux: " + lux);
        });
        brightnessManager.start();
    }

    private void setupWebSocket() {
        webSocketClient.setConnectionStateListener((state, message) -> {
            updateConnectionStatus(state, message);
        });

        webSocketClient.setEtaUpdateListener(eta -> {
            currentEta = eta;
            updateVehicleDisplay(eta);
        });
    }

    private void startService() {
        try {
            Intent serviceIntent = new Intent(this, StopBoardService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service", e);
        }
    }

    private void startClock() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                tvTime.setText(timeFormat.format(now));
                tvDate.setText(dateFormat.format(now));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                updateCountdownDisplay();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(countdownRunnable);
    }

    private void updateConnectionStatus(WebSocketClient.ConnectionState state, String message) {
        int colorRes;
        String statusText;

        switch (state) {
            case CONNECTED:
                colorRes = R.color.status_connected;
                statusText = "数据正常";
                break;
            case CONNECTING:
            case RECONNECTING:
                colorRes = R.color.status_connecting;
                statusText = "连接中...";
                break;
            case FAILED:
            case DISCONNECTED:
            default:
                colorRes = R.color.status_disconnected;
                statusText = "数据更新中";
                break;
        }

        tvStatus.setText(statusText);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            tvStatusDot.setTextColor(getColor(colorRes));
        } else {
            tvStatusDot.setTextColor(getResources().getColor(colorRes));
        }

        if (state == WebSocketClient.ConnectionState.CONNECTING ||
                state == WebSocketClient.ConnectionState.RECONNECTING) {
            tvStatusDot.setAlpha(0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0));
        } else {
            tvStatusDot.setAlpha(1.0f);
        }
    }

    private void updateVehicleDisplay(EtaResponse eta) {
        if (eta == null || eta.getVehicles() == null) {
            for (VehicleViewHolder vh : vehicleHolders) {
                vh.setVisible(false);
            }
            return;
        }

        List<EtaResponse.EtaVehicle> vehicles = eta.getVehicles();
        int count = Math.min(vehicles.size(), vehicleHolders.size());

        for (int i = 0; i < vehicleHolders.size(); i++) {
            VehicleViewHolder vh = vehicleHolders.get(i);
            if (i < count) {
                EtaResponse.EtaVehicle vehicle = vehicles.get(i);
                vh.setVisible(true);
                bindVehicleData(vh, vehicle, i + 1);
            } else {
                vh.setVisible(false);
            }
        }
    }

    private void bindVehicleData(VehicleViewHolder vh, EtaResponse.EtaVehicle vehicle, int rank) {
        vh.tvRank.setText(String.valueOf(rank));
        vh.tvMinutes.setText(String.valueOf(vehicle.getEstimatedMinutes()));
        vh.tvCountdown.setText(formatCountdown(vehicle.getEstimatedSeconds()));
        vh.tvStations.setText(formatStationsAway(vehicle.getDistanceStationsAway()));
        vh.tvPlate.setText(vehicle.getLicensePlate() != null ? vehicle.getLicensePlate() : "---");

        int crowdLevel = vehicle.getCrowdLevel();
        String crowdText = vehicle.getCrowdText();
        if (crowdText == null) {
            crowdText = getCrowdText(crowdLevel);
        }
        vh.tvCrowd.setText(crowdText);

        int crowdColorRes;
        switch (crowdLevel) {
            case 1: crowdColorRes = R.color.crowd_empty; break;
            case 2: crowdColorRes = R.color.crowd_medium; break;
            case 3: crowdColorRes = R.color.crowd_crowded; break;
            default: crowdColorRes = R.color.crowd_medium;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            vh.tvCrowd.setTextColor(getColor(crowdColorRes));
            vh.tvCrowd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(crowdColorRes)));
        } else {
            vh.tvCrowd.setTextColor(getResources().getColor(crowdColorRes));
        }

        int progress = calculateProgress(vehicle.getDistanceStationsAway());
        ViewGroup.LayoutParams lp = vh.progressFill.getLayoutParams();
        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
            ((android.widget.LinearLayout.LayoutParams) lp).weight = progress;
        }
        vh.progressFill.requestLayout();

        int textColorRes;
        if (rank == 1) {
            textColorRes = R.color.vehicle_1;
        } else if (rank == 2) {
            textColorRes = R.color.vehicle_2;
        } else {
            textColorRes = R.color.vehicle_3;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            vh.tvMinutes.setTextColor(getColor(textColorRes));
        } else {
            vh.tvMinutes.setTextColor(getResources().getColor(textColorRes));
        }

        vh.tvRank.setBackgroundResource(
                rank == 1 ? R.drawable.bg_rank_1 :
                rank == 2 ? R.drawable.bg_rank_2 : R.drawable.bg_rank_3
        );
    }

    private void updateCountdownDisplay() {
        if (currentEta == null || currentEta.getVehicles() == null) return;

        List<EtaResponse.EtaVehicle> vehicles = currentEta.getVehicles();
        int count = Math.min(vehicles.size(), vehicleHolders.size());

        for (int i = 0; i < count; i++) {
            EtaResponse.EtaVehicle v = vehicles.get(i);
            VehicleViewHolder vh = vehicleHolders.get(i);

            int seconds = v.getEstimatedSeconds();
            if (seconds > 0) {
                seconds--;
                v.setEstimatedSeconds(seconds);
                vh.tvCountdown.setText(formatCountdown(seconds));
            }

            int minutes = (int) Math.ceil(seconds / 60.0);
            vh.tvMinutes.setText(String.valueOf(minutes));
        }
    }

    private String formatCountdown(int seconds) {
        if (seconds <= 0) return "即将到站";
        if (seconds < 60) return seconds + "秒";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + "分" + String.format("%02d", secs) + "秒";
    }

    private String formatStationsAway(int stations) {
        if (stations <= 0) return "即将到站";
        if (stations == 1) return "还有1站";
        return "还有" + stations + "站";
    }

    private String getCrowdText(int level) {
        switch (level) {
            case 1: return "空 座";
            case 2: return "适 中";
            case 3: return "拥 挤";
            default: return "适 中";
        }
    }

    private int calculateProgress(int stationsAway) {
        int maxStations = 10;
        return Math.max(1, Math.min(10, maxStations - stationsAway));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (brightnessManager != null) {
            brightnessManager.start();
        }
        app.connectWebSocket();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (brightnessManager != null) {
            brightnessManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(clockRunnable);
            handler.removeCallbacks(countdownRunnable);
        }
        if (brightnessManager != null) {
            brightnessManager.release();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
}
