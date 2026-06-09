package com.bus.predictor.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_schedule")
public class ScheduleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lineId;

    private String vehicleId;

    private String driverName;

    private LocalDate scheduleDate;

    private Integer departureTime;

    private Integer tripIndex;

    private Integer direction;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
