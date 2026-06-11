package com.bus.predictor.webapi.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bus.predictor.common.model.EtaResponse;
import com.bus.predictor.dal.redis.EtaQueryCacheDao;
import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.entity.LineStationEntity;
import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.mapper.LineMapper;
import com.bus.predictor.route.mapper.LineStationMapper;
import com.bus.predictor.route.mapper.StationMapper;
import com.bus.predictor.traffic.model.EtaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EtaPrecomputeScheduler {

    private static final Logger log = LoggerFactory.getLogger(EtaPrecomputeScheduler.class);

    private static final int MAX_HOT_STATIONS_PER_LINE = 5;

    private final LineMapper lineMapper;
    private final LineStationMapper lineStationMapper;
    private final StationMapper stationMapper;
    private final EtaService etaService;
    private final EtaQueryCacheDao etaQueryCacheDao;

    public EtaPrecomputeScheduler(LineMapper lineMapper,
                                   LineStationMapper lineStationMapper,
                                   StationMapper stationMapper,
                                   EtaService etaService,
                                   EtaQueryCacheDao etaQueryCacheDao) {
        this.lineMapper = lineMapper;
        this.lineStationMapper = lineStationMapper;
        this.stationMapper = stationMapper;
        this.etaService = etaService;
        this.etaQueryCacheDao = etaQueryCacheDao;
    }

    @Scheduled(fixedRate = 30000, initialDelay = 15000)
    public void precomputeHotEtas() {
        log.info("Starting ETA precomputation for hot routes and stations...");
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        try {
            List<LineEntity> activeLines = lineMapper.selectList(
                    new LambdaQueryWrapper<LineEntity>()
                            .eq(LineEntity::getStatus, 1)
                            .last("LIMIT 20")
            );

            for (LineEntity line : activeLines) {
                try {
                    List<LineStationEntity> lineStations = lineStationMapper.findByLineId(line.getLineId());
                    if (lineStations.isEmpty()) continue;

                    List<String> stationIds = lineStations.stream()
                            .map(LineStationEntity::getStationId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    if (stationIds.isEmpty()) continue;

                    Map<String, StationEntity> stationMap = new HashMap<>();
                    try {
                        List<StationEntity> stations = stationMapper.selectBatchIds(stationIds);
                        for (StationEntity s : stations) {
                            if (s != null && s.getStationId() != null) {
                                stationMap.put(s.getStationId(), s);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to load stations for line {}", line.getLineCode());
                    }

                    List<String> hotStationNames = selectHotStations(lineStations, stationMap);

                    for (String stationName : hotStationNames) {
                        for (String direction : Arrays.asList("up", "down")) {
                            try {
                                EtaResponse response = etaService.getEta(
                                        line.getLineCode(), stationName, direction, true
                                );
                                if (response != null && response.getVehicles() != null
                                        && !response.getVehicles().isEmpty()) {
                                    etaQueryCacheDao.saveEtaResponse(
                                            line.getLineCode(), stationName, direction, response
                                    );
                                    successCount++;
                                }
                            } catch (Exception e) {
                                failCount++;
                                log.debug("Precompute failed: {} - {} - {}",
                                        line.getLineCode(), stationName, direction);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing line {}: {}", line.getLineCode(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Fatal error in ETA precomputation scheduler", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("ETA precomputation done: success={}, failed={}, {}ms", successCount, failCount, duration);
    }

    private List<String> selectHotStations(List<LineStationEntity> lineStations,
                                            Map<String, StationEntity> stationMap) {
        Set<String> result = new LinkedHashSet<>();

        List<LineStationEntity> sorted = lineStations.stream()
                .filter(ls -> ls.getStationOrder() != null)
                .sorted(Comparator.comparing(LineStationEntity::getStationOrder))
                .collect(Collectors.toList());

        if (sorted.isEmpty()) return new ArrayList<>();

        addStationName(result, sorted.get(0), stationMap);
        addStationName(result, sorted.get(sorted.size() - 1), stationMap);

        if (sorted.size() > 4) {
            addStationName(result, sorted.get(sorted.size() / 2), stationMap);
        }
        if (sorted.size() > 6) {
            addStationName(result, sorted.get(sorted.size() / 4), stationMap);
            addStationName(result, sorted.get(3 * sorted.size() / 4), stationMap);
        }

        return new ArrayList<>(result).subList(0, Math.min(result.size(), MAX_HOT_STATIONS_PER_LINE));
    }

    private void addStationName(Set<String> result, LineStationEntity ls,
                                 Map<String, StationEntity> stationMap) {
        if (ls == null || ls.getStationId() == null) return;
        StationEntity station = stationMap.get(ls.getStationId());
        if (station != null && station.getStationName() != null) {
            result.add(station.getStationName());
        }
    }
}
