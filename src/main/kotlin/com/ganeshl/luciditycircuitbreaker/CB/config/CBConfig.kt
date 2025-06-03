package com.ganeshl.luciditycircuitbreaker.CB.config

import com.ganeshl.luciditycircuitbreaker.CB.CustomCBFactory
import com.ganeshl.luciditycircuitbreaker.CB.interfaces.ICustomCircuitBreaker
import com.ganeshl.luciditycircuitbreaker.CB.model.CustomCBType
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CBConfig(
    private val circuitBreakerFactory: CustomCBFactory
) {

    @Bean
    fun customTimeCB(): ICustomCircuitBreaker {
        return circuitBreakerFactory.createCustomCB(
            cbType = CustomCBType.CustomTimeCB,
            name = "customTimeCB",
            config = mapOf(
                "failureThreshold" to 0.2,
                "resetTimeout" to 60,
                "failureWindow" to 60
            )
        )
    }

    @Bean
    fun customCountCB(): ICustomCircuitBreaker {
        return circuitBreakerFactory.createCustomCB(
            cbType = CustomCBType.CustomCountCB,
            name = "customCountCB",
            config = mapOf(
                "failureThreshold" to 10,
                "resetTimeout" to 60
            )
        )
    }

}