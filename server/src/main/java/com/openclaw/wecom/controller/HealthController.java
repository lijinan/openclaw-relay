package com.openclaw.wecom.controller;

import com.openclaw.wecom.service.MessageBufferService;
import com.openclaw.wecom.websocket.RelayWebSocketHandler;
import com.openclaw.wecom.service.PendingMessageManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private RelayWebSocketHandler webSocketHandler;

    @Autowired
    private PendingMessageManager pendingMessageManager;

    @Autowired
    private MessageBufferService messageBufferService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("connectedClients", webSocketHandler.getConnectedClientCount());
        status.put("pendingMessages", pendingMessageManager.getPendingCount());
        status.put("bufferedMessages", messageBufferService.getBufferSize());
        return status;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedClients", webSocketHandler.getConnectedClientCount());
        status.put("pendingMessages", pendingMessageManager.getPendingCount());
        status.put("bufferedMessages", messageBufferService.getBufferSize());
        status.put("hasClient", webSocketHandler.hasConnectedClient());
        return status;
    }
}
