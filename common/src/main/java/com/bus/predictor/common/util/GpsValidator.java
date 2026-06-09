package com.bus.predictor.common.util;

import com.bus.predictor.common.model.GpsData;

public final class GpsValidator {

    private static final double MAX_SPEED_KMH = 120.0;
    private static final double MAX_SPEED_MS = MAX_SPEED_KMH / 3.6;

    private GpsValidator() {
    }

    public static boolean isValid(GpsData gps) {
        if (gps == null || !gps.isValid()) {
            return false;
        }
        if (gps.getSpeed() != null && gps.getSpeed() < 0) {
            return false;
        }
        if (gps.getSpeed() != null && gps.getSpeed() > MAX_SPEED_MS) {
            return false;
        }
        if (gps.getDirection() != null && (gps.getDirection() < 0 || gps.getDirection() > 360)) {
            return false;
        }
        return true;
    }

    public static boolean isPointJump(GpsData prev, GpsData current) {
        if (prev == null || current == null) {
            return false;
        }
        double distance = GeoHashUtil.haversineDistance(
                prev.getLatitude(), prev.getLongitude(),
                current.getLatitude(), current.getLongitude()
        );
        long timeDiff = Math.abs(current.getTimestamp() - prev.getTimestamp());
        if (timeDiff <= 0) {
            return distance > 10;
        }
        double speed = distance / (timeDiff / 1000.0);
        return speed > MAX_SPEED_MS * 1.5;
    }

    public static GpsData interpolate(GpsData prev, GpsData current) {
        if (prev == null) {
            return current;
        }
        long timeDiff = current.getTimestamp() - prev.getTimestamp();
        if (timeDiff <= 2000) {
            return current;
        }
        return current;
    }
}
