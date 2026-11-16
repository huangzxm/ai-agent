package com.yupi.yuaiagent.cs.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 用户目录路由：优先真实数据库，读异常时回退内存演示账号。
 * 注意：数据库可用但查不到用户时不会回退，避免真实库中已禁用用户仍能登录。
 */
@Component
@Primary
@Slf4j
public class UserDirectoryRouter implements UserDirectory {

    private final DbUserDirectory dbDirectory;
    private final MockUserDirectory mockDirectory;

    private volatile boolean dbHealthy = true;
    private volatile long lastFailureAt;

    public UserDirectoryRouter(DbUserDirectory dbDirectory, MockUserDirectory mockDirectory) {
        this.dbDirectory = dbDirectory;
        this.mockDirectory = mockDirectory;
    }

    @Override
    public Set<String> findRoles(String userId) {
        return read(() -> dbDirectory.findRoles(userId), () -> mockDirectory.findRoles(userId));
    }

    @Override
    public Set<String> findOwnedOrders(String userId) {
        return read(() -> dbDirectory.findOwnedOrders(userId), () -> mockDirectory.findOwnedOrders(userId));
    }

    private Set<String> read(java.util.function.Supplier<Set<String>> dbCall,
                             java.util.function.Supplier<Set<String>> mockCall) {
        if (!useDb()) {
            return mockCall.get();
        }
        try {
            Set<String> result = dbCall.get();
            dbHealthy = true;
            return result;
        } catch (Exception e) {
            dbHealthy = false;
            lastFailureAt = System.currentTimeMillis();
            log.warn("[CS] 用户目录查询失败，回退 Mock：{}", e.getMessage());
            return mockCall.get();
        }
    }

    private boolean useDb() {
        if (!dbDirectory.isAvailable()) {
            return false;
        }
        if (dbHealthy) {
            return true;
        }
        return System.currentTimeMillis() - lastFailureAt > 30_000L;
    }
}
