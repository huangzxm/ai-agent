package com.yupi.yuaiagent.cs.security;

import com.yupi.yuaiagent.cs.model.UserIdentity;
import com.yupi.yuaiagent.cs.tool.MockOrderRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 工具执行前的权限校验：由代码判定，不依赖 LLM 自述，杜绝幻觉越权
 */
@Component
public class PermissionChecker {

    /** 写操作工具：必须登录 */
    private static final Set<String> WRITE_TOOLS = Set.of("applyRefund", "createCrmTicket", "cancelOrder", "modifyAddress");

    /** 涉及订单隐私/资产的工具：必须校验订单归属 */
    private static final Set<String> ORDER_SCOPED_TOOLS = Set.of(
            "queryOrder", "queryLogistics", "applyRefund", "cancelOrder", "modifyAddress");

    private final MockOrderRepository orderRepository;

    public PermissionChecker(MockOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public CheckResult check(UserIdentity identity, String toolName, Map<String, Object> arguments) {
        if (identity.hasRole("AGENT_ADMIN")) {
            return CheckResult.allow();
        }
        boolean anonymous = "anonymous".equals(identity.getUserId());

        if (WRITE_TOOLS.contains(toolName) && anonymous) {
            return CheckResult.deny("AUTH_DENIED", "该操作需要登录后执行，请先完成身份验证");
        }

        if (ORDER_SCOPED_TOOLS.contains(toolName)) {
            Object orderId = arguments == null ? null : arguments.get("orderId");
            if (orderId == null || String.valueOf(orderId).isBlank()) {
                return CheckResult.deny("PARAM_INVALID", "缺少订单号参数 orderId");
            }
            if (anonymous) {
                return CheckResult.deny("AUTH_DENIED", "查询订单信息需要先登录验证身份");
            }
            if (!identity.ownsOrder(String.valueOf(orderId))) {
                return CheckResult.deny("RESOURCE_DENIED",
                        "订单 " + orderId + " 不属于当前账号，已拦截越权访问");
            }
            // 业务状态校验：即使订单归属正确，状态不满足也不允许执行（防止 LLM 幻觉操作）
            return checkOrderState(toolName, String.valueOf(orderId));
        }
        return CheckResult.allow();
    }

    private CheckResult checkOrderState(String toolName, String orderId) {
        MockOrderRepository.Order order = orderRepository.findOrder(orderId);
        if (order == null) {
            return CheckResult.deny("NOT_FOUND", "订单 " + orderId + " 不存在，请确认单号");
        }
        Set<String> allowed = switch (toolName) {
            case "applyRefund" -> Set.of("待收货", "已完成", "待发货");
            case "cancelOrder" -> Set.of("待付款", "待发货");
            case "modifyAddress" -> Set.of("待付款", "待发货");
            default -> Set.of();
        };
        if (!allowed.isEmpty() && !allowed.contains(order.status())) {
            return CheckResult.deny("ORDER_STATE_DENIED",
                    "订单当前状态为「" + order.status() + "」，不满足该操作的执行条件，已拦截");
        }
        return CheckResult.allow();
    }

    public record CheckResult(boolean allowed, String errorCode, String message) {
        public static CheckResult allow() {
            return new CheckResult(true, null, null);
        }

        public static CheckResult deny(String errorCode, String message) {
            return new CheckResult(false, errorCode, message);
        }
    }
}
