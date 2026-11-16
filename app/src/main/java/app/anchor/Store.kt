package app.anchor

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

object Store {
    const val USER_SITES = "domains"
    const val ALLOWED = "allowed"
    const val CATEGORIES = "categories"
    const val USER_HIDDEN_APPS = "user_hidden_apps"
    const val AUTO_HIDDEN = "auto_hidden"
    const val HIDDEN = "hidden"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("anchor", Context.MODE_PRIVATE)

    // commit() rather than apply(): a restart right after a change must not lose it.
    @SuppressLint("ApplySharedPref")
    fun edit(ctx: Context, block: SharedPreferences.Editor.() -> Unit) {
        prefs(ctx).edit().apply(block).commit()
    }

    fun string(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)
    fun int(ctx: Context, key: String, default: Int = 0): Int = prefs(ctx).getInt(key, default)
    fun long(ctx: Context, key: String, default: Long = 0L): Long = prefs(ctx).getLong(key, default)
    fun bool(ctx: Context, key: String, default: Boolean = false): Boolean = prefs(ctx).getBoolean(key, default)

    fun stringSetOrNull(ctx: Context, key: String): Set<String>? = prefs(ctx).getStringSet(key, null)?.toSet()
    fun stringSet(ctx: Context, key: String): Set<String> = stringSetOrNull(ctx, key) ?: emptySet()

    @Synchronized
    fun addTo(ctx: Context, key: String, values: Collection<String>) {
        if (values.isEmpty()) return
        edit(ctx) { putStringSet(key, stringSet(ctx, key) + values) }
    }

    fun addTo(ctx: Context, key: String, value: String) = addTo(ctx, key, listOf(value))

    @Synchronized
    fun removeFrom(ctx: Context, key: String, value: String) {
        edit(ctx) { putStringSet(key, stringSet(ctx, key) - value) }
    }

    fun isOnboarded(ctx: Context) = bool(ctx, "onboarded")
    fun setOnboarded(ctx: Context, value: Boolean) = edit(ctx) { putBoolean("onboarded", value) }

    fun isActivated(ctx: Context) = bool(ctx, "activated")
    fun activatedAt(ctx: Context) = long(ctx, "activated_at")

    fun setActivated(ctx: Context, value: Boolean) = edit(ctx) {
        putBoolean("activated", value)
        if (!value) remove("activated_at")
        else if (activatedAt(ctx) == 0L) putLong("activated_at", System.currentTimeMillis())
    }
}
