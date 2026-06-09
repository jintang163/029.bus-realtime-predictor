package com.bus.predictor.route.service;

import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.route.entity.LineEntity;
import com.bus.predictor.route.entity.LineStationEntity;
import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.mapper.LineMapper;
import com.bus.predictor.route.mapper.LineStationMapper;
import com.bus.predictor.route.mapper.StationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class LineService {

    private static final Logger log = LoggerFactory.getLogger(LineService.class);

    private final LineMapper lineMapper;
    private final LineStationMapper lineStationMapper;
    private final StationMapper stationMapper;
    private final StringRedisTemplate redisTemplate;

    public LineService(LineMapper lineMapper, LineStationMapper lineStationMapper,
                       StationMapper stationMapper, StringRedisTemplate redisTemplate) {
        this.lineMapper = lineMapper;
        this.lineStationMapper = lineStationMapper;
        this.stationMapper = stationMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<LineEntity> listAll() {
        return lineMapper.findAllActive();
    }

    public LineEntity getById(String lineId) {
        return lineMapper.selectById(lineId);
    }

    @Transactional
    public LineEntity create(LineEntity entity) {
        lineMapper.insert(entity);
        return entity;
    }

    @Transactional
    public LineEntity update(LineEntity entity) {
        lineMapper.updateById(entity);
        refreshLineCache(entity.getLineId());
        return entity;
    }

    @Transactional
    public void delete(String lineId) {
        lineMapper.deleteById(lineId);
        lineStationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LineStationEntity>()
                .eq("line_id", lineId));
        redisTemplate.delete("line:" + lineId + ":stations");
    }

    public List<LineStationEntity> getLineStations(String lineId) {
        String cacheKey = "line:" + lineId + ":stations";
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtil.fromJson(cached, List.class);
        }
        List<LineStationEntity> stations = lineStationMapper.findByLineId(lineId);
        if (!stations.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, JsonUtil.toJson(stations), 24, TimeUnit.HOURS);
        }
        return stations;
    }

    @Transactional
    public void saveLineStations(String lineId, List<LineStationEntity> stations) {
        lineStationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LineStationEntity>()
                .eq("line_id", lineId));

        for (LineStationEntity ls : stations) {
            ls.setLineId(lineId);
            lineStationMapper.insert(ls);
        }

        refreshLineCache(lineId);
    }

    public void refreshLineCache(String lineId) {
        List<LineStationEntity> stations = lineStationMapper.findByLineId(lineId);
        String cacheKey = "line:" + lineId + ":stations";
        if (!stations.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, JsonUtil.toJson(stations), 24, TimeUnit.HOURS);
        } else {
            redisTemplate.delete(cacheKey);
        }
        log.info("Refreshed line cache: {}, stations: {}", lineId, stations.size());
    }

    public void refreshAllLineCache() {
        List<LineEntity> lines = lineMapper.findAllActive();
        for (LineEntity line : lines) {
            refreshLineCache(line.getLineId());
        }
        log.info("Refreshed all line cache, total: {}", lines.size());
    }

    public List<Map<String, Object>> getLineStationsWithDetail(String lineId) {
        List<LineStationEntity> lsList = lineStationMapper.findByLineId(lineId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (LineStationEntity ls : lsList) {
            StationEntity station = stationMapper.selectById(ls.getStationId());
            Map<String, Object> item = new HashMap<>();
            item.put("lineId", ls.getLineId());
            item.put("stationId", ls.getStationId());
            item.put("stationOrder", ls.getStationOrder());
            item.put("distanceFromStart", ls.getDistanceFromStart());
            item.put("distanceToNext", ls.getDistanceToNext());
            item.put("estimatedSeconds", ls.getEstimatedSeconds());
            if (station != null) {
                item.put("stationName", station.getStationName());
                item.put("longitude", station.getLongitude());
                item.put("latitude", station.getLatitude());
                item.put("district", station.getDistrict());
            }
            result.add(item);
        }
        return result;
    }
}
