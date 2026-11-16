package com.yupi.yuaiagent.cs.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库用户目录：从 cs_user / cs_user_order 读取角色与订单归属。
 */
@Component
public class DbUserDirectory implements UserDirectory {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DbUserDirectory(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public boolean isAvailable() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    public Set<String> findRoles(String userId) {
        List<String> rows = jdbc().query(
                "SELECT roles FROM cs_user WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("roles"),
                userId);
        Set<String> roles = new HashSet<>();
        for (String row : rows) {
            if (row != null && !row.isBlank()) {
                for (String role : row.split(",")) {
                    roles.add(role.trim());
                }
            }
        }
        return roles;
    }

    @Override
    public Set<String> findOwnedOrders(String userId) {
        List<String> rows = jdbc().query(
                "SELECT order_id FROM cs_user_order WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("order_id"),
                userId);
        return new HashSet<>(rows);
    }

    private JdbcTemplate jdbc() {
        return jdbcTemplateProvider.getObject();
    }
}
