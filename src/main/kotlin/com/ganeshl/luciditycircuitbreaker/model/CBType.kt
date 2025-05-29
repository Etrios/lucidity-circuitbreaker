package com.ganeshl.luciditycircuitbreaker.model

enum class CBType(val typeName: String) {
    CountCB("CountCB"),
    TimeCB("TimeCB")
}