package app.anchor.filter

import android.content.Context
import app.anchor.data.Categories
import app.anchor.data.Store

object BlockList {
    @Volatile private var dnsDomains: Set<String> = emptySet()
    @Volatile private var allowed: Set<String> = emptySet()
    @Volatile private var chromeBlock: List<String> = emptyList()
    @Volatile private var downloaded: Set<String>? = null

    fun reload(ctx: Context) {
        val categories = Categories.enabled(ctx)
        val user = Store.stringSet(ctx, Store.USER_SITES)
        // Paths are encrypted on the wire, so entries like youtube.com/shorts only work in Chrome.
        dnsDomains = (categories.flatMap { it.domains } + user).filter { '/' !in it }.toHashSet()
        chromeBlock = (categories.flatMap { it.domains + it.chromeOnly } + user).distinct()
        allowed = Store.stringSet(ctx, Store.ALLOWED)
        if (downloaded == null) downloaded = ListUpdater.load(ctx)
    }

    fun reloadDownloaded(ctx: Context) {
        downloaded = ListUpdater.load(ctx)
    }

    fun chromeBlocklist(): List<String> = chromeBlock
    fun chromeAllowlist(): List<String> = allowed.toList()

    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (matches(h, allowed)) return false
        return matches(h, dnsDomains) || matches(h, downloaded ?: emptySet())
    }

    private fun matches(host: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var h = host
        while (true) {
            if (h in domains) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    fun normalize(input: String): String? {
        val s = input.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .trimEnd('/')
        val host = s.substringBefore('/')
        if (host.isEmpty() || '.' !in host || !host.matches(Regex("[a-z0-9.-]+"))) return null
        if (s.any { it.isWhitespace() }) return null
        return s
    }
}
