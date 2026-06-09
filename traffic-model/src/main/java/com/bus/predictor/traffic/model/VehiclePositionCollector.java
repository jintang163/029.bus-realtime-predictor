package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VehiclePositionCollector {

    private final VehiclePositionRedisDao vehiclePositionRedisDao;

    public VehiclePositionCollector(VehiclePositionRedisDao vehiclePositionRedisDao) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
    }

    public Set<String> getOnlineVehicleIds() {
        return vehiclePositionRedisDao.getOnlineVehicleIds();
    }

    public VehiclePosition getLatestPosition(String vehicleId) {
        return vehiclePositionRedisDao.getPosition(vehicleId);
    }
}
