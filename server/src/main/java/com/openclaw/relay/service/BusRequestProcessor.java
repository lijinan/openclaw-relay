package com.openclaw.relay.service;

import com.openclaw.relay.bus.BusRequest;
import com.openclaw.relay.bus.RelayMessageBus;
import com.openclaw.relay.config.RelayConfig;
import com.openclaw.relay.model.ClientMessage;
import com.openclaw.relay.websocket.RelayWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class BusRequestProcessor {

    @Autowired
    private RelayMessageBus messageBus;

    @Autowired
    private RelayWebSocketHandler webSocketHandler;

    @Autowired
    private RelayConfig relayConfig;

    @Scheduled(fixedDelayString = "${openclaw.relay.bus.processor-fixed-delay-ms:50}")
    public void pollAndDispatch() {
        Set<String> connectedClientIds = webSocketHandler.getConnectedClientIds();
        if (connectedClientIds.isEmpty()) {
            return;
        }

        BusRequest request = messageBus.consumeRequest(connectedClientIds, relayConfig.getBus().getPollTimeoutMs());
        if (request == null) {
            return;
        }

        if (request.getMessage() == null) {
            ClientMessage response = ClientMessage.builder()
                    .type("response")
                    .clientId(request.getClientId())
                    .messageId(request.getMessageId())
                    .error("Invalid bus request")
                    .build();
            messageBus.publishResponse(response);
            return;
        }

        if (request.getMessageId() == null && request.getMessage() != null) {
            request.setMessageId(request.getMessage().getMessageId());
        }

        boolean sent = webSocketHandler.sendMessageToClient(request.getClientId(), request.getMessage());
        if (!sent) {
            ClientMessage response = ClientMessage.builder()
                    .type("response")
                    .clientId(request.getClientId())
                    .messageId(request.getMessageId())
                    .error("No connected client for clientId: " + request.getClientId())
                    .build();
            messageBus.publishResponse(response);
        }
    }
}
