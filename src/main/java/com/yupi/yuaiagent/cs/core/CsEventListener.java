package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.model.CsChatResponse;
import com.yupi.yuaiagent.cs.model.RouteDecision;
import com.yupi.yuaiagent.cs.model.TransferRecord;

/**
 * 处理过程事件回调，同步接口传空实现，SSE 接口推送给前端
 */
public interface CsEventListener {

    default void onRoute(RouteDecision decision) {
    }

    default void onStage(String stage) {
    }

    default void onMessage(String chunk) {
    }

    default void onTransfer(TransferRecord record) {
    }

    default void onDone(CsChatResponse response) {
    }

    default void onError(Throwable throwable) {
    }

    CsEventListener NOOP = new CsEventListener() {
    };
}
