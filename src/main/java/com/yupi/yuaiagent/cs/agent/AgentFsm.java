package com.yupi.yuaiagent.cs.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 售后任务状态机：用代码约束每一步允许调用的工具，解决 LLM 跳步与越权问题。
 *
 * START → AUTHENTICATE → COLLECT_PARAMS → VERIFY_ORDER → CONFIRM → EXECUTE → FINISHED
 */
public final class AgentFsm {

    public enum State {
        START,
        AUTHENTICATE,
        COLLECT_PARAMS,
        VERIFY_ORDER,
        CONFIRM,
        EXECUTE,
        FINISHED,
        FAILED
    }

    /** 每个状态允许的工具白名单 */
    private static final Map<State, Set<String>> ALLOWED_TOOLS = Map.of(
            State.START, Set.of("queryOrder"),
            State.AUTHENTICATE, Set.of("queryOrder"),
            State.COLLECT_PARAMS, Set.of("queryOrder"),
            State.VERIFY_ORDER, Set.of("queryOrder", "queryLogistics"),
            // CONFIRM 阶段不允许任何写操作，必须等用户明确确认
            State.CONFIRM, Set.of("queryOrder"),
            State.EXECUTE, Set.of("applyRefund"),
            State.FINISHED, Set.of(),
            State.FAILED, Set.of());

    /** 明确确认：可直接进入执行阶段 */
    private static final List<String> STRONG_CONFIRM_WORDS = List.of(
            "确认", "确定", "同意", "是的", "申请退款", "确认退款", "没问题", "退吧", "就这么办");

    /** 模糊确认：先追问一次，避免误操作，也不直接转人工 */
    private static final List<String> AMBIGUOUS_CONFIRM_WORDS = List.of(
            "嗯", "哦", "好的", "好", "可以", "行", "成", "对");

    private static final List<String> REJECT_WORDS = List.of("取消", "不用了", "算了", "先不", "不要退");

    private static final List<String> NEGATIVE_WORDS = List.of(
            "不好", "不对", "不行", "不要", "不用", "别", "算了", "取消", "先不");

    private AgentFsm() {
    }

    public static Set<String> allowedTools(State state) {
        return ALLOWED_TOOLS.getOrDefault(state, Set.of());
    }

    public static boolean isAllowed(State state, String toolName) {
        return allowedTools(state).contains(toolName);
    }

    public static State parse(String value) {
        if (value == null || value.isBlank()) {
            return State.START;
        }
        try {
            return State.valueOf(value);
        } catch (IllegalArgumentException e) {
            return State.START;
        }
    }

    /**
     * 工具执行成功后推进状态
     */
    public static State advanceAfterTool(State current, String toolName, boolean success) {
        if (!success) {
            return current == State.EXECUTE ? State.FAILED : current;
        }
        return switch (current) {
            case START, AUTHENTICATE, COLLECT_PARAMS -> "queryOrder".equals(toolName)
                    ? State.VERIFY_ORDER : current;
            case VERIFY_ORDER -> "queryOrder".equals(toolName) || "queryLogistics".equals(toolName)
                    ? State.VERIFY_ORDER : current;
            case CONFIRM -> "applyRefund".equals(toolName) ? State.FINISHED : current;
            case EXECUTE -> "applyRefund".equals(toolName) ? State.FINISHED : current;
            default -> current;
        };
    }

    public static boolean isConfirm(String message) {
        return isStrongConfirm(message);
    }

    /** 明确确认（含"确认退款"等），可直接执行 */
    public static boolean isStrongConfirm(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = message.trim();
        if (NEGATIVE_WORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        return STRONG_CONFIRM_WORDS.stream().anyMatch(text::contains);
    }

    /**
     * 模糊确认（"嗯""好的""可以"）：语义上倾向确认，但不足以直接执行退款。
     * 由 Agent 追问一次明确确认，避免误操作，也不应因此转人工。
     */
    public static boolean isAmbiguousConfirm(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = message.trim();
        if (text.length() > 8 || NEGATIVE_WORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        if (STRONG_CONFIRM_WORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        return AMBIGUOUS_CONFIRM_WORDS.stream().anyMatch(text::contains);
    }

    public static boolean isReject(String message) {
        return message != null && REJECT_WORDS.stream().anyMatch(message::contains);
    }

    public static String describe(State state) {
        return switch (state) {
            case START -> "开始受理";
            case AUTHENTICATE -> "验证用户身份";
            case COLLECT_PARAMS -> "收集订单号等必要信息";
            case VERIFY_ORDER -> "核实订单状态";
            case CONFIRM -> "等待用户确认退款";
            case EXECUTE -> "执行退款";
            case FINISHED -> "任务完成";
            case FAILED -> "任务失败";
        };
    }
}
