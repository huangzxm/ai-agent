package com.yupi.yuaiagent.cs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private Rewrite rewrite = new Rewrite();
    private Token token = new Token();
    private Skill skill = new Skill();
    private Persistence persistence = new Persistence();

    @Data
    public static class Cache {
        /** 未命中问题全量暂存的短 TTL */
        private long ttlSeconds = 300;
        /** 命中次数达到阈值后晋升为长效缓存 */
        private long promotedTtlSeconds = 86400;
        /** 热点晋升阈值 */
        private int promoteThreshold = 5;
        private int maxSize = 500;
        /** 语义缓存命中阈值（余弦相似度） */
        private double semanticThreshold = 0.88;
        /** 冷数据回收时长（毫秒），默认 24 小时 */
        private long coldRetentionMillis = 86400000L;
    }

    @Data
    public static class Route {
        /** 低于该置信度转人工 */
        private double confidenceThreshold = 0.6;
        /** 情绪分高于该值开始计数 */
        private double emotionThreshold = 0.7;
        /** 连续情绪激动达到该次数才转人工 */
        private int angerStrikes = 2;
    }

    @Data
    public static class Rag {
        private double similarityThreshold = 0.5;
        private int topK = 3;
        /** 取最近多少条历史消息参与上下文 */
        private int historySize = 8;
        private Hybrid hybrid = new Hybrid();
        private Rerank rerank = new Rerank();
    }

    @Data
    public static class Hybrid {
        /** RRF 融合权重 */
        private double vectorWeight = 0.5;
        private double bm25Weight = 0.5;
        /** RRF 平滑常数 */
        private int rrfK = 60;
        /** 粗排后进入精排的候选数量 */
        private int candidateTopN = 20;
    }

    @Data
    public static class Rerank {
        /** local：内置轻量精排；dashscope：调用 gte-rerank */
        private String provider = "local";
        private String model = "gte-rerank-v2";
        private int timeoutMillis = 3000;
    }

    @Data
    public static class Rewrite {
        private boolean enabled = true;
        /** 重写结果缓存上限（命中即复用，语义相对稳定） */
        private int maxCacheSize = 2000;
        /** 业务术语标准化映射 */
        private Map<String, String> synonyms = new LinkedHashMap<>();
    }

    @Data
    public static class Token {
        /** 输入 token 单价（元/千 token），0 表示不估算 */
        private double inputPricePer1k = 0.0;
        /** 输出 token 单价（元/千 token） */
        private double outputPricePer1k = 0.0;
    }

    @Data
    public static class Skill {
        /** 声明式技能配置目录（classpath） */
        private String location = "classpath:skills/*.yml";
        /** 是否启用声明式技能 */
        private boolean enabled = true;
    }

    /**
     * 真实数据库接入。enabled=false 时全部走内存 Mock（本地开发默认）。
     * 开启后订单、用户、会话落库，数据库读失败自动回退 Mock（写操作不静默回退）。
     */
    @Data
    public static class Persistence {
        private boolean enabled = false;
        /** mysql | postgresql */
        private String dialect = "mysql";
        private String url = "";
        private String username = "";
        private String password = "";
        private String driverClassName = "";
        /** 是否写入演示数据（仅空表时写入，生产务必关闭） */
        private boolean seed = false;
        /** 连接池大小 */
        private int poolSize = 5;
    }

    @Data
    public static class Agent {
        private int maxSteps = 8;
        /** 同一工具+参数重复调用多少次判定为死循环 */
        private int repeatLimit = 2;
        /** AI 主动申请转人工需连续申请的次数，未达到则先继续澄清 */
        private int escalateAttempts = 3;
    }

    @Data
    public static class Human {
        /** 模拟坐席数量 */
        private int agentCount = 3;
        private int queueLimit = 100;
        /** 同一问题重复多少次后，主动询问是否需要转人工 */
        private int repeatQuestionStrikes = 3;
    }
}
