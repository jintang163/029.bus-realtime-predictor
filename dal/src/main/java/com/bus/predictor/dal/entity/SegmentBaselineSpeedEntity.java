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
@TableName("t_segment_baseline_speed")
public class SegmentBaselineSpeedEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String segmentId;

    private String lineId;

    private Integer dayOfWeek;

    private Integer hourOfDay;

    private Double baselineSpeed;

    private Double baselineCongestion;

    private Integer sampleCount;

    private Double stdDev;

    private Integer speedSource;

    private LocalDateTime trainTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
