package com.conquest.service.deposit

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class DepositController(
    private val depositService: DepositService,
) {
    @PostMapping("/wallets/{walletId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    fun deposit(@PathVariable walletId: Long, @RequestBody request: DepositRequest): DepositResponse =
        DepositResponse(depositService.deposit(walletId, request.amount))
}

data class DepositRequest(val amount: Long)

data class DepositResponse(val ledgerId: Long)
