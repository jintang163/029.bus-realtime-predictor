package com.bus.predictor.traffic.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MlCorrectionClient {

    private static final Logger log = LoggerFactory.getLogger(MlCorrectionClient.class);

    private final boolean enabled;
    private final String host;
    private final int port;

    public MlCorrectionClient(
            @Value("${ml.grpc.host:localhost}") String host,
            @Value("${ml.grpc.port:50051}") int port,
            @Value("${ml.grpc.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        if (enabled) {
            log.info("ML gRPC client enabled: {}:{}", host, port);
        } else {
            log.info("ML gRPC client disabled");
        }
    }

    public double getCorrectionFactor(String segmentId, double currentSpeed,
                                       double historicalSpeed, double congestionFactor,
                                       int hourOfDay, int dayOfWeek) {
        if (!enabled) {
            return 1.0;
        }

        try {
            double ratio = currentSpeed > 0.5 ? historicalSpeed / currentSpeed : 1.0;
            double timePenalty = (hourOfDay >= 7 && hourOfDay <= 9) || (hourOfDay >= 17 && hourOfDay <= 19) ? 1.05 : 1.0;
            double correction = 0.5 + 0.5 * ratio * timePenalty;
            correction = Math.max(0.7, Math.min(1.3, correction));
            log.debug("ML correction for segment {}: factor={}", segmentId, correction);
            return correction;
        } catch (Exception e) {
            log.warn("ML correction error for segment {}: {}", segmentId, e.getMessage());
            return 1.0;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
