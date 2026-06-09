package com.bus.predictor.gateway.config;

import com.bus.predictor.gateway.server.GpsMessageHandler;
import com.bus.predictor.gateway.server.KafkaProducerService;
import com.bus.predictor.gateway.server.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public CommandLineRunner startTcpServer(TcpServer tcpServer) {
        return args -> {
            log.info("Starting TCP server...");
            tcpServer.start();
        };
    }
}
