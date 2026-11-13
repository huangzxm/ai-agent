package com.yupi.yuaiagent.cs.tool;

import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客服业务工具：订单/物流/退款/工单。统一经 ToolGateway 调用，网关负责鉴权与归属校验
 */
@Component
public class CsBusinessTools {

    private final MockOrderRepository repository;

    public CsBusinessTools(MockOrderRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "Query order detail by orderId, including product, amount, status and address")
    public String queryOrder(@ToolParam(description = "Order number, e.g. 1001") String orderId) {
        MockOrderRepository.Order order = repository.findOrder(orderId);
        if (order == null) {
            return "未找到订单 " + orderId + "，请确认订单号是否正确";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.orderId());
        result.put("product", order.product());
        result.put("amount", order.amount());
        result.put("status", order.status());
        result.put("payTime", order.payTime());
        result.put("address", order.address());
        return JSONUtil.toJsonStr(result);
    }

    @Tool(description = "Query logistics tracking records by orderId")
    public String queryLogistics(@ToolParam(description = "Order number, e.g. 1001") String orderId) {
        return String.join("\n", repository.findLogistics(orderId));
    }

    @Tool(description = "Apply refund for an order. Only call this AFTER the user has explicitly confirmed the refund.")
    public String applyRefund(@ToolParam(description = "Order number") String orderId,
                              @ToolParam(description = "Refund reason provided by user") String reason) {
        MockOrderRepository.Order order = repository.findOrder(orderId);
        if (order == null) {
            return "退款失败：订单不存在";
        }
        if ("售后中".equals(order.status())) {
            return "订单 " + orderId + " 已存在进行中的售后申请，请勿重复提交";
        }
        if ("待付款".equals(order.status()) || "已取消".equals(order.status())) {
            return "退款失败：订单 " + orderId + " 当前状态为 " + order.status() + "，无需退款";
        }
        String refundId = repository.applyRefund(orderId, reason);
        return "退款申请已提交，退款单号 " + refundId + "，金额 " + order.amount()
                + " 元，预计 1-3 个工作日原路退回，请留意到账通知";
    }

    @Tool(description = "Create a CRM ticket for issues that need follow-up by support staff")
    public String createCrmTicket(@ToolParam(description = "User id, e.g. u1") String userId,
                                  @ToolParam(description = "Short summary of the issue") String summary) {
        String ticketId = repository.createTicket(userId, summary);
        return "工单已创建，工单号 " + ticketId + "，客服将在 1 个工作日内跟进处理";
    }
}
