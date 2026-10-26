package com.conquest.service.deposit

import com.conquest.service.ledger.Ledger
import com.conquest.service.ledger.LedgerRepository
import com.conquest.service.wallet.WalletRepository
import com.conquest.service.wallet.getWallet
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 입금 반영. 지금은 API로 직접 호출한다 (폴러는 이후 단계).
 * 차감 v1과 같은 방식(조회 → 수정 → 변경 감지)이라 동시 차감과 겹치면 Lost Update가 난다.
 */
@Service
class DepositService(
    private val walletRepository: WalletRepository,
    private val ledgerRepository: LedgerRepository,
) {
    @Transactional
    fun deposit(walletId: Long, amount: Long, vbankTxNo: String? = null): Long {
        val wallet = walletRepository.getWallet(walletId)
        val ledger = ledgerRepository.save(Ledger.deposit(walletId, amount, vbankTxNo))
        wallet.increase(amount, ledger.id)
        return ledger.id
    }
}
