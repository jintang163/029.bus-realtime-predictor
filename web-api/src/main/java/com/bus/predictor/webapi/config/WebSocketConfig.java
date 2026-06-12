package com.bus.predictor.webapi.config;

import com.bus.predictor.webapi.websocket.AlertWebSocketHandler;
import com.bus.predictor.webapi.websocket.ArrivalWebSocketHandler;
import com.bus.predictor.webapi.websocket.EtaWebSocketHandler;
import com.bus.predictor.webapi.websocket.TrafficHeatmapWebSocketHandler;
import com.bus.predictor.webapi.websocket.VehiclePositionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VehiclePositionWebSocketHandler vehiclePositionWebSocketHandler;
    private final TrafficHeatmapWebSocketHandler trafficHeatmapWebSocketHandler;
    private final ArrivalWebSocketHandler arrivalWebSocketHandler;
    private final EtaWebSocketHandler etaWebSocketHandler;
    private final AlertWebSocketHandler alertWebSocketHandler;

    public WebSocketConfig(VehiclePositionWebSocketHandler vehiclePositionWebSocketHandler,
                           TrafficHeatmapWebSocketHandler trafficHeatmapWebSocketHandler,
                           ArrivalWebSocketHandler arrivalWebSocketHandler,
                           EtaWebSocketHandler etaWebSocketHandler,
                           AlertWebSocketHandler alertWebSocketHandler) {
        this.vehiclePositionWebSocketHandler = vehiclePositionWebSocketHandler;
        this.trafficHeatmapWebSocketHandler = trafficHeatmapWebSocketHandler;
        this.arrivalWebSocketHandler = arrivalWebSocketHandler;
        this.etaWebSocketHandler = etaWebSocketHandler;
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(vehiclePositionWebSocketHandler, "/ws/vehicle")
                .setAllowedOriginPatterns("*");
        registry.addHandler(trafficHeatmapWebSocketHandler, "/ws/traffic")
                .setAllowedOriginPatterns("*");
        registry.addHandler(arrivalWebSocketHandler, "/ws/arrival")
                .setAllowedOriginPatterns("*");
        registry.addHandler(etaWebSocketHandler, "/ws/eta")
                .setAllowedOriginPatterns("*");
        registry.addHandler(alertWebSocketHandler, "/ws/alert")
                .setAllowedOriginPatterns("*");
    }
}
