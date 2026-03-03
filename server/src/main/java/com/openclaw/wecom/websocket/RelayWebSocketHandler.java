package com.openclaw.wecom.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.wecom.config.RelayConfig;
import com.openclaw.wecom.model.ClientMessage;
import com.openclaw.wecom.model.ServerMessage;
import com.openclaw.wecom.service.MessageBufferService;
import com.openclaw.wecom.service.PendingMessageManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RelayWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelayConfig relayConfig;

    @Autowired
    private PendingMessageManager pendingMessageManager;

    @Autowired
    private MessageBufferService messageBufferService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToSessionId = new ConcurrentHashMap<>();
    private final Map<String, Boolean> authenticatedSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        sessions.put(session.getId(), session);
        authenticatedSessions.put(session.getId(), false);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from session {}: {}", session.getId(), payload.substring(0, Math.min(200, payload.length())));

        ClientMessage clientMessage;
        try {
            clientMessage = objectMapper.readValue(payload, ClientMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse client message: {}", e.getMessage());
            return;
        }

        String type = clientMessage.getType();
        if (type == null) {
            log.warn("Message type is null");
            return;
        }

        switch (type) {
            case "register":
                handleRegister(session, clientMessage);
                break;
            case "ping":
                // 收到 ping，回复 pong
                sendMessage(session, ServerMessage.builder().type("pong").build());
                break;
            case "pong":
                // 收到 pong，无需处理
                break;
            case "response":
                handleResponse(clientMessage);
                break;
            default:
                log.warn("Unknown message type: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        String sessionId = session.getId();
        sessions.remove(sessionId);
        authenticatedSessions.remove(sessionId);

        String clientId = null;
        for (Map.Entry<String, String> entry : clientIdToSessionId.entrySet()) {
            if (entry.getValue().equals(sessionId)) {
                clientId = entry.getKey();
                break;
            }
        }
        if (clientId != null) {
            clientIdToSessionId.remove(clientId);
            log.info("Client unregistered: {}", clientId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private void handleRegister(WebSocketSession session, ClientMessage message) throws IOException {
        String clientId = message.getClientId();
        String authToken = message.getAuthToken();

        if (clientId == null || clientId.isEmpty()) {
            log.warn("Registration attempt with empty clientId");
            ServerMessage response = ServerMessage.builder()
                    .type("error")
                    .error("Authentication required. Please provide clientId.")
                    .build();
            sendMessage(session, response);
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "Authentication required"));
            return;
        }

        if (authToken == null || authToken.isEmpty()) {
            log.warn("Registration attempt with empty authToken");
            ServerMessage response = ServerMessage.builder()
                    .type("error")
                    .error("Authentication required. Please provide authToken.")
                    .build();
            sendMessage(session, response);
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "Authentication required"));
            return;
        }

        if (relayConfig.authenticate(clientId, authToken)) {
            authenticatedSessions.put(session.getId(), true);
            log.info("Client authenticated: {} (clientId: {})", session.getId(), clientId);
        } else {
            log.warn("Authentication failed for session {}: clientId={}, invalid token", session.getId(), clientId);
            ServerMessage response = ServerMessage.builder()
                    .type("error")
                    .error("Authentication failed: invalid clientId or token")
                    .build();
            sendMessage(session, response);
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "Invalid authentication"));
            return;
        }

        clientIdToSessionId.put(clientId, session.getId());
        log.info("Client registered: {} -> session {}", clientId, session.getId());

        ServerMessage response = ServerMessage.registered();
        sendMessage(session, response);

        log.info("Flushing buffered messages after client registration");
        messageBufferService.flushBuffer();
    }

    private void handleResponse(ClientMessage message) {
        String messageId = message.getMessageId();
        if (messageId == null) {
            log.warn("Response message missing messageId");
            return;
        }

        log.info("Received response for message: {}", messageId);
        pendingMessageManager.completeMessage(messageId, message);
    }

    public boolean sendMessage(WebSocketSession session, ServerMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
            log.debug("Sent message to session {}: {}", session.getId(), message.getType());
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            return false;
        }
    }

    public boolean sendMessageToClient(ServerMessage message) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen() && Boolean.TRUE.equals(authenticatedSessions.get(session.getId()))) {
                return sendMessage(session, message);
            }
        }
        log.warn("No active WebSocket session to send message");
        return false;
    }

    public boolean hasConnectedClient() {
        return sessions.values().stream()
                .anyMatch(s -> s.isOpen() && Boolean.TRUE.equals(authenticatedSessions.get(s.getId())));
    }

    public int getConnectedClientCount() {
        return (int) sessions.values().stream()
                .filter(s -> s.isOpen() && Boolean.TRUE.equals(authenticatedSessions.get(s.getId())))
                .count();
    }
}
