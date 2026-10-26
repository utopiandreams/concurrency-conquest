# 스키마 설계 (v1)

운영하던 고정 가상계좌 입금 구조를 재현한다. 벤더 이름 대신 `vbank`(가상계좌)로 일반화했고,
실험에 필요 없는 세부 기능(입금 가능 시간, 입금 횟수 제한, 에러로그 등)은 생략했다.

## 전체 구조

```
[가상계좌 데몬] ──INSERT──→ [가상계좌 DB (hecto VM)]
 (vbank-sim이 대체)            ├ vbank_account      계좌 목록
                               ├ vbank_deposit      입금 거래 (입금만, 취소 없음)
                               ├ vbank_settlement   은행별 일 입금 합계
                               └ vbank_checkpoint   폴러 진행 위치 ← 서비스가 직접 만들어 관리
                                        │
                          폴러: deposited_at >= checkpoint 로 읽음 (같은 초 재조회)
                                        ↓
                               [서비스 DB (svc-db)]
                                 ├ vaccount_mapping / _history   계좌 ↔ 회원
                                 ├ wallet                        잔액, last_ledger_id
                                 └ ledger                        입금/차감/조정, IN/OUT (금액은 항상 양수)
```

## 입금 처리 흐름 (당시 구조)

1. 폴러가 `vbank_deposit`을 `deposited_at >= 체크포인트`로 조회한다.
   - 입금일시가 초 단위라 같은 초에 늦게 들어온 건을 놓치지 않도록 **마지막 초를 다시 읽는다.**
2. 건마다 서비스 원장에 `vbank_tx_no`가 있는지 **SELECT로 확인**하고, 있으면 건너뛴다.
3. 서비스 DB 트랜잭션: 원장 INSERT → 지갑 반영 → **커밋**
4. 그 뒤 가상계좌 DB의 체크포인트를 갱신한다 (**별도 DB, 별도 커밋**).

두 DB가 한 트랜잭션으로 묶이지 않는다. 3과 4 사이에서 죽으면 같은 범위를 다시 읽고,
2의 멱등 처리로 걸러진다 (최소 한 번 처리 + 멱등 = 결과적으로 한 번 반영).

## 가상계좌 DB (hecto VM)

```sql
CREATE TABLE vbank_account (
  account_no      VARCHAR(20) NOT NULL,
  bank_code       CHAR(3)     NOT NULL,
  holder_name     VARCHAR(50) NOT NULL,   -- 예금주
  assigned        CHAR(1)     NOT NULL,   -- 할당여부 Y/N
  deposit_enabled CHAR(1)     NOT NULL,   -- 입금가능여부 Y/N
  PRIMARY KEY (account_no)
);

CREATE TABLE vbank_deposit (
  tx_no           VARCHAR(30) NOT NULL,   -- 고유식별번호 (auto increment 아님)
  account_no      VARCHAR(20) NOT NULL,
  bank_code       CHAR(3)     NOT NULL,
  depositor_name  VARCHAR(50) NOT NULL,   -- 입금자명
  amount          BIGINT      NOT NULL,   -- 입금만 있으므로 항상 양수
  bank_paid_at    DATETIME    NOT NULL,   -- 은행입금시각
  deposited_at    DATETIME    NOT NULL,   -- 입금일시 (데몬 기록, 초 단위). 폴링 기준
  PRIMARY KEY (tx_no),
  KEY idx_deposited_at (deposited_at)
);

CREATE TABLE vbank_settlement (
  settle_date     DATE        NOT NULL,
  bank_code       CHAR(3)     NOT NULL,
  total_amount    BIGINT      NOT NULL,   -- 은행별 일 입금 합계
  PRIMARY KEY (settle_date, bank_code)
);

-- 벤더 테이블이 아니라 서비스 개발자가 가상계좌 DB에 직접 만든 테이블
CREATE TABLE vbank_checkpoint (
  name            VARCHAR(30) NOT NULL,
  last_read_at    DATETIME    NOT NULL,   -- 마지막으로 읽은 deposited_at
  updated_at      DATETIME(6) NOT NULL,
  PRIMARY KEY (name)
);
```

## 서비스 DB (svc-db)

```sql
CREATE TABLE vaccount_mapping (
  account_no   VARCHAR(20) NOT NULL,
  bank_code    CHAR(3)     NOT NULL,
  member_id    BIGINT      NOT NULL,
  mapped_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (account_no),
  KEY idx_member (member_id)
);

CREATE TABLE vaccount_mapping_history (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  account_no   VARCHAR(20) NOT NULL,
  member_id    BIGINT      NOT NULL,
  action       VARCHAR(10) NOT NULL,   -- ASSIGN / RELEASE
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_account (account_no)
);

CREATE TABLE wallet (
  wallet_id      BIGINT      NOT NULL AUTO_INCREMENT,
  member_id      BIGINT      NOT NULL,
  balance        BIGINT      NOT NULL,
  last_ledger_id BIGINT      NULL,      -- 마지막으로 반영한 원장 ID
  updated_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (wallet_id),
  UNIQUE KEY uk_member (member_id)
);

CREATE TABLE ledger (
  ledger_id    BIGINT      NOT NULL AUTO_INCREMENT,
  wallet_id    BIGINT      NOT NULL,
  tx_type      VARCHAR(10) NOT NULL,   -- DEPOSIT / DEBIT / ADJUST
  direction    VARCHAR(3)  NOT NULL,   -- IN / OUT (잔액 증감 방향)
  amount       BIGINT      NOT NULL,   -- 항상 양수. 증감은 direction으로 구분
  vbank_tx_no  VARCHAR(30) NULL,       -- 입금이면 가상계좌 거래번호 (멱등 키)
  ref_id       VARCHAR(50) NULL,       -- 조정 근거 (다른 계좌에서 옮겨온 경우 등)
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (ledger_id),
  UNIQUE KEY uk_vbank_tx_no (vbank_tx_no),
  KEY idx_wallet (wallet_id, ledger_id),
  CONSTRAINT chk_amount_positive CHECK (amount > 0)
);
```

## 당시 구조와 다르게 한 것 / 불확실한 것

| 항목 | 당시 | 재현 | 비고 |
|---|---|---|---|
| `wallet.version` | 있었음 | **v1에서 제외** | Lost Update 방지 방법을 하나씩 적용하는 순서상, @Version 실험 단계에서 `ALTER TABLE`로 추가 |
| `ledger.vbank_tx_no` unique | 있었던 것 같음 (불확실) | 있음 | SELECT 체크는 평상시 중복 거르기, unique는 동시성 방어선 |
| 체크포인트 갱신 실패 대비 조치 | 별도 조치가 있었던 것 같으나 기억 안 남 | 순서(서비스 커밋 → 체크포인트) + 멱등만 재현 | |
| 폴러 대수 | 1대 | 1대 기준, 실험에서 2대 | k8s 롤링 배포 시 겹침 |
| 차감 멱등 키 | 없었음 | 없음 | 배치 재호출 이중 차감 시나리오의 전제 |
| 입금취소 | 벤더 측에서 기능 제거 | 없음 | |
| 운영 중 입금 누락 | **한 번도 없었음** | — | 블로그는 "누락이 없던 이유와 깨지는 조건"으로 쓴다 |

## 미결

- `ref_id`의 형태 (다른 원장 ID인지, 외부 문서 번호인지)
- `vbank_settlement`의 정확한 컬럼 (건수 포함 여부 등). 대사 배치 실험 전에 정한다.

## 정합성 검증 쿼리

실험 종료 후 지갑 잔액과 원장 합계가 같아야 한다. 다르면 Lost Update 등으로 반영이 유실된 것.

```sql
SELECT w.wallet_id, w.balance,
       COALESCE(SUM(CASE l.direction WHEN 'IN' THEN l.amount ELSE -l.amount END), 0) AS ledger_sum
FROM wallet w
LEFT JOIN ledger l ON l.wallet_id = w.wallet_id
GROUP BY w.wallet_id, w.balance
HAVING w.balance <> ledger_sum;
```

## 이 구조에서 나오는 실험

| 실험 | 근거가 되는 구조 |
|---|---|
| Lost Update (차감 4가지 방식) | `wallet.balance` 갱신, version 없음 → 단계별 추가 |
| 늦은 커밋으로 인한 입금 누락 | 초 단위 커서 + 마지막 1초만 재조회 |
| 체크포인트 갱신 전 크래시 | 두 DB 별도 커밋 → 멱등으로 방어되는지, 순서를 바꾸면 누락되는지 |
| 개선: 체크포인트를 서비스 DB로 이동 | 결과와 진행 위치를 한 트랜잭션으로 |
| 폴러 2대 (롤링 배포) | SELECT 멱등 체크의 경쟁 조건, unique가 막는지 |
| 배치 재호출 이중 차감 | 차감 멱등 키 없음 |
| 대사 배치 | `vbank_settlement` vs 서비스 원장 은행별 합계 |
