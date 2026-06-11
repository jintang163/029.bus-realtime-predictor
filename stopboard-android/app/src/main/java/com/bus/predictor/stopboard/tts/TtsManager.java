package com.bus.predictor.stopboard.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bus.predictor.stopboard.BuildConfig;
import com.bus.predictor.stopboard.model.EtaResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TtsManager {

    private static final String TAG = "TtsManager";

    public enum TtsEngine {
        IFLYTEK,
        BUILT_IN,
        CLOUD,
        NONE
    }

    private final Context context;
    private final Handler handler;
    private final ExecutorService audioExecutor;

    private TtsEngine currentEngine = TtsEngine.NONE;
    private boolean initialized = false;

    private long lastSpeakTime = 0;
    private static final int MIN_SPEAK_INTERVAL_MS = BuildConfig.TTS_MIN_INTERVAL_SECONDS * 1000;

    private String lastSpokenVehicleId = null;
    private int lastSpokenMinutes = -1;

    private AudioTrack audioTrack;
    private volatile boolean playing = false;

    public interface TtsInitListener {
        void onInitComplete(TtsEngine engine, boolean success);
    }

    public TtsManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.audioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TTS-Audio");
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        init();
    }

    public void init() {
        if (initialized) return;

        new Thread(() -> {
            TtsEngine engine = initIflytek();
            if (engine == TtsEngine.IFLYTEK) {
                currentEngine = engine;
                initialized = true;
                Log.i(TAG, "Using iFlytek offline TTS");
                return;
            }

            if (testCloudTts()) {
                currentEngine = TtsEngine.CLOUD;
                initialized = true;
                Log.i(TAG, "Using cloud TTS fallback");
                return;
            }

            currentEngine = TtsEngine.NONE;
            initialized = true;
            Log.w(TAG, "No TTS engine available");
        }).start();
    }

    private TtsEngine initIflytek() {
        try {
            Class<?> speechUtility = Class.forName("com.iflytek.cloud.SpeechUtility");
            speechUtility.getMethod("createUtility", Context.class, String.class)
                    .invoke(null, context, "appid=bus_stop_board");
            Log.i(TAG, "iFlytek SDK detected, TTS available");
            return TtsEngine.IFLYTEK;
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "iFlytek SDK not available");
        } catch (Exception e) {
            Log.w(TAG, "iFlytek init failed: " + e.getMessage());
        }
        return TtsEngine.NONE;
    }

    private boolean testCloudTts() {
        try {
            String testUrl = BuildConfig.API_BASE_URL + "/tts/health";
            URL url = new URL(testUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            Log.d(TAG, "Cloud TTS not available: " + e.getMessage());
            return false;
        }
    }

    public void onEtaUpdate(EtaResponse eta) {
        if (eta == null || eta.getVehicles() == null || eta.getVehicles().isEmpty()) {
            return;
        }

        EtaResponse.EtaVehicle firstVehicle = eta.getVehicles().get(0);
        if (firstVehicle == null) return;

        int minutes = firstVehicle.getEstimatedMinutes();
        String vehicleId = firstVehicle.getVehicleId();

        if (shouldSpeak(firstVehicle)) {
            String text = generateSpeakText(eta.getLineCode(), firstVehicle);
            speak(text);
            lastSpokenVehicleId = vehicleId;
            lastSpokenMinutes = minutes;
            lastSpeakTime = System.currentTimeMillis();
        }
    }

    private boolean shouldSpeak(EtaResponse.EtaVehicle vehicle) {
        if (currentEngine == TtsEngine.NONE) return false;

        long now = System.currentTimeMillis();
        if (now - lastSpeakTime < MIN_SPEAK_INTERVAL_MS) {
            return false;
        }

        int minutes = vehicle.getEstimatedMinutes();

        if (minutes <= 1 && lastSpokenMinutes > 1) {
            return true;
        }

        if (minutes <= 3 && (lastSpokenMinutes > 3 || lastSpokenMinutes < 0)) {
            return true;
        }

        if (minutes <= 5 && (lastSpokenMinutes > 5 || lastSpokenMinutes < 0)) {
            return true;
        }

        if (!vehicle.getVehicleId().equals(lastSpokenVehicleId) && minutes <= 3) {
            return true;
        }

        return false;
    }

    private String generateSpeakText(String lineCode, EtaResponse.EtaVehicle vehicle) {
        StringBuilder sb = new StringBuilder();
        sb.append(lineCode).append("路");

        int minutes = vehicle.getEstimatedMinutes();
        if (minutes <= 0) {
            sb.append("即将进站");
        } else if (minutes == 1) {
            sb.append("还有1分钟到站");
        } else {
            sb.append("还有").append(minutes).append("分钟到站");
        }

        int stations = vehicle.getDistanceStationsAway();
        if (stations > 0) {
            sb.append("，距离").append(stations).append("站");
        }

        if (vehicle.getCrowdLevel() == 3) {
            sb.append("，车内拥挤");
        } else if (vehicle.getCrowdLevel() == 1) {
            sb.append("，车内宽敞");
        }

        return sb.toString();
    }

    public void speak(String text) {
        if (text == null || text.isEmpty()) return;

        Log.i(TAG, "TTS speak: " + text + " (engine: " + currentEngine + ")");

        switch (currentEngine) {
            case IFLYTEK:
                speakIflytek(text);
                break;
            case CLOUD:
                speakCloud(text);
                break;
            default:
                Log.w(TAG, "No TTS engine available");
        }
    }

    private void speakIflytek(String text) {
        try {
            Class<?> speechSynthesizer = Class.forName("com.iflytek.cloud.SpeechSynthesizer");
            Object synthesizer = speechSynthesizer.getMethod("createSynthesizer", Context.class, Object.class)
                    .invoke(null, context, null);

            speechSynthesizer.getMethod("startSpeaking", String.class, Object.class)
                    .invoke(synthesizer, text, null);
        } catch (Exception e) {
            Log.e(TAG, "iFlytek TTS failed: " + e.getMessage());
            speakCloud(text);
        }
    }

    private void speakCloud(String text) {
        audioExecutor.execute(() -> {
            try {
                byte[] audioData = fetchCloudTts(text);
                if (audioData != null && audioData.length > 0) {
                    playWavAudio(audioData);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cloud TTS failed: " + e.getMessage());
            }
        });
    }

    private byte[] fetchCloudTts(String text) throws Exception {
        String urlStr = BuildConfig.API_BASE_URL + "/tts/speak?text=" +
                java.net.URLEncoder.encode(text, "UTF-8") +
                "&speed=1.0&volume=1.0";

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code != 200) {
            Log.e(TAG, "Cloud TTS HTTP error: " + code);
            return null;
        }

        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        is.close();
        conn.disconnect();

        return baos.toByteArray();
    }

    private void playWavAudio(byte[] wavData) {
        try {
            if (playing) {
                stopAudio();
            }

            WavHeader header = parseWavHeader(wavData);
            if (header == null) {
                Log.e(TAG, "Invalid WAV header");
                return;
            }

            int bufferSize = AudioTrack.getMinBufferSize(
                    header.sampleRate,
                    header.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                    header.bitsPerSample == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT
            );

            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(header.sampleRate)
                            .setChannelMask(header.channels == 1 ?
                                    AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(header.bitsPerSample == 16 ?
                                    AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();

            playing = true;
            audioTrack.play();

            int offset = header.dataOffset;
            int remaining = wavData.length - offset;

            while (remaining > 0 && playing) {
                int chunk = Math.min(bufferSize, remaining);
                audioTrack.write(wavData, offset, chunk);
                offset += chunk;
                remaining -= chunk;
            }

            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
            playing = false;

        } catch (Exception e) {
            Log.e(TAG, "Audio playback failed", e);
            playing = false;
        }
    }

    private void stopAudio() {
        playing = false;
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
    }

    private WavHeader parseWavHeader(byte[] data) {
        if (data == null || data.length < 44) return null;

        try {
            WavHeader header = new WavHeader();
            header.sampleRate = readIntLittleEndian(data, 24);
            header.channels = readShortLittleEndian(data, 22);
            header.bitsPerSample = readShortLittleEndian(data, 34);
            header.dataOffset = 44;

            int dataSize = readIntLittleEndian(data, 40);
            Log.d(TAG, "WAV: sampleRate=" + header.sampleRate +
                    ", channels=" + header.channels +
                    ", bits=" + header.bitsPerSample +
                    ", dataSize=" + dataSize);

            return header;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse WAV header", e);
            return null;
        }
    }

    private int readIntLittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private short readShortLittleEndian(byte[] data, int offset) {
        return (short) ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8));
    }

    private static class WavHeader {
        int sampleRate;
        int channels;
        int bitsPerSample;
        int dataOffset;
    }

    public TtsEngine getCurrentEngine() {
        return currentEngine;
    }

    public boolean isAvailable() {
        return currentEngine != TtsEngine.NONE && initialized;
    }

    public void release() {
        stopAudio();
        audioExecutor.shutdown();
        handler.removeCallbacksAndMessages(null);
        initialized = false;
    }
}
