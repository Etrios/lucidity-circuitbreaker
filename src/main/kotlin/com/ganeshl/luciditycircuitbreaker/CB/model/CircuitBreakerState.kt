package com.ganeshl.luciditycircuitbreaker.CB.model

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}