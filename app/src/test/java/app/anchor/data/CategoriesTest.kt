package app.anchor.data

import app.anchor.appContext
import app.anchor.filter.BlockList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesTest {
    private val ctx get() = appContext
    private val all get() = Categories.all(ctx)
    private fun category(id: String) = all.first { it.id == id }

    @Test
    fun `parses every category in order`() {
        assertEquals(listOf("adult", "bypass", "social", "shorts", "video", "gambling", "dating"), all.map { it.id })
        all.forEach { assertTrue(it.id, it.name.isNotBlank() && it.description.isNotBlank()) }
    }

    @Test
    fun `sorts lines into domains, paths, chrome-only entries and apps`() {
        assertTrue("pornhub.com" in category("adult").domains)
        assertTrue("youtube.com/shorts" in category("shorts").domains)
        assertTrue("facebook.com" in category("social").chromeOnly)
        assertTrue("com.instagram.android" in category("social").apps)
    }

    @Test
    fun `a fresh install has the default categories on`() {
        assertEquals(setOf("adult", "bypass", "social", "shorts"), Categories.enabledIds(ctx))
    }

    @Test
    fun `adult and bypass can't be switched off`() {
        Categories.setEnabled(ctx, "adult", false)
        Store.edit(ctx) { putStringSet(Store.CATEGORIES, emptySet()) }
        assertEquals(setOf("adult", "bypass"), Categories.enabledIds(ctx))
    }

    @Test
    fun `switches are saved`() {
        Categories.setEnabled(ctx, "video", true)
        Categories.setEnabled(ctx, "social", false)
        assertEquals(setOf("adult", "bypass", "shorts", "video"), Categories.enabledIds(ctx))
    }

    @Test
    fun `every site entry is already in normalized form`() {
        all.flatMap { it.domains + it.chromeOnly }.forEach { assertEquals(it, BlockList.normalize(it)) }
    }

    @Test
    fun `every app entry is a package name`() {
        val pkg = Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z_][\\w]*)+$")
        all.flatMap { it.apps }.forEach { assertTrue(it, pkg.matches(it)) }
    }

    @Test
    fun `presets only use categories that exist`() {
        val ids = all.map { it.id }.toSet()
        Presets.ALL.forEach { assertTrue(it.id, ids.containsAll(it.categories)) }
    }
}
