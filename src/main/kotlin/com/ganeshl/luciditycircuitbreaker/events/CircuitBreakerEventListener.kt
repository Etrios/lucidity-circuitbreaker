package com.ganeshl.luciditycircuitbreaker.events

import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSuccessEvent
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent
import org.slf4j.LoggerFactory

@Component
class CircuitBreakerEventListener {

    private val logger = LoggerFactory.getLogger(CircuitBreakerEventListener::class.java)

    /**
     * Listens for successful calls through any Resilience4j Circuit Breaker.
     */
    @EventListener
    fun handleSuccessEvent(event: CircuitBreakerOnSuccessEvent) {
        logger.info("Circuit Breaker SUCCESS Event: CB '{}' - Duration: {}ms", event.circuitBreakerName, event.elapsedDuration.toMillis())
    }

    /**
     * Listens for calls that resulted in an error through any Resilience4j Circuit Breaker.
     */
    @EventListener
    fun handleErrorEvent(event: CircuitBreakerOnErrorEvent) {
        logger.warn("Circuit Breaker ERROR Event: CB '{}' - Cause: '{}' - Duration: {}ms",
            event.circuitBreakerName, event.throwable.javaClass.simpleName, event.elapsedDuration.toMillis())
    }

    /**
     * Listens for state transitions of any Resilience4j Circuit Breaker.
     */
    @EventListener
    fun handleStateTransitionEvent(event: CircuitBreakerOnStateTransitionEvent) {
        logger.error("Circuit Breaker STATE TRANSITION Event: CB '{}' - From: {} - To: {}",
            event.circuitBreakerName, event.stateTransition.fromState, event.stateTransition.toState)
    }

    // CircuitBreakerOnCallNotPermittedEvent
    // CircuitBreakerOnIgnoredErrorEvent
    // CircuitBreakerOnResetEvent
    // CircuitBreakerOnForcedOpenEvent
    // CircuitBreakerOnForcedClosedEvent

     @EventListener
     fun handleSpringCloudCircuitBreakerEvent(event: CircuitBreakerEvent) {
         logger.info("Spring Cloud Circuit Breaker Event: CB '{}' - Type: '{}'", event.getCircuitBreakerName(), event.eventType)
     }
}