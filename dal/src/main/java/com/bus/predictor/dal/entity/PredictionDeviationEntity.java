package com.bus.predictor.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_prediction_deviation")
public class PredictionDeviationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vehicleId;

    private String routeId;

    private String segmentId;

    private String stationId;

    private Integer predictedSeconds;

    private Integer actualSeconds;

    private Integer deviationSeconds;

    private Double deviationRate;

    private Double predictedSpeed;

    private Double actualSpeed;

    private LocalDateTime predictTime;

    private LocalDateTime arrivalTime;

    private Integer hourOfDay;

    private Integer dayOfWeek;

    private Integer isAccurate;

    private LocalDateTime createTime;
}
