package com.bus.predictor.route.controller;

import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.service.StationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/route/station")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping("/list")
    public Result<List<StationEntity>> list() {
        return Result.success(stationService.listAll());
    }

    @GetMapping("/{stationId}")
    public Result<StationEntity> getById(@PathVariable String stationId) {
        return Result.success(stationService.getById(stationId));
    }

    @PostMapping
    public Result<StationEntity> create(@RequestBody StationEntity entity) {
        return Result.success(stationService.create(entity));
    }

    @PutMapping
    public Result<StationEntity> update(@RequestBody StationEntity entity) {
        return Result.success(stationService.update(entity));
    }

    @DeleteMapping("/{stationId}")
    public Result<Void> delete(@PathVariable String stationId) {
        stationService.delete(stationId);
        return Result.success(null);
    }

    @GetMapping("/nearby")
    public Result<List<StationEntity>> findNearby(
            @RequestParam Double longitude,
            @RequestParam Double latitude,
            @RequestParam(defaultValue = "500") Double radius,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(stationService.findNearby(longitude, latitude, radius, limit));
    }
}
