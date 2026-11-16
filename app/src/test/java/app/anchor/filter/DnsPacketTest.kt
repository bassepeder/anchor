package app.anchor.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class DnsPacketTest {
    private val client = ip("10.111.222.1")
    private val fakeDns = ip("10.111.222.2")

    private fun ip(s: String): ByteArray = InetAddress.getByName(s).address
    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /** An IPv4/UDP packet from the client to the fake DNS server, as it arrives from the tunnel. */
    private fun clientPacket(dns: ByteArray, srcPort: Int = 40_000): ByteArray =
        DnsPacket.reply(DnsQuery(fakeDns, client, srcPort = 53, dstPort = srcPort, dns = dns), dns)

    @Test
    fun `query round-trips name and type`() {
        val q = DnsPacket.query("WWW.Example.com", DnsPacket.TYPE_A)
        assertEquals("www.example.com", DnsPacket.questionName(q))
        assertEquals(DnsPacket.TYPE_A, DnsPacket.questionType(q))
    }

    @Test
    fun `compressed or truncated question names are rejected`() {
        val header = ByteArray(12)
        assertNull(DnsPacket.questionName(header + byteArrayOf(0xC0.toByte(), 0x0C)))
        assertNull(DnsPacket.questionName(header + byteArrayOf(5, 'a'.code.toByte())))
    }

    @Test
    fun `NXDOMAIN keeps the id and question`() {
        val q = DnsPacket.query("example.com", DnsPacket.TYPE_A)
        val r = DnsPacket.nxdomain(q)
        assertEquals(u16(q, 0), u16(r, 0))
        assertEquals(0x80, r[2].toInt() and 0x80) // response
        assertEquals(0x01, r[2].toInt() and 0x01) // recursion desired is echoed
        assertEquals(3, r[3].toInt() and 0x0F)
        assertEquals(1, u16(r, 4))
        assertEquals(0, u16(r, 6))
        assertEquals("example.com", DnsPacket.questionName(r))
    }

    @Test
    fun `no-data answer is a success with no records`() {
        val r = DnsPacket.noData(DnsPacket.query("example.com", 28))
        assertEquals(0, r[3].toInt() and 0x0F)
        assertEquals(0, u16(r, 6))
    }

    @Test
    fun `A answers round-trip through aRecords`() {
        val ips = listOf(ip("216.239.38.120"), ip("216.239.38.121"))
        val r = DnsPacket.answerA(DnsPacket.query("forcesafesearch.google.com", DnsPacket.TYPE_A), ips, ttl = 70_000)
        assertEquals(2, u16(r, 6))
        val parsed = DnsPacket.aRecords(r)
        assertEquals(2, parsed.size)
        ips.zip(parsed).forEach { (want, got) -> assertArrayEquals(want, got) }
    }

    @Test
    fun `parse reads a UDP query to port 53`() {
        val dns = DnsPacket.query("example.com", DnsPacket.TYPE_A)
        val packet = clientPacket(dns)
        val q = DnsPacket.parse(packet, packet.size)!!
        assertArrayEquals(client, q.srcIp)
        assertArrayEquals(fakeDns, q.dstIp)
        assertEquals(40_000, q.srcPort)
        assertEquals(53, q.dstPort)
        assertArrayEquals(dns, q.dns)
    }

    @Test
    fun `parse ignores everything that isn't an IPv4 UDP DNS query`() {
        val packet = clientPacket(DnsPacket.query("example.com", DnsPacket.TYPE_A))
        assertNull(DnsPacket.parse(packet, 19))
        assertNull(DnsPacket.parse(packet.copyOf().also { it[9] = 6 }, packet.size)) // TCP
        assertNull(DnsPacket.parse(packet.copyOf().also { it[0] = 0x65 }, packet.size)) // IPv6
        assertNull(DnsPacket.parse(packet.copyOf().also { it[22] = 0; it[23] = 54 }, packet.size)) // port 54
    }

    @Test
    fun `reply swaps addresses and ports and has a valid header checksum`() {
        val packet = clientPacket(DnsPacket.query("example.com", DnsPacket.TYPE_A))
        val q = DnsPacket.parse(packet, packet.size)!!
        val payload = DnsPacket.nxdomain(q.dns)
        val r = DnsPacket.reply(q, payload)

        assertArrayEquals(fakeDns, r.copyOfRange(12, 16))
        assertArrayEquals(client, r.copyOfRange(16, 20))
        assertEquals(53, u16(r, 20))
        assertEquals(40_000, u16(r, 22))
        assertEquals(r.size, u16(r, 2))
        assertEquals(8 + payload.size, u16(r, 24))
        assertArrayEquals(payload, r.copyOfRange(28, r.size))

        var sum = (0 until 20 step 2).sumOf { u16(r, it) }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }
}
