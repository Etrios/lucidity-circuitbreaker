package com.ganeshl.luciditycircuitbreaker.service

import com.ganeshl.luciditycircuitbreaker.CB.CustomCBFactory
import com.ganeshl.luciditycircuitbreaker.CB.model.CustomCBType
import com.ganeshl.luciditycircuitbreaker.config.RuntimeConfigManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.function.Function
import java.util.function.Supplier


@Service
class CBService(
    // private val circuitBreakerFactory: Resilience4JCircuitBreakerFactory,
    private val circuitBreakerFactory: CustomCBFactory,
    private val runtimeConfigManager: RuntimeConfigManager
) {

    private val logger: Logger = LoggerFactory.getLogger(RuntimeConfigManager::class.java)

    fun <T> executeWithCircuitBreaker(
        operation: Supplier<T>,
        fallback: Function<Throwable, T>
    ): T {
        val cbName = runtimeConfigManager.getCurrentCB()
        logger.info("Using Runtime circuit breaker $cbName")

        return executeWithCircuitBreaker(cbName, operation, fallback)
    }

    /**
     * A generic wrapper function to execute any operation with a circuit breaker.
     *
     * @param circuitBreakerId The ID of the circuit breaker configuration to use.
     * @param operation The main operation to execute, as a Supplier that returns a result.
     * @param fallback The fallback function to execute if the main operation fails or circuit opens.
     * It accepts a Throwable and returns a result.
     * @return The result of the operation or the fallback.
     */

    fun<T> executeWithCircuitBreaker(
        circuitBreakerId: String,
        operation: Supplier<T>,
        fallback: Function<Throwable, T>
    ): T {
        val circuitBreaker = circuitBreakerFactory.create(CustomCBType.valueOf(circuitBreakerId))
        val supplierWrapper = Supplier<T> {
            logger.debug("Operation Wrapper Logic Triggered")
            val errorRate = runtimeConfigManager.getErrorRate()
            if (runtimeConfigManager.isRandomErrorEnabled() && Math.random() < errorRate) {
                logger.debug("Triggered error for Operation with ErrorRate $errorRate")
                throw RuntimeException("Simulated External Service Error")
            } else {
                if (runtimeConfigManager.isTimeDelayEnabled()) {
                    val waitTimeInMillis = runtimeConfigManager.getWaitTimeInMillis()
                    logger.debug("Triggered Time Delay with $waitTimeInMillis")
                    Thread.sleep(waitTimeInMillis)

                }

            }
            operation.get()
        }

        return circuitBreaker.execute(supplierWrapper, fallback)
    }


}