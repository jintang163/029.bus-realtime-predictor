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
@TableName("t_alert_rule")
public class AlertRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private String ruleType;

    private String targetType;

    private String targetValue;

    private Double threshold;

    private String operator;

    private Integer duration;

    private String notificationType;

    private String notificationTarget;

    private Integer enabled;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
