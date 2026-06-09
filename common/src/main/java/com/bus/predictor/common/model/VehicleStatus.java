package com.bus.predictor.common.model;

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

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static VehicleStatus fromCode(int code) {
        for (VehicleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return OFFLINE;
    }
}
