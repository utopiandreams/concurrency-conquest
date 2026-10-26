package com.conquest.service.debit

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class DebitController(
    private val debitService: DebitService,
) {
    @PostMapping("/wallets/{walletId}/debits")
    @ResponseStatus(HttpStatus.CREATED)
    fun debit(@PathVariable walletId: Long, @RequestBody request: DebitRequest): DebitResponse =
        DebitResponse(debitService.debit(walletId, request.amount, request.method))
}

data class DebitRequest(
    val amount: Long,
    val method: DebitMethod = DebitMethod.DIRTY_CHECKING,
)

data class DebitResponse(val ledgerId: Long)
