package app.anchor.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `durations`() {
        assertEquals("1 minute", formatDuration(minute))
        assertEquals("5 minutes", formatDuration(5 * minute))
        assertEquals("1 hour", formatDuration(hour))
        assertEquals("24 hours", formatDuration(24 * hour))
        assertEquals("2 days", formatDuration(48 * hour))
        assertEquals("50 hours", formatDuration(50 * hour))
        assertEquals("5 days", formatDuration(120 * hour))
    }

    @Test
    fun `countdowns`() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:00", formatCountdown(-5_000))
        assertEquals("1:01", formatCountdown(61_500))
        assertEquals("30:00", formatCountdown(30 * minute))
    }
}
