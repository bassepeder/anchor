package app.anchor.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.anchor.data.Store
import app.anchor.filter.FilterVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Store.isActivated(context)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Unlock.onBoot(context)
        Policies.applyAll(context)
        FilterVpnService.start(context)
    }
}
