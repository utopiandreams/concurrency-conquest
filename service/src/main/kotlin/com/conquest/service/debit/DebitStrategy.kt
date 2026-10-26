package com.conquest.service.debit

/**
 * 차감 방식. 같은 조건에서 나란히 비교하기 위해 모든 방식을 코드에 공존시킨다.
 * 방식이 추가될 때마다 enum과 구현체를 하나씩 늘린다.
 */
enum class DebitMethod {
    /** v1: 조회 → 수정 → 변경 감지. 락이 없어 Lost Update 발생 (당시 구조) */
    DIRTY_CHECKING,
}

interface DebitStrategy {
    val method: DebitMethod

    /** @return 생성된 원장 ID */
    fun debit(walletId: Long, amount: Long): Long
}
