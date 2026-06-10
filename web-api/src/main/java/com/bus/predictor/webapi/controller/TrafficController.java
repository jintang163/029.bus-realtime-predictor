package com.bus.predictor.webapi.controller;

import com.bus.predictor.traffic.model.RoadSegmentManager;
import com.bus.predictor.traffic.model.SegmentSpeedHistoryService;
import com.bus.predictor.traffic.model.PredictionDeviationService;
import com.bus.predictor.traffic.model.SelfLearningBaselineService;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final RoadSegmentRedisDao roadSegmentRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final SegmentSpeedHistoryService segmentSpeedHistoryService;
    private final PredictionDeviationService deviationService;
    private final SelfLearningBaselineService baselineService;

    public TrafficController(RoadSegmentRedisDao roadSegmentRedisDao,
                             RoadSegmentManager roadSegmentManager,
                             SegmentSpeedHistoryService segmentSpeedHistoryService,
                             PredictionDeviationService deviationService,
                             SelfLearningBaselineService baselineService) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.segmentSpeedHistoryService = segmentSpeedHistoryService;
        this.deviationService = deviationService;
        this.baselineService = baselineService;
    }

    @GetMapping("/segment/{segmentId}/speed")
    public Result<Map<String, Object>> getSegmentSpeed(@PathVariable String segmentId) {
        Double speed = roadSegmentRedisDao.getSegmentSpeed(segmentId);
        Double congestion = roadSegmentRedisDao.getSegmentCongestion(segmentId);

        Map<String, Object> data = new HashMap<>();
        data.put("segmentId", segmentId);
        data.put("currentSpeed", speed);
        data.put("congestionFactor", congestion);
        return Result.success(data);
    }

    @GetMapping("/heatmap")
    public Result<List<Map<String, Object>>> getHeatmapData() {
        List<Map<String, Object>> data = roadSegmentManager.getHeatmapData();
        return Result.success(data);
    }

    @GetMapping("/segments")
    public Result<List<Map<String, Object>>> getAllSegments() {
        List<Map<String, Object>> data = roadSegmentManager.getAllSegmentsWithSpeed();
        return Result.success(data);
    }

    @GetMapping("/segment/{segmentId}/detail")
    public Result<Map<String, Object>> getSegmentDetail(@PathVariable String segmentId) {
        Map<String, Object> data = roadSegmentManager.getSegmentDetail(segmentId);
        if (data == null) {
            return Result.fail(404, "Segment not found");
        }
        return Result.success(data);
    }

    @GetMapping("/segment/{segmentId}/history")
    public Result<List<Map<String, Object>>> getSegmentHistory(
            @PathVariable String segmentId,
            @RequestParam(required = false) String startTime) {
        List<Map<String, Object>> data = segmentSpeedHistoryService.getHistory(segmentId, startTime);
        return Result.success(data);
    }

    @GetMapping("/segment/{segmentId}/comparison")
    public Result<Map<String, Object>> getSegmentComparison(@PathVariable String segmentId) {
        Map<String, Object> data = segmentSpeedHistoryService.getHistoryComparison(segmentId);
        return Result.success(data);
    }

    @GetMapping("/deviation/trend")
    public Result<Map<String, Object>> getDeviationTrend(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> data = deviationService.getDailyAccuracyTrend(days);
        return Result.success(data);
    }

    @GetMapping("/deviation/hourly")
    public Result<Map<String, Object>> getHourlyAccuracy() {
        Map<String, Object> data = deviationService.getHourlyAccuracy();
        return Result.success(data);
    }

    @GetMapping("/deviation/segment-ranking")
    public Result<List<Map<String, Object>>> getSegmentDeviationRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> data = deviationService.getSegmentDeviationRanking(limit);
        return Result.success(data);
    }

    @GetMapping("/baseline/status")
    public Result<Map<String, Object>> getBaselineStatus() {
        Map<String, Object> data = baselineService.getBaselineStatus();
        return Result.success(data);
    }

    @PostMapping("/baseline/train")
    public Result<Map<String, Object>> triggerBaselineTraining() {
        Map<String, Object> result = baselineService.trainBaselines(true);
        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            return Result.success(result);
        }
        return Result.fail(500, (String) result.get("message"));
    }

    @GetMapping("/baseline/segment/{segmentId}")
    public Result<List<Map<String, Object>>> getSegmentBaselines(@PathVariable String segmentId) {
        List<Map<String, Object>> data = baselineService.getSegmentBaselines(segmentId);
        return Result.success(data);
    }

    @GetMapping("/deviation/overview")
    public Result<Map<String, Object>> getDeviationOverview() {
        Map<String, Object> trend = deviationService.getDailyAccuracyTrend(7);
        Map<String, Object> hourly = deviationService.getHourlyAccuracy();
        Map<String, Object> baselineStatus = baselineService.getBaselineStatus();
        List<Map<String, Object>> ranking = deviationService.getSegmentDeviationRanking(5);

        Map<String, Object> result = new HashMap<>();
        result.put("dailyTrend", trend);
        result.put("hourlyAccuracy", hourly);
        result.put("baselineStatus", baselineStatus);
        result.put("worstSegments", ranking);
        return Result.success(result);
    }
}
