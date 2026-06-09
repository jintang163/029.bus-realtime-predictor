package com.bus.predictor.webapi.websocket;

import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class VehiclePositionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final VehiclePositionRedisDao vehiclePositionRedisDao;

    public VehiclePositionWebSocketHandler(VehiclePositionRedisDao vehiclePositionRedisDao) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Scheduled(fixedRate = 2000)
    public void broadcastPositions() {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            List<VehiclePosition> positions = vehiclePositionRedisDao.getAllOnlinePositions();
            if (positions.isEmpty()) {
                return;
            }

            String json = JsonUtil.toJson(positions);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message to session {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast positions failed", e);
        }
    }
}
