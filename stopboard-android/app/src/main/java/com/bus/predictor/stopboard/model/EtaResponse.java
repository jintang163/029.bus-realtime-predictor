package com.bus.predictor.stopboard.model;

import java.util.List;

public class EtaResponse {

    private String lineCode;
    private String lineName;
    private String stationName;
    private String direction;
    private long queryTime;
    private boolean fromCache;
    private List<EtaVehicle> vehicles;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public long getQueryTime() { return queryTime; }
    public void setQueryTime(long queryTime) { this.queryTime = queryTime; }

    public boolean isFromCache() { return fromCache; }
    public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }

    public List<EtaVehicle> getVehicles() { return vehicles; }
    public void setVehicles(List<EtaVehicle> vehicles) { this.vehicles = vehicles; }

    public static class EtaVehicle {
        private String vehicleId;
        private String licensePlate;
        private int estimatedMinutes;
        private int estimatedSeconds;
        private int distanceStationsAway;
        private double distanceMeters;
        private int crowdLevel;
        private String crowdText;
        private double currentSpeed;

        public String getVehicleId() { return vehicleId; }
        public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

        public String getLicensePlate() { return licensePlate; }
        public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

        public int getEstimatedMinutes() { return estimatedMinutes; }
        public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

        public int getEstimatedSeconds() { return estimatedSeconds; }
        public void setEstimatedSeconds(int estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }

        public int getDistanceStationsAway() { return distanceStationsAway; }
        public void setDistanceStationsAway(int distanceStationsAway) { this.distanceStationsAway = distanceStationsAway; }

        public double getDistanceMeters() { return distanceMeters; }
        public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }

        public int getCrowdLevel() { return crowdLevel; }
        public void setCrowdLevel(int crowdLevel) { this.crowdLevel = crowdLevel; }

        public String getCrowdText() { return crowdText; }
        public void setCrowdText(String crowdText) { this.crowdText = crowdText; }

        public double getCurrentSpeed() { return currentSpeed; }
        public void setCurrentSpeed(double currentSpeed) { this.currentSpeed = currentSpeed; }
    }
}
