package com.yupi.yuaiagent.cs.persistence;

import com.yupi.yuaiagent.cs.CsProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

/**
 * 真实数据库接入配置。仅在 cs.persistence.enabled=true 时生效；
 * 未启用时应用仍可使用内存 Mock，完全不影响本地开发与演示。
 */
@Configuration
@Conditional(PersistenceConfiguredCondition.class)
@Slf4j
public class PersistenceConfig {

    @Bean
    public DataSource dataSource(CsProperties properties) {
        CsProperties.Persistence config = properties.getPersistence();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setDriverClassName(resolveDriver(config));
        dataSource.setMaximumPoolSize(config.getPoolSize());
        dataSource.setPoolName("cs-persistence");
        log.info("[CS] 数据库接入已启用：{} ({})", config.getUrl(), config.getDialect());
        return dataSource;
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public JdbcTemplate csJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 初始化业务表，并按需写入演示数据
     */
    @Bean
    public ApplicationRunner csSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate,
                                                 CsProperties properties) {
        return args -> {
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
            populator.execute(dataSource);
            log.info("[CS] 业务表初始化完成");
            if (properties.getPersistence().isSeed()) {
                seed(jdbcTemplate);
            }
        };
    }

    private String resolveDriver(CsProperties.Persistence config) {
        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            return config.getDriverClassName();
        }
        return "postgresql".equalsIgnoreCase(config.getDialect())
                ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver";
    }

    /**
     * 仅在表为空时插入演示数据，避免覆盖真实数据
     */
    private void seed(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cs_order", Integer.class);
        if (count != null && count > 0) {
            log.info("[CS] 业务表已有数据，跳过演示数据写入");
            return;
        }
        jdbcTemplate.update("INSERT INTO cs_order (order_id, user_id, product, amount, status, pay_time, address) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "1001", "u1", "机械键盘", 199.0, "待收货", "2026-11-10 10:20:00", "上海市浦东新区世纪大道 100 号");
        jdbcTemplate.update("INSERT INTO cs_order (order_id, user_id, product, amount, status, pay_time, address) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "1002", "u1", "降噪耳机", 899.0, "已完成", "2026-10-28 09:12:00", "上海市浦东新区世纪大道 100 号");
        jdbcTemplate.update("INSERT INTO cs_order (order_id, user_id, product, amount, status, pay_time, address) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "2001", "u2", "保温杯", 59.0, "待发货", "2026-11-12 21:05:00", "杭州市西湖区文三路 20 号");

        List<String> logistics1001 = List.of(
                "2026-11-10 18:00 商家已发货，顺丰速运 SF1234567890",
                "2026-11-11 08:30 到达上海浦东转运中心",
                "2026-11-12 09:10 派送中，快递员张师傅 13800000000");
        for (int i = 0; i < logistics1001.size(); i++) {
            jdbcTemplate.update("INSERT INTO cs_order_logistics (order_id, seq, content) VALUES (?, ?, ?)",
                    "1001", i + 1, logistics1001.get(i));
        }
        jdbcTemplate.update("INSERT INTO cs_order_logistics (order_id, seq, content) VALUES (?, ?, ?)",
                "1002", 1, "2026-10-29 15:20 已签收，签收人本人");
        jdbcTemplate.update("INSERT INTO cs_order_logistics (order_id, seq, content) VALUES (?, ?, ?)",
                "2001", 1, "订单待发货，暂无物流轨迹");

        jdbcTemplate.update("INSERT INTO cs_user (user_id, roles) VALUES (?, ?)", "u1", "CUSTOMER");
        jdbcTemplate.update("INSERT INTO cs_user (user_id, roles) VALUES (?, ?)", "u2", "CUSTOMER");
        jdbcTemplate.update("INSERT INTO cs_user (user_id, roles) VALUES (?, ?)", "admin", "CUSTOMER,AGENT_ADMIN");
        jdbcTemplate.update("INSERT INTO cs_user_order (user_id, order_id) VALUES (?, ?)", "u1", "1001");
        jdbcTemplate.update("INSERT INTO cs_user_order (user_id, order_id) VALUES (?, ?)", "u1", "1002");
        jdbcTemplate.update("INSERT INTO cs_user_order (user_id, order_id) VALUES (?, ?)", "u2", "2001");
        log.info("[CS] 演示数据写入完成");
    }
}
