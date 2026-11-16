package com.yupi.yuaiagent.cs.persistence;

/**
 * 写操作落库失败。写路径不静默回退 Mock，避免"提示成功但数据未落库"。
 */
public class PersistenceWriteException extends RuntimeException {

    public PersistenceWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
