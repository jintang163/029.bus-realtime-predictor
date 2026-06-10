package com.bus.predictor.dal.redis;

import com.bus.predictor.common.constant.RedisKeyConstant;
import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class ArrivalPredictionCacheDao {

    private static final Logger log = LoggerFactory.getLogger(ArrivalPredictionCacheDao.class);

    private static final long CACHE_TTL_SECONDS = 30;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void savePredictions(String vehicleId, String routeId, List<ArrivalPrediction> predictions) {
        String key = buildKey(vehicleId, routeId);
        String value = JsonUtil.toJson(predictions);
        redisTemplate.opsForValue().set(key, value, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    public List<ArrivalPrediction> getPredictions(String vehicleId, String routeId) {
        String key = buildKey(vehicleId, routeId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return JsonUtil.fromJson(value, List.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached predictions for {}/{}", vehicleId, routeId);
            return null;
        }
    }

    public List<ArrivalPrediction> getPredictionsTyped(String vehicleId, String routeId) {
        String key = buildKey(vehicleId, routeId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return JsonUtil.parseList(value, ArrivalPrediction.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached predictions for {}/{}", vehicleId, routeId);
            return null;
        }
    }

    private String buildKey(String vehicleId, String routeId) {
        return RedisKeyConstant.ARRIVAL_PREDICTION_PREFIX + vehicleId + ":" + routeId;
    }
}
