# 모바일 상품권 관리 시스템

지역사랑상품권의 발행 → 유통 → 정산 전 생애주기를 관리하는 백엔드 시스템.
복식부기 원장, 보상 트랜잭션, 분산락 기반 동시성 제어로 **재무 무결성**과 **감사 추적성**을 보장한다.

---

## 설계 원칙

| 원칙 | 구현 |
|------|------|
| **재무 무결성** | 모든 금전 변동을 복식부기 원장(debit/credit 2행)으로 기록. 잔액 캐시와 원장의 정합성을 배치로 검증 |
| **감사 추적성** | 취소/환불을 DELETE가 아닌 보상 트랜잭션으로 처리. 모든 금전 흐름이 삭제 없이 원장에 보존 |
| **동시성 안전** | Redisson 분산락 + DB 비관적 락 이중 방어. 동일 상품권 동시 결제 시 잔액 초과 차감 원천 방지 |

---

## 시스템 아키텍처

Aggregate 중심 모듈러 모놀리스. 6개 도메인 모듈 + 공통 모듈.

```
com.komsco.voucher/
├── common/       ← BaseEntity, ErrorCode, AuditLog, Idempotency
├── region/       ← 지자체 (Region + RegionPolicy)
├── member/       ← 회원 (Member + JWT 인증)
├── merchant/     ← 가맹점 (Merchant + 승인 플로우 + Settlement)
├── voucher/      ← 상품권 (발행, 결제, 환불, 청약철회, 만료)
├── transaction/  ← 거래 (Transaction + 보상 트랜잭션)
├── ledger/       ← 원장 (LedgerEntry + 정합성 검증)
└── config/       ← Redis, Security, JWT
```

### 핵심 흐름

```
구매 ──→ 결제(부분사용) ──→ 추가결제 ──→ 잔액환불(60%+) ──→ 정산
  │         │                              │
  │         └── 취소 (보상 트랜잭션)          └── 원장 기록 (동기)
  │
  └── 청약철회 (7일 이내)
```

### 모듈 간 의존 관계

- `voucher` → `ledger` (동기 호출: 잔액 변경 시 원장 기록, 같은 DB 트랜잭션)
- `voucher` → `transaction` (동기 호출: 거래 생성)
- 나머지 모듈 간 통신: **Domain Event** (비동기, Kafka-replaceable)

---

## 기술적 의사결정

### 1. 왜 복식부기 원장인가

단순 잔액 필드 차감 방식은 "돈이 어디서 와서 어디로 갔는지" 추적이 불가능하다. 지역사랑상품권은 지자체 예산으로 운영되는 공공 자금이므로, 모든 금전 흐름을 차변/대변 쌍으로 기록하여 감사 시 원장만으로 완벽한 자금 추적이 가능하도록 설계했다.

성능을 위해 Voucher에 `balance` 캐시 필드를 두되, 진실의 원천(source of truth)은 항상 원장이다. 정합성 검증 배치가 주기적으로 캐시 잔액과 원장 합산을 비교한다.

### 2. 왜 보상 트랜잭션인가

거래 취소를 DELETE나 상태 변경으로 구현하면 "왜 이 금액이 변경되었는가"를 증명할 수 없다. 모든 취소/환불은 원 거래를 수정하지 않고 **역방향 보상 트랜잭션 + 역방향 원장 엔트리**를 새로 생성한다. `original_transaction_id`로 연결되어 감사 추적이 가능하다.

### 3. 왜 분산락 + DB 비관적 락 이중 방어인가

동일 상품권에 대한 동시 결제 요청은 잔액 초과 차감이라는 치명적 사고를 유발한다. Redisson 분산락으로 1차 직렬화하고, Redis 장애 시 DB `SELECT FOR UPDATE`가 2차 방어한다. 두 계층이 독립적으로 동작하므로 단일 장애점이 없다.

### 4. 왜 멱등키 이중 저장인가

네트워크 불안정으로 클라이언트가 같은 결제 요청을 반복 전송하면 이중 차감이 발생한다. Redis TTL(24시간)로 빠르게 중복을 감지하고, DB에도 저장하여 Redis 장애/TTL 만료 후에도 중복을 방지한다. 중복 감지 시 409가 아닌 **원래 응답을 그대로 반환**하여 클라이언트가 정상 흐름을 이어갈 수 있도록 한다.

### 5. 왜 원장 기록은 이벤트가 아닌 동기 호출인가

이벤트 리스너(`@TransactionalEventListener(AFTER_COMMIT)`)로 원장을 기록하면 커밋 후 리스너 실행 전 장애 시 원장 누락이 발생한다. 잔액 변경과 원장 기록은 반드시 같은 DB 트랜잭션에서 동기적으로 처리하여 불변식(I2, I3)을 보장한다. 이벤트는 감사 로그, 알림 등 비핵심 부수효과에만 사용한다.

---

## 동시성 제어 전략

| 작업 | 전략 | 방지하는 장애 |
|------|------|-------------|
| 상품권 결제 | Redisson 분산락 + DB 비관적 락 | 이중 사용, 잔액 초과 차감 |
| 상품권 발행 | Member 분산락 + Region Redis 원자적 카운터 | 한도 초과 발행, 데드락 |
| 잔액 환불 / 청약철회 | Redisson 분산락 (`voucher:{id}`) | 사용 중 환불 경합 |
| 만료 배치 | DB 비관적 락 (건별 `SELECT FOR UPDATE`) | 만료 중 결제 경합 |
| 가맹점 수정 | JPA Optimistic Lock (`@Version`) | 동시 상태 변경 |
| 정산 생성 | DB Unique Constraint | 중복 정산 |

---

## 도메인 이벤트 설계

Spring `ApplicationEventPublisher`를 사용한 인프로세스 이벤트. 이벤트 클래스는 순수 데이터 클래스로 Spring 의존성이 없으므로, Kafka 전환 시 도메인 코드 변경 없이 발행/구독 계층만 교체 가능하다.

| Event | 트리거 | Listener 책임 |
|-------|--------|--------------|
| `VoucherIssuedEvent` | 발행 완료 | CRITICAL 감사 로그 |
| `VoucherRedeemedEvent` | 결제 완료 | CRITICAL 감사 로그 |
| `VoucherRefundedEvent` | 잔액 환불 완료 | CRITICAL 감사 로그 |
| `VoucherWithdrawnEvent` | 청약철회 완료 | CRITICAL 감사 로그 |
| `VoucherExpiredEvent` | 만료 처리 | MEDIUM 감사 로그 |
| `TransactionCancelledEvent` | 거래 취소 | CRITICAL 감사 로그 |
| `MerchantApprovedEvent` | 가맹점 승인 | HIGH 감사 로그 |

**감사 등급별 처리:**
- **CRITICAL**: `BEFORE_COMMIT` — 동일 트랜잭션에서 기록. 실패 시 전체 롤백
- **HIGH/MEDIUM**: `AFTER_COMMIT` + `REQUIRES_NEW` — 별도 트랜잭션. 실패 시 `failed_events` 테이블에 기록 후 스케줄러가 재처리

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Kotlin 1.9 |
| Framework | Spring Boot 3.2 |
| ORM | Spring Data JPA + QueryDSL |
| DB | MySQL 8.x (JSON Column, Generated Column) |
| Cache / Lock | Redis 7 (Redisson) |
| Events | Spring ApplicationEventPublisher |
| Auth | JWT + Spring Security |
| Test | JUnit 5 + Kotest + Testcontainers |
| Monitoring | Spring Actuator + Micrometer (Prometheus) |
| Build | Gradle Kotlin DSL |
| Infra | Docker Compose |

---

## 실행 방법

```bash
# 1. MySQL + Redis 실행
docker compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 테스트 실행 (Testcontainers가 MySQL/Redis를 자동 구동)
./gradlew test
```

---

## 테스트

### 단위 테스트

- **Voucher 상태 머신**: 8개 상태 × 전이 조합, 잘못된 전이 시 예외 검증
- **Region/Member/Merchant 상태 머신**: 각 엔티티별 전이 규칙 검증
- **VoucherCodeGenerator**: Luhn mod 36 체크 디짓 생성/검증, 유일성
- **LedgerService**: 복식부기 2행 생성, 글로벌 차대변 균형

### 통합 테스트 (Testcontainers)

- **RegionService**: Region 생성 및 조회
- **LedgerService**: 실제 DB에서 복식부기 기록 및 균형 검증

---

## 프로젝트 구조

```
komsco/
├── docs/
│   ├── 01-domain-design.md           # 도메인 & 비즈니스 규칙
│   ├── 02-architecture-decisions.md   # 아키텍처 & 설계 결정
│   ├── 03-implementation-roadmap.md   # 구현 로드맵
│   └── 04-implementation-plan.md      # 상세 구현 계획
├── src/
│   ├── main/kotlin/com/komsco/voucher/
│   │   ├── common/     # 75 files total
│   │   ├── region/
│   │   ├── member/
│   │   ├── merchant/
│   │   ├── voucher/
│   │   ├── transaction/
│   │   ├── ledger/
│   │   └── config/
│   └── test/
├── docker-compose.yml
└── build.gradle.kts
```
