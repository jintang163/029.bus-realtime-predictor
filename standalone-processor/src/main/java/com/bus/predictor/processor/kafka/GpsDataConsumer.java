package com.bus.predictor.processor.kafka;

import com.bus.predictor.common.constant.KafkaTopicConstant;
import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.model.VehicleStatus;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.common.util.GpsValidator;
import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.dal.entity.TrajectoryRecordEntity;
import com.bus.predictor.dal.mapper.TrajectoryRecordMapper;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class GpsDataConsumer {

    private static final Logger log = LoggerFactory.getLogger(GpsDataConsumer.class);

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final TrajectoryRecordMapper trajectoryRecordMapper;

    public GpsDataConsumer(VehiclePositionRedisDao vehiclePositionRedisDao,
                           RoadSegmentRedisDao roadSegmentRedisDao,
                           TrajectoryRecordMapper trajectoryRecordMapper) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.trajectoryRecordMapper = trajectoryRecordMapper;
    }

    @KafkaListener(topics = KafkaTopicConstant.GPS_RAW_TOPIC, groupId = "standalone-processor")
    public void consumeGpsRaw(String message) {
        try {
            GpsData gps = JsonUtil.fromJson(message, GpsData.class);

            if (!GpsValidator.isValid(gps)) {
                log.warn("Invalid GPS data discarded: vehicle={}", gps.getVehicleId());
                return;
            }

            VehiclePosition position = VehiclePosition.builder()
                    .vehicleId(gps.getVehicleId())
                    .longitude(gps.getLongitude())
                    .latitude(gps.getLatitude())
                    .speed(gps.getSpeed())
                    .direction(gps.getDirection())
                    .geoHash(GeoHashUtil.encode6(gps.getLatitude(), gps.getLongitude()))
                    .gpsTime(gps.getTimestamp())
                    .receiveTime(System.currentTimeMillis())
                    .status(VehicleStatus.ONLINE)
                    .build();

            vehiclePositionRedisDao.savePosition(position);
            vehiclePositionRedisDao.updateOnlineStatus(gps.getVehicleId(), VehicleStatus.ONLINE);

            updateRoadSegmentSpeed(position);

            saveTrajectory(gps);

            log.debug("Processed GPS: vehicle={}, lng={}, lat={}",
                    gps.getVehicleId(), gps.getLongitude(), gps.getLatitude());

        } catch (Exception e) {
            log.error("Failed to process GPS message: {}", message, e);
        }
    }

    private void updateRoadSegmentSpeed(VehiclePosition position) {
        if (position.getSpeed() == null || position.getSpeed() <= 0) {
            return;
        }
        String geoHash = position.getGeoHash();
        Double existingSpeed = roadSegmentRedisDao.getSegmentSpeed(geoHash);
        double updatedSpeed;
        if (existingSpeed != null && existingSpeed > 0) {
            updatedSpeed = 0.3 * position.getSpeed() + 0.7 * existingSpeed;
        } else {
            updatedSpeed = position.getSpeed();
        }
        roadSegmentRedisDao.saveSegmentSpeed(geoHash, updatedSpeed);

        double freeFlowSpeed = 13.89;
        double congestionFactor = freeFlowSpeed / Math.max(updatedSpeed, 0.5);
        roadSegmentRedisDao.saveSegmentCongestion(geoHash, congestionFactor);
    }

    private void saveTrajectory(GpsData gps) {
        try {
            LocalDateTime gpsTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(gps.getTimestamp()), ZoneId.systemDefault());

            TrajectoryRecordEntity entity = new TrajectoryRecordEntity();
            entity.setVehicleId(gps.getVehicleId());
            entity.setLongitude(gps.getLongitude());
            entity.setLatitude(gps.getLatitude());
            entity.setSpeed(gps.getSpeed() != null ? gps.getSpeed() : 0);
            entity.setDirection(gps.getDirection() != null ? gps.getDirection() : 0);
            entity.setGpsTime(gpsTime);
            entity.setCreateTime(LocalDateTime.now());

            trajectoryRecordMapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to save trajectory for vehicle={}", gps.getVehicleId(), e);
        }
    }
}
