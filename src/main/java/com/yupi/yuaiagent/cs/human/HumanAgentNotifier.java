package com.yupi.yuaiagent.cs.human;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 人工客服通知出口：此处对接真实客服系统/企微/短信即可，业务代码不感知
 */
@Component
@Slf4j
public class HumanAgentNotifier {

    public void notifyHumanAgent(EscalationContext context) {
        String message = String.format("【AI客服降级】业务类型=%s，原因=%s，会话ID=%s，TraceId=%s，情绪=%.2f",
                context.getBusinessType(), context.getReason(), context.getSessionId(),
                context.getTraceId(), context.getUserSentiment());
        log.warn("[CS-人工通知] {}", message);
        // 生产环境替换为：httpClient.post(humanAgentSystemUrl, context) / 企微机器人 / 短信
    }
}
