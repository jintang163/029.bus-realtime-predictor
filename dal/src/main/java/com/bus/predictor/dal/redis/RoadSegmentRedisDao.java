package com.bus.predictor.dal.redis;

import com.bus.predictor.common.constant.RedisKeyConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RoadSegmentRedisDao {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void saveSegmentSpeed(String segmentId, double speed) {
        String key = RedisKeyConstant.ROAD_SEGMENT_SPEED_PREFIX + segmentId;
        redisTemplate.opsForValue().set(key, String.valueOf(speed), 10, TimeUnit.MINUTES);
    }

    public Double getSegmentSpeed(String segmentId) {
        String key = RedisKeyConstant.ROAD_SEGMENT_SPEED_PREFIX + segmentId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : null;
    }

    public void saveSegmentCongestion(String segmentId, double factor) {
        String key = RedisKeyConstant.ROAD_SEGMENT_CONGESTION_PREFIX + segmentId;
        redisTemplate.opsForValue().set(key, String.valueOf(factor), 10, TimeUnit.MINUTES);
    }

    public Double getSegmentCongestion(String segmentId) {
        String key = RedisKeyConstant.ROAD_SEGMENT_CONGESTION_PREFIX + segmentId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : null;
    }
}
