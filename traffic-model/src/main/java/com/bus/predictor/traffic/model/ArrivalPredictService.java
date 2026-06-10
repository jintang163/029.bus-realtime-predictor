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
    private static final double KALMAN_PROCESS_NOISE = 0.01;
    private static final double KALMAN_MEASUREMENT_NOISE = 0.1;
    private static final double HISTORICAL_WEIGHT = 0.3;
    private static final double REALTIME_WEIGHT = 0.7;
    private static final double BASELINE_WEIGHT = 0.4;
    private static final double REALTIME_WITH_BASELINE_WEIGHT = 0.6;

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final SegmentSpeedHistoryMapper segmentSpeedHistoryMapper;
    private final MlCorrectionClient mlCorrectionClient;
    private final ArrivalPredictionCacheDao predictionCacheDao;
    private final CongestionModel congestionModel;
    private final SelfLearningBaselineService selfLearningBaselineService;
    private final PredictionDeviationService predictionDeviationService;

    private final Map<String, KalmanFilter> kalmanFilters = new ConcurrentHashMap<>();

    public ArrivalPredictService(VehiclePositionRedisDao vehiclePositionRedisDao,
                                 RoadSegmentRedisDao roadSegmentRedisDao,
                                 RoadSegmentManager roadSegmentManager,
                                 SegmentSpeedHistoryMapper segmentSpeedHistoryMapper,
                                 MlCorrectionClient mlCorrectionClient,
                                 ArrivalPredictionCacheDao predictionCacheDao,
                                 CongestionModel congestionModel,
                                 SelfLearningBaselineService selfLearningBaselineService,
                                 PredictionDeviationService predictionDeviationService) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.segmentSpeedHistoryMapper = segmentSpeedHistoryMapper;
        this.mlCorrectionClient = mlCorrectionClient;
        this.predictionCacheDao = predictionCacheDao;
        this.congestionModel = congestionModel;
        this.selfLearningBaselineService = selfLearningBaselineService;
        this.predictionDeviationService = predictionDeviationService;
    }

    public List<ArrivalPrediction> predict(String routeId, String vehicleId) {
        List<ArrivalPrediction> cached = predictionCacheDao.getPredictionsTyped(vehicleId, routeId);
        if (cached != null) {
            return cached;
        }

        com.bus.predictor.common.model.VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
        if (position == null) {
            return new ArrayList<>();
        }

        List<SegmentInfo> routeSegments = filterRouteSegments(roadSegmentManager.getAllSegments(), routeId);
        if (routeSegments.isEmpty()) {
            return new ArrayList<>();
        }

        double currentLng = position.getLongitude();
        double currentLat = position.getLatitude();
        double gpsSpeed = position.getSpeed() != null && position.getSpeed() > 0.5
                ? position.getSpeed() : DEFAULT_SPEED_MS;

        double smoothedGpsSpeed = applyKalmanFilter(vehicleId, gpsSpeed);

        LocalDateTime now = LocalDateTime.now();
        int hourOfDay = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();

        int currentOrder = locateCurrentSegmentOrder(currentLat, currentLng, routeSegments);
        SegmentInfo currentSegment = findSegmentByOrder(routeSegments, currentOrder);

        double remainingDistInCurrentSegment = calculateRemainingDistanceInSegment(
                currentLat, currentLng, currentSegment);

        List<ArrivalPrediction> predictions = new ArrayList<>();
        double accumulatedTime = 0;
        double accumulatedDistance = 0;

        if (currentSegment != null && remainingDistInCurrentSegment > 10) {
            double speed = calculateEffectiveSpeed(currentSegment, smoothedGpsSpeed, hourOfDay, dayOfWeek);
            accumulatedTime += remainingDistInCurrentSegment / speed;
            accumulatedDistance += remainingDistInCurrentSegment;

            double congestionFactor = congestionModel.calculateSegmentCongestion(currentSegment.getSegmentId());

            predictions.add(ArrivalPrediction.builder()
                    .vehicleId(vehicleId)
                    .routeId(routeId)
                    .stationId(currentSegment.getEndStationId())
                    .stationName(currentSegment.getEndStationName())
                    .distanceToStation(remainingDistInCurrentSegment)
                    .estimatedSeconds((int) accumulatedTime)
                    .congestionFactor(congestionFactor)
                    .currentSpeed(smoothedGpsSpeed)
                    .predictTime(System.currentTimeMillis() + (long) (accumulatedTime * 1000))
                    .gpsTime(position.getGpsTime())
                    .build());
        }

        for (int i = 0; i < routeSegments.size(); i++) {
            SegmentInfo seg = routeSegments.get(i);
            if (seg.getStationOrder() <= currentOrder) {
                continue;
            }

            double segSpeed = calculateEffectiveSpeed(seg, smoothedGpsSpeed, hourOfDay, dayOfWeek);
            double segTime = seg.getLength() / segSpeed;
            accumulatedTime += segTime;
            accumulatedDistance += seg.getLength();

            if (seg.getStationOrder() > currentOrder + 1) {
                accumulatedTime += STOP_PENALTY_SECONDS;
            } else if (!predictions.isEmpty()) {
                accumulatedTime += STOP_PENALTY_SECONDS;
            }

            double congestionFactor = congestionModel.calculateSegmentCongestion(seg.getSegmentId());

            predictions.add(ArrivalPrediction.builder()
                    .vehicleId(vehicleId)
                    .routeId(routeId)
                    .stationId(seg.getEndStationId())
                    .stationName(seg.getEndStationName())
                    .distanceToStation(accumulatedDistance)
                    .estimatedSeconds((int) accumulatedTime)
                    .congestionFactor(congestionFactor)
                    .currentSpeed(smoothedGpsSpeed)
                    .predictTime(System.currentTimeMillis() + (long) (accumulatedTime * 1000))
                    .gpsTime(position.getGpsTime())
                    .build());
        }

        predictionCacheDao.savePredictions(vehicleId, routeId, predictions);
        predictionDeviationService.recordPrediction(vehicleId, routeId, predictions);
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

    int locateCurrentSegmentOrder(double currentLat, double currentLng, List<SegmentInfo> routeSegments) {
        SegmentInfo nearest = null;
        double minDist = Double.MAX_VALUE;

        for (SegmentInfo seg : routeSegments) {
            double distToStart = GeoHashUtil.haversineDistance(
                    currentLat, currentLng, seg.getStartLat(), seg.getStartLng());
            double distToEnd = GeoHashUtil.haversineDistance(
                    currentLat, currentLng, seg.getEndLat(), seg.getEndLng());
            double dist = Math.min(distToStart, distToEnd);
            if (dist < minDist) {
                minDist = dist;
                nearest = seg;
            }
        }

        if (nearest == null) {
            return 0;
        }

        double distToStart = GeoHashUtil.haversineDistance(
                currentLat, currentLng, nearest.getStartLat(), nearest.getStartLng());
        double distToEnd = GeoHashUtil.haversineDistance(
                currentLat, currentLng, nearest.getEndLat(), nearest.getEndLng());

        if (distToStart <= distToEnd) {
            return nearest.getStationOrder();
        }

        return nearest.getStationOrder() + 1;
    }

    private double calculateRemainingDistanceInSegment(double currentLat, double currentLng,
                                                        SegmentInfo segment) {
        if (segment == null) {
            return 0;
        }

        double distToEnd = GeoHashUtil.haversineDistance(
                currentLat, currentLng, segment.getEndLat(), segment.getEndLng());
        double distToStart = GeoHashUtil.haversineDistance(
                currentLat, currentLng, segment.getStartLat(), segment.getStartLng());

        double progress;
        double totalLength = segment.getLength();
        if (totalLength > 0) {
            progress = Math.max(0, Math.min(1, 1.0 - distToEnd / (distToStart + distToEnd)));
        } else {
            progress = distToStart < distToEnd ? 0.5 : 0.8;
        }

        double remaining = totalLength * (1.0 - progress);
        return remaining > 0 ? remaining : distToEnd;
    }

    private SegmentInfo findSegmentByOrder(List<SegmentInfo> routeSegments, int order) {
        for (SegmentInfo seg : routeSegments) {
            if (seg.getStationOrder() == order) {
                return seg;
            }
        }
        return null;
    }

    private double applyKalmanFilter(String vehicleId, double gpsSpeed) {
        KalmanFilter kf = kalmanFilters.computeIfAbsent(vehicleId,
                k -> new KalmanFilter(KALMAN_PROCESS_NOISE, KALMAN_MEASUREMENT_NOISE));
        return kf.update(gpsSpeed);
    }

    private double calculateEffectiveSpeed(SegmentInfo segment, double smoothedGpsSpeed,
                                            int hourOfDay, int dayOfWeek) {
        if (segment == null) {
            return Math.max(smoothedGpsSpeed, 1.0);
        }

        boolean amapHealthy = roadSegmentRedisDao.isAmapApiHealthy();

        Double realtimeSpeed = amapHealthy ? roadSegmentRedisDao.getSegmentSpeed(segment.getSegmentId()) : null;
        Double baselineSpeed = selfLearningBaselineService.getEffectiveBaselineSpeed(
                segment.getSegmentId(), dayOfWeek, hourOfDay);
        double historicalSpeed = getHistoricalSpeed(segment.getSegmentId(), hourOfDay);

        double fusedSpeed;

        if (!amapHealthy && baselineSpeed != null && baselineSpeed > 0.5) {
            if (historicalSpeed > 0.5) {
                fusedSpeed = BASELINE_WEIGHT * baselineSpeed + (1 - BASELINE_WEIGHT) * historicalSpeed;
            } else {
                fusedSpeed = baselineSpeed;
            }
            log.debug("Amap API degraded, using self-learning baseline for segment {}: {:.2f} m/s",
                    segment.getSegmentId(), fusedSpeed);
        } else if (realtimeSpeed != null && realtimeSpeed > 0.5) {
            if (baselineSpeed != null && baselineSpeed > 0.5) {
                fusedSpeed = REALTIME_WITH_BASELINE_WEIGHT * realtimeSpeed
                        + (1 - REALTIME_WITH_BASELINE_WEIGHT) * baselineSpeed;
            } else if (historicalSpeed > 0.5) {
                fusedSpeed = REALTIME_WEIGHT * realtimeSpeed + HISTORICAL_WEIGHT * historicalSpeed;
            } else {
                fusedSpeed = realtimeSpeed;
            }
        } else if (baselineSpeed != null && baselineSpeed > 0.5) {
            if (historicalSpeed > 0.5) {
                fusedSpeed = BASELINE_WEIGHT * baselineSpeed + (1 - BASELINE_WEIGHT) * historicalSpeed;
            } else {
                fusedSpeed = baselineSpeed;
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
}
