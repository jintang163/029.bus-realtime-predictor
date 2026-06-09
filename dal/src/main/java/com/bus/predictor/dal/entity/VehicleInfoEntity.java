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
@TableName("t_vehicle_info")
public class VehicleInfoEntity {

    @TableId(type = IdType.INPUT)
    private String vehicleId;

    private String plateNumber;

    private String routeId;

    private String driverName;

    private Integer status;

    private Double longitude;

    private Double latitude;

    private Double speed;

    private LocalDateTime lastGpsTime;

    private LocalDateTime lastOnlineTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
