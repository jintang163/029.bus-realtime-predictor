package com.bus.predictor.traffic.model;

import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoadSegmentManager {

    private static final Logger log = LoggerFactory.getLogger(RoadSegmentManager.class);

    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final Map<String, SegmentInfo> segmentMap = new ConcurrentHashMap<>();
    private final List<SegmentChangeListener> listeners = new ArrayList<>();

    public RoadSegmentManager(RoadSegmentRedisDao roadSegmentRedisDao) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
    }

    public void registerSegment(SegmentInfo segment) {
        segmentMap.put(segment.getSegmentId(), segment);
    }

    public void registerSegments(List<SegmentInfo> segments) {
        for (SegmentInfo seg : segments) {
            segmentMap.put(seg.getSegmentId(), seg);
        }
        log.info("Registered {} road segments", segments.size());
    }

    public SegmentInfo getSegment(String segmentId) {
        return segmentMap.get(segmentId);
    }

    public List<SegmentInfo> getAllSegments() {
        return new ArrayList<>(segmentMap.values());
    }

    public void addListener(SegmentChangeListener listener) {
        listeners.add(listener);
    }

    public void notifySegmentSpeedUpdated() {
        for (SegmentChangeListener listener : listeners) {
            try {
                listener.onSegmentSpeedUpdated();
            } catch (Exception e) {
                log.warn("Segment change listener failed", e);
            }
        }
    }

    public List<Map<String, Object>> getAllSegmentsWithSpeed() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SegmentInfo seg : segmentMap.values()) {
            Map<String, Object> item = seg.toMap();
            Double speed = roadSegmentRedisDao.getSegmentSpeed(seg.getSegmentId());
            Double congestion = roadSegmentRedisDao.getSegmentCongestion(seg.getSegmentId());
            item.put("currentSpeed", speed);
            item.put("congestionFactor", congestion);
            if (speed != null) {
                item.put("speedKmh", speed * 3.6);
            }
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getSegmentDetail(String segmentId) {
        SegmentInfo seg = segmentMap.get(segmentId);
        if (seg == null) {
            return null;
        }
        Map<String, Object> item = seg.toMap();
        Double speed = roadSegmentRedisDao.getSegmentSpeed(segmentId);
        Double congestion = roadSegmentRedisDao.getSegmentCongestion(segmentId);
        item.put("currentSpeed", speed);
        item.put("congestionFactor", congestion);
        if (speed != null) {
            item.put("speedKmh", speed * 3.6);
        }
        return item;
    }

    public List<Map<String, Object>> getHeatmapData() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SegmentInfo seg : segmentMap.values()) {
            Double congestion = roadSegmentRedisDao.getSegmentCongestion(seg.getSegmentId());
            Double speed = roadSegmentRedisDao.getSegmentSpeed(seg.getSegmentId());
            if (congestion != null) {
                Map<String, Object> point = new java.util.HashMap<>();
                point.put("segmentId", seg.getSegmentId());
                point.put("startLng", seg.getStartLng());
                point.put("startLat", seg.getStartLat());
                point.put("endLng", seg.getEndLng());
                point.put("endLat", seg.getEndLat());
                point.put("congestionFactor", congestion);
                point.put("currentSpeed", speed);
                point.put("congestionLevel", toCongestionLevel(congestion));
                result.add(point);
            }
        }
        return result;
    }

    private String toCongestionLevel(double factor) {
        if (factor < 1.2) return "smooth";
        if (factor < 1.8) return "slow";
        if (factor < 3.0) return "congested";
        return "heavy";
    }

    public interface SegmentChangeListener {
        void onSegmentSpeedUpdated();
    }
}
