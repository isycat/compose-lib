package com.isycat.compose.util

import kotlin.time.Duration
import kotlin.time.TimeMark

class CooldownFunction(
    private val cooldown: Duration
) {
    private var lastTrigger: TimeMark? = null

    fun shouldTrigger(now: TimeMark): Boolean {
        val previous = lastTrigger
        if (previous == null || previous.elapsedNow() >= cooldown) {
            lastTrigger = now
            return true
        }
        return false
    }
}
