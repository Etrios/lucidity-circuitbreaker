package com.ganeshl.luciditycircuitbreaker.config

import com.ganeshl.luciditycircuitbreaker.CB.model.CustomCBType
import com.ganeshl.luciditycircuitbreaker.model.CBType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.settings")
data class AppConfigProperties(
    var currentCB: CustomCBType = CustomCBType.CustomCountCB,
    var isRandomErrorEnabled : Boolean = false,
    var errorRate: Float = 0.5f,
    var timeDelayEnabled: Boolean = false,
    var operationTimeDelayInMillis: Long = 3000L
)