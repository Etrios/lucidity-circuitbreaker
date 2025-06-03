package com.ganeshl.luciditycircuitbreaker.events

import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerFailureEvent
import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerRequestBlockedEvent
import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerStateChangedEvent
import com.ganeshl.luciditycircuitbreaker.CB.model.CircuitBreakerSuccessEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component


@Component
class CustomCBEventListener {

    private val logger = LoggerFactory.getLogger(CustomCBEventListener::class.java)

    @EventListener
    fun handleSuccessEvent(event: CircuitBreakerSuccessEvent) {
        logger.info("Circuit Breaker ${event.circuitBreakerName} SUCCESS Event: CB Name ${event.circuitBreakerName}")
    }

    @EventListener
    fun handleFailureEvent(event: CircuitBreakerFailureEvent) {
        logger.info("Circuit Breaker ${event.circuitBreakerName} FAILURE Event: CB Name ${event.circuitBreakerName}")
    }

    @EventListener
    fun handleBlockedEvent(event: CircuitBreakerRequestBlockedEvent) {
        logger.info("Circuit Breaker ${event.circuitBreakerName} BLOCKED Event: CB Name ${event.circuitBreakerName}")
    }

    @EventListener
    fun handleStateChangeEvent(event: CircuitBreakerStateChangedEvent) {
        logger.info("Circuit Breaker ${event.circuitBreakerName}State changed from ${event.oldState} to ${event.newState}")
    }
}