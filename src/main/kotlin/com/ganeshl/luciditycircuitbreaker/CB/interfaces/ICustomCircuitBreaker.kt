package com.ganeshl.luciditycircuitbreaker.CB.interfaces

import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerState
import org.springframework.context.ApplicationEventPublisher
import java.util.function.Function
import java.util.function.Supplier

interface ICustomCircuitBreaker {
    val name: String
    val state: CircuitBreakerState
    val eventPublisher: ApplicationEventPublisher


    fun recordSuccess()
    fun recordFailure()
    fun allowRequest(): Boolean


    fun <T> execute(supplier: Supplier<T>, fallback: Function<Throwable, T>? = null): T

}