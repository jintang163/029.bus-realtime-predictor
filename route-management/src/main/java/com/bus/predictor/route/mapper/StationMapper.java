package com.bus.predictor.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.route.entity.StationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StationMapper extends BaseMapper<StationEntity> {

    @Select("SELECT s.*, ST_Distance_Sphere(POINT(s.longitude, s.latitude), POINT(#{lng}, #{lat})) AS distance_m " +
            "FROM t_station s " +
            "WHERE ST_Distance_Sphere(POINT(s.longitude, s.latitude), POINT(#{lng}, #{lat})) <= #{radius} " +
            "ORDER BY distance_m " +
            "LIMIT #{limit}")
    List<StationEntity> findNearbyStations(@Param("lng") double longitude,
                                            @Param("lat") double latitude,
                                            @Param("radius") double radiusMeters,
                                            @Param("limit") int limit);
}
