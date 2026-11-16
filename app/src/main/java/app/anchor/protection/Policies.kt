package app.anchor.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.net.toUri
import app.anchor.BuildConfig
import app.anchor.data.Categories
import app.anchor.data.Rules
import app.anchor.data.Store
import app.anchor.filter.FilterVpnService
import kotlin.concurrent.thread

object Policies {
    const val CHROME = "com.android.chrome"
    private const val TAG = "Anchor"

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    fun dpm(ctx: Context): DevicePolicyManager = ctx.getSystemService(DevicePolicyManager::class.java)
    fun isDeviceOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)
    fun isEnforcing(ctx: Context) = isDeviceOwner(ctx) && Store.isActivated(ctx)

    private fun baseRestrictions(strict: Boolean): List<String> = buildList {
        add(UserManager.DISALLOW_CONFIG_DATE_TIME)    // can't fast-forward the unlock clock
        add(UserManager.DISALLOW_ADD_USER)            // no guest or second user
        add(UserManager.DISALLOW_USER_SWITCH)
        // Raw strings because the constants aren't public on every SDK level.
        add("no_add_clone_profile")                   // Android 14+: app cloning (Dual Messenger)
        add("no_add_private_profile")                 // Android 15+: private space
        add(UserManager.DISALLOW_FACTORY_RESET)       // Settings reset blocked; recovery mode still works
        if (strict) {
            add(UserManager.DISALLOW_SAFE_BOOT)                         // safe mode would skip Anchor
            add(UserManager.DISALLOW_DEBUGGING_FEATURES)                // no adb
            add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)  // no sideloaded browsers
        }
    }

    fun applyAll(ctx: Context) {
        if (!isEnforcing(ctx)) return
        val dpm = dpm(ctx)
        val a = admin(ctx)
        baseRestrictions(BuildConfig.STRICT).forEach { r -> safe("restriction $r") { dpm.addUserRestriction(a, r) } }
        setRestriction(dpm, a, UserManager.DISALLOW_INSTALL_APPS, Rules.freezeInstalls(ctx))
        safe("block own uninstall") { dpm.setUninstallBlocked(a, ctx.packageName, true) }
        safe("automatic time") { dpm.setAutoTimeEnabled(a, true) }
        safe("enable chrome") { dpm.enableSystemApp(a, CHROME) }
        safe("unhide chrome") { dpm.setApplicationHidden(a, CHROME, false) }
        safe("keep chrome") { dpm.setUninstallBlocked(a, CHROME, true) }
        ChromePolicy.apply(ctx)
        syncApps(ctx)
        applyDns(ctx, dpm, a)
    }

    private fun applyDns(ctx: Context, dpm: DevicePolicyManager, a: ComponentName) {
        val filter = Rules.dnsFilter(ctx)
        val host = Rules.privateDnsHost(ctx)
        setRestriction(dpm, a, UserManager.DISALLOW_CONFIG_VPN, filter)                       // can't switch VPN or turn off always-on
        setRestriction(dpm, a, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, filter || host != null) // can't point DNS around the filter
        if (filter) {
            // Strict Private DNS would send lookups past the VPN, so it's kept on automatic.
            safe("private dns") { dpm.setGlobalPrivateDnsModeOpportunistic(a) }
            // lockdown = true: if the filter isn't running, nothing gets online.
            safe("always-on vpn") { dpm.setAlwaysOnVpnPackage(a, ctx.packageName, true) }
        } else {
            // Give up the VPN slot so another filter (e.g. the AdGuard app) can use it.
            safe("always-on vpn off") {
                if (dpm.getAlwaysOnVpnPackage(a) == ctx.packageName) dpm.setAlwaysOnVpnPackage(a, null, false)
            }
            FilterVpnService.stop(ctx)
            // This call checks that the host answers, which can take seconds, so it runs off the main thread.
            if (host != null) thread(name = "anchor-private-dns") {
                safe("private dns host") { dpm.setGlobalPrivateDnsModeSpecifiedHost(a, host) }
            }
        }
    }

    private fun setRestriction(dpm: DevicePolicyManager, a: ComponentName, restriction: String, on: Boolean) {
        safe("restriction $restriction") {
            if (on) dpm.addUserRestriction(a, restriction) else dpm.clearUserRestriction(a, restriction)
        }
    }

    fun desiredHidden(ctx: Context): Set<String> =
        Store.stringSet(ctx, Store.AUTO_HIDDEN) +
            Categories.enabled(ctx).flatMap { it.apps } +
            Store.stringSet(ctx, Store.USER_HIDDEN_APPS)

    /** Why an app is hidden when it's not the user's own choice, or null. */
    fun managedBy(ctx: Context, pkg: String): String? {
        if (pkg in Store.stringSet(ctx, Store.AUTO_HIDDEN)) return "Other browsers aren't allowed"
        return Categories.enabled(ctx).firstOrNull { pkg in it.apps }?.let { "Blocked by ${it.name}" }
    }

    fun syncApps(ctx: Context) {
        if (!isEnforcing(ctx)) return
        val dpm = dpm(ctx)
        val a = admin(ctx)
        val keep = protectedPackages(ctx)
        // Browsers stay hidden once found, since a hidden app no longer shows up in the browser query.
        Store.addTo(ctx, Store.AUTO_HIDDEN, installedBrowsers(ctx) - keep)
        val wanted = desiredHidden(ctx) - keep
        for (pkg in wanted) {
            safe("hide $pkg") {
                // isApplicationHidden is also true for apps that aren't installed.
                if (!dpm.isApplicationHidden(a, pkg) && dpm.setApplicationHidden(a, pkg, true)) {
                    Store.addTo(ctx, Store.HIDDEN, pkg)
                }
            }
        }
        for (pkg in Store.stringSet(ctx, Store.HIDDEN) - wanted) {
            safe("unhide $pkg") {
                dpm.setApplicationHidden(a, pkg, false)
                Store.removeFrom(ctx, Store.HIDDEN, pkg)
            }
        }
    }

    /** Apps that must never be hidden, or the phone stops being usable. */
    fun protectedPackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val dialer = runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()
        return setOfNotNull(ctx.packageName, CHROME, "com.android.settings", home, dialer, sms)
    }

    private fun installedBrowsers(ctx: Context): Set<String> {
        // A made-up host only matches general-purpose browsers, not apps with deep links for their own domain.
        val probe = Intent(Intent.ACTION_VIEW, "https://anchor-probe.invalid/".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        @Suppress("DEPRECATION")
        return ctx.packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    fun releaseAll(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val a = admin(ctx)
        safe("always-on vpn off") { dpm.setAlwaysOnVpnPackage(a, null, false) }
        FilterVpnService.stop(ctx)
        val extra = listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        )
        (baseRestrictions(strict = true) + extra).forEach { r ->
            safe("clear $r") { dpm.clearUserRestriction(a, r) }
        }
        Store.stringSet(ctx, Store.HIDDEN).forEach { pkg -> safe("unhide $pkg") { dpm.setApplicationHidden(a, pkg, false) } }
        Store.edit(ctx) {
            remove(Store.HIDDEN)
            remove(Store.AUTO_HIDDEN)
        }
        safe("chrome policy") { dpm.setApplicationRestrictions(a, CHROME, Bundle()) }
        safe("release chrome") { dpm.setUninstallBlocked(a, CHROME, false) }
        safe("release self") { dpm.setUninstallBlocked(a, ctx.packageName, false) }
        Store.setActivated(ctx, false)
        @Suppress("DEPRECATION")
        safe("clear device owner") { dpm.clearDeviceOwnerApp(ctx.packageName) }
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Failed: $what", e)
        }
    }
}
