package com.bus.predictor.simulator.client;

import com.bus.predictor.common.constant.ProtocolConstant;
import com.bus.predictor.simulator.model.VehicleConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class TcpClient {

    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final String host;
    private final int port;
    private final VehicleConfig config;

    private EventLoopGroup workerGroup;
    private Channel channel;

    public TcpClient(String host, int port, VehicleConfig config) {
        this.host = host;
        this.port = port;
        this.config = config;
    }

    public void connect() throws InterruptedException {
        workerGroup = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        ch.pipeline().addLast(new TcpClientHandler(config.getVehicleId()));
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
        log.info("[{}] Connected to gateway {}:{}", config.getVehicleId(), host, port);
    }

    public void sendGps(double longitude, double latitude, double speed, double direction,
                        long timestamp, int satelliteCount, int hdop) {
        if (channel == null || !channel.isActive()) {
            log.warn("[{}] Channel not active, skip send", config.getVehicleId());
            return;
        }

        byte[] vehicleIdBytes = config.getVehicleId().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int bodyLen = 1 + 1 + vehicleIdBytes.length + 8 + 8 + 8 + 8 + 8 + 1 + 1;
        int totalLen = 1 + bodyLen;

        io.netty.buffer.ByteBuf buf = channel.alloc().buffer(4 + totalLen);
        buf.writeInt(totalLen);
        buf.writeByte(ProtocolConstant.MSG_TYPE_GPS);
        buf.writeByte(vehicleIdBytes.length);
        buf.writeBytes(vehicleIdBytes);
        buf.writeDouble(longitude);
        buf.writeDouble(latitude);
        buf.writeDouble(speed);
        buf.writeDouble(direction);
        buf.writeLong(timestamp);
        buf.writeByte(satelliteCount);
        buf.writeByte(hdop);

        channel.writeAndFlush(buf);
    }

    public void sendHeartbeat() {
        if (channel == null || !channel.isActive()) {
            return;
        }

        byte[] vehicleIdBytes = config.getVehicleId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int bodyLen = 1 + 1 + vehicleIdBytes.length;
        int totalLen = 1 + bodyLen;

        io.netty.buffer.ByteBuf buf = channel.alloc().buffer(4 + totalLen);
        buf.writeInt(totalLen);
        buf.writeByte(ProtocolConstant.MSG_TYPE_HEARTBEAT);
        buf.writeByte(vehicleIdBytes.length);
        buf.writeBytes(vehicleIdBytes);

        channel.writeAndFlush(buf);
    }

    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public VehicleConfig getConfig() {
        return config;
    }
}
