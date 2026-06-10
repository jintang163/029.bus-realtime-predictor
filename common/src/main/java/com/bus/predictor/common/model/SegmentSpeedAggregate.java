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
public class SegmentSpeedAggregate implements Serializable {

    private static final long serialVersionUID = 1L;

    private String segmentId;
    private String vehicleId;
    private double speed;
    private long enterTime;
    private long exitTime;
    private int hourOfDay;
    private int dayOfWeek;
}
