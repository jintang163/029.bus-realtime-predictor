package com.bus.predictor.common.constant;

public interface ProtocolConstant {

    int HEADER_LENGTH = 4;

    short MSG_TYPE_GPS = 0x01;
    short MSG_TYPE_HEARTBEAT = 0x02;
    short MSG_TYPE_AUTH = 0x03;
    short MSG_TYPE_BATCH_GPS = 0x04;

    int MAX_FRAME_LENGTH = 1024 * 64;
}
