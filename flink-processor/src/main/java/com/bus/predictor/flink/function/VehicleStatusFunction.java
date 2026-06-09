package com.bus.predictor.flink.function;

import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.model.VehicleStatus;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleStatusFunction extends KeyedProcessFunction<String, GpsData, VehiclePosition> {

    private static final Logger log = LoggerFactory.getLogger(VehicleStatusFunction.class);

    private static final long OFFLINE_THRESHOLD_MS = 120_000;
    private static final long GPS_LOST_THRESHOLD_MS = 60_000;

    private transient long lastGpsTime;
    private transient int satelliteCount;

    @Override
    public void processElement(GpsData gps, Context ctx, Collector<VehiclePosition> out) {
        long now = System.currentTimeMillis();
        long gpsAge = now - gps.getTimestamp();

        VehicleStatus status;
        if (gpsAge > OFFLINE_THRESHOLD_MS) {
            status = VehicleStatus.OFFLINE;
        } else if (gps.getSatelliteCount() != null && gps.getSatelliteCount() < 3) {
            status = VehicleStatus.GPS_LOST;
        } else if (gps.getSpeed() != null && gps.getSpeed() < 0.1) {
            status = VehicleStatus.STOPPED;
        } else {
            status = VehicleStatus.ONLINE;
        }

        VehiclePosition position = VehiclePosition.builder()
                .vehicleId(gps.getVehicleId())
                .longitude(gps.getLongitude())
                .latitude(gps.getLatitude())
                .speed(gps.getSpeed())
                .direction(gps.getDirection())
                .gpsTime(gps.getTimestamp())
                .receiveTime(now)
                .status(status)
                .build();

        out.collect(position);

        lastGpsTime = gps.getTimestamp();
        if (gps.getSatelliteCount() != null) {
            satelliteCount = gps.getSatelliteCount();
        }
    }
}
