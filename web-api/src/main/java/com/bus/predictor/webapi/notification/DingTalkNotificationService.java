package com.bus.predictor.webapi.notification;

import com.alibaba.fastjson.JSON;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class DingTalkNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotificationService.class);

    @Value("${notification.dingtalk.webhook:}")
    private String webhookUrl;

    @Value("${notification.dingtalk.secret:}")
    private String secret;

    @Value("${notification.dingtalk.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendAlert(String title, String content, String level) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("DingTalk notification is disabled or not configured");
            return;
        }

        try {
            String url = buildSignUrl();
            Map<String, Object> request = buildMarkdownRequest(title, content, level);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("DingTalk alert sent: {}, response: {}", title, response.getBody());
        } catch (Exception e) {
            log.error("Failed to send DingTalk alert: {}", e.getMessage(), e);
        }
    }

    private String buildSignUrl() throws Exception {
        if (secret == null || secret.isEmpty()) {
            return webhookUrl;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), StandardCharsets.UTF_8.name());

        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    private Map<String, Object> buildMarkdownRequest(String title, String content, String level) {
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);

        String emoji = "⚠️";
        if ("CRITICAL".equals(level)) {
            emoji = "🔴";
        } else if ("WARNING".equals(level)) {
            emoji = "🟡";
        } else if ("INFO".equals(level)) {
            emoji = "🔵";
        }

        markdown.put("text", emoji + " **" + title + "**\n\n" + content);

        Map<String, Object> request = new HashMap<>();
        request.put("msgtype", "markdown");
        request.put("markdown", markdown);

        Map<String, Object> at = new HashMap<>();
        at.put("isAtAll", true);
        request.put("at", at);

        return request;
    }
}
