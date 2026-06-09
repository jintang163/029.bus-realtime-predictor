package com.bus.predictor.flink.function;

import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.util.GpsValidator;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GpsCleanFunction extends KeyedProcessFunction<String, GpsData, GpsData> {

    private static final Logger log = LoggerFactory.getLogger(GpsCleanFunction.class);

    private static final OutputTag<GpsData> DIRTY_TAG = new OutputTag<GpsData>("dirty-data") {};

    private transient GpsData lastValidGps;

    @Override
    public void processElement(GpsData gps, Context ctx, Collector<GpsData> out) {
        if (lastValidGps == null) {
            lastValidGps = gps;
            out.collect(gps);
            return;
        }

        if (GpsValidator.isPointJump(lastValidGps, gps)) {
            log.warn("GPS point jump detected: vehicle={}, prev=({},{}) current=({},{})",
                    gps.getVehicleId(),
                    lastValidGps.getLatitude(), lastValidGps.getLongitude(),
                    gps.getLatitude(), gps.getLongitude());
            ctx.output(DIRTY_TAG, gps);
            return;
        }

        if (gps.getSpeed() != null && gps.getSpeed() < 0.5
                && lastValidGps.getSpeed() != null && lastValidGps.getSpeed() < 0.5) {
            GpsData smoothed = GpsData.builder()
                    .vehicleId(gps.getVehicleId())
                    .longitude((lastValidGps.getLongitude() + gps.getLongitude()) / 2)
                    .latitude((lastValidGps.getLatitude() + gps.getLatitude()) / 2)
                    .speed(0.0)
                    .direction(lastValidGps.getDirection())
                    .timestamp(gps.getTimestamp())
                    .satelliteCount(gps.getSatelliteCount())
                    .hdop(gps.getHdop())
                    .build();
            lastValidGps = smoothed;
            out.collect(smoothed);
            return;
        }

        lastValidGps = gps;
        out.collect(gps);
    }
}
