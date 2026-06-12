package com.bus.predictor.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.dal.entity.AlertRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRuleEntity> {

    @Select("SELECT * FROM t_alert_rule WHERE enabled = 1")
    List<AlertRuleEntity> selectEnabledRules();
}
