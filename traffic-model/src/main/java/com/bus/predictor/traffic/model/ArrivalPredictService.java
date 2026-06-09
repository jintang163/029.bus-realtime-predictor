package com.bus.predictor.traffic.model;

import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArrivalPredictService {

    private static final double DEFAULT_SPEED_MS = 8.33;
    private static final double STOP_PENALTY_SECONDS = 30.0;

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final CongestionModel congestionModel;

    public ArrivalPredictService(VehiclePositionRedisDao vehiclePositionRedisDao,
                                 CongestionModel congestionModel) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.congestionModel = congestionModel;
    }

    public List<ArrivalPrediction> predict(String routeId, String vehicleId) {
        VehiclePosition position = vehiclePositionRedisDao.getPosition(vehicleId);
        if (position == null) {
            return new ArrayList<>();
        }

        List<RouteStationInfo> stations = getRouteStations(routeId);
        if (stations.isEmpty()) {
            return new ArrayList<>();
        }

        double currentLng = position.getLongitude();
        double currentLat = position.getLatitude();
        double currentSpeed = position.getSpeed() != null && position.getSpeed() > 0.5
                ? position.getSpeed() : DEFAULT_SPEED_MS;

        List<ArrivalPrediction> predictions = new ArrayList<>();
        double accumulatedTime = 0;

        double prevLng = currentLng;
        double prevLat = currentLat;

        for (RouteStationInfo station : stations) {
            double distance = GeoHashUtil.haversineDistance(
                    prevLat, prevLng, station.getLatitude(), station.getLongitude());

            double congestionFactor = congestionModel.calculateCongestion(
                    prevLat, prevLng, station.getLatitude(), station.getLongitude());

            double effectiveSpeed = currentSpeed / congestionFactor;
            if (effectiveSpeed < 1.0) {
                effectiveSpeed = 1.0;
            }

            double segmentTime = (distance / effectiveSpeed);
            accumulatedTime += segmentTime;

            if (station.getOrder() > 1) {
                accumulatedTime += STOP_PENALTY_SECONDS;
            }

            predictions.add(ArrivalPrediction.builder()
                    .vehicleId(vehicleId)
                    .routeId(routeId)
                    .stationId(station.getStationId())
                    .stationName(station.getStationName())
                    .distanceToStation(distance)
                    .estimatedSeconds((int) accumulatedTime)
                    .congestionFactor(congestionFactor)
                    .currentSpeed(currentSpeed)
                    .predictTime(System.currentTimeMillis() + (long) (accumulatedTime * 1000))
                    .gpsTime(position.getGpsTime())
                    .build());

            prevLng = station.getLongitude();
            prevLat = station.getLatitude();
        }

        return predictions;
    }

    private List<RouteStationInfo> getRouteStations(String routeId) {
        List<RouteStationInfo> stations = new ArrayList<>();

        stations.add(new RouteStationInfo("S001", "火车站", 1, 116.407526, 39.904030));
        stations.add(new RouteStationInfo("S002", "中山路", 2, 116.410526, 39.908030));
        stations.add(new RouteStationInfo("S003", "人民广场", 3, 116.415526, 39.912030));
        stations.add(new RouteStationInfo("S004", "市政府", 4, 116.420526, 39.916030));
        stations.add(new RouteStationInfo("S005", "科技园", 5, 116.425526, 39.920030));

        return stations;
    }
}
