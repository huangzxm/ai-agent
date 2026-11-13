package com.yupi.yuaiagent.cs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 客服系统可调参数，统一走 cs.* 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "cs")
public class CsProperties {

    private Cache cache = new Cache();
    private Route route = new Route();
    private Rag rag = new Rag();
    private Agent agent = new Agent();
    private Human human = new Human();

    @Data
    public static class Cache {
        /** 未命中问题全量暂存的短 TTL */
        private long ttlSeconds = 300;
        /** 命中次数达到阈值后晋升为长效缓存 */
        private long promotedTtlSeconds = 86400;
        /** 热点晋升阈值 */
        private int promoteThreshold = 5;
        private int maxSize = 500;
    }

    @Data
    public static class Route {
        /** 低于该置信度转人工 */
        private double confidenceThreshold = 0.6;
        /** 情绪分高于该值转人工 */
        private double emotionThreshold = 0.7;
    }

    @Data
    public static class Rag {
        private double similarityThreshold = 0.5;
        private int topK = 3;
        /** 取最近多少条历史消息参与上下文 */
        private int historySize = 8;
    }

    @Data
    public static class Agent {
        private int maxSteps = 8;
        /** 同一工具+参数重复调用多少次判定为死循环 */
        private int repeatLimit = 2;
    }

    @Data
    public static class Human {
        /** 模拟坐席数量 */
        private int agentCount = 3;
        private int queueLimit = 100;
    }
}
