package com.openclaw.relay.bus;

import com.openclaw.relay.model.ClientMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MemoryRelayMessageBus implements RelayMessageBus {

    private final ConcurrentHashMap<String, LinkedBlockingQueue<BusRequest>> requestQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedBlockingQueue<ClientMessage>> responseQueues = new ConcurrentHashMap<>();
    private final Set<String> onlineClients = ConcurrentHashMap.newKeySet();

    @Override
    public void publishRequest(BusRequest request) {
        if (request == null || request.getClientId() == null || request.getClientId().isEmpty()) {
            return;
        }
        requestQueues.computeIfAbsent(request.getClientId(), k -> new LinkedBlockingQueue<>()).offer(request);
    }

    @Override
    public BusRequest consumeRequest(Set<String> clientIds, long timeoutMs) {
        if (clientIds == null || clientIds.isEmpty() || timeoutMs <= 0) {
            return pollOnce(clientIds);
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            BusRequest request = pollOnce(clientIds);
            if (request != null) {
                return request;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private BusRequest pollOnce(Set<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return null;
        }
        for (String clientId : clientIds) {
            LinkedBlockingQueue<BusRequest> queue = requestQueues.get(clientId);
            if (queue == null) {
                continue;
            }
            BusRequest request = queue.poll();
            if (request != null) {
                return request;
            }
        }
        return null;
    }

    @Override
    public void publishResponse(ClientMessage response) {
        if (response == null || response.getMessageId() == null || response.getMessageId().isEmpty()) {
            return;
        }
        LinkedBlockingQueue<ClientMessage> queue = responseQueues.computeIfAbsent(response.getMessageId(), k -> new LinkedBlockingQueue<>(1));
        queue.clear();
        queue.offer(response);
    }

    @Override
    public ClientMessage consumeResponse(String messageId, long timeoutMs) {
        if (messageId == null || messageId.isEmpty()) {
            return null;
        }

        LinkedBlockingQueue<ClientMessage> queue = responseQueues.computeIfAbsent(messageId, k -> new LinkedBlockingQueue<>(1));
        try {
            ClientMessage response = timeoutMs > 0
                    ? queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
                    : queue.poll();
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            responseQueues.remove(messageId);
        }
    }

    @Override
    public void markClientOnline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        onlineClients.add(clientId);
    }

    @Override
    public void markClientOffline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        onlineClients.remove(clientId);
    }

    @Override
    public boolean isClientOnline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return false;
        }
        return onlineClients.contains(clientId);
    }
}
