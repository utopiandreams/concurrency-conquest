package com.conquest.service.integrity

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

data class WalletMismatch(
    val walletId: Long,
    val balance: Long,
    val ledgerSum: Long,
) {
    /** 양수면 지갑에 원장보다 돈이 많음, 음수면 원장보다 적음 */
    val diff: Long get() = balance - ledgerSum
}

/** 정합성 검증: 지갑 잔액과 원장 합계가 다른 지갑을 찾는다. 정상이면 빈 목록. */
@Component
class IntegrityChecker(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findWalletMismatches(): List<WalletMismatch> = jdbcTemplate.query(
        """
        SELECT w.wallet_id, w.balance,
               COALESCE(SUM(CASE l.direction WHEN 'IN' THEN l.amount ELSE -l.amount END), 0) AS ledger_sum
        FROM wallet w
        LEFT JOIN ledger l ON l.wallet_id = w.wallet_id
        GROUP BY w.wallet_id, w.balance
        HAVING w.balance <> ledger_sum
        """.trimIndent(),
    ) { rs, _ -> WalletMismatch(rs.getLong("wallet_id"), rs.getLong("balance"), rs.getLong("ledger_sum")) }
}
