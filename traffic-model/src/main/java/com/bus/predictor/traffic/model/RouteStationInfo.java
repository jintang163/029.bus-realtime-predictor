package com.bus.predictor.traffic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStationInfo {

    private String stationId;
    private String stationName;
    private int order;
    private double longitude;
    private double latitude;
}
