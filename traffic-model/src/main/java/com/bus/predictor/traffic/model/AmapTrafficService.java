package com.bus.predictor.traffic.model;

import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AmapTrafficService {

    private static final Logger log = LoggerFactory.getLogger(AmapTrafficService.class);

    private static final String AMAP_TRAFFIC_URL = "https://restapi.amap.com/v3/traffic/status/rectangle";
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final RestTemplate restTemplate;

    @Value("${traffic.amap.key:}")
    private String amapKey;

    @Value("${traffic.amap.enabled:false}")
    private boolean amapEnabled;

    @Autowired
    private RoadSegmentRedisDao roadSegmentRedisDao;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public AmapTrafficService() {
        this.restTemplate = new RestTemplate();
    }

    public List<AmapTrafficSegment> fetchTrafficRectangle(double lng1, double lat1, double lng2, double lat2) {
        if (!amapEnabled || amapKey == null || amapKey.isEmpty()) {
            log.debug("Amap traffic API disabled or key not configured");
            return Collections.emptyList();
        }

        try {
            String rectangle = String.format("%s,%s;%s,%s",
                    String.format("%.6f", lng1), String.format("%.6f", lat1),
                    String.format("%.6f", lng2), String.format("%.6f", lat2));

            String url = AMAP_TRAFFIC_URL + "?key=" + amapKey + "&rectangle=" + rectangle + "&level=5&extensions=all";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getBody() == null) {
                handleApiFailure("Empty response body");
                return Collections.emptyList();
            }

            Map<String, Object> result = JsonUtil.fromJson(response.getBody(), Map.class);
            if (result == null || !"1".equals(String.valueOf(result.get("status")))) {
                handleApiFailure(result != null ? String.valueOf(result.get("info")) : "null response");
                return Collections.emptyList();
            }

            consecutiveFailures.set(0);
            roadSegmentRedisDao.setAmapApiHealthy(true);
            return parseTrafficResponse(result);
        } catch (Exception e) {
            handleApiFailure(e.getMessage());
            return Collections.emptyList();
        }
    }

    private void handleApiFailure(String errorMsg) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("Amap traffic API failed (consecutive: {}): {}", failures, errorMsg);
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            log.error("Amap traffic API marked as unhealthy after {} consecutive failures", failures);
            roadSegmentRedisDao.setAmapApiHealthy(false);
        }
    }

    public boolean isApiHealthy() {
        return roadSegmentRedisDao.isAmapApiHealthy();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    @SuppressWarnings("unchecked")
    private List<AmapTrafficSegment> parseTrafficResponse(Map<String, Object> result) {
        List<AmapTrafficSegment> segments = new ArrayList<>();
        Object trafficInfoObj = result.get("trafficinfo");
        if (!(trafficInfoObj instanceof Map)) {
            return segments;
        }

        Map<String, Object> trafficInfo = (Map<String, Object>) trafficInfoObj;
        Object evaluationObj = trafficInfo.get("evaluation");
        if (evaluationObj instanceof Map) {
            Map<String, Object> evaluation = (Map<String, Object>) evaluationObj;
            Object roadsObj = evaluation.get("roads");
            if (roadsObj instanceof List) {
                List<Map<String, Object>> roads = (List<Map<String, Object>>) roadsObj;
                for (Map<String, Object> road : roads) {
                    AmapTrafficSegment seg = new AmapTrafficSegment();
                    seg.setRoadName(getStringValue(road, "name"));
                    seg.setCongestionLevel(getIntValue(road, "status"));
                    seg.setSpeed(getDoubleValue(road, "speed"));
                    seg.setDirection(getStringValue(road, "direction"));
                    seg.setLngLatPairs(getStringValue(road, "polyline"));
                    segments.add(seg);
                }
            }
        }

        return segments;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static class AmapTrafficSegment {
        private String roadName;
        private int congestionLevel;
        private double speed;
        private String direction;
        private String lngLatPairs;

        public String getRoadName() { return roadName; }
        public void setRoadName(String roadName) { this.roadName = roadName; }
        public int getCongestionLevel() { return congestionLevel; }
        public void setCongestionLevel(int congestionLevel) { this.congestionLevel = congestionLevel; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getLngLatPairs() { return lngLatPairs; }
        public void setLngLatPairs(String lngLatPairs) { this.lngLatPairs = lngLatPairs; }
    }
}
