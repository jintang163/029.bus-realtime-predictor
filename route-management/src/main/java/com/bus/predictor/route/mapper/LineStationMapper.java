package com.bus.predictor.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.route.entity.LineStationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LineStationMapper extends BaseMapper<LineStationEntity> {

    @Select("SELECT ls.*, s.station_name, s.longitude, s.latitude " +
            "FROM t_line_station ls " +
            "LEFT JOIN t_station s ON ls.station_id = s.station_id " +
            "WHERE ls.line_id = #{lineId} " +
            "ORDER BY ls.station_order")
    List<LineStationEntity> findByLineId(@Param("lineId") String lineId);
}
