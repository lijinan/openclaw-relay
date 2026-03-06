package com.openclaw.relay.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.relay.config.RelayConfig;
import com.openclaw.relay.model.ClientMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisRelayMessageBus implements RelayMessageBus {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RelayConfig relayConfig;

    public RedisRelayMessageBus(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, RelayConfig relayConfig) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.relayConfig = relayConfig;
    }

    @Override
    public void publishRequest(BusRequest request) {
        if (request == null || request.getClientId() == null || request.getClientId().isEmpty()) {
            return;
        }
        if (request.getCreatedAt() == 0) {
            request.setCreatedAt(System.currentTimeMillis());
        }
        try {
            String json = objectMapper.writeValueAsString(request);
            String key = requestKey(request.getClientId());
            redisTemplate.opsForList().leftPush(key, json);
            int ttl = relayConfig.getBus().getRequestTtlSeconds();
            if (ttl > 0) {
                redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to publish request: {}", e.getMessage());
        }
    }

    @Override
    public BusRequest consumeRequest(Set<String> clientIds, long timeoutMs) {
        if (clientIds == null || clientIds.isEmpty()) {
            return null;
        }

        List<String> keys = new ArrayList<>(clientIds.size());
        for (String clientId : clientIds) {
            if (clientId == null || clientId.isEmpty()) {
                continue;
            }
            keys.add(requestKey(clientId));
        }
        if (keys.isEmpty()) {
            return null;
        }

        if (timeoutMs <= 0) {
            for (String key : keys) {
                String json = redisTemplate.opsForList().rightPop(key);
                BusRequest request = parseRequest(json);
                if (request != null) {
                    return request;
                }
            }
            return null;
        }

        int timeoutSeconds = (int) Math.max(1, (timeoutMs + 999) / 1000);
        byte[][] keyBytes = new byte[keys.size()][];
        for (int i = 0; i < keys.size(); i++) {
            keyBytes[i] = keys.get(i).getBytes(StandardCharsets.UTF_8);
        }

        String json = redisTemplate.execute((RedisConnection connection) -> {
            List<byte[]> result = connection.bRPop(timeoutSeconds, keyBytes);
            if (result == null || result.size() < 2 || result.get(1) == null) {
                return null;
            }
            return new String(result.get(1), StandardCharsets.UTF_8);
        });

        return parseRequest(json);
    }

    @Override
    public void publishResponse(ClientMessage response) {
        if (response == null || response.getMessageId() == null || response.getMessageId().isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            String key = responseKey(response.getMessageId());
            redisTemplate.opsForList().leftPush(key, json);
            int ttl = relayConfig.getBus().getResponseTtlSeconds();
            if (ttl > 0) {
                redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to publish response: {}", e.getMessage());
        }
    }

    @Override
    public ClientMessage consumeResponse(String messageId, long timeoutMs) {
        if (messageId == null || messageId.isEmpty()) {
            return null;
        }
        String key = responseKey(messageId);

        String json;
        if (timeoutMs <= 0) {
            json = redisTemplate.opsForList().rightPop(key);
        } else {
            int timeoutSeconds = (int) Math.max(1, (timeoutMs + 999) / 1000);
            json = redisTemplate.execute((RedisConnection connection) -> {
                List<byte[]> result = connection.bRPop(timeoutSeconds, key.getBytes(StandardCharsets.UTF_8));
                if (result == null || result.size() < 2 || result.get(1) == null) {
                    return null;
                }
                return new String(result.get(1), StandardCharsets.UTF_8);
            });
        }

        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ClientMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse response: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void markClientOnline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().add(onlineClientsKey(), clientId);
    }

    @Override
    public void markClientOffline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().remove(onlineClientsKey(), clientId);
    }

    @Override
    public boolean isClientOnline(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return false;
        }
        Boolean member = redisTemplate.opsForSet().isMember(onlineClientsKey(), clientId);
        return Boolean.TRUE.equals(member);
    }

    private BusRequest parseRequest(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BusRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse request: {}", e.getMessage());
            return null;
        }
    }

    private String requestKey(String clientId) {
        return relayConfig.getBus().getKeyPrefix() + ":req:" + clientId;
    }

    private String responseKey(String messageId) {
        return relayConfig.getBus().getKeyPrefix() + ":resp:" + messageId;
    }

    private String onlineClientsKey() {
        return relayConfig.getBus().getKeyPrefix() + ":clients:online";
    }
}
