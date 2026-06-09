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
@TableName("t_road_segment")
public class RoadSegmentEntity {

    @TableId(type = IdType.INPUT)
    private String segmentId;

    private String startNode;

    private String endNode;

    private Double startLng;

    private Double startLat;

    private Double endLng;

    private Double endLat;

    private Double length;

    private Double freeFlowSpeed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
