package com.openclaw.relay.bus;

import com.openclaw.relay.model.ServerMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusRequest {
    private String clientId;
    private String messageId;
    private ServerMessage message;
    private long createdAt;
    private int attempts;
}
