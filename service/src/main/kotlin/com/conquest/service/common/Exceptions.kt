package com.conquest.service.common

class WalletNotFoundException(walletId: Long) : RuntimeException("지갑을 찾을 수 없습니다. walletId=$walletId")

class InsufficientBalanceException(walletId: Long, balance: Long, amount: Long) :
    RuntimeException("잔액이 부족합니다. walletId=$walletId, balance=$balance, amount=$amount")
