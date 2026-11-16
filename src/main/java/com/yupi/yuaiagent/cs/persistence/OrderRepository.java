package com.yupi.yuaiagent.cs.persistence;

import java.util.List;

/**
 * 订单数据访问接口：可切换真实数据库或内存 Mock。
 */
public interface OrderRepository {

    record Order(String orderId, String userId, String product, double amount,
                 String status, String payTime, String address) {
    }

    Order findOrder(String orderId);

    List<String> findLogistics(String orderId);

    /** 写操作：返回退款单号，订单不存在返回 null */
    String applyRefund(String orderId, String reason);

    String createTicket(String userId, String summary);

    /** 采购单由 ERP/MCP 提供，数据库模式下仍使用演示数据 */
    String queryErpPurchaseOrder(String orderId);
}
