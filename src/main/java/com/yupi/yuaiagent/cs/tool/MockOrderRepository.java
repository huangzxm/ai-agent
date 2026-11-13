package com.yupi.yuaiagent.cs.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
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

    /**
     * 模拟老旧 ERP 采购单接口：返回字段全、噪音多的长 JSON。
     * 关键事实（供应商反馈缺料停产）夹在中间，最后一条是「系统预排产」干扰项。
     */
    public String queryErpPurchaseOrder(String orderId) {
        JSONObject json = new JSONObject();
        json.set("orderId", orderId);
        json.set("supplier", "宁波精密制造有限公司");
        json.set("totalAmount", 128600.00);
        json.set("expectedDelivery", "2026-03-20");

        JSONArray history = new JSONArray();
        history.add(status("2026-03-01 09:12", "创建", "张三", "生产急需，请尽快审批"));
        history.add(status("2026-03-02 10:05", "审批", "李四", "同意采购"));
        history.add(status("2026-03-03 14:20", "审批", "王五", "财务复核通过"));
        history.add(status("2026-03-05 11:00", "下发供应商", "系统", "已发送采购订单至供应商门户"));
        // 关键业务事实：被大量系统日志淹没
        history.add(status("2026-03-10 16:40", "供应商反馈", "供应商", "原材料缺料，预计停产，交期需延后"));
        for (int i = 6; i <= 20; i++) {
            history.add(status("2026-03-1" + (i % 10) + " 08:00", "系统流转", "系统",
                    "节点自动流转 step-" + i + "，无需人工处理"));
        }
        history.add(status("2026-03-12 09:00", "系统预排产", "系统", "预排产中"));
        json.set("status_history", history);

        JSONArray materials = new JSONArray();
        for (int i = 1; i <= 50; i++) {
            JSONObject material = new JSONObject();
            material.set("code", "M" + String.format("%04d", i));
            material.set("name", "零件-" + i);
            material.set("quantity", 100 + i);
            material.set("unitPrice", 12.5 + i);
            material.set("warehouse", "WH-0" + (i % 5 + 1));
            materials.add(material);
        }
        json.set("material_list", materials);

        JSONArray approvals = new JSONArray();
        for (int i = 1; i <= 10; i++) {
            approvals.add(status("2026-03-0" + (i % 9 + 1) + " 10:00", "审批记录" + i, "审批人" + i, "同意"));
        }
        json.set("approval_form_history", approvals);
        json.set("internal_note", "该接口由 2013 年 ERP 系统提供，字段未做裁剪");
        return json.toString();
    }

    private JSONObject status(String time, String action, String operator, String comment) {
        JSONObject item = new JSONObject();
        item.set("time", time);
        item.set("action", action);
        item.set("operator", operator);
        item.set("comment", comment);
        return item;
    }
}
