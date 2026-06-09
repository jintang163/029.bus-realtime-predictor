package com.bus.predictor.route.entity;

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
@TableName("t_line")
public class LineEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String lineId;

    private String lineName;

    private String lineCode;

    private Integer direction;

    private String startStation;

    private String endStation;

    private Double totalDistance;

    private Integer stationCount;

    private Integer firstBusTime;

    private Integer lastBusTime;

    private Integer intervalMinutes;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
