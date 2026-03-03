package com.openclaw.wecom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {

    private String method;

    private String path;

    private Map<String, String> query;

    private String body;
}
