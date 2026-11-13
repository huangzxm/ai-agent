package com.yupi.yuaiagent.cs.support;

/**
 * 关键服务不可用（向量库熔断、MCP/ERP 超时）。
 * 在 ToB 场景下"宁可不回，不可乱回"，捕获后直接转人工，不做重试。
 */
public class CriticalServiceDownException extends RuntimeException {

    private final String service;

    public CriticalServiceDownException(String service, String message) {
        super(message);
        this.service = service;
    }

    public CriticalServiceDownException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
