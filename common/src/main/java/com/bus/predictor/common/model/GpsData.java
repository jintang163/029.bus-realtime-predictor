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
public class GpsData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String vehicleId;

    private Double longitude;

    private Double latitude;

    private Double speed;

    private Double direction;

    private Long timestamp;

    private Integer satelliteCount;

    private Integer hdop;

    public boolean isValid() {
        return vehicleId != null && !vehicleId.isEmpty()
                && longitude != null && latitude != null
                && longitude >= -180.0 && longitude <= 180.0
                && latitude >= -90.0 && latitude <= 90.0
                && timestamp != null && timestamp > 0;
    }
}
