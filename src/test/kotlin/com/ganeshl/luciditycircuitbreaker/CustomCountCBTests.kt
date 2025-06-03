package com.ganeshl.luciditycircuitbreaker

import com.ganeshl.luciditycircuitbreaker.CB.implement.CustomCountCBImplement
import com.ganeshl.luciditycircuitbreaker.CB.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.util.function.Function
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@DisplayName("CountBasedCircuitBreaker Tests")
class CustomCountCBTests {

    private val CB_NAME = "testCountCB"
    private val FAILURE_THRESHOLD = 3
    private val RESET_TIMEOUT = 5
    private val RESET_TIMEOUT_IN_MILLIS = (this.RESET_TIMEOUT * 1000).toLong()

    @Mock
    private lateinit var mockEventPublisher: ApplicationEventPublisher

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<CircuitBreakerEvent> // Captures any type of event

    private lateinit var circuitBreaker: CustomCountCBImplement

    val successFunction = { "Success"}
    val failingFunction = { throw RuntimeException("Simulated failure") }

    private lateinit var mockFallbackFunction: Function<Throwable, String>

    private val logger: Logger = LoggerFactory.getLogger(CustomCountCBTests::class.java)
    @BeforeEach
    fun setUp() {
        // Initialize the circuit breaker before each test
        circuitBreaker = CustomCountCBImplement(
            CB_NAME,
            FAILURE_THRESHOLD,
            RESET_TIMEOUT,
            mockEventPublisher
        )

        @Suppress("UNCHECKED_CAST") // This suppression is often needed for mocking generic interfaces
        mockFallbackFunction = mock(Function::class.java) as Function<Throwable, String>

        lenient().`when`(mockFallbackFunction.apply(any<Throwable>())).thenReturn("Fallback")

        clearInvocations(mockFallbackFunction)
        reset(mockEventPublisher) // Good practice to reset mock publisher
    }

    @Nested
    @DisplayName("CLOSED State Behavior")
    inner class ClosedStateTests {
        @Test
        @DisplayName("Should allow requests when CLOSED and throw Success Events")
        fun shouldAllowRequestsWhenClosed() {
            circuitBreaker.execute(successFunction, mockFallbackFunction)

            // 1. verify the state is closed
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            assertEquals(0, circuitBreaker.getFailureCount())
            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val capturedEvent = eventCaptor.value
            assertTrue { capturedEvent is CircuitBreakerSuccessEvent }
            assertEquals(CB_NAME, (capturedEvent as CircuitBreakerSuccessEvent).circuitBreakerName)

            // Verify Fallback function is never invoked
            verify(mockFallbackFunction, never()).apply(any())

            verifyNoMoreInteractions(mockEventPublisher)
        }

        @Test
        @DisplayName("Should record failure, increment count, and remain CLOSED below threshold")
        fun shouldRecordFailureAndRemainClosedBelowThreshold() {
            repeat(FAILURE_THRESHOLD - 1) {   // Fail 2 times if threshold is 3
                assertFailsWith<RuntimeException> {
                    circuitBreaker.execute(failingFunction)
                }
            }

            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            assertEquals(FAILURE_THRESHOLD - 1, circuitBreaker.getFailureCount())
            // We expect a CircuitBreakerFailureEvent for each failed execution
            verify(mockEventPublisher, times(FAILURE_THRESHOLD - 1)).publishEvent(eventCaptor.capture())
            assertTrue(eventCaptor.value is CircuitBreakerFailureEvent)
            assertEquals(CB_NAME, (eventCaptor.value as CircuitBreakerFailureEvent).circuitBreakerName)

            // Fallback is only invoked at OPEN States
            verify(mockFallbackFunction, never()).apply(any())

            verifyNoMoreInteractions(mockEventPublisher)
        }

        @Test
        @DisplayName("Should transition to OPEN when failure threshold is reached")
        fun shouldTransitionToOpenWhenThresholdReached() {
            repeat(FAILURE_THRESHOLD) { // Fail 3 times if threshold is 3
                assertFailsWith<RuntimeException> {
                    circuitBreaker.execute(failingFunction)
                }
            }

            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            // Verify events: N failure events + 1 state changed event
            verify(mockEventPublisher, times(FAILURE_THRESHOLD + 1)).publishEvent(eventCaptor.capture())

            val events = eventCaptor.allValues
            events.forEach { event ->
                logger.info("${event.circuitBreakerName} and ${event.javaClass}")
            }
            // Verify there are failure events equal to Failure Thresholds
            assertEquals(FAILURE_THRESHOLD, events.count { it is CircuitBreakerFailureEvent })
            // Get the first State change Event. There should be only one
            val stateChangeEvent: CircuitBreakerStateChangedEvent =
                events.filterIsInstance<CircuitBreakerStateChangedEvent>()[0]
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.CLOSED, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.OPEN, stateChangeEvent.newState)

            // Fallback is only invoked at OPEN States
            verify(mockFallbackFunction, never()).apply(any())

            verifyNoMoreInteractions(mockEventPublisher)
        }
    }

    @Nested
    @DisplayName("OPEN State Behavior")
    inner class OpenStateTests {

        @BeforeEach
        fun transitionToOpen() {
            // Helper to ensure circuit is in OPEN state for these tests
            repeat(FAILURE_THRESHOLD) {
                circuitBreaker.execute(failingFunction, mockFallbackFunction)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            // Reset mock to only verify actions within these tests
            reset(mockEventPublisher)
        }

        @Test
        @DisplayName("Should block requests when OPEN and timeout not elapsed")
        fun shouldBlockRequestsWhenOpenAndTimeoutNotElapsed() {
            assertFalse(circuitBreaker.allowRequest())
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            verifyNoInteractions(mockEventPublisher) // No events when simply blocking in OPEN
        }

        @Test
        @DisplayName("Should transition to HALF_OPEN when timeout elapses")
        fun shouldTransitionToHalfOpenWhenTimeoutElapses() {
            // Simulate time passing (e.g., beyond RESET_TIMEOUT)
            Thread.sleep(RESET_TIMEOUT_IN_MILLIS + 1000) // Sleep slightly more than timeout

            assertTrue(circuitBreaker.allowRequest()) // This should trigger the transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)

            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val stateChangeEvent = eventCaptor.value as CircuitBreakerStateChangedEvent
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.OPEN, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.HALF_OPEN, stateChangeEvent.newState)
        }

        @Test
        @DisplayName("Should record failure and remain OPEN when already OPEN")
        fun shouldRecordFailureAndRemainOpenWhenAlreadyOpen() {
            try {
                circuitBreaker.execute(failingFunction, mockFallbackFunction)
            } catch (ex: Exception) {
                // Empty Exception block since we ignore the known error in the failing funciton
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val event = eventCaptor.value

            assertTrue(event is CircuitBreakerRequestBlockedEvent)
            verifyNoMoreInteractions(mockEventPublisher) // No state change if already OPEN
        }

        @Test
        @DisplayName("Should record success and remain OPEN when already OPEN")
        fun shouldRecordSuccessAndRemainOpenWhenAlreadyOpen() {
            try {
                circuitBreaker.execute(successFunction)
            } catch (exception: Exception) {
                // Empty Catch block to not fail tests
            }

            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val event = eventCaptor.value
            assertTrue(event is CircuitBreakerRequestBlockedEvent)
            verifyNoMoreInteractions(mockEventPublisher) // No state change if already OPEN
        }
    }

    @Nested
    @DisplayName("HALF_OPEN State Behavior")
    inner class HalfOpenStateTests {

        @BeforeEach
        fun transitionToHalfOpen() {
            // Open the circuit
            repeat(FAILURE_THRESHOLD) {
                try {
                    circuitBreaker.execute(failingFunction, mockFallbackFunction)
                } catch (ex: Exception) {
                    // Empty Exception block since we ignore the known error in the failing funciton
                }
            }
            // Wait for timeout to transition to HALF_OPEN
            Thread.sleep(RESET_TIMEOUT_IN_MILLIS + 1000)
            circuitBreaker.allowRequest() // Trigger HALF_OPEN transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            // Reset mock to only verify actions within these tests
            reset(mockEventPublisher)
        }

        @Test
        @DisplayName("Should allow one request when HALF_OPEN")
        fun shouldAllowOneRequestWhenHalfOpen() {
            assertTrue(circuitBreaker.allowRequest())
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            verifyNoInteractions(mockEventPublisher) // allowRequest itself doesn't publish state changes
        }

        @Test
        @DisplayName("Should transition to CLOSED on successful request in HALF_OPEN")
        fun shouldTransitionToClosedOnSuccessInHalfOpen() {
            circuitBreaker.recordSuccess()
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)

            // Verify events: Success event + State changed event
            verify(mockEventPublisher, times(1)).publishEvent(eventCaptor.capture())

            val stateChangeEvent = eventCaptor.value as CircuitBreakerStateChangedEvent
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.HALF_OPEN, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.CLOSED, stateChangeEvent.newState)
        }

        @Test
        @DisplayName("Should transition to OPEN on failed request in HALF_OPEN")
        fun shouldTransitionToOpenOnFailureInHalfOpen() {

            val result = circuitBreaker.execute(failingFunction, mockFallbackFunction)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            assertEquals("Fallback", result)

            // Verify events: Failure event + State changed event
            verify(mockEventPublisher, times(2)).publishEvent(eventCaptor.capture())
            val events = eventCaptor.allValues

            events.forEach { event ->
                logger.info("${event.circuitBreakerName} and ${event.javaClass}")
            }

            assertTrue(events[1] is CircuitBreakerFailureEvent)
            val stateChangeEvent = events[0] as CircuitBreakerStateChangedEvent
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.HALF_OPEN, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.OPEN, stateChangeEvent.newState)
        }
    }

    @Nested
    @DisplayName("Execute Method Behavior")
    inner class ExecuteMethodTests {
        @BeforeEach
        fun setup() {
            // Reset circuit breaker to ensure fresh state for each test
            circuitBreaker = CustomCountCBImplement(
                CB_NAME,
                FAILURE_THRESHOLD,
                RESET_TIMEOUT,
                mockEventPublisher
            )
            reset(mockEventPublisher) // Clear interactions from setup
        }

        @Test
        @DisplayName("Execute should return primary result on success in CLOSED state")
        fun executeShouldReturnPrimaryResultOnSuccessInClosed() {
            val result = circuitBreaker.execute(successFunction)
            assertEquals("Success", result)
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
        }

        @Test
        @DisplayName("Execute should call fallback on failure in CLOSED state if provided")
        fun executeShouldCallFallbackOnFailureInClosed() {
            val result = circuitBreaker.execute(failingFunction, mockFallbackFunction)

            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state) // Should remain closed for first failure
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerFailureEvent::class.java))
        }

        @Test
        @DisplayName("Execute should open circuit on consecutive failures and then call fallback if provided")
        fun executeShouldOpenCircuitOnConsecutiveFailuresAndCallFallback() {

            // Create an open circuit scenario
            repeat(FAILURE_THRESHOLD) {
                try {
                    circuitBreaker.execute(failingFunction, mockFallbackFunction)
                } catch (ex: Exception) {
                    // Empty Exception block
                }
            }

            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            // 3rd failure - circuit opens, fallback is called
            val result = circuitBreaker.execute(failingFunction, mockFallbackFunction)
            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
        }

        @Test
        @DisplayName("Execute should throw CircuitBreakerOpenException if OPEN and no fallback")
        fun executeShouldThrowIfOpenAndNoFallback() {
            // Force open the circuit
            repeat(FAILURE_THRESHOLD) { circuitBreaker.recordFailure() }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            reset(mockEventPublisher) // Clear setup events

            assertFailsWith<CircuitBreakerOpenException> {
                circuitBreaker.execute(successFunction)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state) // Still open
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerRequestBlockedEvent::class.java))
        }

        @Test
        @DisplayName("Execute should re-throw if fallback fails in OPEN state")
        fun executeShouldRethrowIfFallbackFailsInOpenState() {
            // Force open the circuit
            repeat(FAILURE_THRESHOLD) {
                try {
                    circuitBreaker.execute(failingFunction, mockFallbackFunction)
                } catch (ex: Exception) {
                    // Empty Exception Block
                }
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            reset(mockEventPublisher)

            val failingFallBackFunction : (Throwable) -> String = {throw java.lang.IllegalStateException()}
            assertFailsWith<IllegalStateException> { // Expect the fallback's exception
                circuitBreaker.execute(successFunction, failingFallBackFunction)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state) // Still open
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerRequestBlockedEvent::class.java))
        }
    }
}