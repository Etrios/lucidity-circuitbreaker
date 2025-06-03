package com.ganeshl.luciditycircuitbreaker.CB.implement

import com.ganeshl.luciditycircuitbreaker.CB.interfaces.AbstractCustomCircuitBreaker
import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.Duration

class CustomCountCBImplement (
    override val name: String,
    private val failureThreshold: Int,
    private val resetTimeoutInSeconds: Int,
    override val eventPublisher: ApplicationEventPublisher
) : AbstractCustomCircuitBreaker(name, eventPublisher) {

    private val logger : Logger = LoggerFactory.getLogger(CustomCountCBImplement::class.java)

    // Handling the CB State
    private var failureCount = 0
    private var lastFailureTime: Instant? = null


    override fun recordSuccess() = when (state) {
        CircuitBreakerState.CLOSED -> {
            failureCount = 0
        }
        CircuitBreakerState.HALF_OPEN -> {
            closeState()
        }
        else -> {
            logger.debug("CB $name is already in $state. Hence success has no impact")
        }
    }

    override fun recordFailure() {
        when(state) {
            CircuitBreakerState.CLOSED -> {
                this.failureCount ++
                if (failureCount >= failureThreshold) {
                    logger.warn("CB $name has breached Failure Threshold $failureThreshold. Moving to OPEN state")
                    openState()
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                openState()
            }
            else -> {
                logger.debug("CB $name already in $state for failure")
            }
        }
    }

    override fun allowRequest(): Boolean {
        return when(state) {
            CircuitBreakerState.CLOSED, CircuitBreakerState.HALF_OPEN -> true
            CircuitBreakerState.OPEN -> {
                val now = Instant.now()
                // ResetTimeout in seconds
                if (lastFailureTime != null && Duration.between(lastFailureTime, now).seconds > resetTimeoutInSeconds) {
                    logger.info("CB $name timeoout has reached. Changing to half Open")
                    changeState(CircuitBreakerState.HALF_OPEN)
                    failureCount = 0
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun closeState() {
        // Using the Interface method to trigger events
        changeState(CircuitBreakerState.CLOSED)

        failureCount = 0
        lastFailureTime = null
    }

    private fun openState() {
        logger.warn("CB $name from $state to OPEN for a failure")
        // Using the Interface method to trigger events
        changeState(CircuitBreakerState.OPEN)
        lastFailureTime = Instant.now()
    }

    fun getFailureCount(): Int {
        return failureCount
    }

}