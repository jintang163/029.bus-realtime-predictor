package com.bus.predictor.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArrivalPrediction implements Serializable {

    private static final long serialVersionUID = 1L;

    private String vehicleId;

    private String routeId;

    private String stationId;

    private String stationName;

    private Double distanceToStation;

    private Integer estimatedSeconds;

    private Double congestionFactor;

    private Double currentSpeed;

    private Long predictTime;

    private Long gpsTime;

    private Integer distanceStationsAway;

    private Integer crowdLevel;

    private String licensePlate;
}
