package com.yupi.yuaiagent.cs.persistence;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 内存用户目录（演示账号），数据库不可用时回退。
 */
@Component
public class MockUserDirectory implements UserDirectory {

    private static final Set<String> ROLES_U1 = Set.of("CUSTOMER");
    private static final Set<String> ROLES_ADMIN = Set.of("CUSTOMER", "AGENT_ADMIN");

    @Override
    public Set<String> findRoles(String userId) {
        return switch (userId) {
            case "u1", "u2" -> ROLES_U1;
            case "admin" -> ROLES_ADMIN;
            default -> Set.of();
        };
    }

    @Override
    public Set<String> findOwnedOrders(String userId) {
        return switch (userId) {
            case "u1" -> Set.of("1001", "1002");
            case "u2" -> Set.of("2001");
            default -> Set.of();
        };
    }
}
