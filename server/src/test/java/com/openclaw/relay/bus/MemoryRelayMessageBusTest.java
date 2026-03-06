package com.openclaw.relay.bus;

import com.openclaw.relay.model.ClientMessage;
import com.openclaw.relay.model.ServerMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MemoryRelayMessageBusTest {

    @Test
    void requestAndResponseRoundTrip() {
        MemoryRelayMessageBus bus = new MemoryRelayMessageBus();

        bus.markClientOnline("client-a");
        Assertions.assertTrue(bus.isClientOnline("client-a"));

        ServerMessage serverMessage = ServerMessage.webhook("m1", null);
        bus.publishRequest(BusRequest.builder()
                .clientId("client-a")
                .messageId("m1")
                .message(serverMessage)
                .createdAt(System.currentTimeMillis())
                .attempts(0)
                .build());

        BusRequest request = bus.consumeRequest(Collections.singleton("client-a"), 200);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("client-a", request.getClientId());
        Assertions.assertEquals("m1", request.getMessageId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", 200);
        payload.put("body", "ok");

        bus.publishResponse(ClientMessage.builder()
                .type("response")
                .clientId("client-a")
                .messageId("m1")
                .payload(payload)
                .build());

        ClientMessage response = bus.consumeResponse("m1", 200);
        Assertions.assertNotNull(response);
        Assertions.assertEquals("m1", response.getMessageId());
        Assertions.assertNull(response.getError());

        ClientMessage response2 = bus.consumeResponse("m1", 0);
        Assertions.assertNull(response2);
    }
}
