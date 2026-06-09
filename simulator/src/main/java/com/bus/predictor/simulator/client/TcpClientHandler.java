package com.bus.predictor.simulator.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TcpClientHandler.class);

    private final String vehicleId;

    public TcpClientHandler(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.debug("[{}] Received from server: {}", vehicleId, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("[{}] Channel active", vehicleId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("[{}] Channel inactive", vehicleId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[{}] Channel exception", vehicleId, cause);
        ctx.close();
    }
}
