package com.bus.predictor.webapi.config;

import com.bus.predictor.webapi.websocket.VehiclePositionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VehiclePositionWebSocketHandler vehiclePositionWebSocketHandler;

    public WebSocketConfig(VehiclePositionWebSocketHandler vehiclePositionWebSocketHandler) {
        this.vehiclePositionWebSocketHandler = vehiclePositionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(vehiclePositionWebSocketHandler, "/ws/vehicle")
                .setAllowedOriginPatterns("*");
    }
}
