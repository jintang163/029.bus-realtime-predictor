package com.bus.predictor.route.controller;

import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.service.LineService;
import com.bus.predictor.route.service.StationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stopboard")
public class StopBoardController {

    private final LineService lineService;
    private final StationService stationService;

    public StopBoardController(LineService lineService, StationService stationService) {
        this.lineService = lineService;
        this.stationService = stationService;
    }

    @GetMapping("/{stationId}/lines")
    public Result<List<Map<String, Object>>> getStationLines(@PathVariable String stationId) {
        StationEntity station = stationService.getById(stationId);
        if (station == null) {
            return Result.fail("站点不存在");
        }

        List<LineEntity> allLines = lineService.listAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (LineEntity line : allLines) {
            List<Map<String, Object>> stations = lineService.getLineStationsWithDetail(line.getLineId());
            for (Map<String, Object> ls : stations) {
                if (stationId.equals(ls.get("stationId"))) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("lineId", line.getLineId());
                    item.put("lineName", line.getLineName());
                    item.put("direction", line.getDirection());
                    item.put("firstBusTime", line.getFirstBusTime());
                    item.put("lastBusTime", line.getLastBusTime());
                    item.put("stationOrder", ls.get("stationOrder"));
                    item.put("stationName", ls.get("stationName"));
                    result.add(item);
                    break;
                }
            }
        }

        return Result.success(result);
    }

    @GetMapping("/{stationId}/info")
    public Result<Map<String, Object>> getStationInfo(@PathVariable String stationId) {
        StationEntity station = stationService.getById(stationId);
        if (station == null) {
            return Result.fail("站点不存在");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("stationId", station.getStationId());
        info.put("stationName", station.getStationName());
        info.put("longitude", station.getLongitude());
        info.put("latitude", station.getLatitude());
        info.put("district", station.getDistrict());
        info.put("street", station.getStreet());

        return Result.success(info);
    }
}
