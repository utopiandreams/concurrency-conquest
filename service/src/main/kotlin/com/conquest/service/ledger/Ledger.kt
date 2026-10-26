package com.conquest.service.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class TxType { DEPOSIT, DEBIT, ADJUST }

/** 잔액 증감 방향. 금액은 항상 양수이고 부호는 이걸로 정한다. */
enum class Direction { IN, OUT }

/** 서비스 원장. 한 번 쓰면 바꾸지 않는다 (INSERT only). */
@Entity
@Table(name = "ledger")
class Ledger(
    @Column(name = "wallet_id", nullable = false, updatable = false)
    val walletId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, updatable = false)
    val txType: TxType,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false)
    val direction: Direction,

    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,

    @Column(name = "vbank_tx_no", updatable = false)
    val vbankTxNo: String? = null,

    @Column(name = "ref_id", updatable = false)
    val refId: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    init {
        require(amount > 0) { "금액은 양수여야 합니다. amount=$amount" }
    }

    companion object {
        fun deposit(walletId: Long, amount: Long, vbankTxNo: String? = null) =
            Ledger(walletId, TxType.DEPOSIT, Direction.IN, amount, vbankTxNo = vbankTxNo)

        fun debit(walletId: Long, amount: Long) =
            Ledger(walletId, TxType.DEBIT, Direction.OUT, amount)
    }
}
