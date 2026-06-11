package com.bus.predictor.common.constant;

public interface RedisKeyConstant {

    String VEHICLE_POSITION_PREFIX = "bus:vehicle:pos:";
    String VEHICLE_STATUS_PREFIX = "bus:vehicle:status:";
    String VEHICLE_ONLINE_SET = "bus:vehicle:online";
    String ROAD_SEGMENT_SPEED_PREFIX = "bus:road:speed:";
    String ROAD_SEGMENT_CONGESTION_PREFIX = "bus:road:congestion:";
    String STATION_INFO_PREFIX = "bus:station:info:";
    String ROUTE_STATION_PREFIX = "bus:route:station:";
    String ARRIVAL_PREDICTION_PREFIX = "bus:arrival:prediction:";
    String KALMAN_SPEED_PREFIX = "bus:kalman:speed:";

    String SELF_LEARNING_BASELINE_SPEED_PREFIX = "bus:baseline:speed:";
    String SELF_LEARNING_BASELINE_CONGESTION_PREFIX = "bus:baseline:congestion:";
    String SELF_LEARNING_HOURLY_AGG_PREFIX = "bus:agg:hourly:";
    String PREDICTION_DEVIATION_PREFIX = "bus:deviation:";
    String ACCURACY_STATS_PREFIX = "bus:accuracy:stats:";
    String AMAP_API_HEALTH_KEY = "bus:api:amap:health";
    String BASELINE_TRAIN_STATUS_KEY = "bus:baseline:train:status";
    String VEHICLE_SEGMENT_ENTRY_PREFIX = "bus:vehicle:segment:entry:";
    String ETA_QUERY_CACHE_PREFIX = "bus:eta:query:";
}
