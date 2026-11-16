package app.anchor.protection

import app.anchor.FakeClock
import app.anchor.appContext
import app.anchor.data.Categories
import app.anchor.data.Category
import app.anchor.data.Rules
import app.anchor.data.Store
import app.anchor.filter.BlockList
import app.anchor.setBootCount
import app.anchor.turnOnProtection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControlTest {
    private val ctx get() = appContext
    private val clock = FakeClock()

    @Before
    fun setUp() {
        Unlock.clock = { clock.now }
        turnOnProtection()
    }

    @After
    fun tearDown() {
        Unlock.clock = System::currentTimeMillis
    }

    private fun openUnlock() {
        Rules.setRestarts(ctx, 1)
        setBootCount(1)
        Unlock.request(ctx)
        clock.advance(Rules.gapMs(ctx))
        setBootCount(2)
        Unlock.onBoot(ctx)
        assertEquals(UnlockState.OPEN, Unlock.state(ctx))
    }

    private fun category(id: String): Category = Categories.all(ctx).first { it.id == id }
    private fun sites() = Store.stringSet(ctx, Store.USER_SITES)

    @Test
    fun `adding a site is instant while locked`() {
        Control.addSite(ctx, "example.com")
        assertTrue("example.com" in sites())
        assertTrue(BlockList.isBlocked("www.example.com"))
    }

    @Test
    fun `removing a site needs an unlock`() {
        Control.addSite(ctx, "example.com")
        assertFalse(Control.removeSite(ctx, "example.com"))
        assertTrue("example.com" in sites())

        openUnlock()
        assertTrue(Control.removeSite(ctx, "example.com"))
        assertFalse("example.com" in sites())
        assertFalse(BlockList.isBlocked("www.example.com"))
    }

    @Test
    fun `allowing a site needs an unlock, un-allowing is instant`() {
        assertFalse(Control.allowSite(ctx, "example.com"))
        assertTrue(Store.stringSet(ctx, Store.ALLOWED).isEmpty())

        openUnlock()
        assertTrue(Control.allowSite(ctx, "example.com"))
        Unlock.lock(ctx)
        Control.unallowSite(ctx, "example.com")
        assertTrue(Store.stringSet(ctx, Store.ALLOWED).isEmpty())
    }

    @Test
    fun `showing a hidden app needs an unlock`() {
        Control.hideApp(ctx, "com.example.feed")
        assertTrue("com.example.feed" in Store.stringSet(ctx, Store.USER_HIDDEN_APPS))
        assertFalse(Control.showApp(ctx, "com.example.feed"))

        openUnlock()
        assertTrue(Control.showApp(ctx, "com.example.feed"))
        assertFalse("com.example.feed" in Store.stringSet(ctx, Store.USER_HIDDEN_APPS))
    }

    @Test
    fun `categories switch on instantly but need an unlock to switch off`() {
        assertTrue(Control.setCategory(ctx, category("gambling"), on = true))
        assertFalse(Control.setCategory(ctx, category("gambling"), on = false))
        assertTrue("gambling" in Categories.enabledIds(ctx))

        openUnlock()
        assertTrue(Control.setCategory(ctx, category("gambling"), on = false))
        assertFalse("gambling" in Categories.enabledIds(ctx))
    }

    @Test
    fun `always-on categories can't be switched off, even unlocked`() {
        openUnlock()
        assertFalse(Control.setCategory(ctx, category("adult"), on = false))
        assertTrue("adult" in Categories.enabledIds(ctx))
    }

    @Test
    fun `more restarts is stricter, fewer is looser`() {
        assertTrue(Control.setRestarts(ctx, 4))
        assertTrue(Control.setRestarts(ctx, 4))
        assertFalse(Control.setRestarts(ctx, 2))
        assertEquals(4, Rules.restarts(ctx))
    }

    @Test
    fun `a longer gap is stricter, a shorter one is looser`() {
        assertTrue(Control.setGapHours(ctx, 24))
        assertFalse(Control.setGapHours(ctx, 1))
        assertEquals(24, Rules.gapHours(ctx))
    }

    @Test
    fun `a shorter unlock window is stricter, a longer one is looser`() {
        assertTrue(Control.setWindowMinutes(ctx, 10))
        assertFalse(Control.setWindowMinutes(ctx, 60))
        assertEquals(10, Rules.windowMinutes(ctx))
    }

    @Test
    fun `strict YouTube and frozen installs only switch on while locked`() {
        assertTrue(Control.setYoutubeStrict(ctx, true))
        assertFalse(Control.setYoutubeStrict(ctx, false))
        assertTrue(Rules.youtubeStrict(ctx))

        assertTrue(Control.setFreezeInstalls(ctx, true))
        assertFalse(Control.setFreezeInstalls(ctx, false))
        assertTrue(Rules.freezeInstalls(ctx))
    }

    @Test
    fun `the DNS filter can't be switched off while locked`() {
        assertFalse(Control.setDnsFilter(ctx, false))
        assertTrue(Rules.dnsFilter(ctx))
    }

    @Test
    fun `locking a Private DNS host is stricter, changing or clearing it is looser`() {
        assertTrue(Control.setPrivateDnsHost(ctx, "a.example.com"))
        assertFalse(Control.setPrivateDnsHost(ctx, "b.example.com"))
        assertFalse(Control.setPrivateDnsHost(ctx, null))
        assertEquals("a.example.com", Rules.privateDnsHost(ctx))

        openUnlock()
        assertTrue(Control.setPrivateDnsHost(ctx, null))
        assertNull(Rules.privateDnsHost(ctx))
    }

    @Test
    fun `everything can be loosened before protection is on`() {
        Store.setActivated(ctx, false)
        Control.addSite(ctx, "example.com")
        assertTrue(Control.removeSite(ctx, "example.com"))
        assertTrue(Control.setRestarts(ctx, 1))
        assertTrue(Control.setDnsFilter(ctx, false))
    }

    @Test
    fun `an expired unlock blocks loosening again`() {
        Control.addSite(ctx, "example.com")
        openUnlock()
        clock.advance(Rules.windowMs(ctx) + 1)
        assertFalse(Control.removeSite(ctx, "example.com"))
    }

    @Test
    fun `turning Anchor off needs an unlock`() {
        assertFalse(Control.turnOff(ctx))
        assertTrue(Store.isActivated(ctx))

        openUnlock()
        assertTrue(Control.turnOff(ctx))
        assertFalse(Store.isActivated(ctx))
        assertEquals(UnlockState.LOCKED, Unlock.state(ctx))
    }
}
