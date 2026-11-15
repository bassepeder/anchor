package app.anchor

import android.content.Context

/**
 * Every user-facing change goes through here. Stricter changes apply instantly; looser ones only
 * while an unlock is open (or before Anchor is turned on). The UI disables controls too, but this
 * re-checks at the moment of the change, in case the unlock expired while the screen was open.
 */
object Control {
    fun canLoosen(ctx: Context) = !Policies.isEnforcing(ctx) || Unlock.state(ctx) == UnlockState.OPEN

    fun addSite(ctx: Context, entry: String) {
        Store.addTo(ctx, Store.USER_SITES, entry)
        refreshBlocking(ctx)
    }

    fun removeSite(ctx: Context, entry: String) = loosen(ctx) {
        Store.removeFrom(ctx, Store.USER_SITES, entry)
        refreshBlocking(ctx)
    }

    fun allowSite(ctx: Context, entry: String) = loosen(ctx) {
        Store.addTo(ctx, Store.ALLOWED, entry)
        refreshBlocking(ctx)
    }

    fun unallowSite(ctx: Context, entry: String) {
        Store.removeFrom(ctx, Store.ALLOWED, entry)
        refreshBlocking(ctx)
    }

    fun setCategory(ctx: Context, category: Category, on: Boolean): Boolean {
        if (category.alwaysOn) return false
        return change(ctx, stricter = on) {
            Categories.setEnabled(ctx, category.id, on)
            refreshBlocking(ctx)
            Policies.syncApps(ctx)
        }
    }

    fun hideApp(ctx: Context, pkg: String) {
        Store.addTo(ctx, Store.USER_HIDDEN_APPS, pkg)
        Policies.syncApps(ctx)
    }

    fun showApp(ctx: Context, pkg: String) = loosen(ctx) {
        Store.removeFrom(ctx, Store.USER_HIDDEN_APPS, pkg)
        Policies.syncApps(ctx)
    }

    fun setRestarts(ctx: Context, value: Int) =
        change(ctx, stricter = value >= Rules.restarts(ctx)) { Rules.setRestarts(ctx, value) }

    fun setGapHours(ctx: Context, value: Int) =
        change(ctx, stricter = value >= Rules.gapHours(ctx)) { Rules.setGapHours(ctx, value) }

    fun setWindowMinutes(ctx: Context, value: Int) =
        change(ctx, stricter = value <= Rules.windowMinutes(ctx)) { Rules.setWindowMinutes(ctx, value) }

    fun setYoutubeStrict(ctx: Context, on: Boolean) = change(ctx, stricter = on) {
        Rules.setYoutubeStrict(ctx, on)
        ChromePolicy.apply(ctx)
    }

    fun setFreezeInstalls(ctx: Context, on: Boolean) = change(ctx, stricter = on) {
        Rules.setFreezeInstalls(ctx, on)
        Policies.applyAll(ctx)
    }

    fun setDnsFilter(ctx: Context, on: Boolean) = change(ctx, stricter = on) {
        Rules.setDnsFilter(ctx, on)
        Policies.applyAll(ctx)
        FilterVpnService.start(ctx)
    }

    /** Locking a host when none is locked is stricter; changing or clearing a locked one is looser. */
    fun setPrivateDnsHost(ctx: Context, host: String?) =
        change(ctx, stricter = Rules.privateDnsHost(ctx) == null && host != null) {
            Rules.setPrivateDnsHost(ctx, host)
            Policies.applyAll(ctx)
        }

    fun turnOff(ctx: Context) = loosen(ctx) {
        Policies.releaseAll(ctx)
        Unlock.lock(ctx)
    }

    private inline fun change(ctx: Context, stricter: Boolean, block: () -> Unit): Boolean =
        if (stricter) {
            block()
            true
        } else {
            loosen(ctx, block)
        }

    private inline fun loosen(ctx: Context, block: () -> Unit): Boolean {
        if (!canLoosen(ctx)) return false
        block()
        return true
    }

    private fun refreshBlocking(ctx: Context) {
        BlockList.reload(ctx)
        ChromePolicy.apply(ctx)
    }
}
