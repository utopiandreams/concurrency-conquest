package com.conquest.service.debit

import org.springframework.stereotype.Service

@Service
class DebitService(strategies: List<DebitStrategy>) {
    private val strategies: Map<DebitMethod, DebitStrategy> = strategies.associateBy { it.method }

    fun debit(walletId: Long, amount: Long, method: DebitMethod): Long {
        val strategy = requireNotNull(strategies[method]) { "구현되지 않은 차감 방식입니다. method=$method" }
        return strategy.debit(walletId, amount)
    }
}
