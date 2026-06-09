package com.bus.predictor.route.service;

import com.bus.predictor.route.entity.StationEntity;
import com.bus.predictor.route.mapper.StationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationService {

    private final StationMapper stationMapper;

    public StationService(StationMapper stationMapper) {
        this.stationMapper = stationMapper;
    }

    public List<StationEntity> listAll() {
        return stationMapper.selectList(null);
    }

    public StationEntity getById(String stationId) {
        return stationMapper.selectById(stationId);
    }

    public StationEntity create(StationEntity entity) {
        stationMapper.insert(entity);
        return entity;
    }

    public StationEntity update(StationEntity entity) {
        stationMapper.updateById(entity);
        return entity;
    }

    public void delete(String stationId) {
        stationMapper.deleteById(stationId);
    }

    public List<StationEntity> findNearby(double longitude, double latitude, double radiusMeters, int limit) {
        return stationMapper.findNearbyStations(longitude, latitude, radiusMeters, limit);
    }
}
