package com.bus.predictor.gateway.server;

import com.bus.predictor.common.constant.KafkaTopicConstant;
import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.util.JsonUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable
public class GpsMessageHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(GpsMessageHandler.class);

    private static final ConcurrentHashMap<String, ChannelHandlerContext> VEHICLE_CHANNELS = new ConcurrentHashMap<>();

    private final KafkaProducerService kafkaProducerService;

    public GpsMessageHandler(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof GpsData) {
            handleGpsData((GpsData) msg, ctx);
        } else if (msg instanceof List) {
            @SuppressWarnings("unchecked")
            List<GpsData> batch = (List<GpsData>) msg;
            for (GpsData gps : batch) {
                handleGpsData(gps, ctx);
            }
        } else if (msg instanceof String) {
            handleAuthOrHeartbeat((String) msg, ctx);
        }
    }

    private void handleGpsData(GpsData gps, ChannelHandlerContext ctx) {
        if (!gps.isValid()) {
            log.warn("Invalid GPS data from channel={}, vehicle={}", ctx.channel().id(), gps.getVehicleId());
            return;
        }

        String vehicleId = gps.getVehicleId();
        VEHICLE_CHANNELS.put(vehicleId, ctx);

        String json = JsonUtil.toJson(gps);
        kafkaProducerService.send(KafkaTopicConstant.GPS_RAW_TOPIC, vehicleId, json);

        if (log.isDebugEnabled()) {
            log.debug("GPS data received: vehicle={}, lng={}, lat={}, speed={}",
                    vehicleId, gps.getLongitude(), gps.getLatitude(), gps.getSpeed());
        }
    }

    private void handleAuthOrHeartbeat(String vehicleId, ChannelHandlerContext ctx) {
        VEHICLE_CHANNELS.put(vehicleId.trim(), ctx);
        log.info("Auth/Heartbeat from vehicle={}", vehicleId.trim());

        ctx.writeAndFlush("OK\n");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.warn("Channel idle, closing: {}", ctx.channel().id());
            VEHICLE_CHANNELS.entrySet().removeIf(e -> e.getValue() == ctx);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        VEHICLE_CHANNELS.entrySet().removeIf(e -> e.getValue() == ctx);
        log.info("Channel disconnected: {}", ctx.channel().id());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception: {}", ctx.channel().id(), cause);
        VEHICLE_CHANNELS.entrySet().removeIf(e -> e.getValue() == ctx);
        ctx.close();
    }

    public static boolean isVehicleOnline(String vehicleId) {
        ChannelHandlerContext ctx = VEHICLE_CHANNELS.get(vehicleId);
        return ctx != null && ctx.channel().isActive();
    }

    public static int getOnlineCount() {
        return VEHICLE_CHANNELS.size();
    }
}
