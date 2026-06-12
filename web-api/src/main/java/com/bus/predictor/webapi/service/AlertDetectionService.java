package com.bus.predictor.webapi.service;

import com.bus.predictor.dal.entity.AlertRecordEntity;
import com.bus.predictor.dal.entity.AlertRuleEntity;
import com.bus.predictor.dal.mapper.AlertRecordMapper;
import com.bus.predictor.dal.mapper.AlertRuleMapper;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import com.bus.predictor.traffic.model.RoadSegmentManager;
import com.bus.predictor.webapi.notification.DingTalkNotificationService;
import com.bus.predictor.webapi.notification.SmsNotificationService;
import com.bus.predictor.webapi.websocket.AlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AlertDetectionService.class);

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final DingTalkNotificationService dingTalkService;
    private final SmsNotificationService smsService;
    private final AlertWebSocketHandler alertWebSocketHandler;

    private final Map<String, Long> triggerStartTime = new ConcurrentHashMap<>();
    private final Set<String> activeAlertKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000;

    public AlertDetectionService(AlertRuleMapper alertRuleMapper,
                                 AlertRecordMapper alertRecordMapper,
                                 VehiclePositionRedisDao vehiclePositionRedisDao,
                                 RoadSegmentManager roadSegmentManager,
                                 DingTalkNotificationService dingTalkService,
                                 SmsNotificationService smsService,
                                 AlertWebSocketHandler alertWebSocketHandler) {
        this.alertRuleMapper = alertRuleMapper;
        this.alertRecordMapper = alertRecordMapper;
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.dingTalkService = dingTalkService;
        this.smsService = smsService;
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Scheduled(fixedRate = 10000)
    public void detectAlerts() {
        List<AlertRuleEntity> rules = alertRuleMapper.selectEnabledRules();
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (AlertRuleEntity rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.error("Failed to check alert rule {}: {}", rule.getRuleName(), e.getMessage(), e);
            }
        }
    }

    private void checkRule(AlertRuleEntity rule) {
        String ruleType = rule.getRuleType();
        switch (ruleType) {
            case "PREDICTION_DEVIATION":
                checkPredictionDeviation(rule);
                break;
            case "VEHICLE_OFFLINE":
                checkVehicleOffline(rule);
                break;
            case "CONGESTION":
                checkCongestion(rule);
                break;
            case "API_RESPONSE":
                checkApiResponse(rule);
                break;
            case "ONLINE_RATE":
                checkOnlineRate(rule);
                break;
            default:
                log.warn("Unknown alert rule type: {}", ruleType);
        }
    }

    private void checkPredictionDeviation(AlertRuleEntity rule) {
        List<Map<String, Object>> segments = roadSegmentManager.getAllSegmentsWithSpeed();
        if (segments == null) return;

        for (Map<String, Object> seg : segments) {
            String segmentId = (String) seg.get("segmentId");
            String lineId = (String) seg.get("lineId");
            Object deviationObj = seg.get("avgDeviationSeconds");
            if (deviationObj == null) continue;

            double deviation = ((Number) deviationObj).doubleValue();
            String key = "PREDICTION_DEVIATION:" + lineId + ":" + segmentId;

            if (evaluateCondition(deviation, rule.getThreshold(), rule.getOperator())) {
                processTrigger(rule, key, segmentId, lineId + " 路段 " + segmentId,
                        deviation, "线路预测偏差 " + (deviation / 60) + " 分钟，超过阈值 " + (rule.getThreshold() / 60) + " 分钟");
            } else {
                clearTrigger(key, rule);
            }
        }
    }

    private void checkVehicleOffline(AlertRuleEntity rule) {
        Set<String> vehicleIds = vehiclePositionRedisDao.getOnlineVehicleIds();
        if (vehicleIds == null) return;

        long now = System.currentTimeMillis();
        for (String vehicleId : vehicleIds) {
            try {
                var position = vehiclePositionRedisDao.getPosition(vehicleId);
                if (position == null || position.getGpsTime() == null) continue;

                long offlineMs = now - position.getGpsTime();
                double offlineSeconds = offlineMs / 1000.0;
                String key = "VEHICLE_OFFLINE:" + vehicleId;

                if (evaluateCondition(offlineSeconds, rule.getThreshold(), rule.getOperator())) {
                    processTrigger(rule, key, vehicleId, "车辆 " + vehicleId,
                            offlineSeconds, "车辆 " + vehicleId + " 已离线 " + (offlineSeconds / 60) + " 分钟");
                } else {
                    clearTrigger(key, rule);
                }
            } catch (Exception e) {
                log.warn("Check vehicle {} offline failed: {}", vehicleId, e.getMessage());
            }
        }
    }

    private void checkCongestion(AlertRuleEntity rule) {
        List<Map<String, Object>> segments = roadSegmentManager.getAllSegmentsWithSpeed();
        if (segments == null) return;

        for (Map<String, Object> seg : segments) {
            String segmentId = (String) seg.get("segmentId");
            Object congestionObj = seg.get("congestionFactor");
            if (congestionObj == null) continue;

            double congestion = ((Number) congestionObj).doubleValue();
            String key = "CONGESTION:" + segmentId;
            String segName = seg.get("startStationName") + " → " + seg.get("endStationName");

            if (evaluateCondition(congestion, rule.getThreshold(), rule.getOperator())) {
                processTrigger(rule, key, segmentId, segName,
                        congestion, "路段 " + segName + " 拥堵系数 " + String.format("%.2f", congestion) + "，超过阈值 " + rule.getThreshold());
            } else {
                clearTrigger(key, rule);
            }
        }
    }

    private void checkApiResponse(AlertRuleEntity rule) {
        String key = "API_RESPONSE:GLOBAL";
        double avgResponseMs = getAverageApiResponseTime();

        if (evaluateCondition(avgResponseMs, rule.getThreshold(), rule.getOperator())) {
            processTrigger(rule, key, "GLOBAL", "API服务",
                    avgResponseMs, "API平均响应时间 " + avgResponseMs + "ms，超过阈值 " + rule.getThreshold() + "ms");
        } else {
            clearTrigger(key, rule);
        }
    }

    private void checkOnlineRate(AlertRuleEntity rule) {
        String key = "ONLINE_RATE:SYSTEM";
        double onlineRate = calculateOnlineRate();

        if (evaluateCondition(onlineRate, rule.getThreshold(), rule.getOperator())) {
            processTrigger(rule, key, "SYSTEM", "系统设备",
                    onlineRate, "设备在线率 " + String.format("%.1f", onlineRate) + "%，低于阈值 " + rule.getThreshold() + "%");
        } else {
            clearTrigger(key, rule);
        }
    }

    private double getAverageApiResponseTime() {
        return Math.random() * 500 + 100;
    }

    private double calculateOnlineRate() {
        Set<String> onlineIds = vehiclePositionRedisDao.getOnlineVehicleIds();
        int onlineCount = onlineIds != null ? onlineIds.size() : 0;
        int totalCount = 50;
        return totalCount > 0 ? (onlineCount * 100.0 / totalCount) : 100.0;
    }

    private boolean evaluateCondition(double value, double threshold, String operator) {
        switch (operator) {
            case ">":
                return value > threshold;
            case ">=":
                return value >= threshold;
            case "<":
                return value < threshold;
            case "<=":
                return value <= threshold;
            case "==":
                return value == threshold;
            case "!=":
                return value != threshold;
            default:
                return false;
        }
    }

    private void processTrigger(AlertRuleEntity rule, String key, String targetId, String targetName,
                                double alertValue, String message) {
        long now = System.currentTimeMillis();
        Long startTime = triggerStartTime.get(key);

        if (startTime == null) {
            triggerStartTime.put(key, now);
            return;
        }

        long durationMs = now - startTime;
        int ruleDurationMs = (rule.getDuration() != null ? rule.getDuration() : 60) * 1000;

        if (durationMs >= ruleDurationMs && !activeAlertKeys.contains(key)) {
            String level = determineAlertLevel(alertValue, rule.getThreshold(), rule.getOperator());

            AlertRecordEntity alert = AlertRecordEntity.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getRuleName())
                    .alertType(rule.getRuleType())
                    .alertLevel(level)
                    .targetId(targetId)
                    .targetName(targetName)
                    .alertValue(alertValue)
                    .threshold(rule.getThreshold())
                    .operator(rule.getOperator())
                    .message(message)
                    .status("ACTIVE")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            alertRecordMapper.insert(alert);
            activeAlertKeys.add(key);

            alertWebSocketHandler.broadcastAlert(alert);

            sendNotification(rule, alert);
        }
    }

    private String determineAlertLevel(double value, double threshold, String operator) {
        double ratio = operator.contains(">") ? value / threshold : threshold / value;
        if (ratio >= 2.0) return "CRITICAL";
        if (ratio >= 1.5) return "WARNING";
        return "INFO";
    }

    private void sendNotification(AlertRuleEntity rule, AlertRecordEntity alert) {
        String notificationType = rule.getNotificationType();
        String notificationTarget = rule.getNotificationTarget();
        String key = "NOTIFY:" + rule.getId() + ":" + alert.getTargetId();

        long now = System.currentTimeMillis();
        Long lastNotify = lastNotificationTime.get(key);
        if (lastNotify != null && now - lastNotify < NOTIFICATION_COOLDOWN_MS) {
            return;
        }
        lastNotificationTime.put(key, now);

        String title = "【告警】" + alert.getRuleName();
        String content = buildNotificationContent(alert);

        if ("DINGTALK".equals(notificationType) || "ALL".equals(notificationType)) {
            dingTalkService.sendAlert(title, content, alert.getAlertLevel());
        }
        if ("SMS".equals(notificationType) || "ALL".equals(notificationType)) {
            if (notificationTarget != null && !notificationTarget.isEmpty()) {
                smsService.sendAlert(notificationTarget, content, alert.getAlertLevel());
            }
        }
    }

    private String buildNotificationContent(AlertRecordEntity alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("告警级别: ").append(alert.getAlertLevel()).append("\n");
        sb.append("告警类型: ").append(alert.getAlertType()).append("\n");
        sb.append("目标: ").append(alert.getTargetName()).append("\n");
        sb.append("告警值: ").append(String.format("%.2f", alert.getAlertValue())).append("\n");
        sb.append("阈值: ").append(alert.getThreshold()).append(" (").append(alert.getOperator()).append(")\n");
        sb.append("描述: ").append(alert.getMessage()).append("\n");
        sb.append("时间: ").append(alert.getCreateTime()).append("\n");
        return sb.toString();
    }

    private void clearTrigger(String key, AlertRuleEntity rule) {
        triggerStartTime.remove(key);
        if (activeAlertKeys.remove(key)) {
            List<AlertRecordEntity> records = alertRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AlertRecordEntity>()
                            .eq("target_id", key.substring(key.indexOf(':') + 1))
                            .eq("rule_id", rule.getId())
                            .eq("status", "ACTIVE")
                            .orderByDesc("create_time")
                            .last("LIMIT 1")
            );
            if (records != null && !records.isEmpty()) {
                AlertRecordEntity record = records.get(0);
                record.setStatus("RESOLVED");
                record.setResolvedTime(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                alertRecordMapper.updateById(record);
                log.info("Alert resolved: {} - {}", record.getRuleName(), record.getTargetName());
            }
        }
    }

    public List<AlertRecordEntity> getActiveAlerts(int limit) {
        return alertRecordMapper.selectActiveAlerts(limit);
    }

    public Map<String, Object> getAlertStats() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> stats = alertRecordMapper.selectAlertStats24h();
        List<Map<String, Object>> trend = alertRecordMapper.selectAlertTrend24h();

        int criticalCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        if (stats != null) {
            for (Map<String, Object> row : stats) {
                String level = (String) row.get("alert_level");
                int count = ((Number) row.get("count")).intValue();
                if ("CRITICAL".equals(level)) criticalCount += count;
                else if ("WARNING".equals(level)) warningCount += count;
                else if ("INFO".equals(level)) infoCount += count;
            }
        }

        result.put("criticalCount", criticalCount);
        result.put("warningCount", warningCount);
        result.put("infoCount", infoCount);
        result.put("totalCount", criticalCount + warningCount + infoCount);
        result.put("activeCount", activeAlertKeys.size());
        result.put("trend", trend);
        return result;
    }
}
