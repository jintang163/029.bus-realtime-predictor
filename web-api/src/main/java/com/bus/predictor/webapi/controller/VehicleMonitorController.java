package com.bus.predictor.webapi.controller;

import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import com.bus.predictor.traffic.model.ArrivalPredictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleMonitorController {

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final ArrivalPredictService arrivalPredictService;

    public VehicleMonitorController(VehiclePositionRedisDao vehiclePositionRedisDao,
                                    ArrivalPredictService arrivalPredictService) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.arrivalPredictService = arrivalPredictService;
    }

    @GetMapping("/position/{vehicleId}")
    public Result<VehiclePosition> getPosition(@PathVariable String vehicleId) {
        VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
        return Result.success(position);
    }

    @GetMapping("/online")
    public Result<List<VehiclePosition>> getOnlineVehicles() {
        List<VehiclePosition> positions = vehiclePositionRedisDao.getAllOnlinePositions();
        return Result.success(positions);
    }

    @GetMapping("/status/{vehicleId}")
    public Result<Map<String, Object>> getVehicleStatus(@PathVariable String vehicleId) {
        VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
        boolean online = vehiclePositionRedisDao.isVehicleOnline(vehicleId);

        Map<String, Object> status = new HashMap<>();
        status.put("vehicleId", vehicleId);
        status.put("online", online);
        status.put("position", position);
        if (position != null) {
            status.put("lastGpsTime", position.getGpsTime());
            long age = System.currentTimeMillis() - position.getGpsTime();
            status.put("dataAgeMs", age);
        }
        return Result.success(status);
    }

    @GetMapping("/online/count")
    public Result<Map<String, Object>> getOnlineCount() {
        List<VehiclePosition> positions = vehiclePositionRedisDao.getAllOnlinePositions();
        Map<String, Object> data = new HashMap<>();
        data.put("onlineCount", positions.size());
        return Result.success(data);
    }

    @GetMapping("/prediction/{vehicleId}")
    public Result<List<ArrivalPrediction>> predictArrival(
            @PathVariable String vehicleId,
            @RequestParam String routeId) {
        List<ArrivalPrediction> predictions = arrivalPredictService.predict(routeId, vehicleId);
        return Result.success(predictions);
    }
}
