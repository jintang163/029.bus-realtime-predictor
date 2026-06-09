package com.bus.predictor.webapi.init;

import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.service.LineService;
import com.bus.predictor.traffic.model.LineSegmentSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TrafficSegmentInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TrafficSegmentInitializer.class);

    private final LineService lineService;
    private final LineSegmentSplitter lineSegmentSplitter;

    public TrafficSegmentInitializer(LineService lineService,
                                     LineSegmentSplitter lineSegmentSplitter) {
        this.lineService = lineService;
        this.lineSegmentSplitter = lineSegmentSplitter;
    }

    @Override
    public void run(String... args) {
        try {
            List<LineEntity> allLines = lineService.listAll();
            if (allLines == null || allLines.isEmpty()) {
                log.info("No lines found, skipping traffic segment initialization");
                return;
            }

            for (LineEntity line : allLines) {
                splitLine(line.getLineId());
            }

            log.info("Traffic segment initialization completed. Lines: {}", allLines.size());
        } catch (Exception e) {
            log.warn("Failed to initialize traffic segments on startup: {}", e.getMessage());
        }
    }

    private void splitLine(String lineId) {
        try {
            List<Map<String, Object>> stations = lineService.getLineStationsWithDetail(lineId);
            if (stations != null && !stations.isEmpty()) {
                lineSegmentSplitter.splitAndRegister(lineId, stations);
            }
        } catch (Exception e) {
            log.warn("Failed to split line {}: {}", lineId, e.getMessage());
        }
    }
}
