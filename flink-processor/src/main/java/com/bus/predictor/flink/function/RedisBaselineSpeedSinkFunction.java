package com.bus.predictor.flink.function;

import com.bus.predictor.common.constant.RedisKeyConstant;
import com.bus.predictor.common.model.SegmentSpeedAggregate;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisBaselineSpeedSinkFunction extends RichSinkFunction<SegmentSpeedAggregate> {

    private static final Logger log = LoggerFactory.getLogger(RedisBaselineSpeedSinkFunction.class);

    private final String redisHost;
    private final int redisPort;
    private final double freeFlowSpeed;

    private transient JedisPool jedisPool;

    private final Map<String, AggregateState> aggregateStateMap = new HashMap<>();
    private long lastFlushTime = System.currentTimeMillis();
    private static final long FLUSH_INTERVAL_MS = 60000L;

    public RedisBaselineSpeedSinkFunction(String redisHost, int redisPort, double freeFlowSpeed) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.freeFlowSpeed = freeFlowSpeed;
    }

    @Override
    public void open(Configuration parameters) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(5);
        poolConfig.setMaxIdle(2);
        jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
    }

    @Override
    public void invoke(SegmentSpeedAggregate value, Context context) {
        if (value == null || value.getSegmentId() == null) {
            return;
        }

        String aggKey = value.getSegmentId() + ":" + value.getHourOfDay();
        AggregateState state = aggregateStateMap.computeIfAbsent(aggKey, k -> new AggregateState());

        state.count++;
        double alpha = Math.min(0.3, 2.0 / (state.count + 1));
        state.avgSpeed = state.avgSpeed * (1 - alpha) + value.getSpeed() * alpha;
        state.dayOfWeek = value.getDayOfWeek();

        long now = System.currentTimeMillis();
        if (now - lastFlushTime >= FLUSH_INTERVAL_MS) {
            flushAll();
            lastFlushTime = now;
        }
    }

    private void flushAll() {
        if (aggregateStateMap.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            for (Map.Entry<String, AggregateState> entry : aggregateStateMap.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String segmentId = parts[0];
                int hourOfDay = Integer.parseInt(parts[1]);
                AggregateState state = entry.getValue();

                String speedKey = RedisKeyConstant.SELF_LEARNING_BASELINE_SPEED_PREFIX
                        + segmentId + ":" + state.dayOfWeek + ":" + hourOfDay;
                jedis.setex(speedKey, (int) TimeUnit.HOURS.toSeconds(24),
                        String.valueOf(state.avgSpeed));

                double congestionFactor = Math.max(1.0, Math.min(5.0,
                        freeFlowSpeed / Math.max(state.avgSpeed, 1.0)));
                String congestionKey = RedisKeyConstant.SELF_LEARNING_BASELINE_CONGESTION_PREFIX
                        + segmentId + ":" + state.dayOfWeek + ":" + hourOfDay;
                jedis.setex(congestionKey, (int) TimeUnit.HOURS.toSeconds(24),
                        String.valueOf(congestionFactor));

                String hourlyKey = RedisKeyConstant.SELF_LEARNING_HOURLY_AGG_PREFIX
                        + segmentId + ":" + hourOfDay;
                jedis.setex(hourlyKey, (int) TimeUnit.HOURS.toSeconds(2),
                        state.avgSpeed + "," + state.count);
            }
            log.info("Flushed {} baseline speed aggregates to Redis", aggregateStateMap.size());
        } catch (Exception e) {
            log.error("Failed to flush baseline speeds to Redis", e);
        }

        aggregateStateMap.clear();
    }

    @Override
    public void close() {
        flushAll();
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    private static class AggregateState {
        double avgSpeed = 0.0;
        long count = 0;
        int dayOfWeek = 1;
    }
}
