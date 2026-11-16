package com.yupi.yuaiagent.cs.persistence;

import com.yupi.yuaiagent.cs.tool.MockOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 订单仓储路由：优先真实数据库，读操作在 DB 异常时回退 Mock；
 * 写操作在 DB 异常时抛出异常交给 Agent 处理，不静默回退，避免数据不一致。
 */
@Component
@Primary
@Slf4j
public class OrderRepositoryRouter implements OrderRepository {

    private static final long RETRY_MILLIS = 30_000L;

    private final DbOrderRepository dbRepository;
    private final MockOrderRepository mockRepository;

    private volatile boolean dbHealthy = true;
    private volatile long lastFailureAt = 0L;

    public OrderRepositoryRouter(DbOrderRepository dbRepository, MockOrderRepository mockRepository) {
        this.dbRepository = dbRepository;
        this.mockRepository = mockRepository;
    }

    @Override
    public Order findOrder(String orderId) {
        return read(() -> dbRepository.findOrder(orderId), () -> mockRepository.findOrder(orderId));
    }

    @Override
    public List<String> findLogistics(String orderId) {
        return read(() -> dbRepository.findLogistics(orderId), () -> mockRepository.findLogistics(orderId));
    }

    @Override
    public String applyRefund(String orderId, String reason) {
        if (!useDb()) {
            return mockRepository.applyRefund(orderId, reason);
        }
        try {
            String refundId = dbRepository.applyRefund(orderId, reason);
            markSuccess();
            return refundId;
        } catch (Exception e) {
            markFailure(e);
            throw new PersistenceWriteException("退款落库失败，已通知人工处理", e);
        }
    }

    @Override
    public String createTicket(String userId, String summary) {
        if (!useDb()) {
            return mockRepository.createTicket(userId, summary);
        }
        try {
            String ticketId = dbRepository.createTicket(userId, summary);
            markSuccess();
            return ticketId;
        } catch (Exception e) {
            markFailure(e);
            throw new PersistenceWriteException("工单落库失败，已通知人工处理", e);
        }
    }

    @Override
    public String queryErpPurchaseOrder(String orderId) {
        // ERP 数据由 MCP 提供，避免数据库模式下能力缺失
        return mockRepository.queryErpPurchaseOrder(orderId);
    }

    private <T> T read(Supplier<T> dbCall, Supplier<T> mockCall) {
        if (!useDb()) {
            return mockCall.get();
        }
        try {
            T result = dbCall.get();
            markSuccess();
            return result;
        } catch (Exception e) {
            markFailure(e);
            return mockCall.get();
        }
    }

    private boolean useDb() {
        if (!dbRepository.isAvailable()) {
            return false;
        }
        if (dbHealthy) {
            return true;
        }
        // 熔断后 30s 放行一次探测
        return System.currentTimeMillis() - lastFailureAt > RETRY_MILLIS;
    }

    private void markSuccess() {
        if (!dbHealthy) {
            log.info("[CS] 数据库读取恢复，切回真实数据源");
        }
        dbHealthy = true;
    }

    private void markFailure(Exception e) {
        dbHealthy = false;
        lastFailureAt = System.currentTimeMillis();
        log.warn("[CS] 数据库访问异常，读操作回退 Mock：{}", e.getMessage());
    }

    public boolean isDbActive() {
        return useDb();
    }
}
