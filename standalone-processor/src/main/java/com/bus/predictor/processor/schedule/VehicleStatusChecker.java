package com.bus.predictor.processor.schedule;

import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.model.VehicleStatus;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VehicleStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(VehicleStatusChecker.class);

    private static final long OFFLINE_THRESHOLD_MS = 120_000;

    private final VehiclePositionRedisDao vehiclePositionRedisDao;

    public VehicleStatusChecker(VehiclePositionRedisDao vehiclePositionRedisDao) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
    }

    @Scheduled(fixedRate = 30000)
    public void checkVehicleStatus() {
        Set<String> onlineIds = vehiclePositionRedisDao.getOnlineVehicleIds();
        if (onlineIds == null || onlineIds.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String vehicleId : onlineIds) {
            VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
            if (position == null) {
                vehiclePositionRedisDao.updateOnlineStatus(vehicleId, VehicleStatus.OFFLINE);
                continue;
            }

            long age = now - position.getGpsTime();
            if (age > OFFLINE_THRESHOLD_MS) {
                vehiclePositionRedisDao.updateOnlineStatus(vehicleId, VehicleStatus.OFFLINE);
                log.info("Vehicle {} marked as OFFLINE (data age: {}ms)", vehicleId, age);
            }
        }
    }
}
