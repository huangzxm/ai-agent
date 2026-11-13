package com.yupi.yuaiagent.cs.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 认证后的用户身份，用于工具执行前的权限校验
 */
@Data
@AllArgsConstructor
public class UserIdentity {

    private String userId;

    /** CUSTOMER / AGENT_ADMIN */
    private Set<String> roles;

    /** 该用户拥有的订单，资源归属校验依据 */
    private Set<String> ownedOrderIds;

    public static UserIdentity anonymous() {
        return new UserIdentity("anonymous", new HashSet<>(), new HashSet<>());
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean ownsOrder(String orderId) {
        return orderId != null && ownedOrderIds != null && ownedOrderIds.contains(orderId);
    }
}
