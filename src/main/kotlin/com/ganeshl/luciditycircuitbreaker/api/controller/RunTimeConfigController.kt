package com.ganeshl.luciditycircuitbreaker.api.controller

import com.ganeshl.luciditycircuitbreaker.config.RuntimeConfigManager
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/config")
class RunTimeConfigController(
    val runtimeConfigManager: RuntimeConfigManager
) {

    @GetMapping
    fun getRunTimeConfig(): Map<String, Any?> {
        return runtimeConfigManager.getAllCurrentConfig()
    }

    @PostMapping
    fun setRunTimeConfig(@RequestBody map: Map<String, String>): Map<String, Any?> {
        return runtimeConfigManager.setConfig(map)
    }

}