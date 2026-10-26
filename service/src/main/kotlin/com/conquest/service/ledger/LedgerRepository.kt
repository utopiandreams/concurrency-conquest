package com.conquest.service.ledger

import org.springframework.data.jpa.repository.JpaRepository

interface LedgerRepository : JpaRepository<Ledger, Long>
