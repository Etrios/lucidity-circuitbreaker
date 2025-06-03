package com.ganeshl.luciditycircuitbreaker.CB

import com.ganeshl.luciditycircuitbreaker.CB.implement.CustomCountCBImplement
import com.ganeshl.luciditycircuitbreaker.CB.implement.CustomTimeCBImplement
import com.ganeshl.luciditycircuitbreaker.CB.interfaces.ICustomCircuitBreaker
import com.ganeshl.luciditycircuitbreaker.CB.model.CustomCBType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class CustomCBFactory(
    private val eventPublisher: ApplicationEventPublisher
) {

    fun create(cbType: CustomCBType, config: Map<String, Any> = emptyMap()) : ICustomCircuitBreaker {
        return when(cbType) {
            CustomCBType.CustomCountCB -> {
                val failureThreshold = config["failureThreshold"] as? Int ?: 5
                val resetTimeoutInSeconds = config["resetTimeout"] as? Int ?: 60

                CustomCountCBImplement(cbType.name, failureThreshold, resetTimeoutInSeconds, eventPublisher )
            }
            CustomCBType.CustomTimeCB -> {
                val failureThreshold = config["failureThreshold"] as? Double ?: 0.5
                val failureWindowInSeconds = config["failureWindow"] as? Long ?: 60L
                val resetTimeoutInSeconds = config["resetTimeout"] as? Int ?: 60

                CustomTimeCBImplement(cbType.name, failureThreshold, failureWindowInSeconds, resetTimeoutInSeconds, eventPublisher)
            }
        }
    }
}