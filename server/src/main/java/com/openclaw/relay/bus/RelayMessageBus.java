package com.openclaw.relay.bus;

import com.openclaw.relay.model.ClientMessage;

import java.util.Set;

public interface RelayMessageBus {
    void publishRequest(BusRequest request);

    BusRequest consumeRequest(Set<String> clientIds, long timeoutMs);

    void publishResponse(ClientMessage response);

    ClientMessage consumeResponse(String messageId, long timeoutMs);

    void markClientOnline(String clientId);

    void markClientOffline(String clientId);

    boolean isClientOnline(String clientId);
}
