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
     * 会话级身份缓存：同一 token 有效期内只做内存比对，避免重复构造与鉴权开销。
     */
    private final Map<String, CachedIdentity> identityCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long IDENTITY_CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    /**
     * 校验 token，支持 demo-token-{userId} 形式；空 token 返回匿名身份
     */
    public UserIdentity verify(String token) {
        if (token == null || token.isBlank()) {
            return UserIdentity.anonymous();
        }
        CachedIdentity cached = identityCache.get(token);
        if (cached != null && System.currentTimeMillis() < cached.expireAt()) {
            return cached.identity();
        }

        String userId = token.startsWith("demo-token-") ? token.substring("demo-token-".length()) : token;
        UserIdentity identity;
        if (!USER_ROLES.containsKey(userId)) {
            identity = UserIdentity.anonymous();
        } else {
            identity = new UserIdentity(userId,
                    new HashSet<>(USER_ROLES.get(userId)),
                    new HashSet<>(USER_ORDERS.getOrDefault(userId, Set.of())));
        }
        identityCache.put(token, new CachedIdentity(identity, System.currentTimeMillis() + IDENTITY_CACHE_TTL_MILLIS));
        return identity;
    }

    private record CachedIdentity(UserIdentity identity, long expireAt) {
    }

    public Optional<String> resolveUserId(String token) {
        UserIdentity identity = verify(token);
        return "anonymous".equals(identity.getUserId()) ? Optional.empty() : Optional.of(identity.getUserId());
    }
}
