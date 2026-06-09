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
@TableName("t_segment_speed_history")
public class SegmentSpeedHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String segmentId;

    private String lineId;

    private Double speed;

    private Double congestionFactor;

    private Integer speedSource;

    private LocalDateTime recordTime;

    private LocalDateTime createTime;
}
