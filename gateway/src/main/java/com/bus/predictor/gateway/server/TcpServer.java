package com.bus.predictor.gateway.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Component
public class TcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    @Value("${gateway.tcp.port:9090}")
    private int port;

    @Value("${gateway.tcp.boss-threads:1}")
    private int bossThreads;

    @Value("${gateway.tcp.worker-threads:0}")
    private int workerThreads;

    @Value("${gateway.tcp.reader-idle-seconds:300}")
    private int readerIdleSeconds;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    private final GpsMessageHandler gpsMessageHandler;

    public TcpServer(GpsMessageHandler gpsMessageHandler) {
        this.gpsMessageHandler = gpsMessageHandler;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(
                                65536, 0, 4, 0, 4));
                        pipeline.addLast(new StringEncoder());
                        pipeline.addLast(new IdleStateHandler(
                                readerIdleSeconds, 0, 0, TimeUnit.SECONDS));
                        pipeline.addLast(new GpsProtocolDecoder());
                        pipeline.addLast(gpsMessageHandler);
                    }
                });

        channelFuture = bootstrap.bind(port).sync();
        log.info("TCP Gateway started on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("TCP Gateway stopped");
    }
}
