package app.anchor

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.provider.Settings
import app.anchor.data.Store
import app.anchor.protection.Policies
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val appContext: Context get() = RuntimeEnvironment.getApplication()

fun makeDeviceOwner(ctx: Context = appContext) {
    shadowOf(ctx.getSystemService(DevicePolicyManager::class.java)).setDeviceOwner(Policies.admin(ctx))
}

/** Device owner with protection on, so looser changes need an unlock. */
fun turnOnProtection(ctx: Context = appContext) {
    makeDeviceOwner(ctx)
    Store.setOnboarded(ctx, true)
    Store.setActivated(ctx, true)
}

fun setBootCount(count: Int, ctx: Context = appContext) {
    Settings.Global.putInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, count)
}

class FakeClock(var now: Long = 1_700_000_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }
}
