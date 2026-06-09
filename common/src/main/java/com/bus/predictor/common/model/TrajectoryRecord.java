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
public class TrajectoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String vehicleId;

    private Double longitude;

    private Double latitude;

    private Double speed;

    private Double direction;

    private Long gpsTime;

    private Long createTime;
}
