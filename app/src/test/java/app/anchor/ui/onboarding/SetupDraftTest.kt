package app.anchor.ui.onboarding

import app.anchor.appContext
import app.anchor.data.Categories
import app.anchor.data.Presets
import app.anchor.data.Rules
import app.anchor.data.Store
import app.anchor.turnOnProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetupDraftTest {
    private val ctx get() = appContext
    private fun preset(id: String) = Presets.ALL.first { it.id == id }

    @Test
    fun `starts on the recommended preset`() {
        val draft = SetupDraft(ctx)
        assertEquals("balanced", draft.presetId)
        assertEquals(Presets.FIRM, draft.friction)
        assertEquals(Presets.DEFAULT.categories, draft.categories)
    }

    @Test
    fun `changing a choice drops the preset, undoing it brings it back`() {
        val draft = SetupDraft(ctx)
        draft.setCategory("gambling", true)
        assertNull(draft.preset)
        draft.setCategory("gambling", false)
        assertEquals("balanced", draft.presetId)

        draft.setYoutube(true)
        assertNull(draft.preset)
        draft.setCategory("gambling", true)
        draft.setCategory("dating", true)
        assertEquals("focused", draft.presetId)
    }

    @Test
    fun `picking a preset applies all of it`() {
        val draft = SetupDraft(ctx)
        draft.apply(preset("phone"))
        assertEquals(preset("phone").categories, draft.categories)
        assertEquals(Presets.IRON, draft.friction)
        assertTrue(draft.youtubeStrict)
        assertTrue(draft.freezeInstalls)
    }

    @Test
    fun `friction is only named when the numbers match one`() {
        val draft = SetupDraft(ctx)
        draft.restarts = 4
        assertNull(draft.friction)
        draft.setFriction(Presets.GENTLE)
        assertEquals(Presets.GENTLE, draft.friction)
        assertEquals(2 * Rules.gapMs(6), draft.minimumWaitMs)
    }

    @Test
    fun `private DNS host must be blank or a hostname`() {
        val draft = SetupDraft(ctx)
        assertTrue(draft.privateDnsHostValid)
        draft.privateDnsHost = "not a host"
        assertFalse(draft.privateDnsHostValid)
        draft.privateDnsHost = "abc.d.adguard-dns.com"
        assertTrue(draft.privateDnsHostValid)
    }

    @Test
    fun `commit saves every choice`() {
        val draft = SetupDraft(ctx)
        draft.apply(preset("focused"))
        draft.dnsFilter = false
        draft.privateDnsHost = " Abc.D.AdGuard-DNS.com "
        draft.commit(ctx)

        assertEquals(preset("focused").categories, Categories.enabledIds(ctx))
        assertEquals(3, Rules.restarts(ctx))
        assertEquals(12, Rules.gapHours(ctx))
        assertTrue(Rules.youtubeStrict(ctx))
        assertFalse(Rules.dnsFilter(ctx))
        assertEquals("abc.d.adguard-dns.com", Rules.privateDnsHost(ctx))
        assertTrue(Store.isOnboarded(ctx))
    }

    @Test
    fun `commit drops the private DNS host while Anchor's own filter is on`() {
        Rules.setPrivateDnsHost(ctx, "x.example.com")
        SetupDraft(ctx).commit(ctx)
        assertNull(Rules.privateDnsHost(ctx))
    }

    @Test
    fun `commit does nothing once protection is on`() {
        turnOnProtection()
        val draft = SetupDraft(ctx)
        draft.apply(preset("essentials"))
        draft.commit(ctx)
        assertEquals(3, Rules.restarts(ctx))
        assertNull(Store.stringSetOrNull(ctx, Store.CATEGORIES))
    }
}
