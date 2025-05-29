package com.ganeshl.luciditycircuitbreaker.config

import com.ganeshl.luciditycircuitbreaker.model.CBType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.settings")
data class AppConfigProperties(
    var currentCB: CBType = CBType.CountCB,
    var isRandomErrorEnabled : Boolean = false,
    var errorRate: Float = 0.5f,
    var timeDelayEnabled: Boolean = false,
    var operationTimeDelayInMillis: Long = 3000L
)