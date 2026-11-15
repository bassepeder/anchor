package app.anchor

import android.content.Context

class Category(
    val id: String,
    val name: String,
    val description: String,
    val alwaysOn: Boolean,
    val defaultOn: Boolean,
    val domains: List<String>,
    val chromeOnly: List<String>,
    val apps: List<String>,
)

object Categories {
    @Volatile private var cached: List<Category>? = null

    fun all(ctx: Context): List<Category> =
        cached ?: parse(ctx.assets.open("categories.txt").bufferedReader().readLines()).also { cached = it }

    fun enabledIds(ctx: Context): Set<String> {
        val all = all(ctx)
        val stored = Store.stringSetOrNull(ctx, Store.CATEGORIES)
            ?: all.filter { it.defaultOn }.map { it.id }.toSet()
        return stored + all.filter { it.alwaysOn }.map { it.id }
    }

    fun enabled(ctx: Context): List<Category> {
        val ids = enabledIds(ctx)
        return all(ctx).filter { it.id in ids }
    }

    fun setEnabled(ctx: Context, id: String, on: Boolean) {
        val ids = enabledIds(ctx)
        Store.edit(ctx) { putStringSet(Store.CATEGORIES, if (on) ids + id else ids - id) }
    }

    private fun parse(lines: List<String>): List<Category> {
        val result = mutableListOf<Category>()
        var id: String? = null
        val meta = mutableMapOf<String, String>()
        val domains = mutableListOf<String>()
        val chromeOnly = mutableListOf<String>()
        val apps = mutableListOf<String>()

        fun flush() {
            val current = id ?: return
            result += Category(
                id = current,
                name = meta["name"] ?: current,
                description = meta["description"] ?: "",
                alwaysOn = meta["always"] == "true",
                defaultOn = meta["default"] == "true",
                domains = domains.toList(),
                chromeOnly = chromeOnly.toList(),
                apps = apps.toList(),
            )
            meta.clear()
            domains.clear()
            chromeOnly.clear()
            apps.clear()
        }

        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") -> {
                    flush()
                    id = line.substring(1, line.length - 1)
                }
                " = " in line -> meta[line.substringBefore(" = ").trim()] = line.substringAfter(" = ").trim()
                line.startsWith("app:") -> apps += line.removePrefix("app:")
                line.startsWith("chrome:") -> chromeOnly += line.removePrefix("chrome:").lowercase()
                else -> domains += line.lowercase()
            }
        }
        flush()
        return result
    }
}
