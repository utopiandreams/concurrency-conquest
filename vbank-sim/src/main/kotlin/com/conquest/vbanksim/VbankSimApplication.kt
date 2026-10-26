package com.conquest.vbanksim

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 가상계좌 데몬 시뮬레이터. 입금 거래를 가상계좌 DB에 INSERT한다. (폴링 실험 단계에서 구현) */
@SpringBootApplication
class VbankSimApplication

fun main(args: Array<String>) {
    runApplication<VbankSimApplication>(*args)
}
