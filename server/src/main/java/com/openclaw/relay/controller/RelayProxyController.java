package com.openclaw.relay.controller;

import com.openclaw.relay.bus.BusRequest;
import com.openclaw.relay.bus.RelayMessageBus;
import com.openclaw.relay.config.RelayConfig;
import com.openclaw.relay.model.ClientMessage;
import com.openclaw.relay.model.ServerMessage;
import com.openclaw.relay.model.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
public class RelayProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS;
    private static final Set<String> ALLOWED_METHODS;

    static {
        Set<String> set = new HashSet<>();
        set.add("connection");
        set.add("keep-alive");
        set.add("proxy-authenticate");
        set.add("proxy-authorization");
        set.add("te");
        set.add("trailer");
        set.add("transfer-encoding");
        set.add("upgrade");
        set.add("content-length");
        HOP_BY_HOP_HEADERS = set;
    }

    static {
        Set<String> set = new HashSet<>();
        set.add("GET");
        set.add("POST");
        set.add("PUT");
        set.add("PATCH");
        set.add("DELETE");
        set.add("HEAD");
        set.add("OPTIONS");
        ALLOWED_METHODS = set;
    }

    @Autowired
    private RelayMessageBus messageBus;

    @Autowired
    private RelayConfig relayConfig;

    @RequestMapping("/relay/client/{clientId}/**")
    public ResponseEntity<byte[]> relay(
            @PathVariable("clientId") String clientId,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String method = request.getMethod();
        if (method == null || !ALLOWED_METHODS.contains(method.toUpperCase())) {
            return plainText(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
        }

        String requiredToken = relayConfig.getToken();
        if (requiredToken != null && !requiredToken.isEmpty()) {
            String token = request.getHeader("X-Relay-Token");
            if (token == null || !requiredToken.equals(token)) {
                return plainText(HttpStatus.UNAUTHORIZED, "Unauthorized");
            }
        }

        if (!messageBus.isClientOnline(clientId)) {
            return plainText(HttpStatus.SERVICE_UNAVAILABLE, "No connected client for clientId: " + clientId);
        }

        String requestPath = getPathWithoutClientId(request, clientId);
        Map<String, String> query = extractQueryParams(request);
        Map<String, String> headers = extractHeaders(request);

        WebhookPayload payload = WebhookPayload.builder()
                .method(method)
                .path(requestPath)
                .query(query)
                .headers(headers)
                .build();

        if (body != null && body.length > 0) {
            payload.setBodyBase64(Base64.getEncoder().encodeToString(body));
            payload.setIsBase64(true);

            if (isTextContentType(headers.get("content-type")) || isTextContentType(headers.get("Content-Type"))) {
                payload.setBody(new String(body, StandardCharsets.UTF_8));
            }
        } else {
            payload.setBody("");
        }

        String messageId = UUID.randomUUID().toString();
        ServerMessage message = ServerMessage.webhook(messageId, payload);

        messageBus.publishRequest(BusRequest.builder()
                .clientId(clientId)
                .messageId(messageId)
                .message(message)
                .createdAt(System.currentTimeMillis())
                .attempts(0)
                .build());

        ClientMessage response = messageBus.consumeResponse(messageId, relayConfig.getResponseTimeout());
        if (response == null) {
            return plainText(HttpStatus.GATEWAY_TIMEOUT, "Request timeout after " + relayConfig.getResponseTimeout() + "ms");
        }
        if (response.getError() != null) {
            if (response.getError().startsWith("No connected client")) {
                return plainText(HttpStatus.SERVICE_UNAVAILABLE, response.getError());
            }
            return plainText(HttpStatus.BAD_GATEWAY, response.getError());
        }

        Map<String, Object> responsePayload = (Map<String, Object>) response.getPayload();
        int status = 200;
        HttpHeaders responseHeaders = new HttpHeaders();
        byte[] responseBody = new byte[0];

        if (responsePayload != null) {
            Object statusObj = responsePayload.get("status");
            if (statusObj instanceof Number) {
                status = ((Number) statusObj).intValue();
            }

            Object headersObj = responsePayload.get("headers");
            if (headersObj instanceof Map<?, ?>) {
                Map<?, ?> headersMap = (Map<?, ?>) headersObj;
                for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    String key = entry.getKey().toString();
                    if (HOP_BY_HOP_HEADERS.contains(key.toLowerCase())) {
                        continue;
                    }
                    responseHeaders.add(key, entry.getValue().toString());
                }
            }

            Object base64Obj = responsePayload.get("bodyBase64");
            if (base64Obj instanceof String && !((String) base64Obj).isEmpty()) {
                responseBody = Base64.getDecoder().decode((String) base64Obj);
            } else {
                Object bodyObj = responsePayload.get("body");
                if (bodyObj instanceof String) {
                    responseBody = ((String) bodyObj).getBytes(StandardCharsets.UTF_8);
                }
            }
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).headers(responseHeaders);
        return builder.body(responseBody);
    }

    private ResponseEntity<byte[]> plainText(HttpStatus status, String message) {
        String safeMessage = message == null ? "" : message;
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(safeMessage.getBytes(StandardCharsets.UTF_8));
    }

    private String getPathWithoutClientId(HttpServletRequest request, String clientId) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        String prefix = "/relay/client/" + clientId;
        if (uri.startsWith(prefix)) {
            String rest = uri.substring(prefix.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return uri;
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
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        headers.remove("Host");
        headers.remove("host");
        return headers;
    }

    private boolean isTextContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        if (ct.startsWith("text/")) {
            return true;
        }
        if (ct.contains("application/json") || ct.contains("+json")) {
            return true;
        }
        if (ct.contains("application/xml") || ct.contains("text/xml") || ct.contains("+xml")) {
            return true;
        }
        return ct.contains("application/x-www-form-urlencoded");
    }
}
