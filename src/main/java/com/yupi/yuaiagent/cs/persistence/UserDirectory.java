package com.yupi.yuaiagent.cs.persistence;

import java.util.Set;

/**
 * 用户与权限数据访问接口：角色、订单归属。
 */
public interface UserDirectory {

    Set<String> findRoles(String userId);

    Set<String> findOwnedOrders(String userId);
}
