package com.openclaw.wecom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientMessage {

    private String type;

    private String clientId;

    private String authToken;

    private String messageId;

    private Object payload;

    private String error;
}
