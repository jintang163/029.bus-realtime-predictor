package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.mapper.SegmentSpeedHistoryMapper;
import com.bus.predictor.dal.redis.ArrivalPredictionCacheDao;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArrivalPredictService {

    private static final Logger log = LoggerFactory.getLogger(ArrivalPredictService.class);

    private static final double DEFAULT_SPEED_MS = 8.33;
    private static final double STOP_PENALTY_SECONDS = 30.0;
    private static final double FREE_FLOW_SPEED_MS = 13.89;
    private static final double KALMAN_PROCESS_NOISE = 0.01;
    private static final double KALMAN_MEASUREMENT_NOISE = 0.1;
    private static final double HISTORICAL_WEIGHT = 0.3;
    private static final double REALTIME_WEIGHT = 0.7;

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final SegmentSpeedHistoryMapper segmentSpeedHistoryMapper;
    private final MlCorrectionClient mlCorrectionClient;
    private final ArrivalPredictionCacheDao predictionCacheDao;
    private final CongestionModel congestionModel;

    private final Map<String, KalmanFilter> kalmanFilters = new ConcurrentHashMap<>();

    public ArrivalPredictService(VehiclePositionRedisDao vehiclePositionRedisDao,
                                 RoadSegmentRedisDao roadSegmentRedisDao,
                                 RoadSegmentManager roadSegmentManager,
                                 SegmentSpeedHistoryMapper segmentSpeedHistoryMapper,
                                 MlCorrectionClient mlCorrectionClient,
                                 ArrivalPredictionCacheDao predictionCacheDao,
                                 CongestionModel congestionModel) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.segmentSpeedHistoryMapper = segmentSpeedHistoryMapper;
        this.mlCorrectionClient = mlCorrectionClient;
        this.predictionCacheDao = predictionCacheDao;
        this.congestionModel = congestionModel;
    }

    public List<ArrivalPrediction> predict(String routeId, String vehicleId) {
        List<ArrivalPrediction> cached = predictionCacheDao.getPredictionsTyped(vehicleId, routeId);
        if (cached != null) {
            return cached;
        }

        VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
        if (position == null) {
            return new ArrayList<>();
        }

        List<RouteStationInfo> stations = getRouteStations(routeId);
        if (stations.isEmpty()) {
            return new ArrayList<>();
        }

        double currentLng = position.getLongitude();
        double currentLat = position.getLatitude();
        double gpsSpeed = position.getSpeed() != null && position.getSpeed() > 0.5
                ? position.getSpeed() : DEFAULT_SPEED_MS;

        double smoothedGpsSpeed = applyKalmanFilter(vehicleId, gpsSpeed);

        List<SegmentInfo> allSegments = roadSegmentManager.getAllSegments();
        List<SegmentInfo> routeSegments = filterRouteSegments(allSegments, routeId);

        LocalDateTime now = LocalDateTime.now();
        int hourOfDay = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();

        List<ArrivalPrediction> predictions = new ArrayList<>();
        double accumulatedTime = 0;
        double prevLng = currentLng;
        double prevLat = currentLat;

        for (RouteStationInfo station : stations) {
            double distance = GeoHashUtil.haversineDistance(
                    prevLat, prevLng, station.getLatitude(), station.getLongitude());

            SegmentInfo matchedSegment = findMatchingSegment(routeSegments, station.getStationId());
            double effectiveSpeed = calculateEffectiveSpeed(
                    matchedSegment, smoothedGpsSpeed, hourOfDay, dayOfWeek);

            double segmentTime = distance / effectiveSpeed;
            accumulatedTime += segmentTime;

            if (station.getOrder() > 1) {
                accumulatedTime += STOP_PENALTY_SECONDS;
            }

            double congestionFactor = matchedSegment != null
                    ? congestionModel.calculateSegmentCongestion(matchedSegment.getSegmentId())
                    : congestionModel.calculateCongestion(prevLat, prevLng, station.getLatitude(), station.getLongitude());

            predictions.add(ArrivalPrediction.builder()
                    .vehicleId(vehicleId)
                    .routeId(routeId)
                    .stationId(station.getStationId())
                    .stationName(station.getStationName())
                    .distanceToStation(distance)
                    .estimatedSeconds((int) accumulatedTime)
                    .congestionFactor(congestionFactor)
                    .currentSpeed(smoothedGpsSpeed)
                    .predictTime(System.currentTimeMillis() + (long) (accumulatedTime * 1000))
                    .gpsTime(position.getGpsTime())
                    .build());

            prevLng = station.getLongitude();
            prevLat = station.getLatitude();
        }

        predictionCacheDao.savePredictions(vehicleId, routeId, predictions);

        return predictions;
    }

    public List<ArrivalPrediction> predictForStation(String stationId) {
        List<SegmentInfo> allSegments = roadSegmentManager.getAllSegments();
        List<ArrivalPrediction> results = new ArrayList<>();

        for (SegmentInfo seg : allSegments) {
            if (stationId.equals(seg.getStartStationId()) || stationId.equals(seg.getEndStationId())) {
                String routeId = seg.getLineId();
                var onlineVehicles = vehiclePositionRedisDao.getOnlineVehicleIds();
                if (onlineVehicles == null) continue;

                for (String vehicleId : onlineVehicles) {
                    try {
                        List<ArrivalPrediction> preds = predict(routeId, vehicleId);
                        for (ArrivalPrediction p : preds) {
                            if (stationId.equals(p.getStationId())) {
                                results.add(p);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Predict failed for vehicle {} on route {}", vehicleId, routeId);
                    }
                }
            }
        }

        results.sort((a, b) -> Integer.compare(a.getEstimatedSeconds(), b.getEstimatedSeconds()));
        return results;
    }

    private double applyKalmanFilter(String vehicleId, double gpsSpeed) {
        String key = vehicleId;
        KalmanFilter kf = kalmanFilters.computeIfAbsent(key,
                k -> new KalmanFilter(KALMAN_PROCESS_NOISE, KALMAN_MEASUREMENT_NOISE));
        return kf.update(gpsSpeed);
    }

    private double calculateEffectiveSpeed(SegmentInfo segment, double smoothedGpsSpeed,
                                            int hourOfDay, int dayOfWeek) {
        if (segment == null) {
            return Math.max(smoothedGpsSpeed, 1.0);
        }

        Double realtimeSpeed = roadSegmentRedisDao.getSegmentSpeed(segment.getSegmentId());

        double historicalSpeed = getHistoricalSpeed(segment.getSegmentId(), hourOfDay);

        double fusedSpeed;
        if (realtimeSpeed != null && realtimeSpeed > 0.5) {
            if (historicalSpeed > 0.5) {
                fusedSpeed = REALTIME_WEIGHT * realtimeSpeed + HISTORICAL_WEIGHT * historicalSpeed;
            } else {
                fusedSpeed = realtimeSpeed;
            }
        } else if (historicalSpeed > 0.5) {
            fusedSpeed = historicalSpeed;
        } else {
            fusedSpeed = smoothedGpsSpeed;
        }

        if (mlCorrectionClient.isEnabled()) {
            double congestionFactor = congestionModel.calculateSegmentCongestion(segment.getSegmentId());
            double correction = mlCorrectionClient.getCorrectionFactor(
                    segment.getSegmentId(), fusedSpeed, historicalSpeed,
                    congestionFactor, hourOfDay, dayOfWeek);
            fusedSpeed = fusedSpeed * correction;
        }

        return Math.max(fusedSpeed, 1.0);
    }

    private double getHistoricalSpeed(String segmentId, int hourOfDay) {
        try {
            Map<String, Object> avgData = segmentSpeedHistoryMapper
                    .findWeekdayHourlyAverage(segmentId, hourOfDay);
            if (avgData != null && avgData.get("avg_speed") != null) {
                return ((Number) avgData.get("avg_speed")).doubleValue();
            }
        } catch (Exception e) {
            log.debug("Historical speed lookup failed for segment {}: {}", segmentId, e.getMessage());
        }
        return 0.0;
    }

    private SegmentInfo findMatchingSegment(List<SegmentInfo> routeSegments, String stationId) {
        for (SegmentInfo seg : routeSegments) {
            if (stationId.equals(seg.getStartStationId()) || stationId.equals(seg.getEndStationId())) {
                return seg;
            }
        }
        return null;
    }

    private List<SegmentInfo> filterRouteSegments(List<SegmentInfo> allSegments, String routeId) {
        List<SegmentInfo> routeSegments = new ArrayList<>();
        for (SegmentInfo seg : allSegments) {
            if (routeId.equals(seg.getLineId())) {
                routeSegments.add(seg);
            }
        }
        routeSegments.sort((a, b) -> Integer.compare(a.getStationOrder(), b.getStationOrder()));
        return routeSegments;
    }

    private List<RouteStationInfo> getRouteStations(String routeId) {
        List<RouteStationInfo> stations = new ArrayList<>();

        List<SegmentInfo> segments = roadSegmentManager.getAllSegments();
        List<SegmentInfo> routeSegments = filterRouteSegments(segments, routeId);
        if (!routeSegments.isEmpty()) {
            for (SegmentInfo seg : routeSegments) {
                stations.add(new RouteStationInfo(
                        seg.getStartStationId(), seg.getStartStationName(),
                        seg.getStationOrder(), seg.getStartLng(), seg.getStartLat()));
            }
            SegmentInfo last = routeSegments.get(routeSegments.size() - 1);
            stations.add(new RouteStationInfo(
                    last.getEndStationId(), last.getEndStationName(),
                    last.getStationOrder() + 1, last.getEndLng(), last.getEndLat()));
            return stations;
        }

        stations.add(new RouteStationInfo("S001", "火车站", 1, 116.407526, 39.904030));
        stations.add(new RouteStationInfo("S002", "中山路", 2, 116.410526, 39.908030));
        stations.add(new RouteStationInfo("S003", "人民广场", 3, 116.415526, 39.912030));
        stations.add(new RouteStationInfo("S004", "市政府", 4, 116.420526, 39.916030));
        stations.add(new RouteStationInfo("S005", "科技园", 5, 116.425526, 39.920030));

        return stations;
    }
}
