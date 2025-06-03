package com.ganeshl.luciditycircuitbreaker.CB.model


import org.springframework.context.ApplicationEvent
import java.time.Instant

// Base event for all circuit breaker events
abstract class CircuitBreakerEvent(
    source: Any,
    val circuitBreakerName: String,
    val timestamp: Instant = Instant.now()
) : ApplicationEvent(source)

// Event for state changes
class CircuitBreakerStateChangedEvent(
    source: Any,
    circuitBreakerName: String,
    val oldState: CircuitBreakerState,
    val newState: CircuitBreakerState
) : CircuitBreakerEvent(source, circuitBreakerName)

// Event for a request being blocked
class CircuitBreakerRequestBlockedEvent(
    source: Any,
    circuitBreakerName: String,
    val state: CircuitBreakerState,
    val reason: String = "Circuit is OPEN"
) : CircuitBreakerEvent(source, circuitBreakerName)

// Event for a successful operation through the circuit breaker
class CircuitBreakerSuccessEvent(
    source: Any,
    circuitBreakerName: String
) : CircuitBreakerEvent(source, circuitBreakerName)

// Event for a failed operation through the circuit breaker
class CircuitBreakerFailureEvent(
    source: Any,
    circuitBreakerName: String,
    val cause: String? = null
) : CircuitBreakerEvent(source, circuitBreakerName)