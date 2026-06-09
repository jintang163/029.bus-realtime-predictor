package com.bus.predictor.route.controller;

import com.bus.predictor.route.entity.ScheduleEntity;
import com.bus.predictor.route.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/route/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/list")
    public Result<List<ScheduleEntity>> list(
            @RequestParam String lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(scheduleService.listByLineAndDate(lineId, date));
    }

    @PostMapping
    public Result<ScheduleEntity> create(@RequestBody ScheduleEntity entity) {
        return Result.success(scheduleService.create(entity));
    }

    @DeleteMapping
    public Result<Void> delete(
            @RequestParam String lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        scheduleService.deleteByLineAndDate(lineId, date);
        return Result.success(null);
    }

    @PostMapping("/import")
    public Result<Integer> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam String lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate) {
        int count = scheduleService.importFromExcel(file, lineId, scheduleDate);
        return Result.success(count);
    }
}
