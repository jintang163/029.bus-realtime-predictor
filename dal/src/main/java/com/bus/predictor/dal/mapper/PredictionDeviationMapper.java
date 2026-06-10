package com.bus.predictor.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.dal.entity.PredictionDeviationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PredictionDeviationMapper extends BaseMapper<PredictionDeviationEntity> {

    @Select("SELECT DATE(arrival_time) as stat_date, " +
            "COUNT(*) as total_count, " +
            "SUM(is_accurate) as accurate_count, " +
            "AVG(deviation_rate) as avg_deviation_rate, " +
            "AVG(ABS(deviation_seconds)) as avg_deviation_seconds " +
            "FROM t_prediction_deviation " +
            "WHERE arrival_time >= #{startTime} " +
            "GROUP BY DATE(arrival_time) " +
            "ORDER BY stat_date ASC")
    List<Map<String, Object>> findDailyAccuracyTrend(@Param("startTime") String startTime);

    @Select("SELECT hour_of_day, " +
            "COUNT(*) as total_count, " +
            "SUM(is_accurate) as accurate_count, " +
            "AVG(deviation_rate) as avg_deviation_rate " +
            "FROM t_prediction_deviation " +
            "WHERE arrival_time >= #{startTime} " +
            "GROUP BY hour_of_day " +
            "ORDER BY hour_of_day ASC")
    List<Map<String, Object>> findHourlyAccuracy(@Param("startTime") String startTime);

    @Select("SELECT segment_id, " +
            "COUNT(*) as total_count, " +
            "SUM(is_accurate) as accurate_count, " +
            "AVG(deviation_rate) as avg_deviation_rate, " +
            "AVG(actual_speed) as avg_actual_speed " +
            "FROM t_prediction_deviation " +
            "WHERE arrival_time >= #{startTime} " +
            "GROUP BY segment_id " +
            "ORDER BY avg_deviation_rate DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> findSegmentDeviationRanking(@Param("startTime") String startTime,
                                                           @Param("limit") int limit);

    @Select("SELECT route_id, " +
            "COUNT(*) as total_count, " +
            "SUM(is_accurate) as accurate_count, " +
            "AVG(deviation_rate) as avg_deviation_rate " +
            "FROM t_prediction_deviation " +
            "WHERE arrival_time >= #{startTime} " +
            "GROUP BY route_id " +
            "ORDER BY route_id ASC")
    List<Map<String, Object>> findRouteAccuracy(@Param("startTime") String startTime);
}
