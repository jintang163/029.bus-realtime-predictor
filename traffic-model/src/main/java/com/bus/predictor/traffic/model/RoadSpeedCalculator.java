package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class RoadSpeedCalculator {

    private static final Logger log = LoggerFactory.getLogger(RoadSpeedCalculator.class);

    private static final double EMA_ALPHA = 0.3;
    private static final double FREE_FLOW_SPEED_MS = 13.89;

    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final VehiclePositionCollector positionCollector;
    private final RoadSegmentManager roadSegmentManager;

    public RoadSpeedCalculator(RoadSegmentRedisDao roadSegmentRedisDao,
                               VehiclePositionCollector positionCollector,
                               RoadSegmentManager roadSegmentManager) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.positionCollector = positionCollector;
        this.roadSegmentManager = roadSegmentManager;
    }

    @Scheduled(fixedRate = 60000)
    public void calculateAndUpdate() {
        Set<String> onlineVehicles = positionCollector.getOnlineVehicleIds();
        if (onlineVehicles == null || onlineVehicles.isEmpty()) {
            return;
        }

        List<SegmentInfo> segments = roadSegmentManager.getAllSegments();
        if (segments.isEmpty()) {
            calculateByGeoHash(onlineVehicles);
            return;
        }

        for (String vehicleId : onlineVehicles) {
            VehiclePosition position = positionCollector.getLatestPosition(vehicleId);
            if (position == null || position.getSpeed() == null) {
                continue;
            }

            SegmentInfo nearest = findNearestSegment(position, segments);
            if (nearest == null) {
                String geoHash = GeoHashUtil.encode6(position.getLatitude(), position.getLongitude());
                updateSegmentSpeed(geoHash, position.getSpeed());
                continue;
            }

            updateSegmentSpeed(nearest.getSegmentId(), position.getSpeed());
        }
    }

    private void calculateByGeoHash(Set<String> onlineVehicles) {
        for (String vehicleId : onlineVehicles) {
            VehiclePosition position = positionCollector.getLatestPosition(vehicleId);
            if (position == null || position.getSpeed() == null) {
                continue;
            }

            String geoHash = GeoHashUtil.encode6(position.getLatitude(), position.getLongitude());
            updateSegmentSpeed(geoHash, position.getSpeed());
        }
    }

    private void updateSegmentSpeed(String segmentId, double currentSpeed) {
        Double existingSpeed = roadSegmentRedisDao.getSegmentSpeed(segmentId);
        double updatedSpeed;
        if (existingSpeed != null && existingSpeed > 0) {
            updatedSpeed = EMA_ALPHA * currentSpeed + (1 - EMA_ALPHA) * existingSpeed;
        } else {
            updatedSpeed = currentSpeed;
        }

        roadSegmentRedisDao.saveSegmentSpeed(segmentId, updatedSpeed);

        double congestionFactor = FREE_FLOW_SPEED_MS / Math.max(updatedSpeed, 0.5);
        roadSegmentRedisDao.saveSegmentCongestion(segmentId, congestionFactor);
    }

    private SegmentInfo findNearestSegment(VehiclePosition position, List<SegmentInfo> segments) {
        SegmentInfo nearest = null;
        double minDist = Double.MAX_VALUE;

        for (SegmentInfo seg : segments) {
            double dist = pointToSegmentDistance(
                    position.getLatitude(), position.getLongitude(),
                    seg.getStartLat(), seg.getStartLng(),
                    seg.getEndLat(), seg.getEndLng());
            if (dist < minDist) {
                minDist = dist;
                nearest = seg;
            }
        }

        if (minDist > 500) {
            return null;
        }
        return nearest;
    }

    private double pointToSegmentDistance(double lat, double lng,
                                           double lat1, double lng1,
                                           double lat2, double lng2) {
        double d1 = GeoHashUtil.haversineDistance(lat, lng, lat1, lng1);
        double d2 = GeoHashUtil.haversineDistance(lat, lng, lat2, lng2);
        return Math.min(d1, d2);
    }
}
