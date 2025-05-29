package com.ganeshl.luciditycircuitbreaker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LucidityCircuitbreakerApplication

fun main(args: Array<String>) {
    runApplication<LucidityCircuitbreakerApplication>(*args)
}
