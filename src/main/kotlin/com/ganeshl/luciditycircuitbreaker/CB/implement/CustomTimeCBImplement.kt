package com.ganeshl.luciditycircuitbreaker.CB.implement

import com.ganeshl.luciditycircuitbreaker.CB.interfaces.AbstractCustomCircuitBreaker
import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerState
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque


class CustomTimeCBImplement (
    name: String,
    private val failureThreshold: Double,
    private val failureWindowInSeconds: Long,
    private val resetTimeOutInSeconds: Int,
    eventPublisher: ApplicationEventPublisher
) : AbstractCustomCircuitBreaker(name, eventPublisher) {

    private val requests: ConcurrentLinkedDeque<Request> = ConcurrentLinkedDeque()
    private var lastOpenTime: Instant? = null // When the circuit last transitioned to OPEN

    private val logger = LoggerFactory.getLogger(CustomTimeCBImplement::class.java)
    override fun recordSuccess() {
        requests.add(Request(Instant.now(), true))
        if (state == CircuitBreakerState.HALF_OPEN) {
            logger.info("CB $name transitioning from HALF_OPEN to CLOSED")
            changeState(CircuitBreakerState.CLOSED)
            requests.clear()
            lastOpenTime = null
        }
    }

    override fun recordFailure() {
        requests.add(Request(Instant.now(), false))

        when (state) {
            CircuitBreakerState.CLOSED -> {
                if (calculateFailureRate() >= failureThreshold) {
                    logger.warn("CB $name moving to OPEN due to exceeding failure threshold $failureThreshold")
                    openCircuit()
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                logger.warn("CB $name moving from HALF_OPEN to OPEN due to failure")
                openCircuit()
            }
            CircuitBreakerState.OPEN -> {
                logger.debug("CB $name already in $state for failure. Nothing to do.")
            }
        }
    }

    override fun allowRequest(): Boolean {
        cleanOldRequests()
        return when (state) {
            CircuitBreakerState.CLOSED, CircuitBreakerState.HALF_OPEN -> true
            CircuitBreakerState.OPEN -> {
                val now = Instant.now()

                if (lastOpenTime == null || Duration.between(lastOpenTime, now).seconds > resetTimeOutInSeconds) {
                    logger.info("CB $name timeout duration exceeded or lastOpenTime reset. Moving to HALF_OPEN")
                    changeState(CircuitBreakerState.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun cleanOldRequests() {
        val now = Instant.now()
        // Remove requests older than the failure window
        while (requests.isNotEmpty()
            && requests.first().time < now.minus(Duration.ofSeconds(failureWindowInSeconds))) {
            requests.pollFirst()
        }
    }

    private fun calculateFailureRate(): Double {
        // 1. Clean Window
        cleanOldRequests()
        // If there are no previous requests, the failure rate is 0.0
        if (requests.isEmpty()) {
            return 0.0
        }

        val totalRequests = requests.size

        // Do not calculate if the totalRequests is 1
        // ************ IMPORTANT ******************
        // This basically prevents the CB from triggering immediately if the first request in a time window fails
        if (totalRequests == 1) {
            return 0.0
        }

        // Count only failed requests
        val failedRequests = requests.count { !it.success }
        return failedRequests.toDouble() / totalRequests
    }

    private fun openCircuit() {
        logger.warn("CB $name from $state to OPEN for a failure")
        // Using the Interface method to trigger events
        changeState(CircuitBreakerState.OPEN)
        requests.clear()
        lastOpenTime = Instant.now()
    }

}

private class Request(
    val time: Instant,
    val success: Boolean
)