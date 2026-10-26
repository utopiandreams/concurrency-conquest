package com.conquest.service

import com.conquest.service.debit.DebitMethod
import com.conquest.service.debit.DebitService
import com.conquest.service.deposit.DepositService
import com.conquest.service.integrity.IntegrityChecker
import com.conquest.service.wallet.WalletService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 같은 지갑에 입금과 차감을 동시에 보낸다.
 * 입금 합계와 차감 합계가 같으므로, 정상이라면 잔액은 처음 그대로여야 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class LostUpdateTest @Autowired constructor(
    private val walletService: WalletService,
    private val depositService: DepositService,
    private val debitService: DebitService,
    private val integrityChecker: IntegrityChecker,
) {
    @Test
    fun `v1 변경 감지는 동시 입금·차감에서 Lost Update가 발생한다`() {
        val initial = 1_000_000L
        val amount = 10_000L
        val requestsEach = 50

        val walletId = walletService.create(9_000_001).id
        depositService.deposit(walletId, initial)

        val result = runConcurrently(requestsEach * 2) { i ->
            if (i % 2 == 0) depositService.deposit(walletId, amount)
            else debitService.debit(walletId, amount, DebitMethod.DIRTY_CHECKING)
        }

        val balance = walletService.get(walletId).balance
        val mismatch = integrityChecker.findWalletMismatches().single { it.walletId == walletId }

        println(
            """
            |[v1 DIRTY_CHECKING] 입금 ${requestsEach}건 + 차감 ${requestsEach}건 (각 ${amount}원)
            |  성공 ${result.success} / 실패 ${result.failure}
            |  기대 잔액 ${initial} → 실제 잔액 ${balance}
            |  원장 합계 ${mismatch.ledgerSum}, 차이 ${mismatch.diff}원
            """.trimMargin(),
        )

        // 원장은 모두 남았지만 지갑 잔액은 그중 일부가 덮어써져 사라졌다
        assertThat(mismatch.diff).isNotZero()
    }

    private data class Result(val success: Int, val failure: Int)

    private fun runConcurrently(count: Int, task: (Int) -> Unit): Result {
        val pool = Executors.newFixedThreadPool(32)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val success = AtomicInteger()
        val failure = AtomicInteger()

        repeat(count) { i ->
            pool.submit {
                start.await()
                try {
                    task(i)
                    success.incrementAndGet()
                } catch (e: Exception) {
                    failure.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        // 모든 작업을 제출한 뒤 한 번에 출발시켜 동시에 몰리게 한다
        start.countDown()
        check(done.await(60, TimeUnit.SECONDS)) { "60초 안에 끝나지 않음" }
        pool.shutdown()
        return Result(success.get(), failure.get())
    }
}
