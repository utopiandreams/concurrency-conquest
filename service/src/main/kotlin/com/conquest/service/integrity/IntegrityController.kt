package com.conquest.service.integrity

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IntegrityController(
    private val integrityChecker: IntegrityChecker,
) {
    @GetMapping("/integrity/wallets")
    fun walletMismatches(): List<WalletMismatch> = integrityChecker.findWalletMismatches()
}
