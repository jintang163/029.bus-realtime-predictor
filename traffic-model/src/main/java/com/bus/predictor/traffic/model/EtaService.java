package com.bus.predictor.traffic.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.model.EtaResponse;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.entity.VehicleInfoEntity;
import com.bus.predictor.dal.mapper.VehicleInfoMapper;
import com.bus.predictor.dal.redis.EtaQueryCacheDao;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.entity.LineStationEntity;
import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.mapper.LineMapper;
import com.bus.predictor.route.mapper.LineStationMapper;
import com.bus.predictor.route.mapper.StationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class EtaService {

    private static final Logger log = LoggerFactory.getLogger(EtaService.class);

    private static final int MAX_VEHICLES = 3;
    private static final int DEFAULT_AVG_SECONDS_PER_STATION = 180;
    private static final double MIN_CALC_DISTANCE_METERS = 50.0;

    private final EtaQueryCacheDao etaQueryCacheDao;
    private final LineMapper lineMapper;
    private final StationMapper stationMapper;
    private final LineStationMapper lineStationMapper;
    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final VehicleInfoMapper vehicleInfoMapper;
    private final ArrivalPredictService arrivalPredictService;
    private final RoadSegmentManager roadSegmentManager;

    public EtaService(EtaQueryCacheDao etaQueryCacheDao,
                      LineMapper lineMapper,
                      StationMapper stationMapper,
                      LineStationMapper lineStationMapper,
                      VehiclePositionRedisDao vehiclePositionRedisDao,
                      VehicleInfoMapper vehicleInfoMapper,
                      ArrivalPredictService arrivalPredictService,
                      RoadSegmentManager roadSegmentManager) {
        this.etaQueryCacheDao = etaQueryCacheDao;
        this.lineMapper = lineMapper;
        this.stationMapper = stationMapper;
        this.lineStationMapper = lineStationMapper;
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.vehicleInfoMapper = vehicleInfoMapper;
        this.arrivalPredictService = arrivalPredictService;
        this.roadSegmentManager = roadSegmentManager;
    }

    public EtaResponse getEta(String lineCode, String stationName, String direction, boolean forceRefresh) {
        if (forceRefresh) {
            etaQueryCacheDao.invalidateCache(lineCode, stationName, direction);
        }

        if (!forceRefresh) {
            EtaResponse cached = etaQueryCacheDao.getEtaResponse(lineCode, stationName, direction);
            if (cached != null) {
                cached.setFromCache(true);
                log.debug("ETA cache hit for line={}, station={}, direction={}", lineCode, stationName, direction);
                return cached;
            }
        }

        EtaResponse response = calculateEta(lineCode, stationName, direction);
        response.setFromCache(false);

        etaQueryCacheDao.saveEtaResponse(lineCode, stationName, direction, response);

        return response;
    }

    private EtaResponse calculateEta(String lineCode, String stationName, String direction) {
        log.info("Calculating ETA for lineCode={}, stationName={}, direction={}", lineCode, stationName, direction);

        LineEntity line = findLineByCode(lineCode);
        if (line == null) {
            return buildEmptyResponse(lineCode, stationName, direction, "线路不存在");
        }

        int dirValue = "down".equalsIgnoreCase(direction) ? 1 : 0;
        StationEntity targetStation = findStationByName(stationName);
        if (targetStation == null) {
            return buildEmptyResponse(lineCode, stationName, direction, "站点不存在");
        }

        List<LineStationEntity> lineStations = lineStationMapper.findByLineId(line.getLineId());
        lineStations = lineStations.stream()
                .filter(ls -> ls.getStationOrder() != null)
                .sorted(Comparator.comparing(LineStationEntity::getStationOrder))
                .collect(Collectors.toList());

        if (lineStations.isEmpty()) {
            return buildEmptyResponse(lineCode, stationName, direction, "线路站点信息为空");
        }

        int targetStationOrder = -1;
        for (LineStationEntity ls : lineStations) {
            if (targetStation.getStationId().equals(ls.getStationId())) {
                targetStationOrder = ls.getStationOrder();
                break;
            }
        }

        if (targetStationOrder < 0) {
            return buildEmptyResponse(lineCode, stationName, direction, "站点不在该线路上");
        }

        List<VehicleInfoEntity> lineVehicles = vehicleInfoMapper.selectList(
                new LambdaQueryWrapper<VehicleInfoEntity>()
                        .eq(VehicleInfoEntity::getRouteId, line.getLineId())
                        .eq(VehicleInfoEntity::getStatus, 1)
        );

        List<VehiclePosition> onlinePositions = new ArrayList<>();
        Set<String> onlineIds = vehiclePositionRedisDao.getOnlineVehicleIds();
        if (onlineIds != null) {
            for (VehicleInfoEntity ve : lineVehicles) {
                if (onlineIds.contains(ve.getVehicleId())) {
                    VehiclePosition pos = vehiclePositionRedisDao.getPosition(ve.getVehicleId());
                    if (pos != null) {
                        onlinePositions.add(pos);
                    }
                }
            }
        }

        if (onlinePositions.isEmpty()) {
            return buildSimulationResponse(line, targetStation, lineStations, targetStationOrder, direction);
        }

        List<EtaVehicleCandidate> candidates = new ArrayList<>();

        for (VehiclePosition vp : onlinePositions) {
            VehicleInfoEntity vehicleInfo = lineVehicles.stream()
                    .filter(v -> v.getVehicleId().equals(vp.getVehicleId()))
                    .findFirst().orElse(null);

            int currentStationOrder = estimateCurrentStationOrder(vp, lineStations);
            if (currentStationOrder < 0) continue;

            int stationsAway = targetStationOrder - currentStationOrder;
            if (stationsAway < 0) continue;

            int estimatedSeconds;
            double distanceMeters;

            if (stationsAway == 0) {
                distanceMeters = GeoHashUtil.haversineDistance(
                        vp.getLatitude(), vp.getLongitude(),
                        targetStation.getLatitude(), targetStation.getLongitude()
                );
                if (distanceMeters < MIN_CALC_DISTANCE_METERS) {
                    estimatedSeconds = 0;
                } else {
                    double speed = vp.getSpeed() != null && vp.getSpeed() > 0.5 ? vp.getSpeed() : 6.0;
                    estimatedSeconds = (int) (distanceMeters / speed);
                }
            } else {
                distanceMeters = calculateDistance(lineStations, currentStationOrder, vp, targetStation, targetStationOrder);
                estimatedSeconds = stationsAway * DEFAULT_AVG_SECONDS_PER_STATION;

                double avgSpeed = vp.getSpeed() != null && vp.getSpeed() > 1.0 ? vp.getSpeed() : 5.56;
                int estimatedByDistance = (int) (distanceMeters / avgSpeed);
                estimatedSeconds = (estimatedSeconds + estimatedByDistance) / 2;
            }

            int crowdLevel = generateCrowdLevel(vp.getVehicleId());

            candidates.add(EtaVehicleCandidate.builder()
                    .vehicleId(vp.getVehicleId())
                    .licensePlate(vehicleInfo != null ? vehicleInfo.getPlateNumber() : null)
                    .estimatedSeconds(estimatedSeconds)
                    .distanceStationsAway(stationsAway)
                    .distanceMeters(distanceMeters)
                    .crowdLevel(crowdLevel)
                    .currentSpeed(vp.getSpeed() != null ? vp.getSpeed() : 0.0)
                    .build());
        }

        if (candidates.isEmpty()) {
            return buildSimulationResponse(line, targetStation, lineStations, targetStationOrder, direction);
        }

        candidates.sort(Comparator.comparingInt(EtaVehicleCandidate::getEstimatedSeconds));
        candidates = candidates.stream().limit(MAX_VEHICLES).collect(Collectors.toList());

        List<EtaResponse.EtaVehicle> etaVehicles = candidates.stream()
                .map(c -> EtaResponse.EtaVehicle.builder()
                        .vehicleId(c.getVehicleId())
                        .licensePlate(c.getLicensePlate())
                        .estimatedMinutes((int) Math.ceil(c.getEstimatedSeconds() / 60.0))
                        .estimatedSeconds(c.getEstimatedSeconds())
                        .distanceStationsAway(c.getDistanceStationsAway())
                        .distanceMeters(c.getDistanceMeters())
                        .crowdLevel(c.getCrowdLevel())
                        .crowdText(crowdLevelToText(c.getCrowdLevel()))
                        .currentSpeed(c.getCurrentSpeed())
                        .build())
                .collect(Collectors.toList());

        return EtaResponse.builder()
                .lineCode(line.getLineCode())
                .lineName(line.getLineName())
                .stationName(targetStation.getStationName())
                .direction(direction)
                .queryTime(System.currentTimeMillis())
                .fromCache(false)
                .vehicles(etaVehicles)
                .build();
    }

    private EtaResponse buildSimulationResponse(LineEntity line, StationEntity targetStation,
                                                 List<LineStationEntity> lineStations, int targetOrder, String direction) {
        log.debug("No real-time vehicles available, generating simulation ETA for line {}", line.getLineCode());

        List<EtaResponse.EtaVehicle> vehicles = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int baseInterval = line.getIntervalMinutes() != null ? line.getIntervalMinutes() : 5;
        baseInterval = Math.max(baseInterval, 3);

        for (int i = 0; i < MAX_VEHICLES; i++) {
            int estimatedMinutes = baseInterval * (i + 1) + random.nextInt(-1, 2);
            estimatedMinutes = Math.max(estimatedMinutes, 1);
            int estimatedSeconds = estimatedMinutes * 60 + random.nextInt(0, 59);

            int stationsAway = Math.min(estimatedMinutes / 3, Math.max(targetOrder, 1));
            stationsAway = Math.max(stationsAway, i + 1);

            int crowdLevel = 1 + random.nextInt(3);

            vehicles.add(EtaResponse.EtaVehicle.builder()
                    .vehicleId("SIM_" + line.getLineCode() + "_" + (i + 1))
                    .licensePlate("沪A" + String.format("%05d", 10000 + random.nextInt(89999)))
                    .estimatedMinutes(estimatedMinutes)
                    .estimatedSeconds(estimatedSeconds)
                    .distanceStationsAway(stationsAway)
                    .distanceMeters(stationsAway * 800.0 + random.nextDouble() * 400)
                    .crowdLevel(crowdLevel)
                    .crowdText(crowdLevelToText(crowdLevel))
                    .currentSpeed(4.0 + random.nextDouble() * 6.0)
                    .build());
        }

        return EtaResponse.builder()
                .lineCode(line.getLineCode())
                .lineName(line.getLineName())
                .stationName(targetStation.getStationName())
                .direction(direction)
                .queryTime(System.currentTimeMillis())
                .fromCache(false)
                .vehicles(vehicles)
                .build();
    }

    private EtaResponse buildEmptyResponse(String lineCode, String stationName, String direction, String message) {
        log.warn("ETA query failed: {}, line={}, station={}, direction={}", message, lineCode, stationName, direction);
        return EtaResponse.builder()
                .lineCode(lineCode)
                .stationName(stationName)
                .direction(direction)
                .queryTime(System.currentTimeMillis())
                .fromCache(false)
                .vehicles(new ArrayList<>())
                .build();
    }

    private LineEntity findLineByCode(String lineCode) {
        if (lineCode == null || lineCode.isEmpty()) return null;

        List<LineEntity> lines = lineMapper.selectList(
                new LambdaQueryWrapper<LineEntity>()
                        .eq(LineEntity::getStatus, 1)
        );

        for (LineEntity line : lines) {
            if (lineCode.equals(line.getLineCode())) {
                return line;
            }
        }

        for (LineEntity line : lines) {
            if (line.getLineName() != null && line.getLineName().contains(lineCode)) {
                return line;
            }
        }

        String numericCode = lineCode.replaceAll("[^0-9]", "");
        if (!numericCode.isEmpty()) {
            for (LineEntity line : lines) {
                if (line.getLineCode() != null && line.getLineCode().contains(numericCode)) {
                    return line;
                }
            }
        }

        return null;
    }

    private StationEntity findStationByName(String stationName) {
        if (stationName == null || stationName.isEmpty()) return null;

        List<StationEntity> stations = stationMapper.selectList(null);

        for (StationEntity s : stations) {
            if (stationName.equals(s.getStationName())) {
                return s;
            }
        }

        for (StationEntity s : stations) {
            if (s.getStationName() != null && s.getStationName().contains(stationName)) {
                return s;
            }
        }

        return null;
    }

    private int estimateCurrentStationOrder(VehiclePosition vp, List<LineStationEntity> lineStations) {
        int nearestOrder = -1;
        double minDist = Double.MAX_VALUE;

        Map<String, LineStationEntity> stationMap = new HashMap<>();
        for (LineStationEntity ls : lineStations) {
            stationMap.put(ls.getStationId(), ls);
        }

        List<StationEntity> stationEntities = stationMapper.selectBatchIds(
                lineStations.stream().map(LineStationEntity::getStationId).collect(Collectors.toList())
        );
        Map<String, StationEntity> stationInfoMap = new HashMap<>();
        for (StationEntity s : stationEntities) {
            stationInfoMap.put(s.getStationId(), s);
        }

        for (LineStationEntity ls : lineStations) {
            StationEntity se = stationInfoMap.get(ls.getStationId());
            if (se == null || se.getLatitude() == null || se.getLongitude() == null) continue;

            double dist = GeoHashUtil.haversineDistance(
                    vp.getLatitude(), vp.getLongitude(),
                    se.getLatitude(), se.getLongitude()
            );
            if (dist < minDist) {
                minDist = dist;
                nearestOrder = ls.getStationOrder();
            }
        }

        return nearestOrder;
    }

    private double calculateDistance(List<LineStationEntity> lineStations, int fromOrder,
                                      VehiclePosition fromPos, StationEntity toStation, int toOrder) {
        double total = 0;

        Map<String, StationEntity> stationInfoMap = new HashMap<>();
        List<StationEntity> allStations = stationMapper.selectBatchIds(
                lineStations.stream().map(LineStationEntity::getStationId).collect(Collectors.toList())
        );
        for (StationEntity s : allStations) {
            stationInfoMap.put(s.getStationId(), s);
        }

        LineStationEntity fromLs = lineStations.stream()
                .filter(ls -> ls.getStationOrder().equals(fromOrder)).findFirst().orElse(null);
        if (fromLs != null) {
            StationEntity fromStation = stationInfoMap.get(fromLs.getStationId());
            if (fromStation != null && fromStation.getLatitude() != null) {
                total += GeoHashUtil.haversineDistance(
                        fromPos.getLatitude(), fromPos.getLongitude(),
                        fromStation.getLatitude(), fromStation.getLongitude()
                );
            }
        }

        for (int i = fromOrder; i < toOrder; i++) {
            LineStationEntity curr = lineStations.stream()
                    .filter(ls -> ls.getStationOrder().equals(i)).findFirst().orElse(null);
            LineStationEntity next = lineStations.stream()
                    .filter(ls -> ls.getStationOrder().equals(i + 1)).findFirst().orElse(null);

            if (curr != null && next != null) {
                if (curr.getDistanceToNext() != null) {
                    total += curr.getDistanceToNext();
                } else {
                    StationEntity currS = stationInfoMap.get(curr.getStationId());
                    StationEntity nextS = stationInfoMap.get(next.getStationId());
                    if (currS != null && nextS != null && currS.getLatitude() != null && nextS.getLatitude() != null) {
                        total += GeoHashUtil.haversineDistance(
                                currS.getLatitude(), currS.getLongitude(),
                                nextS.getLatitude(), nextS.getLongitude()
                        );
                    } else {
                        total += 800;
                    }
                }
            }
        }

        return total;
    }

    private int generateCrowdLevel(String vehicleId) {
        try {
            int hash = Math.abs(vehicleId.hashCode());
            int base = (hash % 3) + 1;
            int hour = java.time.LocalTime.now().getHour();
            boolean rushHour = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
            if (rushHour) {
                base = Math.min(base + 1, 3);
            }
            return base;
        } catch (Exception e) {
            return 2;
        }
    }

    private String crowdLevelToText(int level) {
        switch (level) {
            case 1: return "空";
            case 2: return "适中";
            case 3: return "拥挤";
            default: return "适中";
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EtaVehicleCandidate {
        private String vehicleId;
        private String licensePlate;
        private int estimatedSeconds;
        private int distanceStationsAway;
        private double distanceMeters;
        private int crowdLevel;
        private double currentSpeed;
    }
}
