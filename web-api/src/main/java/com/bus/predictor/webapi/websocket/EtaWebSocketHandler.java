package com.bus.predictor.webapi.websocket;

import com.bus.predictor.common.model.EtaResponse;
import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.traffic.model.EtaService;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EtaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EtaWebSocketHandler.class);

    private final Map<String, EtaSubscription> subscriptions = new ConcurrentHashMap<>();
    private final EtaService etaService;

    public EtaWebSocketHandler(EtaService etaService) {
        this.etaService = etaService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("ETA WebSocket connected: {}", session.getId());
        try {
            session.sendMessage(new TextMessage(JsonUtil.toJson(Map.of(
                    "type", "welcome",
                    "message", "Please subscribe: {action:'subscribe', line:'5', station:'人民广场', direction:'up'}",
                    "timestamp", System.currentTimeMillis()
            ))));
        } catch (IOException ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.remove(session.getId());
        log.info("ETA WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("pong"));
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> params = JsonUtil.fromJson(payload, Map.class);
            String action = params.get("action");

            if ("subscribe".equalsIgnoreCase(action)) {
                String lineCode = params.get("line");
                String stationName = params.get("station");
                String direction = params.getOrDefault("direction", "up");

                if (lineCode == null || lineCode.isEmpty() || stationName == null || stationName.isEmpty()) {
                    session.sendMessage(new TextMessage(JsonUtil.toJson(Map.of(
                            "type", "error",
                            "message", "line and station are required",
                            "timestamp", System.currentTimeMillis()
                    ))));
                    return;
                }

                subscriptions.put(session.getId(), new EtaSubscription(
                        session, lineCode.trim(), stationName.trim(), direction.trim()
                ));
                log.info("Session {} subscribed: line={}, station={}, direction={}",
                        session.getId(), lineCode, stationName, direction);

                EtaResponse initial = etaService.getEta(lineCode.trim(), stationName.trim(), direction.trim(), false);
                sendEtaUpdate(session, initial);

            } else if ("unsubscribe".equalsIgnoreCase(action)) {
                subscriptions.remove(session.getId());
                log.info("Session {} unsubscribed", session.getId());
            } else if ("refresh".equalsIgnoreCase(action)) {
                EtaSubscription sub = subscriptions.get(session.getId());
                if (sub != null) {
                    EtaResponse fresh = etaService.getEta(sub.lineCode, sub.stationName, sub.direction, true);
                    sendEtaUpdate(session, fresh);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse ETA subscription: {}", payload);
            session.sendMessage(new TextMessage(JsonUtil.toJson(Map.of(
                    "type", "error",
                    "message", "Invalid JSON format",
                    "timestamp", System.currentTimeMillis()
            ))));
        }
    }

    @Scheduled(fixedRate = 10000)
    public void broadcastEtaUpdates() {
        if (subscriptions.isEmpty()) {
            return;
        }

        log.debug("Broadcasting ETA updates to {} subscribers", subscriptions.size());

        for (Map.Entry<String, EtaSubscription> entry : subscriptions.entrySet()) {
            EtaSubscription sub = entry.getValue();
            if (!sub.session.isOpen()) {
                subscriptions.remove(entry.getKey());
                continue;
            }

            try {
                EtaResponse eta = etaService.getEta(sub.lineCode, sub.stationName, sub.direction, false);
                if (eta == null || eta.getVehicles() == null || eta.getVehicles().isEmpty()) {
                    continue;
                }
                sendEtaUpdate(sub.session, eta);
            } catch (Exception e) {
                log.warn("Failed to send ETA update to session {}: {}", entry.getKey(), e.getMessage());
                subscriptions.remove(entry.getKey());
            }
        }
    }

    private void sendEtaUpdate(WebSocketSession session, EtaResponse eta) {
        if (eta == null || !session.isOpen()) return;

        try {
            String json = JsonUtil.toJson(Map.of(
                    "type", "eta",
                    "data", eta,
                    "timestamp", System.currentTimeMillis()
            ));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send ETA message to session {}", session.getId());
        }
    }

    private static class EtaSubscription {
        final WebSocketSession session;
        final String lineCode;
        final String stationName;
        final String direction;

        EtaSubscription(WebSocketSession session, String lineCode, String stationName, String direction) {
            this.session = session;
            this.lineCode = lineCode;
            this.stationName = stationName;
            this.direction = direction;
        }
    }
}
