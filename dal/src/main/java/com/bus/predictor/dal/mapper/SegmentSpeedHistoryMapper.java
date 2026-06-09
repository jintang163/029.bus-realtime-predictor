package com.bus.predictor.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.dal.entity.SegmentSpeedHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SegmentSpeedHistoryMapper extends BaseMapper<SegmentSpeedHistoryEntity> {

    @Select("SELECT record_time, speed, congestion_factor, speed_source " +
            "FROM t_segment_speed_history " +
            "WHERE segment_id = #{segmentId} " +
            "AND record_time >= #{startTime} " +
            "ORDER BY record_time ASC")
    List<Map<String, Object>> findHistoryByTimeRange(@Param("segmentId") String segmentId,
                                                      @Param("startTime") String startTime);

    @Select("SELECT segment_id, AVG(speed) AS avg_speed, AVG(congestion_factor) AS avg_congestion " +
            "FROM t_segment_speed_history " +
            "WHERE segment_id = #{segmentId} " +
            "AND HOUR(record_time) = #{hour} " +
            "AND DAYOFWEEK(record_time) BETWEEN 2 AND 6 " +
            "GROUP BY segment_id")
    Map<String, Object> findWeekdayHourlyAverage(@Param("segmentId") String segmentId,
                                                   @Param("hour") int hour);
}
