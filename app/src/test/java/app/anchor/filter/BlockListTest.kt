package app.anchor.filter

import app.anchor.appContext
import app.anchor.data.Categories
import app.anchor.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockListTest {
    private val ctx get() = appContext

    @Before
    fun setUp() = BlockList.reload(ctx)

    private fun userSites(vararg entries: String) {
        Store.addTo(ctx, Store.USER_SITES, entries.toList())
        BlockList.reload(ctx)
    }

    @Test
    fun `normalize strips scheme, www and trailing slash`() {
        assertEquals("youtube.com/shorts", BlockList.normalize("https://www.YouTube.com/shorts/"))
        assertEquals("example.com", BlockList.normalize("  http://example.com  "))
        assertEquals("m.example.com", BlockList.normalize("m.example.com"))
    }

    @Test
    fun `normalize rejects things that aren't sites`() {
        assertNull(BlockList.normalize(""))
        assertNull(BlockList.normalize("example"))
        assertNull(BlockList.normalize("ex_ample.com"))
        assertNull(BlockList.normalize("example.com/some path"))
    }

    @Test
    fun `built-in domains are blocked with their subdomains`() {
        assertTrue(BlockList.isBlocked("pornhub.com"))
        assertTrue(BlockList.isBlocked("www.pornhub.com"))
        assertTrue(BlockList.isBlocked("PornHub.com."))
    }

    @Test
    fun `matching stops at label boundaries`() {
        assertFalse(BlockList.isBlocked("notpornhub.com"))
        assertFalse(BlockList.isBlocked("pornhub.com.example.org"))
        assertFalse(BlockList.isBlocked("example.com"))
    }

    @Test
    fun `user sites are blocked with their subdomains`() {
        userSites("example.com")
        assertTrue(BlockList.isBlocked("news.example.com"))
    }

    @Test
    fun `paths are only blocked in Chrome`() {
        userSites("example.org/feed")
        assertFalse(BlockList.isBlocked("example.org"))
        assertTrue("example.org/feed" in BlockList.chromeBlocklist())
        assertTrue("youtube.com/shorts" in BlockList.chromeBlocklist())
        assertFalse(BlockList.isBlocked("www.youtube.com"))
    }

    @Test
    fun `chrome-only entries don't block other apps`() {
        assertTrue("facebook.com" in BlockList.chromeBlocklist())
        assertFalse(BlockList.isBlocked("facebook.com"))
    }

    @Test
    fun `allowed sites win, including their subdomains`() {
        userSites("example.com")
        Store.addTo(ctx, Store.ALLOWED, "docs.example.com")
        BlockList.reload(ctx)
        assertFalse(BlockList.isBlocked("docs.example.com"))
        assertFalse(BlockList.isBlocked("api.docs.example.com"))
        assertTrue(BlockList.isBlocked("www.example.com"))
        assertEquals(listOf("docs.example.com"), BlockList.chromeAllowlist())
    }

    @Test
    fun `switching a category off unblocks its domains`() {
        assertTrue(BlockList.isBlocked("instagram.com"))
        Categories.setEnabled(ctx, "social", false)
        BlockList.reload(ctx)
        assertFalse(BlockList.isBlocked("instagram.com"))
    }
}
