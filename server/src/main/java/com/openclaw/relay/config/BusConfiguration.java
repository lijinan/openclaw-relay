package com.openclaw.relay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.relay.bus.MemoryRelayMessageBus;
import com.openclaw.relay.bus.RedisRelayMessageBus;
import com.openclaw.relay.bus.RelayMessageBus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class BusConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "openclaw.relay.bus", name = "type", havingValue = "redis")
    public RelayMessageBus redisRelayMessageBus(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, RelayConfig relayConfig) {
        return new RedisRelayMessageBus(redisTemplate, objectMapper, relayConfig);
    }

    @Bean
    @ConditionalOnProperty(prefix = "openclaw.relay.bus", name = "type", havingValue = "memory", matchIfMissing = true)
    public RelayMessageBus memoryRelayMessageBus() {
        return new MemoryRelayMessageBus();
    }
}
