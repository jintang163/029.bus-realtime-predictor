package com.bus.predictor.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.dal.entity.SegmentBaselineSpeedEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SegmentBaselineSpeedMapper extends BaseMapper<SegmentBaselineSpeedEntity> {

    @Select("SELECT segment_id, day_of_week, hour_of_day, baseline_speed, baseline_congestion, sample_count, train_time " +
            "FROM t_segment_baseline_speed " +
            "WHERE segment_id = #{segmentId} " +
            "AND day_of_week = #{dayOfWeek} " +
            "AND hour_of_day = #{hourOfDay}")
    Map<String, Object> findBySegmentAndDayHour(@Param("segmentId") String segmentId,
                                                 @Param("dayOfWeek") int dayOfWeek,
                                                 @Param("hourOfDay") int hourOfDay);

    @Select("SELECT segment_id, day_of_week, hour_of_day, baseline_speed, baseline_congestion, sample_count " +
            "FROM t_segment_baseline_speed " +
            "WHERE segment_id = #{segmentId} " +
            "ORDER BY day_of_week, hour_of_day")
    List<Map<String, Object>> findAllBySegmentId(@Param("segmentId") String segmentId);

    @Select("SELECT b.segment_id, b.day_of_week, b.hour_of_day, b.baseline_speed, b.baseline_congestion, b.sample_count, b.train_time " +
            "FROM t_segment_baseline_speed b " +
            "INNER JOIN (" +
            "    SELECT segment_id, MAX(train_time) as max_train_time " +
            "    FROM t_segment_baseline_speed " +
            "    GROUP BY segment_id" +
            ") latest ON b.segment_id = latest.segment_id AND b.train_time = latest.max_train_time")
    List<Map<String, Object>> findLatestBaselines();

    @Select("SELECT COUNT(*) as total_count, " +
            "MAX(train_time) as last_train_time, " +
            "COUNT(DISTINCT segment_id) as covered_segments " +
            "FROM t_segment_baseline_speed")
    Map<String, Object> getBaselineStats();
}
