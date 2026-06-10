package com.bus.predictor.dal.redis;

import com.bus.predictor.common.constant.RedisKeyConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
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

    public void saveBaselineSpeed(String segmentId, int dayOfWeek, int hourOfDay, double speed) {
        String key = RedisKeyConstant.SELF_LEARNING_BASELINE_SPEED_PREFIX + segmentId + ":" + dayOfWeek + ":" + hourOfDay;
        redisTemplate.opsForValue().set(key, String.valueOf(speed), 24, TimeUnit.HOURS);
    }

    public Double getBaselineSpeed(String segmentId, int dayOfWeek, int hourOfDay) {
        String key = RedisKeyConstant.SELF_LEARNING_BASELINE_SPEED_PREFIX + segmentId + ":" + dayOfWeek + ":" + hourOfDay;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : null;
    }

    public void saveBaselineCongestion(String segmentId, int dayOfWeek, int hourOfDay, double factor) {
        String key = RedisKeyConstant.SELF_LEARNING_BASELINE_CONGESTION_PREFIX + segmentId + ":" + dayOfWeek + ":" + hourOfDay;
        redisTemplate.opsForValue().set(key, String.valueOf(factor), 24, TimeUnit.HOURS);
    }

    public Double getBaselineCongestion(String segmentId, int dayOfWeek, int hourOfDay) {
        String key = RedisKeyConstant.SELF_LEARNING_BASELINE_CONGESTION_PREFIX + segmentId + ":" + dayOfWeek + ":" + hourOfDay;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : null;
    }

    public void saveHourlyAggSpeed(String segmentId, int hourOfDay, double avgSpeed, long sampleCount) {
        String key = RedisKeyConstant.SELF_LEARNING_HOURLY_AGG_PREFIX + segmentId + ":" + hourOfDay;
        String value = avgSpeed + "," + sampleCount;
        redisTemplate.opsForValue().set(key, value, 2, TimeUnit.HOURS);
    }

    public double[] getHourlyAggSpeed(String segmentId, int hourOfDay) {
        String key = RedisKeyConstant.SELF_LEARNING_HOURLY_AGG_PREFIX + segmentId + ":" + hourOfDay;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            String[] parts = value.split(",");
            return new double[]{Double.parseDouble(parts[0]), Long.parseLong(parts[1])};
        }
        return null;
    }

    public void setAmapApiHealthy(boolean healthy) {
        redisTemplate.opsForValue().set(
                RedisKeyConstant.AMAP_API_HEALTH_KEY,
                healthy ? "1" : "0",
                5, TimeUnit.MINUTES
        );
    }

    public boolean isAmapApiHealthy() {
        String value = redisTemplate.opsForValue().get(RedisKeyConstant.AMAP_API_HEALTH_KEY);
        return value == null || "1".equals(value);
    }

    public void saveVehicleSegmentEntry(String vehicleId, String segmentId, long timestamp, double speed) {
        String key = RedisKeyConstant.VEHICLE_SEGMENT_ENTRY_PREFIX + vehicleId;
        String value = segmentId + "," + timestamp + "," + speed;
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
    }

    public String getVehicleSegmentEntry(String vehicleId) {
        String key = RedisKeyConstant.VEHICLE_SEGMENT_ENTRY_PREFIX + vehicleId;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteVehicleSegmentEntry(String vehicleId) {
        String key = RedisKeyConstant.VEHICLE_SEGMENT_ENTRY_PREFIX + vehicleId;
        redisTemplate.delete(key);
    }

    public void setBaselineTrainStatus(Map<String, String> status) {
        redisTemplate.opsForHash().putAll(RedisKeyConstant.BASELINE_TRAIN_STATUS_KEY, status);
        redisTemplate.expire(RedisKeyConstant.BASELINE_TRAIN_STATUS_KEY, 1, TimeUnit.HOURS);
    }

    public Map<Object, Object> getBaselineTrainStatus() {
        return redisTemplate.opsForHash().entries(RedisKeyConstant.BASELINE_TRAIN_STATUS_KEY);
    }

    public void saveAccuracyStats(String key, String value) {
        String redisKey = RedisKeyConstant.ACCURACY_STATS_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, 1, TimeUnit.HOURS);
    }

    public String getAccuracyStats(String key) {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ACCURACY_STATS_PREFIX + key);
    }
}
