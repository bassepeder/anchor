package app.anchor

import java.io.ByteArrayOutputStream
import kotlin.random.Random

class DnsQuery(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dns: ByteArray,
)

object DnsPacket {
    const val TYPE_A = 1

    fun parse(p: ByteArray, length: Int): DnsQuery? {
        if (length < 20) return null
        if (((p[0].toInt() shr 4) and 0xF) != 4) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return null
        val total = minOf(u16(p, 2), length)
        if (total < ihl + 8 + 12) return null
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return null
        return DnsQuery(p.copyOfRange(12, 16), p.copyOfRange(16, 20), srcPort, dstPort, p.copyOfRange(ihl + 8, total))
    }

    fun questionName(dns: ByteArray): String? {
        val sb = StringBuilder()
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return sb.toString().lowercase()
            if ((len and 0xC0) != 0 || i + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        return null
    }

    fun questionType(dns: ByteArray): Int {
        val end = questionEnd(dns)
        return if (end >= 17) u16(dns, end - 4) else -1
    }

    fun nxdomain(query: ByteArray) = response(query, rcode = 3, answers = 0)

    fun noData(query: ByteArray) = response(query, rcode = 0, answers = 0)

    fun answerA(query: ByteArray, ips: List<ByteArray>, ttl: Int = 300): ByteArray {
        val head = response(query, rcode = 0, answers = ips.size)
        val out = head.copyOf(head.size + ips.size * 16)
        var i = head.size
        for (ip in ips) {
            out[i] = 0xC0.toByte() // name = pointer to the question name at offset 12
            out[i + 1] = 0x0C
            put16(out, i + 2, TYPE_A)
            put16(out, i + 4, 1)
            put16(out, i + 6, ttl ushr 16)
            put16(out, i + 8, ttl and 0xFFFF)
            put16(out, i + 10, 4)
            ip.copyInto(out, i + 12)
            i += 16
        }
        return out
    }

    fun query(host: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val id = Random.nextInt(0x10000)
        out.write(id shr 8)
        out.write(id and 0xFF)
        out.write(0x01) // recursion desired
        out.write(0x00)
        out.write(0)
        out.write(1)
        repeat(6) { out.write(0) }
        for (label in host.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes, 0, bytes.size)
        }
        out.write(0)
        out.write(type shr 8)
        out.write(type and 0xFF)
        out.write(0)
        out.write(1)
        return out.toByteArray()
    }

    fun aRecords(r: ByteArray): List<ByteArray> {
        if (r.size < 12) return emptyList()
        val questions = u16(r, 4)
        val answers = u16(r, 6)
        var i = 12
        repeat(questions) { i = skipName(r, i) + 4 }
        val out = mutableListOf<ByteArray>()
        repeat(answers) {
            i = skipName(r, i)
            if (i + 10 > r.size) return out
            val type = u16(r, i)
            val len = u16(r, i + 8)
            val data = i + 10
            if (data + len > r.size) return out
            if (type == TYPE_A && len == 4) out += r.copyOfRange(data, data + 4)
            i = data + len
        }
        return out
    }

    fun reply(q: DnsQuery, payload: ByteArray): ByteArray {
        val total = 28 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        p[6] = 0x40
        p[8] = 64
        p[9] = 17
        q.dstIp.copyInto(p, 12)
        q.srcIp.copyInto(p, 16)
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, q.dstPort)
        put16(p, 22, q.srcPort)
        // UDP checksum left at 0, which IPv4 allows to mean "none".
        put16(p, 24, 8 + payload.size)
        payload.copyInto(p, 28)
        return p
    }

    private fun response(query: ByteArray, rcode: Int, answers: Int): ByteArray {
        val r = query.copyOf(questionEnd(query))
        r[2] = ((query[2].toInt() and 0x79) or 0x80).toByte() // QR=1, keep opcode and RD
        r[3] = (0x80 or rcode).toByte()                         // RA=1
        r[4] = 0
        r[5] = 1
        put16(r, 6, answers)
        for (i in 8..11) r[i] = 0
        return r
    }

    private fun questionEnd(dns: ByteArray): Int {
        var i = 12
        while (i < dns.size && dns[i].toInt() != 0) i += 1 + (dns[i].toInt() and 0xFF)
        return minOf(i + 1 + 4, dns.size)
    }

    private fun skipName(b: ByteArray, start: Int): Int {
        var i = start
        while (i < b.size) {
            val len = b[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if ((len and 0xC0) == 0xC0) return i + 2
            i += 1 + len
        }
        return b.size
    }

    private fun checksum(b: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += u16(b, i)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }
}
