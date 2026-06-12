package com.bus.predictor.webapi.websocket;

import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.dal.entity.AlertRecordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Alert WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Alert WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    public void broadcastAlert(AlertRecordEntity alert) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            String json = JsonUtil.toJson(alert);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send alert to session {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast alert failed", e);
        }
    }

    public void broadcastAlertList(List<AlertRecordEntity> alerts) {
        if (sessions.isEmpty() || alerts == null || alerts.isEmpty()) {
            return;
        }

        try {
            String json = JsonUtil.toJson(alerts);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send alert list to session {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast alert list failed", e);
        }
    }
}
