package com.bus.predictor.traffic.model;

import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrafficRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrafficRefreshScheduler.class);

    private static final double EMA_ALPHA = 0.3;
    private static final double FREE_FLOW_SPEED_KMH = 50.0;

    private final AmapTrafficService amapTrafficService;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;

    private final Map<String, Double> amapSpeedCache = new ConcurrentHashMap<>();

    public TrafficRefreshScheduler(AmapTrafficService amapTrafficService,
                                   RoadSegmentRedisDao roadSegmentRedisDao,
                                   RoadSegmentManager roadSegmentManager) {
        this.amapTrafficService = amapTrafficService;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
    }

    @Scheduled(fixedRate = 120000)
    public void refreshTrafficData() {
        log.info("Starting traffic data refresh...");

        List<SegmentInfo> segments = roadSegmentManager.getAllSegments();
        if (segments.isEmpty()) {
            log.info("No road segments found, skipping traffic refresh");
            return;
        }

        double minLng = 180, maxLng = -180, minLat = 90, maxLat = -90;
        for (SegmentInfo seg : segments) {
            minLng = Math.min(minLng, Math.min(seg.getStartLng(), seg.getEndLng()));
            maxLng = Math.max(maxLng, Math.max(seg.getStartLng(), seg.getEndLng()));
            minLat = Math.min(minLat, Math.min(seg.getStartLat(), seg.getEndLat()));
            maxLat = Math.max(maxLat, Math.max(seg.getStartLat(), seg.getEndLat()));
        }

        double padding = 0.01;
        List<AmapTrafficService.AmapTrafficSegment> amapSegments =
                amapTrafficService.fetchTrafficRectangle(
                        minLng - padding, minLat - padding,
                        maxLng + padding, maxLat + padding);

        amapSpeedCache.clear();
        for (AmapTrafficService.AmapTrafficSegment amapSeg : amapSegments) {
            if (amapSeg.getRoadName() != null) {
                amapSpeedCache.put(amapSeg.getRoadName(), amapSeg.getSpeed() / 3.6);
            }
        }

        for (SegmentInfo segment : segments) {
            double amapSpeed = findMatchingAmapSpeed(segment);
            Double gpsSpeed = roadSegmentRedisDao.getSegmentSpeed(segment.getSegmentId());

            double finalSpeed;
            if (amapSpeed > 0 && gpsSpeed != null && gpsSpeed > 0) {
                finalSpeed = 0.4 * amapSpeed + 0.6 * gpsSpeed;
            } else if (amapSpeed > 0) {
                finalSpeed = amapSpeed;
            } else if (gpsSpeed != null && gpsSpeed > 0) {
                finalSpeed = gpsSpeed;
            } else {
                continue;
            }

            Double existingSpeed = roadSegmentRedisDao.getSegmentSpeed(segment.getSegmentId());
            double updatedSpeed;
            if (existingSpeed != null && existingSpeed > 0) {
                updatedSpeed = EMA_ALPHA * finalSpeed + (1 - EMA_ALPHA) * existingSpeed;
            } else {
                updatedSpeed = finalSpeed;
            }

            roadSegmentRedisDao.saveSegmentSpeed(segment.getSegmentId(), updatedSpeed);

            double congestionFactor = (FREE_FLOW_SPEED_KMH / 3.6) / Math.max(updatedSpeed, 0.5);
            congestionFactor = Math.max(1.0, Math.min(5.0, congestionFactor));
            roadSegmentRedisDao.saveSegmentCongestion(segment.getSegmentId(), congestionFactor);
        }

        roadSegmentManager.notifySegmentSpeedUpdated();

        log.info("Traffic data refresh completed. Segments: {}, Amap roads: {}",
                segments.size(), amapSegments.size());
    }

    private double findMatchingAmapSpeed(SegmentInfo segment) {
        String startNode = segment.getStartNode();
        String endNode = segment.getEndNode();

        for (Map.Entry<String, Double> entry : amapSpeedCache.entrySet()) {
            String roadName = entry.getKey();
            if ((startNode != null && roadName.contains(startNode))
                    || (endNode != null && roadName.contains(endNode))) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public Map<String, Double> getAmapSpeedCache() {
        return amapSpeedCache;
    }
}
