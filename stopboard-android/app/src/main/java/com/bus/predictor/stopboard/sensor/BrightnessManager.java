package com.bus.predictor.stopboard.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.bus.predictor.stopboard.BuildConfig;

import java.lang.ref.WeakReference;

public class BrightnessManager {

    private static final String TAG = "BrightnessManager";

    private static final float MIN_BRIGHTNESS = 0.1f;
    private static final float MAX_BRIGHTNESS = 1.0f;
    private static final float DEFAULT_BRIGHTNESS = 0.8f;

    private static final float BRIGHTNESS_CHANGE_THRESHOLD = 0.05f;
    private static final int UPDATE_INTERVAL_MS = 500;

    private final Context context;
    private final Handler handler;

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private LightSensorListener sensorListener;

    private WeakReference<Window> windowRef;

    private boolean enabled = false;
    private float currentBrightness = DEFAULT_BRIGHTNESS;
    private float lastLux = -1;
    private long lastUpdateTime = 0;

    private float minLux = 0f;
    private float maxLux = 5000f;

    public interface BrightnessListener {
        void onBrightnessChanged(float brightness, float lux);
    }

    private BrightnessListener listener;

    public BrightnessManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        initSensor();
    }

    private void initSensor() {
        if (!BuildConfig.ENABLE_AUTO_BRIGHTNESS) {
            Log.i(TAG, "Auto brightness disabled in config");
            return;
        }

        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                if (lightSensor != null) {
                    Log.i(TAG, "Light sensor found: " + lightSensor.getName());
                    sensorListener = new LightSensorListener();
                } else {
                    Log.w(TAG, "No light sensor available");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to init light sensor", e);
        }
    }

    public void attachWindow(Window window) {
        this.windowRef = new WeakReference<>(window);
        applyBrightness(currentBrightness);
    }

    public void start() {
        if (!BuildConfig.ENABLE_AUTO_BRIGHTNESS || lightSensor == null) {
            applyBrightness(DEFAULT_BRIGHTNESS);
            return;
        }

        if (enabled) return;

        try {
            sensorManager.registerListener(
                    sensorListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
            enabled = true;
            Log.i(TAG, "Auto brightness started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start light sensor", e);
        }
    }

    public void stop() {
        if (!enabled) return;

        try {
            if (sensorManager != null && sensorListener != null) {
                sensorManager.unregisterListener(sensorListener);
            }
            enabled = false;
            Log.i(TAG, "Auto brightness stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop light sensor", e);
        }
    }

    public void setBrightnessListener(BrightnessListener listener) {
        this.listener = listener;
    }

    private void onLuxChanged(float lux) {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (lastLux >= 0 && Math.abs(lux - lastLux) < BRIGHTNESS_CHANGE_THRESHOLD * maxLux
                && now - lastUpdateTime < UPDATE_INTERVAL_MS) {
            return;
        }

        lastLux = lux;
        lastUpdateTime = now;

        float brightness = calculateBrightness(lux);

        if (Math.abs(brightness - currentBrightness) < BRIGHTNESS_CHANGE_THRESHOLD) {
            return;
        }

        currentBrightness = brightness;
        applyBrightness(brightness);

        if (listener != null) {
            handler.post(() -> listener.onBrightnessChanged(brightness, lux));
        }

        Log.d(TAG, String.format("Lux: %.1f -> Brightness: %.2f", lux, brightness));
    }

    private float calculateBrightness(float lux) {
        if (lux <= 0) return MIN_BRIGHTNESS;
        if (lux >= maxLux) return MAX_BRIGHTNESS;

        float normalized = (float) (Math.log(lux + 1) / Math.log(maxLux + 1));
        float brightness = MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * normalized;

        return Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));
    }

    private void applyBrightness(float brightness) {
        if (windowRef == null) return;

        Window window = windowRef.get();
        if (window == null) return;

        try {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.screenBrightness = brightness;
            window.setAttributes(lp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply brightness", e);
        }
    }

    public void setManualBrightness(float brightness) {
        stop();
        currentBrightness = Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));
        applyBrightness(currentBrightness);
    }

    public float getCurrentBrightness() {
        return currentBrightness;
    }

    public boolean isAutoBrightnessEnabled() {
        return enabled;
    }

    public boolean hasLightSensor() {
        return lightSensor != null;
    }

    private class LightSensorListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                float lux = event.values[0];
                onLuxChanged(lux);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public void release() {
        stop();
        windowRef = null;
        listener = null;
        sensorManager = null;
        lightSensor = null;
        sensorListener = null;
    }
}
