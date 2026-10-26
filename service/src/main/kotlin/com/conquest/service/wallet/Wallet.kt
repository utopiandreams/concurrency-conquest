package com.conquest.service.wallet

import com.conquest.service.common.InsufficientBalanceException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 지갑. 잔액은 반드시 원장(ledger)과 함께 바뀐다.
 * 초기 잔액도 입금 원장으로만 만든다 → 항상 balance == SUM(ledger)가 성립해야 한다.
 *
 * v1 구조에는 version 컬럼이 없다. 이 엔티티를 읽고 고쳐서 저장(변경 감지)하면
 * 동시 요청끼리 서로의 변경을 덮어쓸 수 있다 (Lost Update).
 */
@Entity
@Table(name = "wallet")
class Wallet(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    val id: Long = 0

    @Column(name = "balance", nullable = false)
    var balance: Long = 0
        protected set

    @Column(name = "last_ledger_id")
    var lastLedgerId: Long? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun increase(amount: Long, ledgerId: Long) {
        balance += amount
        touch(ledgerId)
    }

    fun decrease(amount: Long, ledgerId: Long) {
        if (balance < amount) throw InsufficientBalanceException(id, balance, amount)
        balance -= amount
        touch(ledgerId)
    }

    private fun touch(ledgerId: Long) {
        lastLedgerId = ledgerId
        updatedAt = LocalDateTime.now()
    }
}
