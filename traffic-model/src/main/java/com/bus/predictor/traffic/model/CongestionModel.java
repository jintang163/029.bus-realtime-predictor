package com.bus.predictor.traffic.model;

import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class CongestionModel {

    private static final double FREE_FLOW_SPEED_MS = 13.89;
    private static final double MIN_CONGESTION = 1.0;
    private static final double MAX_CONGESTION = 5.0;

    private final RoadSegmentRedisDao roadSegmentRedisDao;

    public CongestionModel(RoadSegmentRedisDao roadSegmentRedisDao) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
    }

    public double calculateCongestion(double startLat, double startLng,
                                       double endLat, double endLng) {
        String geoHash = GeoHashUtil.encode6(startLat, startLng);
        double realTimeFactor = getRealTimeFactor(geoHash);
        double timeOfDayFactor = getTimeOfDayFactor();
        double weatherFactor = getWeatherFactor();

        double congestion = realTimeFactor * 0.5
                + timeOfDayFactor * 0.3
                + weatherFactor * 0.2;

        return Math.max(MIN_CONGESTION, Math.min(MAX_CONGESTION, congestion));
    }

    private double getRealTimeFactor(String geoHash) {
        Double segmentSpeed = roadSegmentRedisDao.getSegmentSpeed(geoHash);
        if (segmentSpeed != null && segmentSpeed > 0) {
            double ratio = FREE_FLOW_SPEED_MS / segmentSpeed;
            return Math.max(MIN_CONGESTION, Math.min(MAX_CONGESTION, ratio));
        }
        return 1.5;
    }

    private double getTimeOfDayFactor() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        DayOfWeek dayOfWeek = LocalDateTime.now().getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            if (hour >= 10 && hour <= 18) {
                return 1.5;
            }
            return 1.1;
        }

        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            return 2.5;
        }
        if ((hour >= 9 && hour <= 10) || (hour >= 16 && hour <= 17) || (hour >= 19 && hour <= 20)) {
            return 1.8;
        }
        if (hour >= 11 && hour <= 14) {
            return 1.4;
        }
        return 1.0;
    }

    private double getWeatherFactor() {
        return 1.0;
    }
}
