package com.yupi.yuaiagent.cs.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 真实数据库订单仓储。未启用数据库时 isAvailable=false，由路由层回退 Mock。
 */
@Component
@Slf4j
public class DbOrderRepository implements OrderRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DbOrderRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public boolean isAvailable() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    private JdbcTemplate jdbc() {
        return jdbcTemplateProvider.getObject();
    }

    @Override
    public Order findOrder(String orderId) {
        List<Order> orders = jdbc().query(
                "SELECT order_id, user_id, product, amount, status, pay_time, address "
                        + "FROM cs_order WHERE order_id = ?",
                (rs, rowNum) -> new Order(
                        rs.getString("order_id"),
                        rs.getString("user_id"),
                        rs.getString("product"),
                        rs.getDouble("amount"),
                        rs.getString("status"),
                        rs.getString("pay_time"),
                        rs.getString("address")),
                orderId);
        return orders.isEmpty() ? null : orders.get(0);
    }

    @Override
    public List<String> findLogistics(String orderId) {
        return jdbc().query(
                "SELECT content FROM cs_order_logistics WHERE order_id = ? ORDER BY seq",
                (rs, rowNum) -> rs.getString("content"),
                orderId);
    }

    @Override
    public String applyRefund(String orderId, String reason) {
        Order order = findOrder(orderId);
        if (order == null) {
            return null;
        }
        String refundId = "RF" + System.currentTimeMillis();
        jdbc().update("UPDATE cs_order SET status = ? WHERE order_id = ?", "售后中", orderId);
        jdbc().update("INSERT INTO cs_refund (refund_id, order_id, user_id, reason, amount, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                refundId, orderId, order.userId(), reason, order.amount(), "APPLIED", System.currentTimeMillis());
        return refundId;
    }

    @Override
    public String createTicket(String userId, String summary) {
        String ticketId = "TK" + System.currentTimeMillis();
        jdbc().update("INSERT INTO cs_ticket (ticket_id, user_id, summary, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                ticketId, userId, summary, "OPEN", System.currentTimeMillis());
        return ticketId;
    }

    @Override
    public String queryErpPurchaseOrder(String orderId) {
        // 采购单由 ERP/MCP 提供，不属于本地业务表
        throw new UnsupportedOperationException("ERP 采购单请通过 MCP 服务查询");
    }
}
