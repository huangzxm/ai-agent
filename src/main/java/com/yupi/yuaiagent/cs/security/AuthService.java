package com.yupi.yuaiagent.cs.security;

import com.yupi.yuaiagent.cs.model.UserIdentity;
import com.yupi.yuaiagent.cs.persistence.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：token → 用户身份。
 * 用户与权限数据由 UserDirectory 提供（数据库优先，Mock 兜底）。
 * 生产可替换为 SSO/OAuth2 校验，UserDirectory 对接真实用户中心。
 */
@Component
public class AuthService {

    private static final long IDENTITY_CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    private final UserDirectory userDirectory;

    /** 会话级身份缓存：有效期内只做内存比对 */
    private final Map<String, CachedIdentity> identityCache = new ConcurrentHashMap<>();

    public AuthService(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    /**
     * 校验 token，支持 demo-token-{userId} 形式；空 token 或未知用户返回匿名身份
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
        Set<String> roles = userDirectory.findRoles(userId);
        UserIdentity identity;
        if (roles.isEmpty()) {
            identity = UserIdentity.anonymous();
        } else {
            identity = new UserIdentity(userId, roles, userDirectory.findOwnedOrders(userId));
        }
        identityCache.put(token, new CachedIdentity(identity, System.currentTimeMillis() + IDENTITY_CACHE_TTL_MILLIS));
        return identity;
    }

    public Optional<String> resolveUserId(String token) {
        UserIdentity identity = verify(token);
        return "anonymous".equals(identity.getUserId()) ? Optional.empty() : Optional.of(identity.getUserId());
    }

    private record CachedIdentity(UserIdentity identity, long expireAt) {
    }
}
