-- AI 客服系统业务表（MySQL / PostgreSQL 兼容）
-- 仅在 cs.persistence.enabled=true 时由 PersistenceConfig 执行

CREATE TABLE IF NOT EXISTS cs_order (
    order_id    VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    product     VARCHAR(128),
    amount      DECIMAL(12, 2),
    status      VARCHAR(32),
    pay_time    VARCHAR(32),
    address     VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS cs_order_logistics (
    order_id    VARCHAR(32) NOT NULL,
    seq         INT         NOT NULL,
    content     VARCHAR(512),
    PRIMARY KEY (order_id, seq)
);

CREATE TABLE IF NOT EXISTS cs_refund (
    refund_id   VARCHAR(32) PRIMARY KEY,
    order_id    VARCHAR(32) NOT NULL,
    user_id     VARCHAR(64) NOT NULL,
    reason      VARCHAR(256),
    amount      DECIMAL(12, 2),
    status      VARCHAR(32) DEFAULT 'APPLIED',
    created_at  BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS cs_ticket (
    ticket_id   VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    summary     VARCHAR(512),
    status      VARCHAR(32) DEFAULT 'OPEN',
    created_at  BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS cs_user (
    user_id     VARCHAR(64) PRIMARY KEY,
    roles       VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS cs_user_order (
    user_id     VARCHAR(64) NOT NULL,
    order_id    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, order_id)
);

CREATE TABLE IF NOT EXISTS cs_session (
    session_id    VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64),
    state         VARCHAR(32),
    context_json  TEXT,
    last_active_at BIGINT
);
