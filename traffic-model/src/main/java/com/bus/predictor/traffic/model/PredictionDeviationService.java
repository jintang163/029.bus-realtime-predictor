package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.entity.PredictionDeviationEntity;
import com.bus.predictor.dal.mapper.PredictionDeviationMapper;
import com.bus.predictor.dal.redis.ArrivalPredictionCacheDao;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PredictionDeviationService {

    private static final Logger log = LoggerFactory.getLogger(PredictionDeviationService.class);
    private static final double STATION_RADIUS_METERS = 50.0;
    private static final double ACCURATE_THRESHOLD_RATE = 0.2;

    private final PredictionDeviationMapper deviationMapper;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final ArrivalPredictionCacheDao predictionCacheDao;
    private final RoadSegmentManager roadSegmentManager;

    private final Map<String, Map<String, ArrivalPrediction>> pendingPredictions = new HashMap<>();

    public PredictionDeviationService(PredictionDeviationMapper deviationMapper,
                                       RoadSegmentRedisDao roadSegmentRedisDao,
                                       VehiclePositionRedisDao vehiclePositionRedisDao,
                                       ArrivalPredictionCacheDao predictionCacheDao,
                                       RoadSegmentManager roadSegmentManager) {
        this.deviationMapper = deviationMapper;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.predictionCacheDao = predictionCacheDao;
        this.roadSegmentManager = roadSegmentManager;
    }

    public void recordPrediction(String vehicleId, String routeId, List<ArrivalPrediction> predictions) {
        Map<String, ArrivalPrediction> vehiclePredictions = pendingPredictions
                .computeIfAbsent(vehicleId, k -> new HashMap<>());
        for (ArrivalPrediction p : predictions) {
            if (p.getStationId() != null) {
                vehiclePredictions.put(p.getStationId(), p);
            }
        }
    }

    public void checkAndRecordDeviation(String vehicleId, VehiclePosition position) {
        if (position == null || position.getLatitude() == null || position.getLongitude() == null) {
            return;
        }

        Map<String, ArrivalPrediction> vehiclePredictions = pendingPredictions.get(vehicleId);
        if (vehiclePredictions == null || vehiclePredictions.isEmpty()) {
            return;
        }

        List<SegmentInfo> allSegments = roadSegmentManager.getAllSegments();
        List<String> arrivedStations = new ArrayList<>();

        for (Map.Entry<String, ArrivalPrediction> entry : vehiclePredictions.entrySet()) {
            String stationId = entry.getKey();
            ArrivalPrediction prediction = entry.getValue();

            SegmentInfo segment = findSegmentByEndStation(allSegments, stationId);
            if (segment == null) continue;

            double distanceToStation = GeoHashUtil.haversineDistance(
                    position.getLatitude(), position.getLongitude(),
                    segment.getEndLat(), segment.getEndLng()
            );

            if (distanceToStation <= STATION_RADIUS_METERS) {
                recordDeviation(vehicleId, prediction, position, segment);
                arrivedStations.add(stationId);
            }
        }

        for (String stationId : arrivedStations) {
            vehiclePredictions.remove(stationId);
        }
    }

    private void recordDeviation(String vehicleId, ArrivalPrediction prediction,
                                  VehiclePosition position, SegmentInfo segment) {
        try {
            long predictTimeMs = prediction.getPredictTime() != null ? prediction.getPredictTime() : 0L;
            long arrivalTimeMs = position.getGpsTime() != null ? position.getGpsTime() : System.currentTimeMillis();

            int predictedSeconds = prediction.getEstimatedSeconds() != null ? prediction.getEstimatedSeconds() : 0;
            int actualSeconds = (int) Math.max(0, (arrivalTimeMs - predictTimeMs) / 1000);
            if (actualSeconds == 0 && predictTimeMs == 0) {
                actualSeconds = 60;
            }

            int deviationSeconds = actualSeconds - predictedSeconds;
            double deviationRate = predictedSeconds > 0
                    ? Math.abs(deviationSeconds) / (double) predictedSeconds
                    : 0.0;

            boolean isAccurate = deviationRate <= ACCURATE_THRESHOLD_RATE;

            double predictedSpeed = segment.getLength() > 0 && predictedSeconds > 0
                    ? segment.getLength() / predictedSeconds : 0.0;
            double actualSpeed = segment.getLength() > 0 && actualSeconds > 0
                    ? segment.getLength() / actualSeconds : 0.0;

            LocalDateTime arrivalTime = LocalDateTime.now();

            PredictionDeviationEntity entity = PredictionDeviationEntity.builder()
                    .vehicleId(vehicleId)
                    .routeId(prediction.getRouteId())
                    .segmentId(segment.getSegmentId())
                    .stationId(prediction.getStationId())
                    .predictedSeconds(predictedSeconds)
                    .actualSeconds(actualSeconds)
                    .deviationSeconds(deviationSeconds)
                    .deviationRate(deviationRate)
                    .predictedSpeed(predictedSpeed)
                    .actualSpeed(actualSpeed)
                    .predictTime(predictTimeMs > 0 ? new java.sql.Timestamp(predictTimeMs).toLocalDateTime() : arrivalTime.minusSeconds(predictedSeconds))
                    .arrivalTime(arrivalTime)
                    .hourOfDay(arrivalTime.getHour())
                    .dayOfWeek(arrivalTime.getDayOfWeek().getValue())
                    .isAccurate(isAccurate ? 1 : 0)
                    .createTime(LocalDateTime.now())
                    .build();

            deviationMapper.insert(entity);
            log.debug("Recorded deviation for vehicle {} at segment {}: predicted={}s, actual={}s, rate={:.2f}%",
                    vehicleId, segment.getSegmentId(), predictedSeconds, actualSeconds, deviationRate * 100);

            updateRealtimeBaseline(segment, actualSpeed, arrivalTime);
        } catch (Exception e) {
            log.warn("Failed to record deviation for vehicle {}: {}", vehicleId, e.getMessage());
        }
    }

    private void updateRealtimeBaseline(SegmentInfo segment, double actualSpeed, LocalDateTime arrivalTime) {
        if (actualSpeed < 0.5 || actualSpeed > 30) {
            return;
        }

        int hourOfDay = arrivalTime.getHour();
        int dayOfWeek = arrivalTime.getDayOfWeek().getValue();

        double[] agg = roadSegmentRedisDao.getHourlyAggSpeed(segment.getSegmentId(), hourOfDay);
        double currentAvg = agg != null ? agg[0] : 0;
        long currentCount = agg != null ? (long) agg[1] : 0;

        double newAvg;
        long newCount;
        if (currentCount == 0) {
            newAvg = actualSpeed;
            newCount = 1;
        } else {
            newCount = currentCount + 1;
            double alpha = Math.min(0.3, 2.0 / (newCount + 1));
            newAvg = currentAvg * (1 - alpha) + actualSpeed * alpha;
        }

        roadSegmentRedisDao.saveHourlyAggSpeed(segment.getSegmentId(), hourOfDay, newAvg, newCount);
        roadSegmentRedisDao.saveBaselineSpeed(segment.getSegmentId(), dayOfWeek, hourOfDay, newAvg);

        double freeFlowSpeed = segment.getFreeFlowSpeed() > 0 ? segment.getFreeFlowSpeed() : 13.89;
        double congestionFactor = Math.max(1.0, Math.min(5.0, freeFlowSpeed / Math.max(newAvg, 1.0)));
        roadSegmentRedisDao.saveBaselineCongestion(segment.getSegmentId(), dayOfWeek, hourOfDay, congestionFactor);
    }

    private SegmentInfo findSegmentByEndStation(List<SegmentInfo> segments, String stationId) {
        for (SegmentInfo seg : segments) {
            if (stationId.equals(seg.getEndStationId())) {
                return seg;
            }
        }
        return null;
    }

    public Map<String, Object> getDailyAccuracyTrend(int days) {
        String startTime = LocalDateTime.now().minusDays(days)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<Map<String, Object>> trend = deviationMapper.findDailyAccuracyTrend(startTime);

        List<String> dates = new ArrayList<>();
        List<Double> accuracyRates = new ArrayList<>();
        List<Double> avgDeviationRates = new ArrayList<>();
        double totalAccurate = 0;
        double totalCount = 0;

        for (Map<String, Object> row : trend) {
            dates.add(String.valueOf(row.get("stat_date")));
            long count = ((Number) row.get("total_count")).longValue();
            long accurate = row.get("accurate_count") != null
                    ? ((Number) row.get("accurate_count")).longValue() : 0;
            double rate = count > 0 ? accurate * 100.0 / count : 0.0;
            accuracyRates.add(Math.round(rate * 100.0) / 100.0);
            avgDeviationRates.add(row.get("avg_deviation_rate") != null
                    ? ((Number) row.get("avg_deviation_rate")).doubleValue() : 0.0);
            totalAccurate += accurate;
            totalCount += count;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("accuracyRates", accuracyRates);
        result.put("avgDeviationRates", avgDeviationRates);
        result.put("overallAccuracy", totalCount > 0
                ? Math.round(totalAccurate * 10000.0 / totalCount) / 100.0 : 0.0);
        result.put("totalPredictions", (long) totalCount);

        if (accuracyRates.size() >= 2) {
            double current = accuracyRates.get(accuracyRates.size() - 1);
            double previous = accuracyRates.get(accuracyRates.size() - 2);
            result.put("accuracyChange", Math.round((current - previous) * 100.0) / 100.0);
        } else {
            result.put("accuracyChange", 0.0);
        }

        return result;
    }

    public Map<String, Object> getHourlyAccuracy() {
        String startTime = LocalDateTime.now().minusDays(7)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<Map<String, Object>> data = deviationMapper.findHourlyAccuracy(startTime);

        List<Integer> hours = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(h);
            rates.add(0.0);
        }
        for (Map<String, Object> row : data) {
            int hour = ((Number) row.get("hour_of_day")).intValue();
            long count = ((Number) row.get("total_count")).longValue();
            long accurate = row.get("accurate_count") != null
                    ? ((Number) row.get("accurate_count")).longValue() : 0;
            if (count > 0 && hour >= 0 && hour < 24) {
                rates.set(hour, Math.round(accurate * 10000.0 / count) / 100.0);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hours", hours);
        result.put("accuracyRates", rates);
        return result;
    }

    public List<Map<String, Object>> getSegmentDeviationRanking(int limit) {
        String startTime = LocalDateTime.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return deviationMapper.findSegmentDeviationRanking(startTime, limit);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 120000)
    public void checkVehicleArrivals() {
        Set<String> onlineVehicles = vehiclePositionRedisDao.getOnlineVehicleIds();
        if (onlineVehicles == null || onlineVehicles.isEmpty()) {
            return;
        }

        int count = 0;
        for (String vehicleId : onlineVehicles) {
            try {
                VehiclePosition pos = vehiclePositionRedisDao.getPosition(vehicleId);
                if (pos != null) {
                    checkAndRecordDeviation(vehicleId, pos);
                    count++;
                }
            } catch (Exception e) {
                log.debug("Check arrival failed for vehicle {}: {}", vehicleId, e.getMessage());
            }
        }
        if (count > 0) {
            log.debug("Checked arrivals for {} online vehicles", count);
        }
    }
}
