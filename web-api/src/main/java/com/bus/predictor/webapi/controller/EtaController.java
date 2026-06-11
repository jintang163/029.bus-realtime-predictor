package com.bus.predictor.webapi.controller;

import com.bus.predictor.common.model.EtaResponse;
import com.bus.predictor.traffic.model.EtaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EtaController {

    private static final Logger log = LoggerFactory.getLogger(EtaController.class);

    private final EtaService etaService;

    public EtaController(EtaService etaService) {
        this.etaService = etaService;
    }

    @GetMapping("/eta")
    public Result<EtaResponse> getEta(
            @RequestParam(value = "line") String lineCode,
            @RequestParam(value = "station") String stationName,
            @RequestParam(value = "direction", defaultValue = "up") String direction,
            @RequestParam(value = "forceRefresh", defaultValue = "false") boolean forceRefresh) {

        log.info("ETA query: line={}, station={}, direction={}, forceRefresh={}",
                lineCode, stationName, direction, forceRefresh);

        if (lineCode == null || lineCode.trim().isEmpty()) {
            return Result.fail(400, "线路编号不能为空");
        }
        if (stationName == null || stationName.trim().isEmpty()) {
            return Result.fail(400, "站点名称不能为空");
        }

        String normalizedDirection = "down".equalsIgnoreCase(direction) ? "down" : "up";

        EtaResponse response = etaService.getEta(
                lineCode.trim(),
                stationName.trim(),
                normalizedDirection,
                forceRefresh
        );

        return Result.success(response);
    }

    @GetMapping("/eta/health")
    public Result<String> healthCheck() {
        return Result.success("ETA service is running");
    }
}
