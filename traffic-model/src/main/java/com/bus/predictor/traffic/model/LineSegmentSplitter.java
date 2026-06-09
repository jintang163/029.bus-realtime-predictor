package com.bus.predictor.traffic.model;

import com.bus.predictor.common.util.GeoHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LineSegmentSplitter {

    private static final Logger log = LoggerFactory.getLogger(LineSegmentSplitter.class);

    private final RoadSegmentManager roadSegmentManager;

    public LineSegmentSplitter(RoadSegmentManager roadSegmentManager) {
        this.roadSegmentManager = roadSegmentManager;
    }

    public void splitAndRegister(String lineId, List<Map<String, Object>> stationDetails) {
        if (stationDetails == null || stationDetails.size() < 2) {
            return;
        }

        stationDetails.sort((a, b) -> {
            int orderA = ((Number) a.getOrDefault("stationOrder", 0)).intValue();
            int orderB = ((Number) b.getOrDefault("stationOrder", 0)).intValue();
            return Integer.compare(orderA, orderB);
        });

        List<SegmentInfo> segments = new ArrayList<>();

        for (int i = 0; i < stationDetails.size() - 1; i++) {
            Map<String, Object> current = stationDetails.get(i);
            Map<String, Object> next = stationDetails.get(i + 1);

            String startStationId = String.valueOf(current.getOrDefault("stationId", ""));
            String endStationId = String.valueOf(next.getOrDefault("stationId", ""));
            String startStationName = String.valueOf(current.getOrDefault("stationName", ""));
            String endStationName = String.valueOf(next.getOrDefault("stationName", ""));
            int stationOrder = ((Number) current.getOrDefault("stationOrder", 0)).intValue();
            double startLng = ((Number) current.getOrDefault("longitude", 0)).doubleValue();
            double startLat = ((Number) current.getOrDefault("latitude", 0)).doubleValue();
            double endLng = ((Number) next.getOrDefault("longitude", 0)).doubleValue();
            double endLat = ((Number) next.getOrDefault("latitude", 0)).doubleValue();

            double distanceToNext = ((Number) current.getOrDefault("distanceToNext", 0)).doubleValue();
            if (distanceToNext <= 0) {
                distanceToNext = GeoHashUtil.haversineDistance(startLat, startLng, endLat, endLng);
            }

            String segmentId = lineId + "_" + startStationId + "_" + endStationId;

            SegmentInfo segment = new SegmentInfo(
                    segmentId, lineId,
                    startStationId, startStationName,
                    endStationId, endStationName,
                    stationOrder,
                    startLng, startLat,
                    endLng, endLat,
                    distanceToNext, 13.89
            );
            segments.add(segment);
        }

        roadSegmentManager.registerSegments(segments);
        log.info("Split line {} into {} segments", lineId, segments.size());
    }
}
