package app.anchor.protection

import app.anchor.FakeClock
import app.anchor.appContext
import app.anchor.data.Rules
import app.anchor.setBootCount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnlockTest {
    private val ctx get() = appContext
    private val clock = FakeClock()
    private var boot = 10
    private val gap get() = Rules.gapMs(ctx)

    @Before
    fun setUp() {
        Unlock.clock = { clock.now }
        Rules.setRestarts(ctx, 2)
        Rules.setGapHours(ctx, 1)
        Rules.setWindowMinutes(ctx, 10)
        setBootCount(boot)
    }

    @After
    fun tearDown() {
        Unlock.clock = System::currentTimeMillis
    }

    private fun restart(after: Long) {
        clock.advance(after)
        setBootCount(++boot)
        Unlock.onBoot(ctx)
    }

    private fun assertState(state: UnlockState, counted: Int) {
        assertEquals(state, Unlock.state(ctx))
        assertEquals(counted, Unlock.counted(ctx))
    }

    @Test
    fun `starts locked`() = assertState(UnlockState.LOCKED, 0)

    @Test
    fun `request starts a pending unlock`() {
        Unlock.request(ctx)
        assertState(UnlockState.PENDING, 0)
    }

    @Test
    fun `restarts while locked are ignored`() {
        restart(gap)
        assertState(UnlockState.LOCKED, 0)
    }

    @Test
    fun `restart before the gap has passed does not count`() {
        Unlock.request(ctx)
        restart(gap - 1)
        assertState(UnlockState.PENDING, 0)
    }

    @Test
    fun `restart after the gap counts`() {
        Unlock.request(ctx)
        restart(gap)
        assertState(UnlockState.PENDING, 1)
    }

    @Test
    fun `an early restart does not reset the wait`() {
        Unlock.request(ctx)
        restart(gap / 2)
        restart(gap / 2)
        assertState(UnlockState.PENDING, 1)
    }

    @Test
    fun `gap is measured from the last counted restart`() {
        Unlock.request(ctx)
        restart(gap)
        restart(gap - 1)
        assertState(UnlockState.PENDING, 1)
    }

    @Test
    fun `the same boot reported twice counts once`() {
        Unlock.request(ctx)
        restart(gap)
        clock.advance(gap)
        Unlock.onBoot(ctx)
        assertState(UnlockState.PENDING, 1)
    }

    @Test
    fun `opens after the required restarts, for the unlock window`() {
        Unlock.request(ctx)
        restart(gap)
        restart(gap)
        assertEquals(UnlockState.OPEN, Unlock.state(ctx))
        assertEquals(clock.now + Rules.windowMs(ctx), Unlock.openUntil(ctx))
    }

    @Test
    fun `locks again when the window ends`() {
        Unlock.request(ctx)
        restart(gap)
        restart(gap)
        clock.advance(Rules.windowMs(ctx))
        assertEquals(UnlockState.OPEN, Unlock.state(ctx))
        clock.advance(1)
        assertState(UnlockState.LOCKED, 0)
    }

    @Test
    fun `requesting again while pending keeps progress`() {
        Unlock.request(ctx)
        restart(gap)
        Unlock.request(ctx)
        assertState(UnlockState.PENDING, 1)
    }

    @Test
    fun `lock cancels a pending unlock`() {
        Unlock.request(ctx)
        restart(gap)
        Unlock.lock(ctx)
        restart(gap)
        assertState(UnlockState.LOCKED, 0)
    }

    @Test
    fun `raising restarts while pending needs the extra restarts`() {
        Unlock.request(ctx)
        Rules.setRestarts(ctx, 3)
        restart(gap)
        restart(gap)
        assertState(UnlockState.PENDING, 2)
        restart(gap)
        assertEquals(UnlockState.OPEN, Unlock.state(ctx))
    }
}
