package com.openclaw.wecom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "wecom.relay")
public class RelayConfig {

    private int responseTimeout = 60000;

    private int pingInterval = 30000;

    private int maxPendingMessages = 1000;

    /**
     * 客户端认证配置：key 为 clientId，value 为 authToken
     * 示例：
     * clients:
     *   openclaw-company-a: token-a
     *   openclaw-company-b: token-b
     */
    private Map<String, String> clients = new HashMap<>();

    private String token = "";

    private String encodingAesKey = "";

    /**
     * 验证 clientId 和 authToken 是否匹配
     * @param clientId 客户端标识
     * @param authToken 认证令牌
     * @return 是否验证通过
     */
    public boolean authenticate(String clientId, String authToken) {
        String expectedToken = clients.get(clientId);
        return expectedToken != null && expectedToken.equals(authToken);
    }
}
