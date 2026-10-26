package com.conquest.service.debit

import com.conquest.service.ledger.Ledger
import com.conquest.service.ledger.LedgerRepository
import com.conquest.service.wallet.WalletRepository
import com.conquest.service.wallet.getWallet
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * v1. 지갑을 평범한 SELECT로 읽고, 엔티티 값을 바꾼 뒤 커밋 시점에 UPDATE.
 *
 * 두 트랜잭션이 같은 잔액(예: 10만)을 동시에 읽으면 각자 계산한 값으로
 * `UPDATE wallet SET balance = ?`를 실행하고, 나중에 커밋한 쪽이 앞의 변경을 덮어쓴다.
 * REPEATABLE READ여도 막히지 않는다 (일반 SELECT는 락을 걸지 않는 스냅샷 읽기).
 */
@Component
class DirtyCheckingDebitStrategy(
    private val walletRepository: WalletRepository,
    private val ledgerRepository: LedgerRepository,
) : DebitStrategy {
    override val method = DebitMethod.DIRTY_CHECKING

    @Transactional
    override fun debit(walletId: Long, amount: Long): Long {
        val wallet = walletRepository.getWallet(walletId)
        val ledger = ledgerRepository.save(Ledger.debit(walletId, amount))
        wallet.decrease(amount, ledger.id)
        return ledger.id
    }
}
