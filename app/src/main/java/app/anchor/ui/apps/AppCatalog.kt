package app.anchor.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.anchor.data.Store
import app.anchor.protection.Policies

class AppEntry(val pkg: String, val label: String, val hidden: Boolean, val managedBy: String?)

object AppCatalog {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val keep = Policies.protectedPackages(ctx)
        @Suppress("DEPRECATION")
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
        val desired = Policies.desiredHidden(ctx)
        // Hidden apps no longer show up as launchable, so they're added back from what we hid.
        val packages = (launchable + Store.stringSet(ctx, Store.HIDDEN) + Store.stringSet(ctx, Store.USER_HIDDEN_APPS)).toSet() - keep
        return packages.mapNotNull { pkg ->
            val info = try {
                pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val hidden = pkg in desired
            AppEntry(pkg, pm.getApplicationLabel(info).toString(), hidden, if (hidden) Policies.managedBy(ctx, pkg) else null)
        }.sortedWith(compareBy({ !it.hidden }, { it.label.lowercase() }))
    }
}
