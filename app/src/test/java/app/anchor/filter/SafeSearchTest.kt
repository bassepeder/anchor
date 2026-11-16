package app.anchor.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSearchTest {
    private fun target(host: String, strict: Boolean = false) = SafeSearch.target(host, strict)

    @Test
    fun `Google search on any country domain`() {
        listOf("google.com", "www.google.com", "google.co.uk", "www.google.com.au", "google.de").forEach {
            assertEquals(it, "forcesafesearch.google.com", target(it))
        }
    }

    @Test
    fun `other Google hosts are left alone`() {
        assertNull(target("mail.google.com"))
        assertNull(target("notgoogle.com"))
        assertNull(target("google.example.org"))
    }

    @Test
    fun `Bing and DuckDuckGo`() {
        assertEquals("strict.bing.com", target("www.bing.com"))
        assertEquals("strict.bing.com", target("bing.com"))
        assertEquals("safe.duckduckgo.com", target("duckduckgo.com"))
    }

    @Test
    fun `YouTube is moderate unless strict`() {
        assertEquals("restrictmoderate.youtube.com", target("www.youtube.com"))
        assertEquals("restrict.youtube.com", target("youtubei.googleapis.com", strict = true))
        assertNull(target("music.youtube.com"))
    }
}
