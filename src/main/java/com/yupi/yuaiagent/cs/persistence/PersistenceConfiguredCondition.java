package com.yupi.yuaiagent.cs.persistence;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 只有「开启数据库」且「配置了真实连接地址」才装配 DataSource。
 * 这样 enabled=true 但 url 留空时，应用仍正常启动并走内存 Mock，不会启动失败。
 */
public class PersistenceConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Boolean enabled = context.getEnvironment()
                .getProperty("cs.persistence.enabled", Boolean.class, false);
        String url = context.getEnvironment().getProperty("cs.persistence.url", "");
        return Boolean.TRUE.equals(enabled) && url != null && !url.isBlank();
    }
}
