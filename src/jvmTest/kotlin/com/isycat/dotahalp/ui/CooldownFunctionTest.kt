package com.isycat.dotahalp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.isycat.compose.util.CooldownFunction
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CooldownFunctionTest {
    @Test
    fun `shouldTrigger enforces cooldown`() {
        val timeSource = TestTimeSource()
        val limiter = CooldownFunction(cooldown = 3.seconds)

        assertTrue(limiter.shouldTrigger(timeSource.markNow()))
        assertFalse(limiter.shouldTrigger(timeSource.markNow()))

        timeSource += 2999.milliseconds
        assertFalse(limiter.shouldTrigger(timeSource.markNow()))

        timeSource += 2.milliseconds
        assertTrue(limiter.shouldTrigger(timeSource.markNow()))
    }
}
