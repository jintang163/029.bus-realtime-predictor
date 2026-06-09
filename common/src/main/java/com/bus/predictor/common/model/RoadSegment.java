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
public class RoadSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String segmentId;

    private String startNode;

    private String endNode;

    private Double startLng;

    private Double startLat;

    private Double endLng;

    private Double endLat;

    private Double length;

    private Double freeFlowSpeed;

    private Double currentSpeed;

    private Double congestionFactor;

    private Long updateTime;
}
