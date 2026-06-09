package com.bus.predictor.webapi.websocket;

import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.traffic.model.RoadSegmentManager;
import com.bus.predictor.traffic.model.RoadSegmentManager.SegmentChangeListener;
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
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TrafficHeatmapWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TrafficHeatmapWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final RoadSegmentManager roadSegmentManager;

    public TrafficHeatmapWebSocketHandler(RoadSegmentManager roadSegmentManager) {
        this.roadSegmentManager = roadSegmentManager;
        roadSegmentManager.addListener(() -> broadcastHeatmap());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Traffic heatmap WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Traffic heatmap WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Scheduled(fixedRate = 10000)
    public void broadcastHeatmap() {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> heatmapData = roadSegmentManager.getHeatmapData();
            if (heatmapData.isEmpty()) {
                return;
            }

            String json = JsonUtil.toJson(heatmapData);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send traffic heatmap message to session {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast traffic heatmap failed", e);
        }
    }
}
