package com.bus.predictor.traffic.model;

import com.bus.predictor.dal.entity.SegmentSpeedHistoryEntity;
import com.bus.predictor.dal.mapper.SegmentSpeedHistoryMapper;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SegmentSpeedHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SegmentSpeedHistoryService.class);

    private final SegmentSpeedHistoryMapper segmentSpeedHistoryMapper;
    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;

    public SegmentSpeedHistoryService(SegmentSpeedHistoryMapper segmentSpeedHistoryMapper,
                                      RoadSegmentRedisDao roadSegmentRedisDao,
                                      RoadSegmentManager roadSegmentManager) {
        this.segmentSpeedHistoryMapper = segmentSpeedHistoryMapper;
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
    }

    @Scheduled(fixedRate = 120000, initialDelay = 60000)
    public void recordSnapshot() {
        List<SegmentInfo> segments = roadSegmentManager.getAllSegments();
        int count = 0;
        for (SegmentInfo seg : segments) {
            Double speed = roadSegmentRedisDao.getSegmentSpeed(seg.getSegmentId());
            Double congestion = roadSegmentRedisDao.getSegmentCongestion(seg.getSegmentId());
            if (speed != null) {
                SegmentSpeedHistoryEntity entity = SegmentSpeedHistoryEntity.builder()
                        .segmentId(seg.getSegmentId())
                        .lineId(seg.getLineId())
                        .speed(speed)
                        .congestionFactor(congestion)
                        .speedSource(1)
                        .recordTime(LocalDateTime.now())
                        .createTime(LocalDateTime.now())
                        .build();
                segmentSpeedHistoryMapper.insert(entity);
                count++;
            }
        }
        if (count > 0) {
            log.debug("Recorded speed snapshot for {} segments", count);
        }
    }

    public List<Map<String, Object>> getHistory(String segmentId, String startTime) {
        if (startTime == null || startTime.isEmpty()) {
            startTime = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return segmentSpeedHistoryMapper.findHistoryByTimeRange(segmentId, startTime);
    }

    public Map<String, Object> getHistoryComparison(String segmentId) {
        Map<String, Object> result = new HashMap<>();

        Double currentSpeed = roadSegmentRedisDao.getSegmentSpeed(segmentId);
        Double currentCongestion = roadSegmentRedisDao.getSegmentCongestion(segmentId);
        result.put("currentSpeed", currentSpeed);
        result.put("currentCongestion", currentCongestion);

        int currentHour = LocalDateTime.now().getHour();
        Map<String, Object> avgData = segmentSpeedHistoryMapper.findWeekdayHourlyAverage(segmentId, currentHour);
        if (avgData != null) {
            result.put("avgSpeed", avgData.get("avg_speed"));
            result.put("avgCongestion", avgData.get("avg_congestion"));
        }

        String twoHoursAgo = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<Map<String, Object>> history = segmentSpeedHistoryMapper.findHistoryByTimeRange(segmentId, twoHoursAgo);
        result.put("historyData", history);

        return result;
    }
}
