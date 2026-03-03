package com.openclaw.wecom.service;

import com.openclaw.wecom.config.RelayConfig;
import com.openclaw.wecom.model.ServerMessage;
import com.openclaw.wecom.model.WebhookPayload;
import com.openclaw.wecom.websocket.RelayWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MessageBufferService {

    @Autowired
    private RelayConfig relayConfig;

    @Autowired
    @Lazy
    private RelayWebSocketHandler webSocketHandler;

    @Autowired
    private PendingMessageManager pendingMessageManager;

    private final LinkedBlockingQueue<BufferedMessage> messageBuffer = new LinkedBlockingQueue<>();

    private static final long MESSAGE_TTL_MS = 5 * 60 * 1000;

    public static class BufferedMessage {
        public final String messageId;
        public final WebhookPayload payload;
        public final long createdAt;

        public BufferedMessage(String messageId, WebhookPayload payload) {
            this.messageId = messageId;
            this.payload = payload;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > MESSAGE_TTL_MS;
        }
    }

    public boolean bufferMessage(String messageId, WebhookPayload payload) {
        if (messageBuffer.size() >= relayConfig.getMaxPendingMessages()) {
            log.warn("Message buffer is full, dropping oldest message");
            messageBuffer.poll();
        }

        boolean added = messageBuffer.offer(new BufferedMessage(messageId, payload));
        if (added) {
            log.info("Message buffered: {}, queue size: {}", messageId, messageBuffer.size());
        }
        return added;
    }

    public void flushBuffer() {
        if (!webSocketHandler.hasConnectedClient()) {
            return;
        }

        int flushed = 0;
        BufferedMessage msg;
        while ((msg = messageBuffer.poll()) != null) {
            if (msg.isExpired()) {
                log.warn("Dropping expired buffered message: {}", msg.messageId);
                continue;
            }

            ServerMessage message = ServerMessage.webhook(msg.messageId, msg.payload);
            if (webSocketHandler.sendMessageToClient(message)) {
                pendingMessageManager.registerMessage(msg.messageId);
                flushed++;
            } else {
                log.warn("Failed to send buffered message, re-queueing: {}", msg.messageId);
                messageBuffer.offer(msg);
                break;
            }
        }

        if (flushed > 0) {
            log.info("Flushed {} buffered messages", flushed);
        }
    }

    public int getBufferSize() {
        return messageBuffer.size();
    }

    @Scheduled(fixedRate = 10000)
    public void cleanupExpiredMessages() {
        int removed = 0;
        java.util.Iterator<BufferedMessage> iterator = messageBuffer.iterator();
        while (iterator.hasNext()) {
            BufferedMessage msg = iterator.next();
            if (msg.isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired buffered messages", removed);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFlush() {
        if (webSocketHandler.hasConnectedClient() && !messageBuffer.isEmpty()) {
            flushBuffer();
        }
    }
}
