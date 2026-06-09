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
@TableName("t_station")
public class StationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String stationId;

    private String stationName;

    private String stationCode;

    private Double longitude;

    private Double latitude;

    private String district;

    private String street;

    private Integer stationType;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
