package app.anchor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    private val byLevel = Presets.ALL.sortedBy { it.level }

    @Test
    fun `Balanced is the one recommended preset`() {
        assertEquals(listOf("balanced"), Presets.ALL.filter { it.recommended }.map { it.id })
        assertEquals("balanced", Presets.DEFAULT.id)
    }

    @Test
    fun `every preset blocks adult content and bypass tools`() {
        Presets.ALL.forEach { assertTrue(it.id, it.categories.containsAll(setOf("adult", "bypass"))) }
    }

    @Test
    fun `each level is at least as strict as the one before`() {
        byLevel.zipWithNext().forEach { (a, b) ->
            val pair = "${a.id} -> ${b.id}"
            assertTrue(pair, a.level < b.level)
            assertTrue(pair, b.categories.containsAll(a.categories))
            assertTrue(pair, b.friction.restarts >= a.friction.restarts)
            assertTrue(pair, b.friction.gapHours >= a.friction.gapHours)
            assertTrue(pair, b.friction.windowMinutes <= a.friction.windowMinutes)
            assertTrue(pair, b.youtubeStrict || !a.youtubeStrict)
            assertTrue(pair, b.freezeInstalls || !a.freezeInstalls)
        }
    }

    @Test
    fun `minimum wait is restarts times the gap`() {
        assertEquals(3 * Rules.gapMs(12), Presets.FIRM.minimumWaitMs)
        assertEquals(5 * Rules.gapMs(24), Presets.IRON.minimumWaitMs)
    }
}
