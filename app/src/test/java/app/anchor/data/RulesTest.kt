package app.anchor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RulesTest {
    @Test
    fun `hostnames are trimmed and lowercased`() {
        assertEquals("abc123.d.adguard-dns.com", Rules.normalizeHost("  ABC123.d.AdGuard-DNS.com "))
    }

    @Test
    fun `anything that isn't a bare hostname is rejected`() {
        assertNull(Rules.normalizeHost(""))
        assertNull(Rules.normalizeHost("localhost"))
        assertNull(Rules.normalizeHost("https://dns.example.com"))
        assertNull(Rules.normalizeHost("dns.example.com/path"))
        assertNull(Rules.normalizeHost("dns example.com"))
    }
}
