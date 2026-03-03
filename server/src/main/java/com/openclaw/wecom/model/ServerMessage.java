package com.openclaw.wecom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMessage {

    private String type;

    private String messageId;

    private Object payload;

    private String error;

    public static ServerMessage registered() {
        return ServerMessage.builder()
                .type("registered")
                .build();
    }

    public static ServerMessage pong() {
        return ServerMessage.builder()
                .type("pong")
                .build();
    }

    public static ServerMessage webhook(String messageId, WebhookPayload payload) {
        return ServerMessage.builder()
                .type("webhook")
                .messageId(messageId)
                .payload(payload)
                .build();
    }

    public static ServerMessage verify(String messageId, VerifyPayload payload) {
        return ServerMessage.builder()
                .type("verify")
                .messageId(messageId)
                .payload(payload)
                .build();
    }

    public static ServerMessage ack(String messageId) {
        return ServerMessage.builder()
                .type("ack")
                .messageId(messageId)
                .build();
    }
}
