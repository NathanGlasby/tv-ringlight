package com.ringlight.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class RingLightSettingsTest {
    @Test fun `duration controls stop at five minutes and eight hours`() {
        assertEquals(5, RingLightSettings.previousDuration(5))
        assertEquals(480, RingLightSettings.nextDuration(480))
    }

    @Test fun `duration controls can recover a custom saved value in either direction`() {
        assertEquals(60, RingLightSettings.previousDuration(75))
        assertEquals(90, RingLightSettings.nextDuration(75))
    }

    @Test fun `each timer choice is reachable and reversible`() {
        val choices = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480)
        choices.zipWithNext().forEach { (before, after) ->
            assertEquals(after, RingLightSettings.nextDuration(before))
            assertEquals(before, RingLightSettings.previousDuration(after))
        }
    }

    @Test fun `invalid saved timer durations remain recoverable`() {
        assertEquals(5, RingLightSettings.nextDuration(Int.MIN_VALUE))
        assertEquals(5, RingLightSettings.previousDuration(Int.MIN_VALUE))
        assertEquals(480, RingLightSettings.nextDuration(Int.MAX_VALUE))
        assertEquals(480, RingLightSettings.previousDuration(Int.MAX_VALUE))
    }
}
