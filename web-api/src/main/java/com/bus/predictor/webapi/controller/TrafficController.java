package com.bus.predictor.webapi.controller;

import com.bus.predictor.dal.redis.RoadSegmentRedisDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final RoadSegmentRedisDao roadSegmentRedisDao;

    public TrafficController(RoadSegmentRedisDao roadSegmentRedisDao) {
        this.roadSegmentRedisDao = roadSegmentRedisDao;
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
}
