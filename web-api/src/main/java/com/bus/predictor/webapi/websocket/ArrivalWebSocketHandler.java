package com.bus.predictor.webapi.websocket;

import com.bus.predictor.common.model.ArrivalPrediction;
import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.traffic.model.ArrivalPredictService;
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
public class ArrivalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ArrivalWebSocketHandler.class);

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ArrivalPredictService arrivalPredictService;

    public ArrivalWebSocketHandler(ArrivalPredictService arrivalPredictService) {
        this.arrivalPredictService = arrivalPredictService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Arrival WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.remove(session.getId());
        log.info("Arrival WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> params = JsonUtil.fromJson(payload, Map.class);
            String action = params.get("action");

            if ("subscribe".equals(action)) {
                String stationId = params.get("stationId");
                String routeId = params.get("routeId");
                subscriptions.put(session.getId(), new Subscription(session, stationId, routeId));
                log.info("Session {} subscribed: stationId={}, routeId={}", session.getId(), stationId, routeId);
            } else if ("unsubscribe".equals(action)) {
                subscriptions.remove(session.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to parse arrival subscription: {}", payload);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void broadcastArrivalPredictions() {
        if (subscriptions.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
            Subscription sub = entry.getValue();
            if (!sub.session.isOpen()) {
                subscriptions.remove(entry.getKey());
                continue;
            }

            try {
                List<ArrivalPrediction> predictions;
                if (sub.stationId != null && !sub.stationId.isEmpty()) {
                    predictions = arrivalPredictService.predictForStation(sub.stationId);
                } else if (sub.routeId != null && !sub.routeId.isEmpty()) {
                    predictions = new java.util.ArrayList<>();
                } else {
                    continue;
                }

                if (predictions.isEmpty()) {
                    continue;
                }

                String json = JsonUtil.toJson(Map.of(
                        "type", "arrival",
                        "stationId", sub.stationId != null ? sub.stationId : "",
                        "predictions", predictions,
                        "timestamp", System.currentTimeMillis()
                ));

                sub.session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("Failed to send arrival data to session {}", sub.session.getId());
                subscriptions.remove(entry.getKey());
            } catch (Exception e) {
                log.error("Error broadcasting arrival predictions", e);
            }
        }
    }

    private static class Subscription {
        final WebSocketSession session;
        final String stationId;
        final String routeId;

        Subscription(WebSocketSession session, String stationId, String routeId) {
            this.session = session;
            this.stationId = stationId;
            this.routeId = routeId;
        }
    }
}
