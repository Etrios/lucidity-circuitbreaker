package com.ganeshl.luciditycircuitbreaker.config

import com.ganeshl.luciditycircuitbreaker.events.CircuitBreakerEventListener
import com.ganeshl.luciditycircuitbreaker.model.CBType
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder
import org.springframework.cloud.client.circuitbreaker.Customizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfig(
    val circuitBreakerEventListener: CircuitBreakerEventListener
) {

    @Bean
    fun defaultCustomizer(): Customizer<Resilience4JCircuitBreakerFactory> {
        return Customizer<Resilience4JCircuitBreakerFactory> { factory ->
            factory.configureDefault { id ->
                Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build())
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .build()
            }
        }
    }

    @Bean
    fun countCustomizer(): Customizer<Resilience4JCircuitBreakerFactory> {
        val customCircuitBreakerConfig =  CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .slowCallRateThreshold(10.0f)
            .failureRateThreshold(10.0f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .slowCallDurationThreshold(Duration.ofMillis(2000))
            .maxWaitDurationInHalfOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(3)
//            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build()


        return Customizer { factory: Resilience4JCircuitBreakerFactory ->
            factory.configure(
                { builder: Resilience4JConfigBuilder ->
                    builder.circuitBreakerConfig(customCircuitBreakerConfig)
                        .timeLimiterConfig(
                            TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()
                        )
                }, CBType.CountCB.typeName
            )
            factory.addCircuitBreakerCustomizer({ circuitBreaker ->
                circuitBreaker.eventPublisher
                    .onError(circuitBreakerEventListener::handleErrorEvent)
                    .onSuccess(circuitBreakerEventListener::handleSuccessEvent)
                    // State transition Events not working most likely to this issue:
                    // https://github.com/resilience4j/resilience4j/issues/2157
                    .onStateTransition(circuitBreakerEventListener::handleStateTransitionEvent)
            }, CBType.CountCB.typeName)
        }

    }

    @Bean
    fun timeCustomizer(): Customizer<Resilience4JCircuitBreakerFactory>? {
        val customCircuitBreakerConfig =  CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(100)
            .slowCallRateThreshold(10.0f)
            .failureRateThreshold(10.0f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .slowCallDurationThreshold(Duration.ofMillis(2000))
            .maxWaitDurationInHalfOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build()

        return Customizer { factory: Resilience4JCircuitBreakerFactory ->
            factory.configure(
                { builder: Resilience4JConfigBuilder ->
                    builder.circuitBreakerConfig(customCircuitBreakerConfig)
                        .timeLimiterConfig(
                            TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()
                        )
                }, CBType.TimeCB.typeName
            )
        }
    }
}