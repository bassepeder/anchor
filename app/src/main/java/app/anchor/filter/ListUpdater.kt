package app.anchor.filter

import android.content.Context
import app.anchor.data.Store
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Keeps a large, maintained list of adult domains on the phone, refreshed weekly. */
object ListUpdater {
    const val SOURCE_URL = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/nsfw.txt"
    private const val FILE = "adult-list.txt"
    private const val INTERVAL_MS = 7L * 24 * 60 * 60_000
    private const val K_UPDATED_AT = "list_updated_at"
    private const val K_SIZE = "list_size"
    private val DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    fun updatedAt(ctx: Context) = Store.long(ctx, K_UPDATED_AT)
    fun size(ctx: Context) = Store.int(ctx, K_SIZE)

    fun load(ctx: Context): Set<String> {
        val file = File(ctx.filesDir, FILE)
        if (!file.exists()) return emptySet()
        return file.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toHashSet() }
    }

    fun updateIfDue(ctx: Context) {
        if (System.currentTimeMillis() - updatedAt(ctx) >= INTERVAL_MS) update(ctx)
    }

    @Synchronized
    fun update(ctx: Context): Boolean {
        val domains = try {
            download()
        } catch (e: IOException) {
            return false
        }
        // A tiny result means a broken download, not a shorter list. Keep the old one.
        if (domains.size < 1000) return false
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.bufferedWriter().use { writer ->
            domains.forEach {
                writer.write(it)
                writer.newLine()
            }
        }
        if (!tmp.renameTo(File(ctx.filesDir, FILE))) return false
        Store.edit(ctx) {
            putLong(K_UPDATED_AT, System.currentTimeMillis())
            putInt(K_SIZE, domains.size)
        }
        BlockList.reloadDownloaded(ctx)
        return true
    }

    private fun download(): Set<String> {
        val conn = URL(SOURCE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().useLines { lines -> lines.mapNotNull(::parseLine).toHashSet() }
        } finally {
            conn.disconnect()
        }
    }

    /** Accepts plain domains, hosts-file lines ("0.0.0.0 example.com") and adblock rules ("||example.com^"). */
    fun parseLine(line: String): String? {
        var s = line.trim()
        if (s.isEmpty() || s[0] == '#' || s[0] == '!') return null
        s = s.removePrefix("||").substringBefore('^').substringBefore('$')
        val parts = s.split(Regex("\\s+"))
        s = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0]
        s = s.removePrefix("*.").lowercase().trimEnd('.')
        return if (s != "localhost" && DOMAIN.matches(s)) s else null
    }
}
