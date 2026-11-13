package com.yupi.yuaiagent.cs.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Mock 订单/物流/工单数据，模拟 CRM 与订单中心
 */
@Component
public class MockOrderRepository {

    public record Order(String orderId, String userId, String product, double amount,
                        String status, String payTime, String address) {
    }

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, List<String>> logistics = new ConcurrentHashMap<>();
    private final Map<String, String> refundRecords = new ConcurrentHashMap<>();
    private final Map<String, String> tickets = new ConcurrentHashMap<>();
    private int sequence = 0;

    public MockOrderRepository() {
        orders.put("1001", new Order("1001", "u1", "机械键盘", 199.0, "待收货", "2026-11-10 10:20:00", "上海市浦东新区世纪大道 100 号"));
        orders.put("1002", new Order("1002", "u1", "降噪耳机", 899.0, "已完成", "2026-10-28 09:12:00", "上海市浦东新区世纪大道 100 号"));
        orders.put("2001", new Order("2001", "u2", "保温杯", 59.0, "待发货", "2026-11-12 21:05:00", "杭州市西湖区文三路 20 号"));

        logistics.put("1001", List.of(
                "2026-11-10 18:00 商家已发货，顺丰速运 SF1234567890",
                "2026-11-11 08:30 到达上海浦东转运中心",
                "2026-11-12 09:10 派送中，快递员张师傅 13800000000"));
        logistics.put("1002", List.of("2026-10-29 15:20 已签收，签收人本人"));
        logistics.put("2001", List.of("订单待发货，暂无物流轨迹"));
    }

    public Order findOrder(String orderId) {
        return orders.get(orderId);
    }

    public List<String> findLogistics(String orderId) {
        return logistics.getOrDefault(orderId, List.of("暂无物流信息"));
    }

    public synchronized String applyRefund(String orderId, String reason) {
        Order order = orders.get(orderId);
        if (order == null) {
            return null;
        }
        String refundId = "RF" + (++sequence) + String.format("%04d", System.currentTimeMillis() % 10000);
        refundRecords.put(refundId, "订单" + orderId + " 退款申请，原因：" + reason + "，金额：" + order.amount());
        orders.put(orderId, new Order(order.orderId(), order.userId(), order.product(),
                order.amount(), "售后中", order.payTime(), order.address()));
        return refundId;
    }

    public synchronized String createTicket(String userId, String summary) {
        String ticketId = "TK" + (++sequence) + String.format("%04d", System.currentTimeMillis() % 10000);
        tickets.put(ticketId, "用户" + userId + " 工单：" + summary);
        return ticketId;
    }
}
