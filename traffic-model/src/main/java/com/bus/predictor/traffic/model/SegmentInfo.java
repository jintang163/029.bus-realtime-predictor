package com.bus.predictor.traffic.model;

import java.util.HashMap;
import java.util.Map;

public class SegmentInfo {

    private String segmentId;
    private String lineId;
    private String startStationId;
    private String startStationName;
    private String endStationId;
    private String endStationName;
    private int stationOrder;
    private double startLng;
    private double startLat;
    private double endLng;
    private double endLat;
    private double length;
    private double freeFlowSpeed;

    public SegmentInfo() {}

    public SegmentInfo(String segmentId, String lineId,
                       String startStationId, String startStationName,
                       String endStationId, String endStationName,
                       int stationOrder,
                       double startLng, double startLat,
                       double endLng, double endLat,
                       double length, double freeFlowSpeed) {
        this.segmentId = segmentId;
        this.lineId = lineId;
        this.startStationId = startStationId;
        this.startStationName = startStationName;
        this.endStationId = endStationId;
        this.endStationName = endStationName;
        this.stationOrder = stationOrder;
        this.startLng = startLng;
        this.startLat = startLat;
        this.endLng = endLng;
        this.endLat = endLat;
        this.length = length;
        this.freeFlowSpeed = freeFlowSpeed;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("segmentId", segmentId);
        map.put("lineId", lineId);
        map.put("startStationId", startStationId);
        map.put("startStationName", startStationName);
        map.put("endStationId", endStationId);
        map.put("endStationName", endStationName);
        map.put("stationOrder", stationOrder);
        map.put("startLng", startLng);
        map.put("startLat", startLat);
        map.put("endLng", endLng);
        map.put("endLat", endLat);
        map.put("length", length);
        map.put("freeFlowSpeed", freeFlowSpeed);
        return map;
    }

    public String getSegmentId() { return segmentId; }
    public void setSegmentId(String segmentId) { this.segmentId = segmentId; }
    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public String getStartStationId() { return startStationId; }
    public void setStartStationId(String startStationId) { this.startStationId = startStationId; }
    public String getStartStationName() { return startStationName; }
    public void setStartStationName(String startStationName) { this.startStationName = startStationName; }
    public String getEndStationId() { return endStationId; }
    public void setEndStationId(String endStationId) { this.endStationId = endStationId; }
    public String getEndStationName() { return endStationName; }
    public void setEndStationName(String endStationName) { this.endStationName = endStationName; }
    public int getStationOrder() { return stationOrder; }
    public void setStationOrder(int stationOrder) { this.stationOrder = stationOrder; }
    public double getStartLng() { return startLng; }
    public void setStartLng(double startLng) { this.startLng = startLng; }
    public double getStartLat() { return startLat; }
    public void setStartLat(double startLat) { this.startLat = startLat; }
    public double getEndLng() { return endLng; }
    public void setEndLng(double endLng) { this.endLng = endLng; }
    public double getEndLat() { return endLat; }
    public void setEndLat(double endLat) { this.endLat = endLat; }
    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }
    public double getFreeFlowSpeed() { return freeFlowSpeed; }
    public void setFreeFlowSpeed(double freeFlowSpeed) { this.freeFlowSpeed = freeFlowSpeed; }
}
