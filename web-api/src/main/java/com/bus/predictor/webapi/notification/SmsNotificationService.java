package com.bus.predictor.webapi.notification;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationService.class);

    @Value("${notification.sms.enabled:false}")
    private boolean enabled;

    @Value("${notification.sms.api-url:}")
    private String apiUrl;

    @Value("${notification.sms.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendAlert(String phones, String content, String level) {
        if (!enabled || apiUrl == null || apiUrl.isEmpty()) {
            log.debug("SMS notification is disabled or not configured");
            return;
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("phones", phones);
            request.put("content", content);
            request.put("level", level);
            request.put("timestamp", System.currentTimeMillis());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            log.info("SMS alert sent to {}, response: {}", phones, response.getBody());
        } catch (Exception e) {
            log.error("Failed to send SMS alert: {}", e.getMessage(), e);
        }
    }
}
