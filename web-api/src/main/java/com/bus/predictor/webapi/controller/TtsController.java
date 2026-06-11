package com.bus.predictor.webapi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    @Value("${tts.enabled:true}")
    private boolean ttsEnabled;

    @Value("${tts.provider:builtin}")
    private String ttsProvider;

    @Value("${tts.sample-rate:16000}")
    private int sampleRate;

    @Value("${tts.sample-bits:16}")
    private int sampleBits;

    @Value("${tts.channels:1}")
    private int channels;

    @GetMapping(value = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@RequestParam("text") String text,
                                         @RequestParam(value = "speed", defaultValue = "1.0") double speed,
                                         @RequestParam(value = "volume", defaultValue = "1.0") double volume) {
        log.info("TTS request: text={}, speed={}, volume={}", text, speed, volume);

        if (!ttsEnabled || text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] audioData = generateTtsAudio(text, speed, volume);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setContentLength(audioData.length);
            headers.set("Content-Disposition", "inline; filename=\"tts.wav\"");
            headers.setCacheControl("no-cache, no-store, must-revalidate");

            log.debug("TTS audio generated: {} bytes, duration ~{}s",
                    audioData.length, audioData.length / (sampleRate * channels * sampleBits / 8));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(audioData);
        } catch (Exception e) {
            log.error("TTS generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("TTS service is " + (ttsEnabled ? "enabled" : "disabled") + " (provider: " + ttsProvider + ")");
    }

    private byte[] generateTtsAudio(String text, double speed, double volume) throws Exception {
        switch (ttsProvider.toLowerCase()) {
            case "builtin":
                return generateBuiltinTts(text, speed, volume);
            case "mock":
            default:
                return generateMockTts(text, speed, volume);
        }
    }

    private byte[] generateBuiltinTts(String text, double speed, double volume) {
        try {
            return generateToneSequence(text, speed, volume);
        } catch (Exception e) {
            log.warn("Builtin TTS failed, falling back to mock: {}", e.getMessage());
            return generateMockTts(text, speed, volume);
        }
    }

    private byte[] generateToneSequence(String text, double speed, double volume) {
        int chars = text.length();
        double effectiveRate = sampleRate / speed;
        int totalSamples = (int) (chars * effectiveRate * 0.15);
        int byteCount = totalSamples * channels * (sampleBits / 8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            writeWavHeader(baos, totalSamples, sampleRate, channels, sampleBits);

            double[] baseFreqs = {262, 294, 330, 349, 392, 440, 494};
            double vol = Math.max(0.0, Math.min(1.0, volume));

            for (int i = 0; i < chars; i++) {
                char c = text.charAt(i);
                int freqIdx = Math.abs(c) % baseFreqs.length;
                double freq = baseFreqs[freqIdx];

                int charSamples = (int) (effectiveRate * 0.12);
                int fadeSamples = (int) (effectiveRate * 0.02);

                for (int s = 0; s < charSamples; s++) {
                    double t = (double) s / sampleRate;
                    double envelope = 1.0;

                    if (s < fadeSamples) {
                        envelope = (double) s / fadeSamples;
                    } else if (s > charSamples - fadeSamples) {
                        envelope = (double) (charSamples - s) / fadeSamples;
                    }

                    double sample = Math.sin(2 * Math.PI * freq * t) * 0.3 * vol * envelope;

                    double harmonic = Math.sin(2 * Math.PI * freq * 2 * t) * 0.1 * vol * envelope;
                    sample += harmonic;

                    short shortSample = (short) (sample * Short.MAX_VALUE);
                    if (sampleBits == 16) {
                        for (int ch = 0; ch < channels; ch++) {
                            baos.write(shortSample & 0xFF);
                            baos.write((shortSample >> 8) & 0xFF);
                        }
                    } else {
                        byte byteSample = (byte) (sample * Byte.MAX_VALUE);
                        for (int ch = 0; ch < channels; ch++) {
                            baos.write(byteSample);
                        }
                    }
                }

                int silenceSamples = (int) (effectiveRate * 0.03);
                for (int s = 0; s < silenceSamples; s++) {
                    for (int ch = 0; ch < channels; ch++) {
                        if (sampleBits == 16) {
                            baos.write(0);
                            baos.write(0);
                        } else {
                            baos.write(0);
                        }
                    }
                }
            }

            byte[] data = baos.toByteArray();
            updateWavDataSize(baos, data.length - 44, data.length - 8);

            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("TTS generation failed", e);
        }
    }

    private byte[] generateMockTts(String text, double speed, double volume) {
        try {
            double duration = Math.max(1.0, text.length() * 0.2 / speed);
            int totalSamples = (int) (sampleRate * duration);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeWavHeader(baos, totalSamples, sampleRate, channels, sampleBits);

            double vol = Math.max(0.0, Math.min(1.0, volume));

            for (int i = 0; i < totalSamples; i++) {
                double t = (double) i / sampleRate;
                double envelope = 1.0;
                int fadeSamples = sampleRate / 10;
                if (i < fadeSamples) {
                    envelope = (double) i / fadeSamples;
                } else if (i > totalSamples - fadeSamples) {
                    envelope = (double) (totalSamples - i) / fadeSamples;
                }

                double sample = 0;
                double freqMod = 200 + 50 * Math.sin(2 * Math.PI * 5 * t);
                sample += Math.sin(2 * Math.PI * freqMod * t) * 0.2;
                sample += Math.sin(2 * Math.PI * freqMod * 1.5 * t) * 0.1;
                sample += Math.random() * 0.02 - 0.01;

                sample *= vol * envelope;

                short shortSample = (short) (sample * Short.MAX_VALUE * 0.5);
                for (int ch = 0; ch < channels; ch++) {
                    if (sampleBits == 16) {
                        baos.write(shortSample & 0xFF);
                        baos.write((shortSample >> 8) & 0xFF);
                    } else {
                        baos.write((byte) (sample * Byte.MAX_VALUE));
                    }
                }
            }

            byte[] data = baos.toByteArray();
            updateWavDataSize(baos, data.length - 44, data.length - 8);

            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Mock TTS generation failed", e);
        }
    }

    private void writeWavHeader(ByteArrayOutputStream baos, int numSamples,
                                int sampleRate, int channels, int sampleBits) {
        int byteRate = sampleRate * channels * sampleBits / 8;
        int blockAlign = channels * sampleBits / 8;
        int dataSize = numSamples * blockAlign;

        try {
            baos.write("RIFF".getBytes());
            baos.write(intToLittleEndian(36 + dataSize));
            baos.write("WAVE".getBytes());
            baos.write("fmt ".getBytes());
            baos.write(intToLittleEndian(16));
            baos.write(shortToLittleEndian((short) 1));
            baos.write(shortToLittleEndian((short) channels));
            baos.write(intToLittleEndian(sampleRate));
            baos.write(intToLittleEndian(byteRate));
            baos.write(shortToLittleEndian((short) blockAlign));
            baos.write(shortToLittleEndian((short) sampleBits));
            baos.write("data".getBytes());
            baos.write(intToLittleEndian(dataSize));
        } catch (Exception e) {
            throw new RuntimeException("WAV header write failed", e);
        }
    }

    private void updateWavDataSize(ByteArrayOutputStream baos, int dataSize, int riffSize) {
        byte[] data = baos.toByteArray();
        byte[] ds = intToLittleEndian(dataSize);
        byte[] rs = intToLittleEndian(riffSize);
        System.arraycopy(rs, 0, data, 4, 4);
        System.arraycopy(ds, 0, data, 40, 4);
        baos.reset();
        baos.write(data, 0, data.length);
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }
}
