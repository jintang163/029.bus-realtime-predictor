package com.bus.predictor.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VehicleStatus {

    ONLINE(1, "在线"),

    OFFLINE(0, "离线"),

    STOPPED(2, "停运"),

    GPS_LOST(3, "GPS信号丢失");

    private final int code;
    private final String desc;

    VehicleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static VehicleStatus fromCode(int code) {
        for (VehicleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return OFFLINE;
    }
}
