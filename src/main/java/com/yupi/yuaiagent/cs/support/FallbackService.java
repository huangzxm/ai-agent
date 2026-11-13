package com.yupi.yuaiagent.cs.support;

import com.yupi.yuaiagent.cs.human.HumanTransferService;
import com.yupi.yuaiagent.cs.human.TransferReason;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.TransferRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

/**
 * 异常分类与优雅降级：重试 → 简化 → 转人工，保证任何异常都有兜底出口
 */
@Component
@Slf4j
public class FallbackService {

    private final HumanTransferService humanTransferService;
    private final MetricsCollector metricsCollector;

    public FallbackService(HumanTransferService humanTransferService, MetricsCollector metricsCollector) {
        this.humanTransferService = humanTransferService;
        this.metricsCollector = metricsCollector;
    }

    public enum FailureType {
        TIMEOUT, RATE_LIMIT, TOOL_ERROR, LLM_ERROR, LOW_CONFIDENCE, UNKNOWN
    }

    public FailureType classify(Throwable throwable) {
        if (throwable == null) {
            return FailureType.UNKNOWN;
        }
        if (throwable instanceof TimeoutException) {
            return FailureType.TIMEOUT;
        }
        String message = String.valueOf(throwable.getMessage()).toLowerCase();
        if (message.contains("timeout") || message.contains("timed out")) {
            return FailureType.TIMEOUT;
        }
        if (message.contains("rate limit") || message.contains("429")) {
            return FailureType.RATE_LIMIT;
        }
        if (message.contains("tool")) {
            return FailureType.TOOL_ERROR;
        }
        return FailureType.LLM_ERROR;
    }

    /**
     * 降级处理：统一转人工，并给出用户可读话术
     */
    public FallbackOutcome fallback(SessionContext session, Throwable throwable, TransferReason reason) {
        FailureType type = classify(throwable);
        metricsCollector.recordFallback(type.name());
        log.warn("[CS] 触发降级 sessionId={}, type={}, reason={}, error={}",
                session.getSessionId(), type, reason, throwable == null ? "n/a" : throwable.getMessage());

        TransferRecord record = humanTransferService.transferToHuman(session, reason);
        String answer = switch (type) {
            case TIMEOUT -> "当前咨询人数较多，响应超时。";
            case RATE_LIMIT -> "系统繁忙，正在限流保护。";
            case TOOL_ERROR -> "业务系统暂时不可用。";
            default -> "抱歉，这个问题我暂时无法准确处理。";
        };
        return new FallbackOutcome(answer + humanTransferService.queueMessage(record), true, record);
    }

    public FallbackOutcome lowConfidence(SessionContext session, String answerPrefix) {
        metricsCollector.recordFallback(FailureType.LOW_CONFIDENCE.name());
        TransferRecord record = humanTransferService.transferToHuman(session, TransferReason.LOW_CONFIDENCE);
        return new FallbackOutcome(answerPrefix + humanTransferService.queueMessage(record), true, record);
    }

    public record FallbackOutcome(String answer, boolean needHuman, TransferRecord record) {
    }
}
