-- V1: 초기 스키마 생성
-- 모바일 상품권 관리 시스템 전체 테이블

-- ============================================================
-- 지자체 (Region)
-- ============================================================
CREATE TABLE regions (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name                    VARCHAR(50)  NOT NULL,
    region_code             VARCHAR(10)  NOT NULL UNIQUE,
    status                  VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    discount_rate           DECIMAL(5,2) NOT NULL,
    purchase_limit_per_person DECIMAL(15,2) NOT NULL,
    monthly_issuance_limit  DECIMAL(15,2) NOT NULL,
    refund_threshold_ratio  DECIMAL(3,2) NOT NULL,
    settlement_period       VARCHAR(10)  NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 회원 (Member)
-- ============================================================
CREATE TABLE members (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    name                    VARCHAR(50)  NOT NULL,
    password                VARCHAR(255) NOT NULL,
    role                    VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status                  VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 가맹점 (Merchant)
-- ============================================================
CREATE TABLE merchants (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name                    VARCHAR(100) NOT NULL,
    business_number         VARCHAR(20)  NOT NULL,
    category                VARCHAR(30)  NOT NULL,
    region_id               BIGINT       NOT NULL,
    owner_id                BIGINT       NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 상품권 (Voucher)
-- ============================================================
CREATE TABLE vouchers (
    id                      BIGINT        PRIMARY KEY AUTO_INCREMENT,
    voucher_code            VARCHAR(19)   NOT NULL UNIQUE,
    face_value              DECIMAL(15,2) NOT NULL,
    balance                 DECIMAL(15,2) NOT NULL,
    member_id               BIGINT        NOT NULL,
    region_id               BIGINT        NOT NULL,
    purchased_at            DATETIME(6)   NOT NULL,
    expires_at              DATETIME(6)   NOT NULL,
    status                  VARCHAR(25)   NOT NULL DEFAULT 'ACTIVE',
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    INDEX idx_voucher_member (member_id, status),
    INDEX idx_voucher_region_status (region_id, status, expires_at),
    INDEX idx_voucher_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 거래 (Transaction)
-- ============================================================
CREATE TABLE transactions (
    id                      BIGINT        PRIMARY KEY AUTO_INCREMENT,
    type                    VARCHAR(20)   NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    voucher_id              BIGINT,
    merchant_id             BIGINT,
    member_id               BIGINT,
    original_transaction_id BIGINT,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    INDEX idx_tx_voucher (voucher_id, created_at),
    INDEX idx_tx_merchant_period (merchant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 원장 (LedgerEntry) - 불변, INSERT ONLY
-- ============================================================
CREATE TABLE ledger_entries (
    id                      BIGINT        PRIMARY KEY AUTO_INCREMENT,
    account                 VARCHAR(30)   NOT NULL,
    side                    VARCHAR(10)   NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    transaction_id          BIGINT        NOT NULL,
    entry_type              VARCHAR(20)   NOT NULL,
    created_at              DATETIME(6)   NOT NULL,

    INDEX idx_ledger_tx (transaction_id),
    INDEX idx_ledger_account (account, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 정산 (Settlement)
-- ============================================================
CREATE TABLE settlements (
    id                      BIGINT        PRIMARY KEY AUTO_INCREMENT,
    merchant_id             BIGINT        NOT NULL,
    period_start            DATE          NOT NULL,
    period_end              DATE          NOT NULL,
    total_amount            DECIMAL(15,2) NOT NULL,
    status                  VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    dispute_reason          TEXT,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    UNIQUE KEY uk_settlement_period (merchant_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 감사 로그 (AuditLog)
-- ============================================================
CREATE TABLE audit_logs (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    event_id                VARCHAR(36)  NOT NULL UNIQUE,
    event_type              VARCHAR(50)  NOT NULL,
    severity                VARCHAR(10)  NOT NULL,
    aggregate_type          VARCHAR(30)  NOT NULL,
    aggregate_id            BIGINT       NOT NULL,
    actor_id                BIGINT,
    actor_type              VARCHAR(20),
    action                  VARCHAR(50)  NOT NULL,
    previous_state          JSON,
    current_state           JSON,
    metadata                JSON,
    idempotency_key         VARCHAR(64),
    created_at              DATETIME(6)  NOT NULL,

    INDEX idx_audit_aggregate (aggregate_type, aggregate_id, created_at),
    INDEX idx_audit_event_type (event_type, created_at),
    INDEX idx_audit_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 멱등키 (IdempotencyKey)
-- ============================================================
CREATE TABLE idempotency_keys (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    idempotency_key         VARCHAR(64)  NOT NULL UNIQUE,
    response_body           TEXT         NOT NULL,
    response_status         INT          NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 실패 이벤트 (FailedEvent) - 재처리 대상
-- ============================================================
CREATE TABLE failed_events (
    id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
    event_type              VARCHAR(50)  NOT NULL,
    payload                 TEXT         NOT NULL,
    error_message           TEXT,
    retry_count             INT          NOT NULL DEFAULT 0,
    resolved                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
