package com.bus.predictor.dal.redis;

import com.bus.predictor.common.constant.RedisKeyConstant;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.model.VehicleStatus;
import com.bus.predictor.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class VehiclePositionRedisDao {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionRedisDao.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void savePosition(VehiclePosition position) {
        String key = RedisKeyConstant.VEHICLE_POSITION_PREFIX + position.getVehicleId();
        String value = JsonUtil.toJson(position);
        redisTemplate.opsForValue().set(key, value, 5, TimeUnit.MINUTES);
    }

    public VehiclePosition getPosition(String vehicleId) {
        String key = RedisKeyConstant.VEHICLE_POSITION_PREFIX + vehicleId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return JsonUtil.fromJson(value, VehiclePosition.class);
    }

    public void updateOnlineStatus(String vehicleId, VehicleStatus status) {
        String statusKey = RedisKeyConstant.VEHICLE_STATUS_PREFIX + vehicleId;
        redisTemplate.opsForValue().set(statusKey, String.valueOf(status.getCode()), 5, TimeUnit.MINUTES);

        if (status == VehicleStatus.ONLINE) {
            redisTemplate.opsForSet().add(RedisKeyConstant.VEHICLE_ONLINE_SET, vehicleId);
        } else {
            redisTemplate.opsForSet().remove(RedisKeyConstant.VEHICLE_ONLINE_SET, vehicleId);
        }
    }

    public Set<String> getOnlineVehicleIds() {
        return redisTemplate.opsForSet().members(RedisKeyConstant.VEHICLE_ONLINE_SET);
    }

    public boolean isVehicleOnline(String vehicleId) {
        String statusKey = RedisKeyConstant.VEHICLE_STATUS_PREFIX + vehicleId;
        String status = redisTemplate.opsForValue().get(statusKey);
        return status != null && Integer.parseInt(status) == VehicleStatus.ONLINE.getCode();
    }

    public List<VehiclePosition> getAllOnlinePositions() {
        Set<String> onlineIds = getOnlineVehicleIds();
        List<VehiclePosition> positions = new ArrayList<>();
        if (onlineIds == null) {
            return positions;
        }
        for (String vehicleId : onlineIds) {
            VehiclePosition pos = getPosition(vehicleId);
            if (pos != null) {
                positions.add(pos);
            }
        }
        return positions;
    }
}
