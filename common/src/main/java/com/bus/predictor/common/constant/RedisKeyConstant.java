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
}
