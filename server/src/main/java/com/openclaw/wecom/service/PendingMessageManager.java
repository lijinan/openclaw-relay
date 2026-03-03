package com.openclaw.wecom.service;

import com.openclaw.wecom.config.RelayConfig;
import com.openclaw.wecom.model.ClientMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class PendingMessageManager {

    @Autowired
    private RelayConfig relayConfig;

    private final Map<String, CompletableFuture<ClientMessage>> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> timeoutFutures = new ConcurrentHashMap<>();
    private ThreadPoolTaskScheduler taskScheduler;

    @PostConstruct
    public void init() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("pending-message-timeout-");
        taskScheduler.initialize();
    }

    @PreDestroy
    public void destroy() {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    public CompletableFuture<ClientMessage> registerMessage(String messageId) {
        CompletableFuture<ClientMessage> future = new CompletableFuture<>();
        pendingMessages.put(messageId, future);

        java.util.Date scheduledTime = new java.util.Date(System.currentTimeMillis() + relayConfig.getResponseTimeout());
        java.util.concurrent.ScheduledFuture<?> timeoutFuture = taskScheduler.schedule(
                () -> {
                    if (pendingMessages.containsKey(messageId)) {
                        future.completeExceptionally(new TimeoutException("Request timeout after " + relayConfig.getResponseTimeout() + "ms"));
                        pendingMessages.remove(messageId);
                        timeoutFutures.remove(messageId);
                        log.warn("Message {} timed out", messageId);
                    }
                },
                scheduledTime
        );
        timeoutFutures.put(messageId, timeoutFuture);

        future.whenComplete((result, error) -> {
            pendingMessages.remove(messageId);
            java.util.concurrent.ScheduledFuture<?> sf = timeoutFutures.remove(messageId);
            if (sf != null) {
                sf.cancel(false);
            }
            if (error != null && !(error instanceof TimeoutException)) {
                log.warn("Message {} failed: {}", messageId, error.getMessage());
            }
        });

        log.debug("Registered pending message: {}", messageId);
        return future;
    }

    public void completeMessage(String messageId, ClientMessage response) {
        CompletableFuture<ClientMessage> future = pendingMessages.remove(messageId);
        if (future != null) {
            future.complete(response);
            log.debug("Completed pending message: {}", messageId);
        } else {
            log.warn("No pending message found for id: {}", messageId);
        }
    }

    public void cancelMessage(String messageId) {
        CompletableFuture<ClientMessage> future = pendingMessages.remove(messageId);
        if (future != null) {
            future.cancel(true);
            log.debug("Cancelled pending message: {}", messageId);
        }
    }

    public int getPendingCount() {
        return pendingMessages.size();
    }
}
