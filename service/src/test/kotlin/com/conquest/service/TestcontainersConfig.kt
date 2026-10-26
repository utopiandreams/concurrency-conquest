package com.conquest.service

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/** svc-db와 같은 MySQL 8.4. 격리 수준은 기본값 REPEATABLE READ (svc-db의 my.cnf와 동일) */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    fun mysql(): MySQLContainer = MySQLContainer("mysql:8.4").withDatabaseName("wallet")
}
