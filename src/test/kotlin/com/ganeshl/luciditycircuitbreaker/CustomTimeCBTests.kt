package com.ganeshl.luciditycircuitbreaker

import com.ganeshl.luciditycircuitbreaker.CB.implement.CustomTimeCBImplement // Make sure this import is correct
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
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@DisplayName("TimeBasedCircuitBreaker Tests")
class CustomTimeCBTests {

    private val CB_NAME = "testTimeCB"
    private val FAILURE_RATE_THRESHOLD = 0.5 // 50% failure rate
    private val FAILURE_WINDOW_SECONDS = 10L
    private val RESET_TIMEOUT_SECONDS = 5
    private val SLEEP_BUFFER_MILLIS = 1000L // Small buffer for Thread.sleep

    @Mock
    private lateinit var mockEventPublisher: ApplicationEventPublisher

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<CircuitBreakerEvent>

    private lateinit var circuitBreaker: CustomTimeCBImplement

    val successFunction = { "Success" }
    val failingFunction = { throw RuntimeException("Simulated failure") }

    @Spy
    private val spyFallbackFunction: () -> String = { "Fallback" }

    private val logger: Logger = LoggerFactory.getLogger(CustomTimeCBTests::class.java)

    @BeforeEach
    fun setUp() {
        circuitBreaker = CustomTimeCBImplement(
            CB_NAME,
            FAILURE_RATE_THRESHOLD,
            FAILURE_WINDOW_SECONDS,
            RESET_TIMEOUT_SECONDS,
            mockEventPublisher
        )

        clearInvocations(spyFallbackFunction)
        reset(mockEventPublisher)
    }

    @Nested
    @DisplayName("CLOSED State Behavior")
    inner class ClosedStateTests {
        @Test
        @DisplayName("Should allow requests when CLOSED and publish Success Events, fallback not called")
        fun shouldAllowRequestsWhenClosed() {
            circuitBreaker.execute(successFunction, spyFallbackFunction)

            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            // Time-based CB doesn't have a simple failure count to assert here,
            // but you could potentially assert `requests` queue size if it were exposed.

            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val capturedEvent = eventCaptor.value
            assertTrue(capturedEvent is CircuitBreakerSuccessEvent)
            assertEquals(CB_NAME, capturedEvent.circuitBreakerName)

            verify(spyFallbackFunction, never()).invoke()
            verifyNoMoreInteractions(mockEventPublisher)
        }

        @Test
        @DisplayName("Should record failure, remain CLOSED below threshold, and fallback called")
        fun shouldRecordFailureAndRemainClosedBelowThreshold() {
            // Simulate 4 requests within window: 2 successes, 2 failures (50% failure rate)
            // (FAILURE_RATE_THRESHOLD is 0.5, so 2 failures out of 4 requests = 50% -> should open)

            // 2 success requests at the start
            circuitBreaker.execute(successFunction, spyFallbackFunction)
            circuitBreaker.execute(successFunction, spyFallbackFunction)
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            verify(mockEventPublisher, times(2)).publishEvent(any(CircuitBreakerSuccessEvent::class.java))
            verify(mockEventPublisher, never()).publishEvent(any(CircuitBreakerFailureEvent::class.java))
            verify(spyFallbackFunction, never()).invoke() // Primary succeeded

            // 3rd Request: Failure
            reset(mockEventPublisher) // Reset for the next interaction
            clearInvocations(spyFallbackFunction)
            assertFailsWith<RuntimeException> {
                circuitBreaker.execute(failingFunction) // No fallback, so it throws
            }
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state) // Should still be closed
            verify(mockEventPublisher, times(1)).publishEvent(any(CircuitBreakerFailureEvent::class.java))
            verify(spyFallbackFunction, never()).invoke() // No fallback provided in this call

        }

        @Test
        @DisplayName("Should transition to OPEN when failure rate threshold is reached")
        fun shouldTransitionToOpenWhenThresholdReached() {
            // Simulate enough failures to exceed FAILURE_RATE_THRESHOLD (0.5)
            circuitBreaker.execute(successFunction)  // 1 success
            Thread.sleep(10)
            // 2nd is a failure -> threshold reached 0.5%
            assertFailsWith<RuntimeException> { circuitBreaker.execute(failingFunction) }

            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            // Verify events: 1 success, 1 failure events, 1 state changed event (CLOSED -> OPEN)
            verify(mockEventPublisher, times(3)).publishEvent(eventCaptor.capture()) // 1S + 2F + 1StateChange
            val events = eventCaptor.allValues
            assertEquals(1, events.count { it is CircuitBreakerSuccessEvent })
            assertEquals(1, events.count { it is CircuitBreakerFailureEvent })
            assertEquals(1, events.count { it is CircuitBreakerStateChangedEvent })

            val stateChangeEvent: CircuitBreakerStateChangedEvent =
                events.filterIsInstance<CircuitBreakerStateChangedEvent>().first()
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.CLOSED, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.OPEN, stateChangeEvent.newState)

            verify(spyFallbackFunction, never()).invoke() // Fallback not provided in failing calls
        }

        @Test
        @DisplayName("Should clear old requests outside failure window and allow transition to CLOSED after reset timeout")
        fun shouldClearOldRequestsOutsideFailureWindow() {
            // Re-initialize CB for this specific test with a very short failure window
            // and a reset timeout that we control for the test.
            val localFailureWindow = Duration.ofSeconds(1) // 1 second
            val localResetTimeout = Duration.ofSeconds(3)  // 3 seconds, shorter than default 5
            val customCB1 = CustomTimeCBImplement(
                CB_NAME,
                FAILURE_RATE_THRESHOLD,
                localFailureWindow.toSeconds(),
                localResetTimeout.toSeconds().toInt(),
                mockEventPublisher
            )
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)

            // Step 1: Make the circuit OPEN with failures within the window
            // We need enough failures to reach FAILURE_RATE_THRESHOLD within `localFailureWindow`
            // If threshold is 0.5, 2 failures within 1s should open it.
            assertFailsWith<RuntimeException> { customCB1.execute(failingFunction) } // Fail 1
            Thread.sleep(20)
            assertFailsWith<RuntimeException> { customCB1.execute(failingFunction) } // Fail 2
            Thread.sleep(20)

            assertEquals(CircuitBreakerState.OPEN, customCB1.state)
            logger.info("CB state after initial failures: ${customCB1.state}")

            // Verify initial events for going to OPEN
            verify(mockEventPublisher, times(3)).publishEvent(eventCaptor.capture())
            val failureEvents = eventCaptor.allValues
            failureEvents.forEach { event ->
                logger.info("CB ${event.circuitBreakerName} and ${event.javaClass}")
            }

            assertEquals(2, failureEvents.filterIsInstance<CircuitBreakerFailureEvent>().count())
            assertEquals(1, failureEvents.filterIsInstance<CircuitBreakerStateChangedEvent>().count())

            // Fallback should have been called for both failures
            verify(spyFallbackFunction, never()).invoke()

            reset(mockEventPublisher) // Clear events for next phase
            clearInvocations(spyFallbackFunction)

            // Step 2: Sleep past the `localResetTimeout` to allow transition to HALF_OPEN
            logger.info("Sleeping for ${localResetTimeout.toMillis() + SLEEP_BUFFER_MILLIS}ms to reach HALF_OPEN...")
            Thread.sleep(localResetTimeout.toMillis() + SLEEP_BUFFER_MILLIS)

            // Step 3: Execute a success function. This should trigger `allowRequest()` -> `cleanOldRequests()` -> `HALF_OPEN` -> `recordSuccess()` -> `CLOSED`
            val result = customCB1.execute(successFunction, spyFallbackFunction) // This is the 'test' request in HALF_OPEN
            assertEquals(CircuitBreakerState.CLOSED, customCB1.state)
            logger.info("Result after success in HALF_OPEN: $result")

            assertEquals("Success", result) // Primary function should have been executed
            assertEquals(CircuitBreakerState.CLOSED, customCB1.state) // Should now be CLOSED


            // Verify events:
            // 1. CircuitBreakerStateChangedEvent (OPEN -> HALF_OPEN) triggered by allowRequest before execute
            // 2. CircuitBreakerSuccessEvent (for the successful execution)
            // 3. CircuitBreakerStateChangedEvent (HALF_OPEN -> CLOSED) triggered by recordSuccess
            verify(mockEventPublisher, times(3)).publishEvent(eventCaptor.capture())
            val events = eventCaptor.allValues

            events.forEach { event ->
                logger.info("cb ${event.circuitBreakerName} and ${event.javaClass}")
            }

            // Assert specific event types and transitions
            assertTrue(events.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.OPEN && it.newState == CircuitBreakerState.HALF_OPEN })
            assertTrue(events.any { it is CircuitBreakerSuccessEvent })
            assertTrue(events.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.HALF_OPEN && it.newState == CircuitBreakerState.CLOSED })

            // Fallback should NOT have been called in this phase (because the primary succeeded)
            verify(spyFallbackFunction, never()).invoke()

            verifyNoMoreInteractions(mockEventPublisher)
        }
    }

    @Nested
    @DisplayName("OPEN State Behavior")
    inner class OpenStateTests {

        @BeforeEach
        fun transitionToOpen() {
            // Force open the circuit
            repeat(3) { // 3 failures, if threshold is 0.5, this should trip it in a small window
                assertFailsWith<RuntimeException> {
                    circuitBreaker.execute(failingFunction)
                }
                Thread.sleep(5) // Ensure they are within a short window
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)
        }

        @Test
        @DisplayName("Should block requests when OPEN and timeout not elapsed, fallback is called")
        fun shouldBlockRequestsWhenOpenAndTimeoutNotElapsed() {
            // This is testing allowRequest directly, which doesn't use fallback.
            // For `execute` behavior, see other tests.
            assertFalse(circuitBreaker.allowRequest())
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            verifyNoInteractions(mockEventPublisher) // No events when simply blocking in OPEN
            verify(spyFallbackFunction, never()).invoke() // Fallback is not involved in allowRequest()
        }

        @Test
        @DisplayName("Should execute fallback if OPEN and timeout not elapsed")
        fun shouldExecuteFallbackIfOpenAndTimeoutNotElapsed() {
            val result = circuitBreaker.execute(successFunction, spyFallbackFunction) // Primary is skipped
            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            verify(spyFallbackFunction, times(1)).invoke() // Fallback should be called
            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            assertTrue(eventCaptor.value is CircuitBreakerRequestBlockedEvent)
            verifyNoMoreInteractions(mockEventPublisher)
        }

        @Test
        @DisplayName("Should transition to HALF_OPEN when timeout elapses")
        fun shouldTransitionToHalfOpenWhenTimeoutElapses() {
            Thread.sleep(Duration.ofSeconds(RESET_TIMEOUT_SECONDS.toLong()).toMillis() + SLEEP_BUFFER_MILLIS)

            assertTrue(circuitBreaker.allowRequest()) // This should trigger the transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)

            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            val stateChangeEvent = eventCaptor.value as CircuitBreakerStateChangedEvent
            assertEquals(CB_NAME, stateChangeEvent.circuitBreakerName)
            assertEquals(CircuitBreakerState.OPEN, stateChangeEvent.oldState)
            assertEquals(CircuitBreakerState.HALF_OPEN, stateChangeEvent.newState)
            verifyNoMoreInteractions(mockEventPublisher) // Only state change event
        }

        @Test
        @DisplayName("Should record failure via execute and remain OPEN when already OPEN, fallback called")
        fun shouldRecordFailureAndRemainOpenWhenAlreadyOpen() {
            val result = circuitBreaker.execute(failingFunction, spyFallbackFunction)
            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            verify(spyFallbackFunction, times(1)).invoke() // Fallback should be called
            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            assertTrue(eventCaptor.value is CircuitBreakerRequestBlockedEvent) // Request is blocked, not failed directly
            verifyNoMoreInteractions(mockEventPublisher)
        }

        @Test
        @DisplayName("Should record success via execute and remain OPEN when already OPEN, fallback called")
        fun shouldRecordSuccessAndRemainOpenWhenAlreadyOpen() {
            val result = circuitBreaker.execute(successFunction, spyFallbackFunction)
            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)

            verify(spyFallbackFunction, times(1)).invoke() // Fallback should be called
            verify(mockEventPublisher).publishEvent(eventCaptor.capture())
            assertTrue(eventCaptor.value is CircuitBreakerRequestBlockedEvent) // Request is blocked
            verifyNoMoreInteractions(mockEventPublisher)
        }
    }

    @Nested
    @DisplayName("HALF_OPEN State Behavior")
    inner class HalfOpenStateTests {

        @BeforeEach
        fun transitionToHalfOpen() {
            // Force open the circuit
            repeat(3) {
                assertFailsWith<RuntimeException> {
                    circuitBreaker.execute(failingFunction)
                }
                Thread.sleep(5)
            }
            // Wait for timeout to transition to HALF_OPEN
            Thread.sleep(Duration.ofSeconds(RESET_TIMEOUT_SECONDS.toLong()).toMillis() + SLEEP_BUFFER_MILLIS)
            circuitBreaker.allowRequest() // Trigger HALF_OPEN transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)
        }

        @Test
        @DisplayName("Should allow one request when HALF_OPEN, fallback not called")
        fun shouldAllowOneRequestWhenHalfOpen() {
            assertTrue(circuitBreaker.allowRequest())
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            verifyNoInteractions(mockEventPublisher)
            verify(spyFallbackFunction, never()).invoke() // allowRequest itself doesn't involve fallback
        }

        @Test
        @DisplayName("Should transition to CLOSED on successful request in HALF_OPEN, fallback not called")
        fun shouldTransitionToClosedOnSuccessInHalfOpen() {
            circuitBreaker.execute(successFunction, spyFallbackFunction) // Primary succeeds, so fallback isn't called
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)

            // Verify events: Success event + State changed event
            verify(mockEventPublisher, times(2)).publishEvent(eventCaptor.capture())
            val events = eventCaptor.allValues
            assertTrue(events.any { it is CircuitBreakerSuccessEvent })
            assertTrue(events.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.HALF_OPEN && it.newState == CircuitBreakerState.CLOSED })

            verify(spyFallbackFunction, never()).invoke() // Success, so fallback not called
        }

        @Test
        @DisplayName("Should transition to OPEN on failed request in HALF_OPEN, fallback IS called")
        fun shouldTransitionToOpenOnFailureInHalfOpen() {
            val result = circuitBreaker.execute(failingFunction, spyFallbackFunction)
            assertEquals("Fallback", result) // Fallback should be called
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state) // Circuit should go back to OPEN

            // Verify events: Failure event + State changed event
            verify(mockEventPublisher, times(2)).publishEvent(eventCaptor.capture())
            val events = eventCaptor.allValues
            assertTrue(events.any { it is CircuitBreakerFailureEvent })
            assertTrue(events.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.HALF_OPEN && it.newState == CircuitBreakerState.OPEN })

            verify(spyFallbackFunction, times(1)).invoke() // Fallback should be called
        }
    }

    @Nested
    @DisplayName("Execute Method Behavior")
    inner class ExecuteMethodTests {
        @BeforeEach
        fun setup() {
            circuitBreaker = CustomTimeCBImplement(
                CB_NAME,
                FAILURE_RATE_THRESHOLD,
                FAILURE_WINDOW_SECONDS,
                RESET_TIMEOUT_SECONDS,
                mockEventPublisher
            )
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)
        }

        @Test
        @DisplayName("Execute should return primary result on success in CLOSED state")
        fun executeShouldReturnPrimaryResultOnSuccessInClosed() {
            val result = circuitBreaker.execute(successFunction)
            assertEquals("Success", result)
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            verify(spyFallbackFunction, never()).invoke()
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerSuccessEvent::class.java))
        }

        @Test
        @DisplayName("Execute should call fallback on failure in CLOSED state if provided")
        fun executeShouldCallFallbackOnFailureInClosed() {
            // Assert it is CLOSED first and then call the failing function
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            val result = circuitBreaker.execute(failingFunction, spyFallbackFunction)

            assertEquals("Fallback", result)
            // The state should remain closed as the failure count is still one
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerFailureEvent::class.java))
            verify(spyFallbackFunction, times(1)).invoke()
        }

        @Test
        @DisplayName("Execute should open circuit on failure rate threshold and then call fallback if provided")
        fun executeShouldOpenCircuitOnFailureRateThresholdAndCallFallback() {
            // Simulate failures to open the circuit based on failure rate
            // 2 failures out of 3 calls will open it (0.66 > 0.5 threshold)
            circuitBreaker.execute(successFunction, spyFallbackFunction)
            Thread.sleep(10)
            val result1 = circuitBreaker.execute(failingFunction, spyFallbackFunction) // This should trip it
            Thread.sleep(10)
            val result2 = circuitBreaker.execute(failingFunction, spyFallbackFunction) // This will be blocked

            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            assertEquals("Fallback", result1) // Fallback for 1st failure
            assertEquals("Fallback", result2) // Fallback for blocked request

            // Verify fallback called for each failure
            verify(spyFallbackFunction, times(2)).invoke() // Called for each failing primary

            // Verify events: 1 success, 2 failures, 1 state change (CLOSED -> OPEN)
            verify(mockEventPublisher, times(4)).publishEvent(eventCaptor.capture())
            val events = eventCaptor.allValues
            events.forEach { event ->
                logger.info("CB ${event.circuitBreakerName} and ${event.javaClass}")
            }
            assertTrue(events.any { it is CircuitBreakerSuccessEvent })
            assertEquals(1,events.filterIsInstance<CircuitBreakerFailureEvent>().count())
            assertEquals(1, events.filterIsInstance<CircuitBreakerRequestBlockedEvent>().count())
            assertTrue(events.any { it is CircuitBreakerStateChangedEvent && it.newState == CircuitBreakerState.OPEN })
        }


        @Test
        @DisplayName("Execute should throw CircuitBreakerOpenException if OPEN and no fallback")
        fun executeShouldThrowIfOpenAndNoFallback() {
            // Force open the circuit
            repeat(3) { // 3 failures
                assertFailsWith<RuntimeException> { circuitBreaker.execute(failingFunction) }
                Thread.sleep(5)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)

            assertFailsWith<CircuitBreakerOpenException> {
                circuitBreaker.execute(successFunction) // No fallback provided
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerRequestBlockedEvent::class.java))
            verify(spyFallbackFunction, never()).invoke() // No fallback provided, so not called
        }

        @Test
        @DisplayName("Execute should re-throw if fallback fails in OPEN state")
        fun executeShouldRethrowIfFallbackFailsInOpenState() {
            // Force open the circuit
            repeat(3) {
                assertFailsWith<RuntimeException> { circuitBreaker.execute(failingFunction) }
                Thread.sleep(5)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)

            val failingFallBackFunction = { throw java.lang.IllegalStateException("Fallback failed!") }
            assertFailsWith<IllegalStateException> {
                circuitBreaker.execute(successFunction, failingFallBackFunction)
            }
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state)
            verify(mockEventPublisher).publishEvent(any(CircuitBreakerRequestBlockedEvent::class.java))
            // The spyFallbackFunction is not used here, the failingFallBackFunction is.
            // If you wanted to spy the failingFallBackFunction, you'd need to spy it.
        }

        @Test
        @DisplayName("Execute should transition to CLOSED from HALF_OPEN on success")
        fun executeShouldTransitionToClosedFromHalfOpenOnSuccess() {
            // 1. Force open
            repeat(3) {
                assertFailsWith<RuntimeException> { circuitBreaker.execute(failingFunction) }
                Thread.sleep(5)
            }
            // 2. Wait and allow transition to HALF_OPEN
            Thread.sleep(Duration.ofSeconds(RESET_TIMEOUT_SECONDS.toLong()).toMillis() + SLEEP_BUFFER_MILLIS)
            circuitBreaker.allowRequest() // Trigger HALF_OPEN transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)

            // 3. Execute with success
            val result = circuitBreaker.execute(successFunction, spyFallbackFunction)
            assertEquals("Success", result)
            assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.state)

            verify(mockEventPublisher, times(2)).publishEvent(eventCaptor.capture()) // Success + State Change
            assertTrue(eventCaptor.allValues.any { it is CircuitBreakerSuccessEvent })
            assertTrue(eventCaptor.allValues.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.HALF_OPEN && it.newState == CircuitBreakerState.CLOSED })
            verify(spyFallbackFunction, never()).invoke() // Primary succeeded
        }

        @Test
        @DisplayName("Execute should transition to OPEN from HALF_OPEN on failure")
        fun executeShouldTransitionToOpenFromHalfOpenOnFailure() {
            // 1. Force open
            repeat(3) {
                assertFailsWith<RuntimeException> { circuitBreaker.execute(failingFunction) }
                Thread.sleep(5)
            }
            // 2. Wait and allow transition to HALF_OPEN
            Thread.sleep(Duration.ofSeconds(RESET_TIMEOUT_SECONDS.toLong()).toMillis() + SLEEP_BUFFER_MILLIS)
            circuitBreaker.allowRequest() // Trigger HALF_OPEN transition
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.state)
            reset(mockEventPublisher)
            clearInvocations(spyFallbackFunction)

            // 3. Execute with failure
            val result = circuitBreaker.execute(failingFunction, spyFallbackFunction)
            assertEquals("Fallback", result)
            assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state) // Circuit should go back to OPEN

            verify(mockEventPublisher, times(2)).publishEvent(eventCaptor.capture()) // Failure + State Change
            assertTrue(eventCaptor.allValues.any { it is CircuitBreakerFailureEvent })
            assertTrue(eventCaptor.allValues.any { it is CircuitBreakerStateChangedEvent && it.oldState == CircuitBreakerState.HALF_OPEN && it.newState == CircuitBreakerState.OPEN })
            verify(spyFallbackFunction, times(1)).invoke() // Fallback should be called
        }
    }
}