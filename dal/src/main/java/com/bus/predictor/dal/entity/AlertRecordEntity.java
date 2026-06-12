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
@TableName("t_alert_record")
public class AlertRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String ruleName;

    private String alertType;

    private String alertLevel;

    private String targetId;

    private String targetName;

    private Double alertValue;

    private Double threshold;

    private String operator;

    private String message;

    private String status;

    private String acknowledgedBy;

    private LocalDateTime acknowledgedTime;

    private LocalDateTime resolvedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
