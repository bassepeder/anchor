package app.anchor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class UnlockState { LOCKED, PENDING, OPEN }

/**
 * Restarts are read from the system boot counter and the clock can't be changed (automatic time is
 * enforced), so neither the restart count nor the gap between restarts can be faked.
 */
object Unlock {
    private const val K_STATE = "unlock_state"
    private const val K_COUNTED = "unlock_counted"
    private const val K_LAST_COUNTED_AT = "unlock_last_counted_at"
    private const val K_LAST_BOOT = "unlock_last_boot"
    private const val K_OPEN_UNTIL = "unlock_open_until"

    fun state(ctx: Context): UnlockState {
        val s = Store.string(ctx, K_STATE)?.let { runCatching { UnlockState.valueOf(it) }.getOrNull() }
            ?: UnlockState.LOCKED
        if (s == UnlockState.OPEN && now() > openUntil(ctx)) {
            lock(ctx)
            return UnlockState.LOCKED
        }
        return s
    }

    fun counted(ctx: Context) = Store.int(ctx, K_COUNTED)
    fun nextCountableAt(ctx: Context) = Store.long(ctx, K_LAST_COUNTED_AT) + Rules.gapMs(ctx)
    fun openUntil(ctx: Context) = Store.long(ctx, K_OPEN_UNTIL)

    fun request(ctx: Context) {
        if (state(ctx) != UnlockState.LOCKED) return
        Store.edit(ctx) {
            putString(K_STATE, UnlockState.PENDING.name)
            putInt(K_COUNTED, 0)
            putLong(K_LAST_COUNTED_AT, now())
            putInt(K_LAST_BOOT, bootCount(ctx))
        }
    }

    fun lock(ctx: Context) = Store.edit(ctx) {
        putString(K_STATE, UnlockState.LOCKED.name)
        putInt(K_COUNTED, 0)
        putLong(K_OPEN_UNTIL, 0L)
    }

    fun onBoot(ctx: Context) {
        if (state(ctx) != UnlockState.PENDING) return
        val boot = bootCount(ctx)
        if (boot <= Store.int(ctx, K_LAST_BOOT)) return
        Store.edit(ctx) { putInt(K_LAST_BOOT, boot) }
        if (now() < nextCountableAt(ctx)) return

        val counted = counted(ctx) + 1
        Store.edit(ctx) {
            putInt(K_COUNTED, counted)
            putLong(K_LAST_COUNTED_AT, now())
            if (counted >= Rules.restarts(ctx)) {
                putString(K_STATE, UnlockState.OPEN.name)
                putLong(K_OPEN_UNTIL, now() + Rules.windowMs(ctx))
            }
        }
    }

    private fun bootCount(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun now() = System.currentTimeMillis()
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Store.isActivated(context)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Unlock.onBoot(context)
        Policies.applyAll(context)
        FilterVpnService.start(context)
    }
}
