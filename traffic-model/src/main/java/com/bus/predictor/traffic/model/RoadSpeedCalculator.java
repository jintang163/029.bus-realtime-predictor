package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RoadSpeedCalculator {

    private static final double EMA_ALPHA = 0.3;

    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final VehiclePositionCollector positionCollector;

    public RoadSpeedCalculator(RoadSegmentRedisDao roadSegmentRedisDao,
                               VehiclePositionCollector positionCollector) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.positionCollector = positionCollector;
    }

    @Scheduled(fixedRate = 60000)
    public void calculateAndUpdate() {
        Set<String> onlineVehicles = positionCollector.getOnlineVehicleIds();
        if (onlineVehicles == null || onlineVehicles.isEmpty()) {
            return;
        }

        for (String vehicleId : onlineVehicles) {
            VehiclePosition position = positionCollector.getLatestPosition(vehicleId);
            if (position == null || position.getSpeed() == null) {
                continue;
            }

            String geoHash = GeoHashUtil.encode6(position.getLatitude(), position.getLongitude());
            double currentSpeed = position.getSpeed();

            Double existingSpeed = roadSegmentRedisDao.getSegmentSpeed(geoHash);
            double updatedSpeed;
            if (existingSpeed != null && existingSpeed > 0) {
                updatedSpeed = EMA_ALPHA * currentSpeed + (1 - EMA_ALPHA) * existingSpeed;
            } else {
                updatedSpeed = currentSpeed;
            }

            roadSegmentRedisDao.saveSegmentSpeed(geoHash, updatedSpeed);

            double freeFlowSpeed = 13.89;
            double congestionFactor = freeFlowSpeed / Math.max(updatedSpeed, 0.5);
            roadSegmentRedisDao.saveSegmentCongestion(geoHash, congestionFactor);
        }
    }
}
