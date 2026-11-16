package app.anchor.filter

import app.anchor.appContext
import app.anchor.data.Rules
import app.anchor.data.Stats
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DnsFilterTest {
    private val ctx get() = appContext
    private val upstreamIp = byteArrayOf(93, 184.toByte(), 216.toByte(), 34)
    private val forwarded = mutableListOf<String>()
    private var upstreamUp = true

    private val filter by lazy {
        DnsFilter(ctx) { query ->
            forwarded += DnsPacket.questionName(query)!!
            if (upstreamUp) DnsPacket.answerA(query, listOf(upstreamIp)) else null
        }
    }

    @Before
    fun setUp() = BlockList.reload(ctx)

    private fun ask(host: String, type: Int = DnsPacket.TYPE_A) = filter.answer(DnsPacket.query(host, type))
    private fun rcode(answer: ByteArray) = answer[3].toInt() and 0x0F
    private fun answerCount(answer: ByteArray) = ((answer[6].toInt() and 0xFF) shl 8) or (answer[7].toInt() and 0xFF)

    @Test
    fun `blocked names get NXDOMAIN without asking upstream`() {
        val answer = ask("www.pornhub.com")!!
        assertEquals(3, rcode(answer))
        assertTrue(forwarded.isEmpty())
        assertEquals(1, Stats.blockedToday(ctx))
    }

    @Test
    fun `other names are forwarded unchanged`() {
        val answer = ask("example.com")!!
        assertEquals(listOf("example.com"), forwarded)
        assertArrayEquals(upstreamIp, DnsPacket.aRecords(answer).single())
    }

    @Test
    fun `Google is answered with the SafeSearch address`() {
        val answer = ask("www.google.com")!!
        assertEquals(listOf("forcesafesearch.google.com"), forwarded)
        assertEquals("www.google.com", DnsPacket.questionName(answer))
        assertArrayEquals(upstreamIp, DnsPacket.aRecords(answer).single())
    }

    @Test
    fun `IPv6 lookups for rewritten names get an empty answer`() {
        val answer = ask("www.google.com", type = 28)!!
        assertEquals(0, rcode(answer))
        assertEquals(0, answerCount(answer))
        assertTrue(forwarded.isEmpty())
    }

    @Test
    fun `YouTube follows the strict setting`() {
        ask("www.youtube.com")
        Rules.setYoutubeStrict(ctx, true)
        ask("m.youtube.com")
        assertEquals(listOf("restrictmoderate.youtube.com", "restrict.youtube.com"), forwarded)
    }

    @Test
    fun `rewrite targets are cached`() {
        ask("www.google.com")
        ask("google.com")
        assertEquals(1, forwarded.size)
    }

    @Test
    fun `no answer when upstream is down`() {
        upstreamUp = false
        assertNull(ask("www.google.com"))
        assertNull(ask("example.com"))
    }
}
