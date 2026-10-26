package com.conquest.service.wallet

import com.conquest.service.common.WalletNotFoundException
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long>

fun WalletRepository.getWallet(walletId: Long): Wallet =
    findById(walletId).orElseThrow { WalletNotFoundException(walletId) }
