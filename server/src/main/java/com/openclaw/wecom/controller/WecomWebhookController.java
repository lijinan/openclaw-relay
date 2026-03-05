package com.openclaw.wecom.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.wecom.model.ClientMessage;
import com.openclaw.wecom.model.ServerMessage;
import com.openclaw.wecom.model.WebhookPayload;
import com.openclaw.wecom.service.MessageBufferService;
import com.openclaw.wecom.service.PendingMessageManager;
import com.openclaw.wecom.websocket.RelayWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequestMapping("/webhooks/wecom")
public class WecomWebhookController {

    @Autowired
    private RelayWebSocketHandler webSocketHandler;

    @Autowired
    private PendingMessageManager pendingMessageManager;

    @Autowired
    private MessageBufferService messageBufferService;

    @GetMapping
    public ResponseEntity<String> handleVerify(HttpServletRequest request) {
        log.info("Received GET request for URL verification");

        Map<String, String> query = extractQueryParams(request);
        Map<String, String> headers = extractHeaders(request);
        log.debug("Query params: {}", query);
        log.debug("Headers: {}", headers);

        // URL 验证转发给 OpenClaw 处理（Java 层不验证签名，由 OpenClaw 负责）
        // 这样 Java 端不需要配置 token 和 encodingAesKey

        if (!webSocketHandler.hasConnectedClient()) {
            log.warn("No connected OpenClaw client for verification");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No connected OpenClaw client. Please start OpenClaw and try again.");
        }

        String messageId = UUID.randomUUID().toString();
        CompletableFuture<ClientMessage> future = pendingMessageManager.registerMessage(messageId);

        WebhookPayload payload = WebhookPayload.builder()
                .method("GET")
                .path(request.getRequestURI())
                .query(query)
                .headers(headers)
                .build();

        ServerMessage message = ServerMessage.webhook(messageId, payload);
        webSocketHandler.sendMessageToClient(message);

        try {
            ClientMessage response = future.get();
            if (response.getError() != null) {
                log.error("Error from OpenClaw client: {}", response.getError());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response.getError());
            }

            Map<String, Object> responsePayload = (Map<String, Object>) response.getPayload();
            int status = 200;
            String body = "";
            Map<String, String> responseHeaders = new HashMap<>();

            if (responsePayload != null) {
                Object statusObj = responsePayload.get("status");
                if (statusObj instanceof Number) {
                    status = ((Number) statusObj).intValue();
                }

                Object bodyObj = responsePayload.get("body");
                if (bodyObj instanceof String) {
                    body = (String) bodyObj;
                }

                Object headersObj = responsePayload.get("headers");
                if (headersObj instanceof Map<?, ?>) {
                    Map<?, ?> headersMap = (Map<?, ?>) headersObj;
                    for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
                        Object key = entry.getKey();
                        Object value = entry.getValue();
                        if (key != null && value != null) {
                            responseHeaders.put(key.toString(), value.toString());
                        }
                    }
                }
            }

            log.info("Verification response: status={}, body={}", status,
                    body.length() > 50 ? body.substring(0, 50) + "..." : body);

            ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
            for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            return builder.body(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Interrupted");
        } catch (ExecutionException e) {
            log.error("Error waiting for response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getCause().getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<String> handleMessage(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        log.info("Received POST request from WeCom");
        log.debug("Request body length: {}", body != null ? body.length() : 0);

        Map<String, String> query = extractQueryParams(request);
        Map<String, String> headers = extractHeaders(request);
        log.debug("Query params: {}", query);

        // 签名验证由 OpenClaw 层处理（需要解密消息体后验证 encrypt 字段）
        // Java 层只负责转发请求

        String messageId = UUID.randomUUID().toString();

        WebhookPayload payload = WebhookPayload.builder()
                .method("POST")
                .path(request.getRequestURI())
                .query(query)
                .headers(headers)
                .body(body != null ? body : "")
                .build();

        if (!webSocketHandler.hasConnectedClient()) {
            log.warn("No connected OpenClaw client, buffering message");
            boolean buffered = messageBufferService.bufferMessage(messageId, payload);
            if (buffered) {
                return ResponseEntity.ok()
                        .body("{\"msgtype\":\"text\",\"text\":{\"content\":\"消息已接收，正在等待处理...\"}}");
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Service temporarily unavailable");
            }
        }

        CompletableFuture<ClientMessage> future = pendingMessageManager.registerMessage(messageId);

        ServerMessage message = ServerMessage.webhook(messageId, payload);
        webSocketHandler.sendMessageToClient(message);

        try {
            ClientMessage response = future.get();
            if (response.getError() != null) {
                log.error("Error from OpenClaw client: {}", response.getError());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response.getError());
            }

            Map<String, Object> responsePayload = (Map<String, Object>) response.getPayload();

            int status = 200;
            String responseBody = "";
            Map<String, String> responseHeaders = new HashMap<>();

            if (responsePayload != null) {
                Object statusObj = responsePayload.get("status");
                if (statusObj instanceof Number) {
                    status = ((Number) statusObj).intValue();
                }

                Object bodyObj = responsePayload.get("body");
                if (bodyObj instanceof String) {
                    responseBody = (String) bodyObj;
                }

                Object headersObj = responsePayload.get("headers");
                if (headersObj instanceof Map<?, ?>) {
                    Map<?, ?> headersMap = (Map<?, ?>) headersObj;
                    for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
                        Object key = entry.getKey();
                        Object value = entry.getValue();
                        if (key != null && value != null) {
                            responseHeaders.put(key.toString(), value.toString());
                        }
                    }
                }
            }

            log.debug("Response: status={}, body length={}", status, responseBody.length());

            ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
            for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            return builder.body(responseBody);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Interrupted");
        } catch (ExecutionException e) {
            log.error("Error waiting for response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getCause().getMessage());
        }
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }
}
