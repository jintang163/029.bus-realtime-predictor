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
@TableName("t_route_station")
public class RouteStationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String routeId;

    private String stationId;

    private String stationName;

    private Integer stationOrder;

    private Double longitude;

    private Double latitude;

    private Double distanceToNext;

    private LocalDateTime createTime;
}
