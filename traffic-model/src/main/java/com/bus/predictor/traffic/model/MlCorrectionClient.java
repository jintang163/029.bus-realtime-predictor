package com.bus.predictor.traffic.model;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Component
public class MlCorrectionClient {

    private static final Logger log = LoggerFactory.getLogger(MlCorrectionClient.class);

    private final boolean enabled;
    private final ManagedChannel channel;
    private final PredictServiceGrpc.PredictServiceBlockingStub blockingStub;

    public MlCorrectionClient(
            @Value("${ml.grpc.host:localhost}") String host,
            @Value("${ml.grpc.port:50051}") int port,
            @Value("${ml.grpc.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            this.blockingStub = PredictServiceGrpc.newBlockingStub(channel);
            log.info("ML gRPC client initialized: {}:{}", host, port);
        } else {
            this.channel = null;
            this.blockingStub = null;
            log.info("ML gRPC client disabled");
        }
    }

    public double getCorrectionFactor(String segmentId, double currentSpeed,
                                       double historicalSpeed, double congestionFactor,
                                       int hourOfDay, int dayOfWeek) {
        if (!enabled || blockingStub == null) {
            return 1.0;
        }

        try {
            PredictServiceProto.PredictRequest request = PredictServiceProto.PredictRequest.newBuilder()
                    .setSegmentId(segmentId)
                    .setCurrentSpeed(currentSpeed)
                    .setHistoricalSpeed(historicalSpeed)
                    .setCongestionFactor(congestionFactor)
                    .setHourOfDay(hourOfDay)
                    .setDayOfWeek(dayOfWeek)
                    .build();

            PredictServiceProto.PredictResponse response = blockingStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .predictCorrection(request);

            double factor = response.getCorrectionFactor();
            double confidence = response.getConfidence();
            log.debug("ML gRPC correction for segment {}: factor={}, confidence={}", segmentId, factor, confidence);
            return factor;
        } catch (StatusRuntimeException e) {
            log.warn("ML gRPC call failed for segment {}: status={}, falling back to local", segmentId, e.getStatus().getCode());
            return fallbackCorrection(currentSpeed, historicalSpeed, congestionFactor, hourOfDay, dayOfWeek);
        } catch (Exception e) {
            log.warn("ML gRPC call error for segment {}: {}, falling back to local", segmentId, e.getMessage());
            return fallbackCorrection(currentSpeed, historicalSpeed, congestionFactor, hourOfDay, dayOfWeek);
        }
    }

    double fallbackCorrection(double currentSpeed, double historicalSpeed,
                              double congestionFactor, int hourOfDay, int dayOfWeek) {
        double ratio = currentSpeed > 0.5 ? historicalSpeed / currentSpeed : 1.0;
        double timePenalty = (hourOfDay >= 7 && hourOfDay <= 9) || (hourOfDay >= 17 && hourOfDay <= 19) ? 1.05 : 1.0;
        double weekendFactor = dayOfWeek >= 6 ? 1.02 : 1.0;
        double congestionAdj = 1.0 + (congestionFactor - 1.0) * 0.1;
        double correction = 0.5 + 0.5 * ratio * timePenalty * weekendFactor * congestionAdj;
        correction = Math.max(0.7, Math.min(1.3, correction));
        log.debug("ML fallback correction: factor={}", correction);
        return correction;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
