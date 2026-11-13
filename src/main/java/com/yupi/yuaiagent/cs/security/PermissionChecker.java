package com.yupi.yuaiagent.cs.security;

import com.yupi.yuaiagent.cs.model.UserIdentity;
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
