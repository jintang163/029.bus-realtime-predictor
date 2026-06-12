package com.bus.predictor.webapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bus.predictor.common.model.TrajectoryRecord;
import com.bus.predictor.dal.entity.TrajectoryRecordEntity;
import com.bus.predictor.dal.mapper.TrajectoryRecordMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trajectory")
public class TrajectoryController {

    private final TrajectoryRecordMapper trajectoryRecordMapper;

    public TrajectoryController(TrajectoryRecordMapper trajectoryRecordMapper) {
        this.trajectoryRecordMapper = trajectoryRecordMapper;
    }

    @GetMapping("/vehicle/{vehicleId}")
    public Result<Map<String, Object>> getVehicleTrajectory(
            @PathVariable String vehicleId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1000") int limit) {

        QueryWrapper<TrajectoryRecordEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("vehicle_id", vehicleId);

        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge("gps_time", startTime);
        } else {
            wrapper.ge("gps_time", LocalDateTime.now().minusHours(2)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le("gps_time", endTime);
        }

        wrapper.orderByAsc("gps_time");
        wrapper.last("LIMIT " + limit);

        List<TrajectoryRecordEntity> entities = trajectoryRecordMapper.selectList(wrapper);
        List<TrajectoryRecord> records = new ArrayList<>();
        double totalDistance = 0;
        double avgSpeed = 0;
        int validSpeedCount = 0;

        TrajectoryRecord prev = null;
        for (TrajectoryRecordEntity entity : entities) {
            TrajectoryRecord record = TrajectoryRecord.builder()
                    .id(entity.getId())
                    .vehicleId(entity.getVehicleId())
                    .longitude(entity.getLongitude())
                    .latitude(entity.getLatitude())
                    .speed(entity.getSpeed())
                    .direction(entity.getDirection())
                    .gpsTime(entity.getGpsTime() != null ? entity.getGpsTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                    .createTime(entity.getCreateTime() != null ? entity.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                    .build();
            records.add(record);

            if (entity.getSpeed() != null && entity.getSpeed() > 0) {
                avgSpeed += entity.getSpeed();
                validSpeedCount++;
            }

            if (prev != null && prev.getLongitude() != null && entity.getLongitude() != null) {
                double dist = haversineDistance(
                        prev.getLatitude(), prev.getLongitude(),
                        entity.getLatitude(), entity.getLongitude()
                );
                totalDistance += dist;
            }
            prev = record;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("totalCount", records.size());
        result.put("totalDistance", Math.round(totalDistance * 100) / 100.0);
        result.put("avgSpeed", validSpeedCount > 0 ? Math.round((avgSpeed / validSpeedCount) * 360) / 100.0 : 0);
        result.put("vehicleId", vehicleId);

        return Result.success(result);
    }

    @GetMapping("/vehicle/{vehicleId}/replay")
    public Result<Map<String, Object>> getReplayData(
            @PathVariable String vehicleId,
            @RequestParam(required = false) String date) {

        String startStr, endStr;
        if (date != null && !date.isEmpty()) {
            startStr = date + " 00:00:00";
            endStr = date + " 23:59:59";
        } else {
            LocalDateTime now = LocalDateTime.now();
            startStr = now.toLocalDate().atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            endStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        QueryWrapper<TrajectoryRecordEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("vehicle_id", vehicleId)
                .between("gps_time", startStr, endStr)
                .orderByAsc("gps_time")
                .last("LIMIT 5000");

        List<TrajectoryRecordEntity> entities = trajectoryRecordMapper.selectList(wrapper);
        List<Map<String, Object>> path = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        List<String> times = new ArrayList<>();

        long startTimeMs = 0;
        long endTimeMs = 0;

        for (int i = 0; i < entities.size(); i++) {
            TrajectoryRecordEntity entity = entities.get(i);
            Map<String, Object> point = new HashMap<>();
            point.put("lng", entity.getLongitude());
            point.put("lat", entity.getLatitude());
            point.put("speed", entity.getSpeed() != null ? Math.round(entity.getSpeed() * 360) / 100.0 : 0);
            point.put("direction", entity.getDirection());
            long gpsMs = entity.getGpsTime() != null ?
                    entity.getGpsTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            point.put("time", gpsMs);
            point.put("index", i);
            path.add(point);

            if (entity.getSpeed() != null) {
                speeds.add(Math.round(entity.getSpeed() * 360) / 100.0);
            }
            if (entity.getGpsTime() != null) {
                times.add(entity.getGpsTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }

            if (i == 0) startTimeMs = gpsMs;
            if (i == entities.size() - 1) endTimeMs = gpsMs;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("speeds", speeds);
        result.put("times", times);
        result.put("startTime", startTimeMs);
        result.put("endTime", endTimeMs);
        result.put("duration", endTimeMs - startTimeMs);
        result.put("pointCount", path.size());

        return Result.success(result);
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> getTrajectorySummary(
            @RequestParam(required = false) String vehicleId,
            @RequestParam(defaultValue = "7") int days) {

        String startTime = LocalDateTime.now().minusDays(days)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        QueryWrapper<TrajectoryRecordEntity> wrapper = new QueryWrapper<>();
        wrapper.ge("gps_time", startTime);
        if (vehicleId != null && !vehicleId.isEmpty()) {
            wrapper.eq("vehicle_id", vehicleId);
        }

        List<TrajectoryRecordEntity> records = trajectoryRecordMapper.selectList(wrapper);

        Map<String, List<TrajectoryRecordEntity>> groupedByVehicle = new HashMap<>();
        for (TrajectoryRecordEntity record : records) {
            groupedByVehicle.computeIfAbsent(record.getVehicleId(), k -> new ArrayList<>()).add(record);
        }

        List<Map<String, Object>> vehicleSummaries = new ArrayList<>();
        for (Map.Entry<String, List<TrajectoryRecordEntity>> entry : groupedByVehicle.entrySet()) {
            String vid = entry.getKey();
            List<TrajectoryRecordEntity> vehicleRecords = entry.getValue();

            double totalDist = 0;
            double avgSpeed = 0;
            int count = 0;
            TrajectoryRecordEntity prev = null;

            for (TrajectoryRecordEntity r : vehicleRecords) {
                if (prev != null) {
                    totalDist += haversineDistance(prev.getLatitude(), prev.getLongitude(),
                            r.getLatitude(), r.getLongitude());
                }
                if (r.getSpeed() != null && r.getSpeed() > 0) {
                    avgSpeed += r.getSpeed();
                    count++;
                }
                prev = r;
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("vehicleId", vid);
            summary.put("recordCount", vehicleRecords.size());
            summary.put("totalDistance", Math.round(totalDist * 100) / 100.0);
            summary.put("avgSpeedKmh", count > 0 ? Math.round((avgSpeed / count) * 360) / 100.0 : 0);
            vehicleSummaries.add(summary);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("vehicleSummaries", vehicleSummaries);
        result.put("totalRecords", records.size());
        result.put("totalVehicles", groupedByVehicle.size());
        result.put("days", days);

        return Result.success(result);
    }

    private double haversineDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 0;
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c / 1000.0;
    }
}
