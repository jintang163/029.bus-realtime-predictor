package com.bus.predictor.flink.function;

import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.model.SegmentSpeedAggregate;
import com.bus.predictor.common.util.GeoHashUtil;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public class SegmentCrossDetectFunction extends KeyedProcessFunction<String, GpsData, SegmentSpeedAggregate> {

    private static final Logger log = LoggerFactory.getLogger(SegmentCrossDetectFunction.class);
    private static final double STATION_RADIUS_METERS = 80.0;
    private static final long MIN_SEGMENT_TIME_MS = 10000L;
    private static final long MAX_SEGMENT_TIME_MS = 30 * 60 * 1000L;

    private final List<Map<String, Object>> segmentConfigs;

    private transient ValueState<SegmentEntry> lastEntry;

    public SegmentCrossDetectFunction(List<Map<String, Object>> segmentConfigs) {
        this.segmentConfigs = segmentConfigs;
    }

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<SegmentEntry> descriptor = new ValueStateDescriptor<>(
                "segment-entry", SegmentEntry.class);
        lastEntry = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(GpsData gps, Context ctx, Collector<SegmentSpeedAggregate> out) throws Exception {
        if (gps == null || gps.getLatitude() == null || gps.getLongitude() == null) {
            return;
        }

        for (Map<String, Object> seg : segmentConfigs) {
            String segmentId = (String) seg.get("segmentId");
            double startLat = ((Number) seg.get("startLat")).doubleValue();
            double startLng = ((Number) seg.get("startLng")).doubleValue();
            double endLat = ((Number) seg.get("endLat")).doubleValue();
            double endLng = ((Number) seg.get("endLng")).doubleValue();
            double length = ((Number) seg.get("length")).doubleValue();

            double distToStart = GeoHashUtil.haversineDistance(
                    gps.getLatitude(), gps.getLongitude(), startLat, startLng);

            if (distToStart <= STATION_RADIUS_METERS) {
                SegmentEntry entry = lastEntry.value();
                if (entry == null || !segmentId.equals(entry.segmentId)) {
                    lastEntry.update(new SegmentEntry(segmentId, gps.getTimestamp()));
                }
            }

            double distToEnd = GeoHashUtil.haversineDistance(
                    gps.getLatitude(), gps.getLongitude(), endLat, endLng);

            if (distToEnd <= STATION_RADIUS_METERS) {
                SegmentEntry entry = lastEntry.value();
                if (entry != null && segmentId.equals(entry.segmentId)) {
                    long elapsed = gps.getTimestamp() - entry.enterTime;
                    if (elapsed >= MIN_SEGMENT_TIME_MS && elapsed <= MAX_SEGMENT_TIME_MS) {
                        double speedMps = length / (elapsed / 1000.0);
                        if (speedMps >= 0.5 && speedMps <= 30.0) {
                            LocalDateTime exitTime = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(gps.getTimestamp()),
                                    ZoneId.systemDefault());

                            out.collect(SegmentSpeedAggregate.builder()
                                    .segmentId(segmentId)
                                    .vehicleId(gps.getVehicleId())
                                    .speed(speedMps)
                                    .enterTime(entry.enterTime)
                                    .exitTime(gps.getTimestamp())
                                    .hourOfDay(exitTime.getHour())
                                    .dayOfWeek(exitTime.getDayOfWeek().getValue())
                                    .build());
                        }
                    }
                    lastEntry.clear();
                }
            }
        }
    }

    public static class SegmentEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public String segmentId;
        public long enterTime;

        public SegmentEntry() {}
        public SegmentEntry(String segmentId, long enterTime) {
            this.segmentId = segmentId;
            this.enterTime = enterTime;
        }
    }
}
