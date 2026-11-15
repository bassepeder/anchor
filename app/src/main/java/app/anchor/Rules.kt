package app.anchor

import android.content.Context

object Rules {
    val RESTART_OPTIONS = listOf(1, 2, 3, 4, 5)
    val GAP_HOUR_OPTIONS = listOf(1, 6, 12, 24, 48)
    val WINDOW_MINUTE_OPTIONS = listOf(10, 30, 60)

    private const val K_RESTARTS = "rule_restarts"
    private const val K_GAP_HOURS = "rule_gap_hours"
    private const val K_WINDOW_MINUTES = "rule_window_minutes"
    private const val K_YOUTUBE_STRICT = "rule_youtube_strict"
    private const val K_FREEZE_INSTALLS = "rule_freeze_installs"
    private const val K_DNS_FILTER = "rule_dns_filter"
    private const val K_PRIVATE_DNS_HOST = "rule_private_dns_host"
    private val HOSTNAME = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    /** Anchor's own filter (always-on VPN). Off = bring your own DNS filter, e.g. AdGuard. */
    fun dnsFilter(ctx: Context) = Store.bool(ctx, K_DNS_FILTER, default = true)

    /** With the filter off: a Private DNS host to lock the phone to, or null to leave Private DNS alone. */
    fun privateDnsHost(ctx: Context): String? = Store.string(ctx, K_PRIVATE_DNS_HOST)

    fun normalizeHost(input: String): String? = input.trim().lowercase().takeIf { HOSTNAME.matches(it) }

    fun setDnsFilter(ctx: Context, value: Boolean) = Store.edit(ctx) { putBoolean(K_DNS_FILTER, value) }
    fun setPrivateDnsHost(ctx: Context, value: String?) = Store.edit(ctx) {
        if (value == null) remove(K_PRIVATE_DNS_HOST) else putString(K_PRIVATE_DNS_HOST, value)
    }

    fun restarts(ctx: Context) = Store.int(ctx, K_RESTARTS, 3)
    fun gapHours(ctx: Context) = Store.int(ctx, K_GAP_HOURS, 12)
    fun windowMinutes(ctx: Context) = Store.int(ctx, K_WINDOW_MINUTES, 30)
    fun youtubeStrict(ctx: Context) = Store.bool(ctx, K_YOUTUBE_STRICT)
    fun freezeInstalls(ctx: Context) = Store.bool(ctx, K_FREEZE_INSTALLS)

    // Debug builds count the gap in minutes instead of hours so the unlock can be tested quickly.
    fun gapMs(ctx: Context) = gapMs(gapHours(ctx))
    fun gapMs(hours: Int) = hours * if (BuildConfig.STRICT) 3_600_000L else 60_000L
    val gapUnit get() = if (BuildConfig.STRICT) "h" else "m"
    fun windowMs(ctx: Context) = windowMinutes(ctx) * 60_000L

    fun setRestarts(ctx: Context, value: Int) = Store.edit(ctx) { putInt(K_RESTARTS, value) }
    fun setGapHours(ctx: Context, value: Int) = Store.edit(ctx) { putInt(K_GAP_HOURS, value) }
    fun setWindowMinutes(ctx: Context, value: Int) = Store.edit(ctx) { putInt(K_WINDOW_MINUTES, value) }
    fun setYoutubeStrict(ctx: Context, value: Boolean) = Store.edit(ctx) { putBoolean(K_YOUTUBE_STRICT, value) }
    fun setFreezeInstalls(ctx: Context, value: Boolean) = Store.edit(ctx) { putBoolean(K_FREEZE_INSTALLS, value) }
}
