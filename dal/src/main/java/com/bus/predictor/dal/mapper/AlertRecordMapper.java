package com.bus.predictor.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.dal.entity.AlertRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecordEntity> {

    @Select("SELECT alert_type, alert_level, COUNT(*) as count FROM t_alert_record " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "GROUP BY alert_type, alert_level")
    List<Map<String, Object>> selectAlertStats24h();

    @Select("SELECT * FROM t_alert_record WHERE status = 'ACTIVE' ORDER BY create_time DESC LIMIT #{limit}")
    List<AlertRecordEntity> selectActiveAlerts(@Param("limit") int limit);

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:00') as hour, COUNT(*) as count " +
            "FROM t_alert_record WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d %H:00') " +
            "ORDER BY hour")
    List<Map<String, Object>> selectAlertTrend24h();
}
