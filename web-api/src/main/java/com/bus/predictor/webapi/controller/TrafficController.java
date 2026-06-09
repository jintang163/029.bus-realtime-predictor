package com.bus.predictor.webapi.controller;

import com.bus.predictor.traffic.model.RoadSegmentManager;
import com.bus.predictor.traffic.model.SegmentSpeedHistoryService;
import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public TrafficController(RoadSegmentRedisDao roadSegmentRedisDao,
                             RoadSegmentManager roadSegmentManager,
                             SegmentSpeedHistoryService segmentSpeedHistoryService) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.segmentSpeedHistoryService = segmentSpeedHistoryService;
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
}
