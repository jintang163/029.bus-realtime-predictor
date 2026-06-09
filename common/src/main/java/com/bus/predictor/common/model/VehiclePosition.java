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
public class VehiclePosition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String vehicleId;

    private Double longitude;

    private Double latitude;

    private Double speed;

    private Double direction;

    private String geoHash;

    private Long gpsTime;

    private Long receiveTime;

    private VehicleStatus status;
}
