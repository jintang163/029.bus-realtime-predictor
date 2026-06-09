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
@TableName("t_line_station")
public class LineStationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lineId;

    private String stationId;

    private Integer stationOrder;

    private Double distanceFromStart;

    private Double distanceToNext;

    private Integer estimatedSeconds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
