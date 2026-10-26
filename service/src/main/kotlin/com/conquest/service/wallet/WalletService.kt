package com.conquest.service.wallet

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val walletRepository: WalletRepository,
) {
    @Transactional
    fun create(memberId: Long): Wallet = walletRepository.save(Wallet(memberId))

    @Transactional(readOnly = true)
    fun get(walletId: Long): Wallet = walletRepository.getWallet(walletId)
}
