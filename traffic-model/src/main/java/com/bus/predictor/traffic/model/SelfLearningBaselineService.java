package com.bus.predictor.traffic.model;

import com.bus.predictor.dal.entity.SegmentBaselineSpeedEntity;
import com.bus.predictor.dal.mapper.SegmentBaselineSpeedMapper;
import com.bus.predictor.dal.mapper.SegmentSpeedHistoryMapper;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SelfLearningBaselineService {

    private static final Logger log = LoggerFactory.getLogger(SelfLearningBaselineService.class);
    private static final int TRAINING_HISTORY_DAYS = 30;
    private static final double FREE_FLOW_SPEED_MS = 13.89;

    private final SegmentBaselineSpeedMapper baselineMapper;
    private final SegmentSpeedHistoryMapper historyMapper;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;

    private volatile boolean trainingInProgress = false;

    public SelfLearningBaselineService(SegmentBaselineSpeedMapper baselineMapper,
                                        SegmentSpeedHistoryMapper historyMapper,
                                        RoadSegmentRedisDao roadSegmentRedisDao,
                                        RoadSegmentManager roadSegmentManager) {
        this.baselineMapper = baselineMapper;
        this.historyMapper = historyMapper;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
    }

    @Scheduled(cron = "0 0 3 ? * SUN")
    public void scheduledWeeklyTraining() {
        log.info("Starting scheduled weekly baseline training...");
        trainBaselines(false);
    }

    @Transactional
    public Map<String, Object> trainBaselines(boolean manual) {
        if (trainingInProgress) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Training already in progress");
            return result;
        }

        trainingInProgress = true;
        long startTime = System.currentTimeMillis();
        int processedSegments = 0;
        int totalRecords = 0;

        try {
            Map<String, String> status = new HashMap<>();
            status.put("status", "running");
            status.put("startTime", LocalDateTime.now().toString());
            status.put("trigger", manual ? "manual" : "scheduled");
            roadSegmentRedisDao.setBaselineTrainStatus(status);

            String startTimeStr = LocalDateTime.now().minusDays(TRAINING_HISTORY_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            List<SegmentInfo> allSegments = roadSegmentManager.getAllSegments();
            log.info("Training baselines for {} segments using {} days of history",
                    allSegments.size(), TRAINING_HISTORY_DAYS);

            for (SegmentInfo segment : allSegments) {
                try {
                    int recordsProcessed = trainSegmentBaseline(segment, startTimeStr);
                    totalRecords += recordsProcessed;
                    processedSegments++;
                } catch (Exception e) {
                    log.warn("Failed to train baseline for segment {}: {}",
                            segment.getSegmentId(), e.getMessage());
                }
            }

            loadBaselinesToRedis();

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            status.put("status", "completed");
            status.put("endTime", LocalDateTime.now().toString());
            status.put("processedSegments", String.valueOf(processedSegments));
            status.put("totalRecords", String.valueOf(totalRecords));
            status.put("durationSeconds", String.valueOf(duration));
            roadSegmentRedisDao.setBaselineTrainStatus(status);

            log.info("Baseline training completed: {} segments, {} records, {}s",
                    processedSegments, totalRecords, duration);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("processedSegments", processedSegments);
            result.put("totalRecords", totalRecords);
            result.put("durationSeconds", duration);
            return result;
        } catch (Exception e) {
            log.error("Baseline training failed", e);

            Map<String, String> status = new HashMap<>();
            status.put("status", "failed");
            status.put("endTime", LocalDateTime.now().toString());
            status.put("error", e.getMessage());
            roadSegmentRedisDao.setBaselineTrainStatus(status);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        } finally {
            trainingInProgress = false;
        }
    }

    private int trainSegmentBaseline(SegmentInfo segment, String startTimeStr) {
        int recordsProcessed = 0;

        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            for (int hourOfDay = 0; hourOfDay < 24; hourOfDay++) {
                List<Map<String, Object>> historyData = historyMapper
                        .findHistoryByTimeRange(segment.getSegmentId(), startTimeStr);

                List<Double> speeds = new ArrayList<>();
                for (Map<String, Object> row : historyData) {
                    Object recordTimeObj = row.get("record_time");
                    Object speedObj = row.get("speed");

                    if (recordTimeObj == null || speedObj == null) continue;

                    LocalDateTime recordTime;
                    if (recordTimeObj instanceof LocalDateTime) {
                        recordTime = (LocalDateTime) recordTimeObj;
                    } else {
                        try {
                            recordTime = LocalDateTime.parse(recordTimeObj.toString(),
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        } catch (Exception e) {
                            continue;
                        }
                    }

                    if (recordTime.getDayOfWeek().getValue() != dayOfWeek) continue;
                    if (recordTime.getHour() != hourOfDay) continue;

                    double speed = ((Number) speedObj).doubleValue();
                    if (speed >= 0.5 && speed <= 30) {
                        speeds.add(speed);
                    }
                }

                if (!speeds.isEmpty()) {
                    double avgSpeed = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double variance = speeds.stream()
                            .mapToDouble(s -> Math.pow(s - avgSpeed, 2))
                            .average().orElse(0.0);
                    double stdDev = Math.sqrt(variance);

                    double freeFlowSpeed = segment.getFreeFlowSpeed() > 0 ? segment.getFreeFlowSpeed() : FREE_FLOW_SPEED_MS;
                    double baselineCongestion = Math.max(1.0, Math.min(5.0, freeFlowSpeed / Math.max(avgSpeed, 1.0)));

                    saveOrUpdateBaseline(segment, dayOfWeek, hourOfDay,
                            avgSpeed, baselineCongestion, speeds.size(), stdDev);

                    recordsProcessed += speeds.size();
                }
            }
        }

        return recordsProcessed;
    }

    private void saveOrUpdateBaseline(SegmentInfo segment, int dayOfWeek, int hourOfDay,
                                       double avgSpeed, double congestion, int sampleCount, double stdDev) {
        Map<String, Object> existing = baselineMapper.findBySegmentAndDayHour(
                segment.getSegmentId(), dayOfWeek, hourOfDay);

        LocalDateTime now = LocalDateTime.now();

        if (existing != null && !existing.isEmpty()) {
            SegmentBaselineSpeedEntity entity = SegmentBaselineSpeedEntity.builder()
                    .id(((Number) existing.get("id")).longValue())
                    .segmentId(segment.getSegmentId())
                    .lineId(segment.getLineId())
                    .dayOfWeek(dayOfWeek)
                    .hourOfDay(hourOfDay)
                    .baselineSpeed(avgSpeed)
                    .baselineCongestion(congestion)
                    .sampleCount(sampleCount)
                    .stdDev(stdDev)
                    .speedSource(2)
                    .trainTime(now)
                    .updateTime(now)
                    .build();
            baselineMapper.updateById(entity);
        } else {
            SegmentBaselineSpeedEntity entity = SegmentBaselineSpeedEntity.builder()
                    .segmentId(segment.getSegmentId())
                    .lineId(segment.getLineId())
                    .dayOfWeek(dayOfWeek)
                    .hourOfDay(hourOfDay)
                    .baselineSpeed(avgSpeed)
                    .baselineCongestion(congestion)
                    .sampleCount(sampleCount)
                    .stdDev(stdDev)
                    .speedSource(2)
                    .trainTime(now)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            baselineMapper.insert(entity);
        }
    }

    public void loadBaselinesToRedis() {
        List<Map<String, Object>> latestBaselines = baselineMapper.findLatestBaselines();
        int count = 0;

        for (Map<String, Object> row : latestBaselines) {
            try {
                String segmentId = String.valueOf(row.get("segment_id"));
                int dayOfWeek = ((Number) row.get("day_of_week")).intValue();
                int hourOfDay = ((Number) row.get("hour_of_day")).intValue();
                double speed = ((Number) row.get("baseline_speed")).doubleValue();

                Object congestionObj = row.get("baseline_congestion");
                double congestion = congestionObj != null
                        ? ((Number) congestionObj).doubleValue() : 1.5;

                roadSegmentRedisDao.saveBaselineSpeed(segmentId, dayOfWeek, hourOfDay, speed);
                roadSegmentRedisDao.saveBaselineCongestion(segmentId, dayOfWeek, hourOfDay, congestion);
                count++;
            } catch (Exception e) {
                log.warn("Failed to load baseline to redis: {}", e.getMessage());
            }
        }

        log.info("Loaded {} baselines to Redis", count);
    }

    public Double getEffectiveBaselineSpeed(String segmentId, int dayOfWeek, int hourOfDay) {
        Double baselineSpeed = roadSegmentRedisDao.getBaselineSpeed(segmentId, dayOfWeek, hourOfDay);

        if (baselineSpeed == null || baselineSpeed <= 0) {
            Map<String, Object> dbBaseline = baselineMapper.findBySegmentAndDayHour(
                    segmentId, dayOfWeek, hourOfDay);
            if (dbBaseline != null && dbBaseline.get("baseline_speed") != null) {
                baselineSpeed = ((Number) dbBaseline.get("baseline_speed")).doubleValue();
                if (baselineSpeed > 0) {
                    roadSegmentRedisDao.saveBaselineSpeed(segmentId, dayOfWeek, hourOfDay, baselineSpeed);
                }
            }
        }

        return baselineSpeed != null && baselineSpeed > 0 ? baselineSpeed : null;
    }

    public Double getEffectiveBaselineCongestion(String segmentId, int dayOfWeek, int hourOfDay) {
        Double baselineCongestion = roadSegmentRedisDao.getBaselineCongestion(segmentId, dayOfWeek, hourOfDay);

        if (baselineCongestion == null || baselineCongestion <= 0) {
            Map<String, Object> dbBaseline = baselineMapper.findBySegmentAndDayHour(
                    segmentId, dayOfWeek, hourOfDay);
            if (dbBaseline != null && dbBaseline.get("baseline_congestion") != null) {
                baselineCongestion = ((Number) dbBaseline.get("baseline_congestion")).doubleValue();
                if (baselineCongestion > 0) {
                    roadSegmentRedisDao.saveBaselineCongestion(segmentId, dayOfWeek, hourOfDay, baselineCongestion);
                }
            }
        }

        return baselineCongestion != null && baselineCongestion > 0 ? baselineCongestion : null;
    }

    public Map<String, Object> getBaselineStatus() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> stats = baselineMapper.getBaselineStats();
        if (stats != null) {
            result.put("totalBaselines", stats.get("total_count"));
            result.put("coveredSegments", stats.get("covered_segments"));
            result.put("lastTrainTime", stats.get("last_train_time"));
        }

        Map<Object, Object> trainStatus = roadSegmentRedisDao.getBaselineTrainStatus();
        result.put("lastTrainStatus", trainStatus);
        result.put("trainingInProgress", trainingInProgress);

        boolean amapHealthy = roadSegmentRedisDao.isAmapApiHealthy();
        result.put("amapApiHealthy", amapHealthy);

        return result;
    }

    public List<Map<String, Object>> getSegmentBaselines(String segmentId) {
        return baselineMapper.findAllBySegmentId(segmentId);
    }

    public boolean isTrainingInProgress() {
        return trainingInProgress;
    }
}
