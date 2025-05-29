package com.ganeshl.luciditycircuitbreaker.config

import com.ganeshl.luciditycircuitbreaker.model.CBType
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

@Component
class RuntimeConfigManager(
    val appConfigProperties: AppConfigProperties
) {

    private val mutableConfig: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    private val lock = ReentrantReadWriteLock()
    private val writeLock = lock.writeLock()
    private val readLock = lock.readLock()

    private val logger: Logger = LoggerFactory.getLogger(RuntimeConfigManager::class.java)

    // This method runs after dependency injection is complete
    @PostConstruct
    fun initializeConfig() {
        // Load initial values from AppConfigProperties
        writeLock.lock() // Acquire write lock for initial population
        try {
            mutableConfig["currentCB"] = appConfigProperties.currentCB.typeName
            mutableConfig["errorRate"] = appConfigProperties.errorRate
            mutableConfig["timeDelayEnabled"] = appConfigProperties.timeDelayEnabled
            mutableConfig["operationTimeDelay"] = appConfigProperties.operationTimeDelayInMillis
            mutableConfig["isRandomErrorEnabled"] = appConfigProperties.isRandomErrorEnabled
            logger.info("RuntimeConfigurationManager initialized with values from application.yml $mutableConfig")
        } finally {
            writeLock.unlock() // Release write lock
        }
    }
    fun isRandomErrorEnabled(): Boolean {
        readLock.lock()
        try {
            return mutableConfig["isRandomErrorEnabled"] as? Boolean ?: false
        } finally {
            readLock.unlock()
        }
    }

    fun getCurrentCB(): String {
        readLock.lock()
        try {
            return mutableConfig["currentCB"] as String
        } finally {
            readLock.unlock()
        }
    }

    fun getErrorRate(): Float {
        readLock.lock()
        try {
            return mutableConfig["errorRate"] as? Float ?: 0.5f
        } finally {
            readLock.unlock()
        }
    }

    fun isTimeDelayEnabled(): Boolean {
        readLock.lock()
        try {
            return mutableConfig["timeDelayEnabled"] as? Boolean ?: false
        } finally {
            readLock.unlock()
        }
    }

    fun getWaitTimeInMillis(): Long {
        readLock.lock()
        try {
            return mutableConfig["operationTimeDelay"] as? Long ?: 3000L
        } finally {
            readLock.unlock()
        }
    }

    // --- For exposing all config via API (returns a consistent snapshot) ---
    fun getAllCurrentConfig(): Map<String, Any> {
        readLock.lock()
        try {
            // Return a defensive copy of the map
            return mutableConfig.toMap()
        } finally {
            readLock.unlock()
        }
    }

    fun setConfig(map: Map<String, String>): Map<String, Any?> {
        logger.error("Received Config change: $map")
        writeLock.lock()
        try {
            mutableConfig["currentCB"] = map["currentCB"] as String
            mutableConfig["errorRate"] = map["errorRate"]?.toFloat() as Float
            mutableConfig["timeDelayEnabled"] = map["timeDelayEnabled"]?.toBoolean() as Boolean
            mutableConfig["operationTimeDelay"] = map["operationTimeDelay"]?.toLong() as Long

            return mutableConfig.toMap()
        } finally {
            writeLock.unlock()
        }
    }
}


