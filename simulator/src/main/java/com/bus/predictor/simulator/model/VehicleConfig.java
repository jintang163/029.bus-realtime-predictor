package com.bus.predictor.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleConfig {

    private String vehicleId;

    private String plateNumber;

    private String routeId;

    private double startLongitude;

    private double startLatitude;

    private double endLongitude;

    private double endLatitude;

    private double avgSpeed;

    private int reportIntervalMs;

    private boolean simulateRoute;
}
