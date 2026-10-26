-- 스키마 v1: 당시 운영 구조 재현 (docs/design/schema.md)
-- wallet.version은 의도적으로 없음. @Version 전략을 만들 때 V2로 추가한다.

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
  action       VARCHAR(10) NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_account (account_no)
);

CREATE TABLE wallet (
  wallet_id      BIGINT      NOT NULL AUTO_INCREMENT,
  member_id      BIGINT      NOT NULL,
  balance        BIGINT      NOT NULL,
  last_ledger_id BIGINT      NULL,
  updated_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (wallet_id),
  UNIQUE KEY uk_member (member_id)
);

CREATE TABLE ledger (
  ledger_id    BIGINT      NOT NULL AUTO_INCREMENT,
  wallet_id    BIGINT      NOT NULL,
  tx_type      VARCHAR(10) NOT NULL,
  direction    VARCHAR(3)  NOT NULL,
  amount       BIGINT      NOT NULL,
  vbank_tx_no  VARCHAR(30) NULL,
  ref_id       VARCHAR(50) NULL,
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (ledger_id),
  UNIQUE KEY uk_vbank_tx_no (vbank_tx_no),
  KEY idx_wallet (wallet_id, ledger_id),
  CONSTRAINT chk_amount_positive CHECK (amount > 0)
);
