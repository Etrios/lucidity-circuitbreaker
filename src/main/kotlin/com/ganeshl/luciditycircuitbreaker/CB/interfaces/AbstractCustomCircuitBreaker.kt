package com.ganeshl.luciditycircuitbreaker.CB.interfaces

import com.ganeshl.luciditycircuitbreaker.CB.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.util.function.Function
import java.util.function.Supplier

abstract class AbstractCustomCircuitBreaker (
    override val name: String,
    override val eventPublisher: ApplicationEventPublisher,

) : ICustomCircuitBreaker
{
    private val logger : Logger = LoggerFactory.getLogger(AbstractCustomCircuitBreaker::class.java)

    private var _state: CircuitBreakerState = CircuitBreakerState.CLOSED
        set(value) {
            if (field != value) {
                val oldState = field
                field = value
                eventPublisher.publishEvent(
                    CircuitBreakerStateChangedEvent(this, name, oldState, value)
                )
            }
        }

    override val state: CircuitBreakerState
        get() = _state

    fun changeState(newState: CircuitBreakerState) {
        this._state = newState
    }

    override fun <T> execute(
        supplier: Supplier<T>,
        fallback: Function<Throwable, T>?
    ): T {
        if (!allowRequest()) {
            eventPublisher.publishEvent(
                CircuitBreakerRequestBlockedEvent(this, name, state)
            )

            if (fallback != null) {
                return try {
                    fallback.apply(CircuitBreakerOpenException("CB $name is in $state state. Requests are stopped."))
                } catch (fallbackException: Exception) {
                    logger.error("CB $name is $state. Fallback failed with ${fallbackException.message}")
                    throw fallbackException
                }
            } else {
                throw CircuitBreakerOpenException("CB $name is $state. Request Blocked and no fallback provided.")
            }
        }

        return try {
            val result = supplier.get()

            logger.debug("CB $name registered success")
            recordSuccess()
            eventPublisher.publishEvent(
                CircuitBreakerSuccessEvent(this, name)
            )
            result
        } catch (ex: Exception) {
            recordFailure()
            logger.debug("CB $name registered failure", ex.message)
            eventPublisher.publishEvent(
                CircuitBreakerFailureEvent(this, name, ex.message)
            )

            // If fallback is not provided, throw the original exception back
            if (fallback == null) {
                throw ex
            }

            try {
                val fallbackResult = fallback.apply(ex) // Call apply on Function, pass original exception
                logger.debug("CB $name returned fallback result after primary failure.")
                fallbackResult
            } catch (fallbackException: Exception) {
                throw fallbackException
            }
        }
    }
}