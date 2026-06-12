package com.bus.predictor.webapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bus.predictor.dal.entity.AlertRecordEntity;
import com.bus.predictor.dal.entity.AlertRuleEntity;
import com.bus.predictor.dal.mapper.AlertRecordMapper;
import com.bus.predictor.dal.mapper.AlertRuleMapper;
import com.bus.predictor.webapi.controller.Result;
import com.bus.predictor.webapi.service.AlertDetectionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final AlertDetectionService alertDetectionService;

    public AlertController(AlertRuleMapper alertRuleMapper,
                           AlertRecordMapper alertRecordMapper,
                           AlertDetectionService alertDetectionService) {
        this.alertRuleMapper = alertRuleMapper;
        this.alertRecordMapper = alertRecordMapper;
        this.alertDetectionService = alertDetectionService;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getAlertStats() {
        return Result.success(alertDetectionService.getAlertStats());
    }

    @GetMapping("/active")
    public Result<List<AlertRecordEntity>> getActiveAlerts(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(alertDetectionService.getActiveAlerts(limit));
    }

    @GetMapping("/rule/list")
    public Result<List<AlertRuleEntity>> getRuleList() {
        return Result.success(alertRuleMapper.selectList(null));
    }

    @GetMapping("/rule/{id}")
    public Result<AlertRuleEntity> getRuleById(@PathVariable Long id) {
        return Result.success(alertRuleMapper.selectById(id));
    }

    @PostMapping("/rule")
    public Result<AlertRuleEntity> createRule(@RequestBody AlertRuleEntity rule) {
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        alertRuleMapper.insert(rule);
        return Result.success(rule);
    }

    @PutMapping("/rule")
    public Result<AlertRuleEntity> updateRule(@RequestBody AlertRuleEntity rule) {
        rule.setUpdateTime(LocalDateTime.now());
        alertRuleMapper.updateById(rule);
        return Result.success(rule);
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertRuleMapper.deleteById(id);
        return Result.success(null);
    }

    @PutMapping("/rule/{id}/toggle")
    public Result<AlertRuleEntity> toggleRule(@PathVariable Long id) {
        AlertRuleEntity rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            return Result.fail(404, "Rule not found");
        }
        rule.setEnabled(rule.getEnabled() == 1 ? 0 : 1);
        rule.setUpdateTime(LocalDateTime.now());
        alertRuleMapper.updateById(rule);
        return Result.success(rule);
    }

    @GetMapping("/record/list")
    public Result<Page<AlertRecordEntity>> getRecordList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status) {

        QueryWrapper<AlertRecordEntity> wrapper = new QueryWrapper<>();
        if (alertType != null && !alertType.isEmpty()) {
            wrapper.eq("alert_type", alertType);
        }
        if (alertLevel != null && !alertLevel.isEmpty()) {
            wrapper.eq("alert_level", alertLevel);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");

        Page<AlertRecordEntity> pageResult = alertRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(pageResult);
    }

    @PutMapping("/record/{id}/acknowledge")
    public Result<AlertRecordEntity> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam(required = false) String operator) {
        AlertRecordEntity record = alertRecordMapper.selectById(id);
        if (record == null) {
            return Result.fail(404, "Alert record not found");
        }
        record.setStatus("ACKNOWLEDGED");
        record.setAcknowledgedBy(operator != null ? operator : "system");
        record.setAcknowledgedTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        alertRecordMapper.updateById(record);
        return Result.success(record);
    }

    @PutMapping("/record/{id}/resolve")
    public Result<AlertRecordEntity> resolveAlert(@PathVariable Long id) {
        AlertRecordEntity record = alertRecordMapper.selectById(id);
        if (record == null) {
            return Result.fail(404, "Alert record not found");
        }
        record.setStatus("RESOLVED");
        record.setResolvedTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        alertRecordMapper.updateById(record);
        return Result.success(record);
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getAlertOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("stats", alertDetectionService.getAlertStats());
        result.put("activeAlerts", alertDetectionService.getActiveAlerts(10));
        result.put("rules", alertRuleMapper.selectEnabledRules());
        return Result.success(result);
    }
}
