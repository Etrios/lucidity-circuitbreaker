package com.ganeshl.luciditycircuitbreaker.service

import com.ganeshl.luciditycircuitbreaker.model.User
import com.ganeshl.luciditycircuitbreaker.repository.UserRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.function.Supplier
import kotlin.jvm.optionals.getOrElse

@Service
class UserService(
    val userRepository: UserRepository,
    val cbService: CBService
) {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    @CircuitBreaker(name = "cbName", fallbackMethod = "fallbackGetAllUsers")
    fun getAllUsers(): List<User> {
        return userRepository.findAll().toList()
    }

    fun findUser(id: String, throwError: Boolean, delay: Long?): User? {
        val operationSupplier = Supplier {
            if (throwError) {
                throw RuntimeException("Throwing a runtime exception")
            } else if (delay != null) {
                Thread.sleep(delay)
            }

            logger.debug("Attempting to get the user details for id: $id and throwError: $throwError")
            userRepository.findByIdOrNull(id)
        }
        return cbService.executeWithCircuitBreaker(
            operationSupplier,
            ::fallbackFindUser
        )
    }

    fun saveUser(user: User): User {
        return userRepository.save(user)
    }

    // This is used as annotation parameter, even though IDE might think it is unused
    // Hence using an explicit @Suppress annotation
    @Suppress("unused")
    fun fallbackGetAllUsers(t: Throwable): List<User> {
        logger.error("Fallback get all users triggered: ${t.message}")
        throw t
    }

    fun fallbackFindUser(t: Throwable): User? {

        logger.error("Fall back find user triggered for find user ${t.message}")
        throw t
    }
}