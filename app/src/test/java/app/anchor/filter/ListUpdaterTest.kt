package app.anchor.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListUpdaterTest {
    private fun parse(line: String) = ListUpdater.parseLine(line)

    @Test
    fun `plain domains`() {
        assertEquals("example.com", parse("example.com"))
        assertEquals("example.com", parse("  Example.COM.  "))
    }

    @Test
    fun `hosts-file lines`() {
        assertEquals("example.com", parse("0.0.0.0 example.com"))
        assertEquals("ads.example.com", parse("127.0.0.1\tads.example.com"))
        assertNull(parse("0.0.0.0 localhost"))
    }

    @Test
    fun `adblock rules`() {
        assertEquals("example.com", parse("||example.com^"))
        assertEquals("example.com", parse("||example.com^\$third-party"))
        assertEquals("example.com", parse("*.example.com"))
    }

    @Test
    fun `comments, blanks and junk are skipped`() {
        assertNull(parse(""))
        assertNull(parse("# comment"))
        assertNull(parse("! adblock comment"))
        assertNull(parse("example"))
        assertNull(parse("example.com/path"))
        assertNull(parse("ex_ample.com"))
    }
}
