package app.anchor.filter

import android.content.Context
import app.anchor.data.Rules
import app.anchor.data.Stats
import java.util.concurrent.ConcurrentHashMap

class DnsFilter(private val ctx: Context, private val forward: (ByteArray) -> ByteArray?) {
    private class Cached(val ips: List<ByteArray>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    fun answer(query: ByteArray): ByteArray? {
        val name = DnsPacket.questionName(query) ?: return forward(query)
        if (BlockList.isBlocked(name)) {
            Stats.recordBlock(ctx)
            return DnsPacket.nxdomain(query)
        }
        val target = SafeSearch.target(name, Rules.youtubeStrict(ctx)) ?: return forward(query)
        // Only IPv4 is rewritten. An empty answer for IPv6 (and HTTPS records) makes apps use it.
        if (DnsPacket.questionType(query) != DnsPacket.TYPE_A) return DnsPacket.noData(query)
        val ips = resolve(target) ?: return null
        return DnsPacket.answerA(query, ips)
    }

    private fun resolve(host: String): List<ByteArray>? {
        val now = System.currentTimeMillis()
        cache[host]?.let { if (it.expiresAt > now) return it.ips }
        val response = forward(DnsPacket.query(host, DnsPacket.TYPE_A)) ?: return null
        val ips = DnsPacket.aRecords(response)
        if (ips.isEmpty()) return null
        cache[host] = Cached(ips, now + 10 * 60_000)
        return ips
    }
}
