package com.bus.predictor.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String lineCode;

    private String lineName;

    private String stationName;

    private String direction;

    private Long queryTime;

    private Boolean fromCache;

    private List<EtaVehicle> vehicles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EtaVehicle implements Serializable {

        private static final long serialVersionUID = 1L;

        private String vehicleId;

        private String licensePlate;

        private Integer estimatedMinutes;

        private Integer estimatedSeconds;

        private Integer distanceStationsAway;

        private Double distanceMeters;

        private Integer crowdLevel;

        private String crowdText;

        private Double currentSpeed;
    }
}
