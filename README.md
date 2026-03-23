# 모바일 상품권 관리 시스템

지역사랑상품권의 발행 → 유통 → 정산 전 생애주기를 관리하는 백엔드 시스템.
복식부기 원장, 보상 트랜잭션, 분산락 기반 동시성 제어로 **재무 무결성**, **감사 추적성**, **동시성 안전**을 보장한다.

---

## 설계 원칙

| 원칙 | 구현 방식 | 핵심 코드 |
|------|----------|----------|
| **재무 무결성** | 모든 금전 변동을 복식부기 원장(DEBIT/CREDIT 2행)으로 기록. 정합성 검증 배치로 캐시 잔액 vs 원장 합산 비교 | `LedgerService.record()` |
| **감사 추적성** | 취소/환불을 DELETE 대신 보상 트랜잭션으로 처리. 원 거래 불변 보존 | `TransactionCancelService.cancel()` |
| **동시성 안전** | Redisson 분산락 + DB 비관적 락 이중 방어. 10스레드 동시 결제 테스트로 검증 | `VoucherLockManager` |

---

## 시스템 아키텍처

Aggregate 중심 모듈러 모놀리스. 7개 모듈, 84개 소스 파일, 35개 API 엔드포인트.

```
com.komsco.voucher/
├── common/       ← BaseEntity, ErrorCode, AuditLog, Idempotency, FailedEvent
├── region/       ← 지자체 (Region + RegionPolicy + QueryDSL)
├── member/       ← 회원 (Member + JWT 인증 + Spring Security)
├── merchant/     ← 가맹점 (Merchant + 승인 플로우 + Settlement + 이의제기)
├── voucher/      ← 상품권 (발행, 결제, 환불, 청약철회, 만료 배치)
├── transaction/  ← 거래 (Transaction + 보상 트랜잭션 + 취소 API)
├── ledger/       ← 원장 (LedgerEntry + 정합성 검증 + 관리자 조회 API)
└── config/       ← Redis, Security, JWT, QueryDSL, Swagger
```

### 핵심 흐름

```
구매 ──→ 결제(부분사용) ──→ 추가결제 ──→ 잔액환불(60%+) ──→ 정산(역월)
  │         │                              │
  │         └── 거래 취소 (보상 트랜잭션)      └── 원장 기록 (동기, 같은 DB 트랜잭션)
  │
  └── 청약철회 (7일 이내 전액 환불)
```

### 모듈 간 의존 관계

```
voucher ──동기──→ ledger    (잔액 변경 시 원장 기록, 같은 DB 트랜잭션)
voucher ──동기──→ transaction (거래 생성)
merchant ─동기──→ transaction (정산 대상 거래 조회)

나머지 ──이벤트──→ audit     (비동기, Kafka 전환 가능)
```

---

## 기술적 의사결정

### 1. 왜 복식부기 원장인가

단순 잔액 필드 차감은 "돈이 어디서 와서 어디로 갔는지" 추적이 불가능하다. 지역사랑상품권은 지자체 예산으로 운영되는 공공 자금이므로, 감사 시 원장만으로 완벽한 자금 추적이 가능해야 한다.

```kotlin
// LedgerService.kt — 복식부기 기록
fun record(debitAccount: AccountCode, creditAccount: AccountCode,
           amount: BigDecimal, transactionId: Long, entryType: LedgerEntryType
): List<LedgerEntry> {
    val debitEntry = LedgerEntry(account = debitAccount, side = LedgerEntrySide.DEBIT, ...)
    val creditEntry = LedgerEntry(account = creditAccount, side = LedgerEntrySide.CREDIT, ...)
    return ledgerRepository.saveAll(listOf(debitEntry, creditEntry))  // 항상 2행
}
```

Voucher에 `balance` 캐시 필드를 두되, 진실의 원천은 항상 원장이다. `LedgerVerificationService`가 매일 캐시 잔액과 원장 합산을 비교하여 불일치를 탐지한다.

### 2. 왜 보상 트랜잭션인가

거래 취소를 DELETE나 상태 변경으로 구현하면 "왜 이 금액이 변경되었는가"를 증명할 수 없다.

```kotlin
// TransactionCancelService.kt — 원 거래를 수정하지 않고 역방향 엔트리 생성
val compensating = transactionService.create(
    type = TransactionType.CANCELLATION,
    amount = original.amount,
    originalTransactionId = original.id,  // 원 거래와 연결
)
ledgerService.record(
    debitAccount = AccountCode.VOUCHER_BALANCE,       // 원래: MERCHANT_RECEIVABLE
    creditAccount = AccountCode.MERCHANT_RECEIVABLE,  // 원래: VOUCHER_BALANCE
    amount = original.amount,                         // 역방향!
    transactionId = compensating.id,
    entryType = LedgerEntryType.CANCELLATION,
)
voucher.restoreBalance(original.amount)  // 잔액 복원
```

### 3. 왜 분산락 + DB 비관적 락 이중 방어인가

동일 상품권 동시 결제는 잔액 초과 차감이라는 치명적 사고를 유발한다.

```kotlin
// VoucherRedemptionService.kt
@Transactional
fun redeem(voucherId: Long, merchantId: Long, amount: BigDecimal): RedemptionResult {
    return lockManager.withVoucherLock(voucherId) {       // 1차: Redisson 분산락
        val voucher = voucherRepository.findByIdForUpdate(voucherId)  // 2차: DB SELECT FOR UPDATE
            ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)

        voucher.redeem(amount)                            // 잔액 차감
        val tx = transactionService.create(...)           // 거래 생성
        ledgerService.record(...)                         // 원장 기록 (동기, 같은 TX)
        tx.complete()
        eventPublisher.publishEvent(VoucherRedeemedEvent(...))  // 감사 로그 (이벤트)
        // ...
    }
}
```

10스레드 동시 결제 테스트로 검증:
- 50,000원 상품권에 10,000원 × 10건 동시 → 정확히 5건 성공, 5건 `INSUFFICIENT_BALANCE`
- 잔액 0원, 원장 차대변 균형 확인

### 4. 왜 멱등키 이중 저장인가

Redis TTL(24시간)로 빠르게 중복을 감지하고, DB에도 저장하여 Redis 장애/TTL 만료 후에도 중복을 방지한다. 중복 감지 시 409가 아닌 **원래 응답을 원래 상태코드와 함께 반환**하여 클라이언트가 정상 흐름을 이어갈 수 있도록 한다.

### 5. 왜 원장 기록은 이벤트가 아닌 동기 호출인가

`@TransactionalEventListener(AFTER_COMMIT)`으로 원장을 기록하면 커밋 후 리스너 실행 전 장애 시 **원장 누락**이 발생한다. 잔액 변경과 원장 기록은 반드시 같은 DB 트랜잭션에서 동기적으로 처리하여 불변식을 보장한다. 이벤트는 감사 로그, 알림 등 비핵심 부수효과에만 사용한다.

---

## 동시성 제어 전략

| 작업 | 전략 | 방지하는 장애 |
|------|------|-------------|
| 상품권 결제 | Redisson 분산락 + DB 비관적 락 | 이중 사용, 잔액 초과 차감 |
| 상품권 발행 | Member 분산락 + Region Redis 원자적 카운터(INCRBY) | 한도 초과 발행, 데드락 |
| 잔액 환불 / 청약철회 | Redisson 분산락 (`voucher:{id}`) | 사용 중 환불 경합 |
| 만료 배치 | DB 비관적 락 (건별 `REQUIRES_NEW` + `SELECT FOR UPDATE`) | 만료 중 결제 경합 |
| 가맹점 수정 | JPA Optimistic Lock (`@Version`) | 동시 상태 변경 |
| 정산 생성 | DB Unique Constraint (`merchant_id + period`) | 중복 정산 |

---

## 감사 로그 체계

모든 금전 변동 이벤트는 도메인 이벤트로 발행되어 자동으로 감사 로그에 기록된다.

| Event | 감사 등급 | 트랜잭션 처리 |
|-------|:--------:|-------------|
| 상품권 발행/결제/환불/철회/취소 | **CRITICAL** | `BEFORE_COMMIT` — 감사 실패 시 전체 롤백 |
| 가맹점 승인/거절/해지, 정산 확정 | **HIGH** | `AFTER_COMMIT` + `REQUIRES_NEW` |
| 가맹점 수정, 만료 처리 | **MEDIUM** | `AFTER_COMMIT` + `REQUIRES_NEW` |

- `AFTER_COMMIT` 리스너 실패 시 `failed_events` 테이블에 기록 → 스케줄러가 자동 재처리
- 감사 로그에 `previousState`/`currentState`를 JSON으로 저장하여 변경 전후 상태 추적

### Kafka 전환 가능 구조

이벤트 클래스는 순수 데이터 클래스로 Spring 의존성이 없다. Kafka 전환 시 `ApplicationEventPublisher` → `KafkaTemplate`, `@TransactionalEventListener` → `@KafkaListener`로 교체하면 도메인 코드 변경 없이 전환 가능.

---

## API 엔드포인트 (35개)

| 모듈 | Method | Endpoint | 설명 |
|------|--------|----------|------|
| **회원** | POST | `/api/v1/members/register` | 회원 가입 |
| | POST | `/api/v1/members/login` | 로그인 (JWT) |
| | POST | `/api/v1/members/{id}/suspend` | 회원 정지 |
| | POST | `/api/v1/members/{id}/withdraw` | 회원 탈퇴 |
| **지자체** | POST | `/api/v1/regions` | 지자체 생성 |
| | PUT | `/api/v1/regions/{id}/policy` | 정책 수정 |
| | POST | `/api/v1/regions/{id}/suspend` | 운영 중지 |
| **가맹점** | POST | `/api/v1/merchants` | 가맹점 등록 |
| | POST | `/api/v1/merchants/{id}/approve` | 심사 승인 |
| | POST | `/api/v1/merchants/{id}/reject` | 심사 거절 |
| | POST | `/api/v1/merchants/{id}/suspend` | 운영 정지 |
| | POST | `/api/v1/merchants/{id}/terminate` | 해지 |
| **상품권** | POST | `/api/v1/vouchers/purchase` | 구매 (멱등) |
| | POST | `/api/v1/vouchers/{id}/redeem` | 결제 (멱등) |
| | POST | `/api/v1/vouchers/{id}/refund` | 잔액 환불 (멱등) |
| | POST | `/api/v1/vouchers/{id}/withdraw` | 청약철회 (멱등) |
| | GET | `/api/v1/vouchers` | 목록 조회 (QueryDSL + 페이지네이션) |
| **거래** | POST | `/api/v1/transactions/{id}/cancel` | 거래 취소 (멱등) |
| **정산** | POST | `/api/v1/settlements/calculate` | 정산 생성 |
| | POST | `/api/v1/settlements/{id}/confirm` | 정산 확정 |
| | POST | `/api/v1/settlements/{id}/dispute` | 이의 제기 |
| **원장** | GET | `/api/v1/admin/ledger/entries/transaction/{id}` | 거래별 원장 조회 |
| | GET | `/api/v1/admin/ledger/balance/{account}` | 계정별 잔액 조회 |
| | POST | `/api/v1/admin/ledger/verify` | 정합성 검증 실행 |

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Kotlin 1.9 |
| Framework | Spring Boot 3.2 |
| ORM | Spring Data JPA + QueryDSL 5.1 |
| DB | MySQL 8.x (JSON Column, Generated Column) |
| Cache / Lock | Redis 7 (Redisson 3.27) |
| Events | Spring ApplicationEventPublisher (Kafka-replaceable) |
| Auth | JWT (jjwt 0.12) + Spring Security |
| Test | JUnit 5 + Kotest 5.8 + Testcontainers 1.19 |
| Monitoring | Spring Actuator + Micrometer (Prometheus) |
| API Docs | springdoc-openapi (Swagger UI) |
| Build | Gradle Kotlin DSL |
| Infra | Docker Compose (MySQL + Redis) |

---

## 실행 방법

```bash
# 1. MySQL + Redis 실행
docker compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 테스트 실행 (Testcontainers가 MySQL/Redis를 자동 구동)
./gradlew test

# 4. Swagger UI 확인
open http://localhost:8080/swagger-ui.html
```

---

## 테스트 (62건, 0 실패)

### 단위 테스트 (41건)

| 테스트 | 건수 | 검증 내용 |
|--------|:----:|----------|
| VoucherTest | 15 | 8개 상태 × 전이 조합, 잘못된 전이 시 예외, usageRatio 계산 |
| MerchantTest | 8 | PENDING→APPROVED/REJECTED→SUSPENDED→TERMINATED 전이 |
| MemberTest | 7 | PENDING→ACTIVE→SUSPENDED→WITHDRAWN 전이 |
| RegionTest | 7 | ACTIVE→SUSPENDED→DEACTIVATED 전이, 정책 수정 |
| VoucherCodeGeneratorTest | 4 | Luhn mod 36 체크 디짓 생성/검증, 유일성(100건) |

### 통합 테스트 (21건, Testcontainers)

| 테스트 | 건수 | 검증 내용 |
|--------|:----:|----------|
| **E2EFlowTest** | 6 | 전체 lifecycle, 보상 트랜잭션, 청약철회, 정산, 감사 로그 |
| **BoundaryTest** | 6 | 환불 60% 경계(59/60/61%), 청약철회 7일 경계, 정산 중복 방지 |
| **VoucherExpiryTest** | 3 | 만료 배치 처리, 부분사용 만료, 원장 균형 검증 |
| **ConcurrencyTest** | 2 | 10스레드 동시 결제(잔액>=0, 정확히 5건 성공), 실패 원인 검증 |
| LedgerServiceTest | 2 | 복식부기 2행 생성, 글로벌 차대변 균형 |
| RegionServiceTest | 2 | Region CRUD |

### 핵심 테스트 시나리오

**동시성 안전 검증:**
```
50,000원 상품권 × 10,000원 결제 × 10스레드 동시 →
  성공 5건 + 실패 5건 (모두 INSUFFICIENT_BALANCE)
  잔액 = 0원 (음수 불가 불변식 I1 보장)
  원장 차대변 균형 (불변식 I2 보장)
```

**보상 트랜잭션 검증:**
```
결제(30,000원) → 취소 →
  원 거래: CANCELLED (수정하지 않음)
  보상 거래: CANCELLATION + originalTransactionId 연결
  역방향 원장: debit VOUCHER_BALANCE, credit MERCHANT_RECEIVABLE
  잔액 복원: 50,000원
```

---

## 프로젝트 구조

```
komsco/
├── docs/
│   ├── 01-domain-design.md           # 도메인 & 비즈니스 규칙 (엔티티, 상태머신, 불변식)
│   ├── 02-architecture-decisions.md   # 아키텍처 설계 결정 (동시성, 이벤트, 감사, 모니터링)
│   ├── 03-implementation-roadmap.md   # 구현 로드맵 (17개 태스크, 의존성 그래프)
│   └── 04-implementation-plan.md      # 상세 구현 계획 (TDD 기반 단계별 코드)
├── src/main/kotlin/                   # 84 소스 파일
│   └── com/komsco/voucher/
│       ├── common/   (15)  ← BaseEntity, ErrorCode, Audit, Idempotency
│       ├── region/    (9)  ← Region + RegionPolicy + QueryDSL
│       ├── member/    (8)  ← Member + JWT + Security
│       ├── merchant/ (12)  ← Merchant + Settlement + Events
│       ├── voucher/  (19)  ← Voucher + 발행/결제/환불/철회/만료
│       ├── transaction/ (7) ← Transaction + 보상 트랜잭션
│       ├── ledger/    (7)  ← LedgerEntry + 정합성 검증
│       └── config/    (5)  ← Redis, Security, JWT, QueryDSL, Swagger
├── src/test/kotlin/                   # 13 테스트 파일, 62 테스트 케이스
├── docker-compose.yml
└── build.gradle.kts
```
