package com.conquest.service

import com.conquest.service.common.InsufficientBalanceException
import com.conquest.service.debit.DebitMethod
import com.conquest.service.debit.DebitService
import com.conquest.service.deposit.DepositService
import com.conquest.service.integrity.IntegrityChecker
import com.conquest.service.wallet.WalletService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.atomic.AtomicLong

/** 순차 실행에서는 v1도 정합성이 맞는다. 문제는 동시성에서만 생긴다는 걸 보여주는 기준선. */
@SpringBootTest
@Import(TestcontainersConfig::class)
class WalletFlowTest @Autowired constructor(
    private val walletService: WalletService,
    private val depositService: DepositService,
    private val debitService: DebitService,
    private val integrityChecker: IntegrityChecker,
) {
    @Test
    fun `순차 입금과 차감은 잔액과 원장이 일치한다`() {
        val walletId = walletService.create(nextMemberId()).id

        depositService.deposit(walletId, 100_000)
        debitService.debit(walletId, 30_000, DebitMethod.DIRTY_CHECKING)
        val lastLedgerId = depositService.deposit(walletId, 5_000)

        val wallet = walletService.get(walletId)
        assertThat(wallet.balance).isEqualTo(75_000)
        assertThat(wallet.lastLedgerId).isEqualTo(lastLedgerId)
        assertThat(integrityChecker.findWalletMismatches()).noneMatch { it.walletId == walletId }
    }

    @Test
    fun `잔액보다 많이 차감하면 실패하고 원장도 남지 않는다`() {
        val walletId = walletService.create(nextMemberId()).id
        depositService.deposit(walletId, 10_000)

        assertThatThrownBy { debitService.debit(walletId, 10_001, DebitMethod.DIRTY_CHECKING) }
            .isInstanceOf(InsufficientBalanceException::class.java)

        assertThat(walletService.get(walletId).balance).isEqualTo(10_000)
        assertThat(integrityChecker.findWalletMismatches()).noneMatch { it.walletId == walletId }
    }

    companion object {
        private val memberSeq = AtomicLong(1_000)
        fun nextMemberId() = memberSeq.incrementAndGet()
    }
}
