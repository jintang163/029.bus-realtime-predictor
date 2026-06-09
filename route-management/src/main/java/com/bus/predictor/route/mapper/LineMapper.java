package com.bus.predictor.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bus.predictor.route.entity.LineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LineMapper extends BaseMapper<LineEntity> {

    @Select("SELECT * FROM t_line WHERE status = 1 ORDER BY line_code")
    List<LineEntity> findAllActive();
}
