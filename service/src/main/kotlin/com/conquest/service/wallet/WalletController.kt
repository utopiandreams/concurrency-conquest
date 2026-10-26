package com.conquest.service.wallet

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/wallets")
class WalletController(
    private val walletService: WalletService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateWalletRequest): WalletResponse =
        WalletResponse.from(walletService.create(request.memberId))

    @GetMapping("/{walletId}")
    fun get(@PathVariable walletId: Long): WalletResponse =
        WalletResponse.from(walletService.get(walletId))
}

data class CreateWalletRequest(val memberId: Long)

data class WalletResponse(
    val walletId: Long,
    val memberId: Long,
    val balance: Long,
    val lastLedgerId: Long?,
) {
    companion object {
        fun from(wallet: Wallet) = WalletResponse(wallet.id, wallet.memberId, wallet.balance, wallet.lastLedgerId)
    }
}
