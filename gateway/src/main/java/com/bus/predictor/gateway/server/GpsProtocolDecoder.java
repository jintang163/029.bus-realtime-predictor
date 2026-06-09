package com.bus.predictor.gateway.server;

import com.bus.predictor.common.constant.ProtocolConstant;
import com.bus.predictor.common.model.GpsData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GpsProtocolDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(GpsProtocolDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 5) {
            return;
        }

        in.markReaderIndex();
        int msgLength = in.readInt();
        short msgType = in.readUnsignedByte();

        if (in.readableBytes() < msgLength - 1) {
            in.resetReaderIndex();
            return;
        }

        try {
            switch (msgType) {
                case ProtocolConstant.MSG_TYPE_GPS:
                    GpsData gps = decodeGpsMessage(in);
                    if (gps != null) {
                        out.add(gps);
                    }
                    break;
                case ProtocolConstant.MSG_TYPE_HEARTBEAT:
                    out.add(decodeHeartbeat(in));
                    break;
                case ProtocolConstant.MSG_TYPE_AUTH:
                    out.add(decodeAuth(in));
                    break;
                case ProtocolConstant.MSG_TYPE_BATCH_GPS:
                    out.add(decodeBatchGps(in));
                    break;
                default:
                    in.skipBytes(msgLength - 1);
                    log.warn("Unknown message type: {}", msgType);
            }
        } catch (Exception e) {
            log.error("Decode message failed, type={}", msgType, e);
            in.clear();
        }
    }

    private GpsData decodeGpsMessage(ByteBuf buf) {
        String vehicleId = readString(buf, 32);
        double longitude = buf.readDouble();
        double latitude = buf.readDouble();
        double speed = buf.readDouble();
        double direction = buf.readDouble();
        long timestamp = buf.readLong();
        int satelliteCount = buf.readUnsignedByte();
        int hdop = buf.readUnsignedByte();

        return GpsData.builder()
                .vehicleId(vehicleId.trim())
                .longitude(longitude)
                .latitude(latitude)
                .speed(speed)
                .direction(direction)
                .timestamp(timestamp)
                .satelliteCount(satelliteCount)
                .hdop(hdop)
                .build();
    }

    private List<GpsData> decodeBatchGps(ByteBuf buf) {
        int count = buf.readShort();
        List<GpsData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GpsData gps = decodeGpsMessage(buf);
            if (gps != null) {
                list.add(gps);
            }
        }
        return list;
    }

    private String decodeHeartbeat(ByteBuf buf) {
        return readString(buf, 32);
    }

    private String decodeAuth(ByteBuf buf) {
        return readString(buf, 32);
    }

    private String readString(ByteBuf buf, int maxLen) {
        int len = buf.readUnsignedByte();
        if (len > maxLen) {
            len = maxLen;
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
