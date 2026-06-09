package com.bus.predictor.flink.function;

import com.bus.predictor.common.constant.RedisKeyConstant;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.JsonUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class RedisPositionSinkFunction extends RichSinkFunction<VehiclePosition> {

    private static final Logger log = LoggerFactory.getLogger(RedisPositionSinkFunction.class);

    private final String redisHost;
    private final int redisPort;

    private transient Jedis jedis;

    public RedisPositionSinkFunction(String redisHost, int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    @Override
    public void open(Configuration parameters) {
        jedis = new Jedis(redisHost, redisPort);
        jedis.connect();
        log.info("Redis connected: {}:{}", redisHost, redisPort);
    }

    @Override
    public void invoke(VehiclePosition position, Context context) {
        try {
            String posKey = RedisKeyConstant.VEHICLE_POSITION_PREFIX + position.getVehicleId();
            String posValue = JsonUtil.toJson(position);
            jedis.setex(posKey, 300, posValue);

            String statusKey = RedisKeyConstant.VEHICLE_STATUS_PREFIX + position.getVehicleId();
            jedis.setex(statusKey, 300, String.valueOf(position.getStatus().getCode()));

            jedis.sadd(RedisKeyConstant.VEHICLE_ONLINE_SET, position.getVehicleId());
        } catch (Exception e) {
            log.error("Redis write failed for vehicle={}", position.getVehicleId(), e);
        }
    }

    @Override
    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
