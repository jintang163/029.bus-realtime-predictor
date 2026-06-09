package com.bus.predictor.route.controller;

import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.entity.LineStationEntity;
import com.bus.predictor.route.service.LineService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/route/line")
public class LineController {

    private final LineService lineService;

    public LineController(LineService lineService) {
        this.lineService = lineService;
    }

    @GetMapping("/list")
    public Result<List<LineEntity>> list() {
        return Result.success(lineService.listAll());
    }

    @GetMapping("/{lineId}")
    public Result<LineEntity> getById(@PathVariable String lineId) {
        return Result.success(lineService.getById(lineId));
    }

    @PostMapping
    public Result<LineEntity> create(@RequestBody LineEntity entity) {
        return Result.success(lineService.create(entity));
    }

    @PutMapping
    public Result<LineEntity> update(@RequestBody LineEntity entity) {
        return Result.success(lineService.update(entity));
    }

    @DeleteMapping("/{lineId}")
    public Result<Void> delete(@PathVariable String lineId) {
        lineService.delete(lineId);
        return Result.success(null);
    }

    @GetMapping("/{lineId}/stations")
    public Result<List<Map<String, Object>>> getStations(@PathVariable String lineId) {
        return Result.success(lineService.getLineStationsWithDetail(lineId));
    }

    @PostMapping("/{lineId}/stations")
    public Result<Void> saveStations(@PathVariable String lineId,
                                     @RequestBody List<LineStationEntity> stations) {
        lineService.saveLineStations(lineId, stations);
        return Result.success(null);
    }

    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache() {
        lineService.refreshAllLineCache();
        return Result.success(null);
    }

    @PostMapping("/cache/refresh/{lineId}")
    public Result<Void> refreshLineCache(@PathVariable String lineId) {
        lineService.refreshLineCache(lineId);
        return Result.success(null);
    }
}
