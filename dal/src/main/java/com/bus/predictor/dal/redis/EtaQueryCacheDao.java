package com.bus.predictor.dal.redis;

import com.bus.predictor.common.constant.RedisKeyConstant;
import com.bus.predictor.common.model.EtaResponse;
import com.bus.predictor.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class EtaQueryCacheDao {

    private static final Logger log = LoggerFactory.getLogger(EtaQueryCacheDao.class);

    private static final long CACHE_TTL_SECONDS = 30;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void saveEtaResponse(String lineCode, String stationName, String direction, EtaResponse response) {
        String key = buildKey(lineCode, stationName, direction);
        String value = JsonUtil.toJson(response);
        redisTemplate.opsForValue().set(key, value, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public EtaResponse getEtaResponse(String lineCode, String stationName, String direction) {
        String key = buildKey(lineCode, stationName, direction);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return JsonUtil.fromJson(value, EtaResponse.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached ETA response for {}/{}/{}", lineCode, stationName, direction);
            return null;
        }
    }

    public void invalidateCache(String lineCode, String stationName, String direction) {
        String key = buildKey(lineCode, stationName, direction);
        redisTemplate.delete(key);
    }

    private String buildKey(String lineCode, String stationName, String direction) {
        return RedisKeyConstant.ETA_QUERY_CACHE_PREFIX
                + (lineCode != null ? lineCode : "all") + ":"
                + (stationName != null ? stationName.replaceAll("\\s+", "_") : "all") + ":"
                + (direction != null ? direction : "up");
    }
}
