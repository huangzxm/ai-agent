package com.yupi.yuaiagent.cs.security;

import com.yupi.yuaiagent.cs.model.UserIdentity;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 模拟认证中心：token → 用户身份。真实场景替换为 SSO/OAuth2 校验即可
 */
@Component
public class AuthService {

    private static final String CUSTOMER = "CUSTOMER";
    private static final String ADMIN = "AGENT_ADMIN";

    /** 模拟用户库：userId → 角色 */
    private static final Map<String, Set<String>> USER_ROLES = Map.of(
            "u1", Set.of(CUSTOMER),
            "u2", Set.of(CUSTOMER),
            "admin", Set.of(CUSTOMER, ADMIN));

    /** 模拟订单归属 */
    private static final Map<String, Set<String>> USER_ORDERS = Map.of(
            "u1", Set.of("1001", "1002"),
            "u2", Set.of("2001"),
            "admin", Set.of());

    /**
     * 校验 token，支持 demo-token-{userId} 形式；空 token 返回匿名身份
     */
    public UserIdentity verify(String token) {
        if (token == null || token.isBlank()) {
            return UserIdentity.anonymous();
        }
        String userId = token.startsWith("demo-token-") ? token.substring("demo-token-".length()) : token;
        if (!USER_ROLES.containsKey(userId)) {
            return UserIdentity.anonymous();
        }
        return new UserIdentity(userId,
                new HashSet<>(USER_ROLES.get(userId)),
                new HashSet<>(USER_ORDERS.getOrDefault(userId, Set.of())));
    }

    public Optional<String> resolveUserId(String token) {
        UserIdentity identity = verify(token);
        return "anonymous".equals(identity.getUserId()) ? Optional.empty() : Optional.of(identity.getUserId());
    }
}
