package app.anchor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@Composable
fun AppsTab(version: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val canLoosen = remember(version) { Control.canLoosen(ctx) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(version) {
        apps = withContext(Dispatchers.IO) { AppCatalog.load(ctx) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    TabColumn {
        item {
            SectionHeader(
                "Apps",
                "Hide anything that pulls you in. Hiding is instant; bringing an app back needs an unlock. " +
                    "Hidden apps stay installed and keep their data.",
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )
        }
        val list = apps
        if (list == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            items(shown, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    canLoosen = canLoosen,
                    onHide = {
                        val hide = {
                            Control.hideApp(ctx, app.pkg)
                            refresh()
                        }
                        if (canLoosen) {
                            hide()
                        } else {
                            confirm = Confirm(
                                title = "Hide ${app.label}?",
                                text = "Bringing it back will need an unlock.",
                                confirmLabel = "Hide",
                                action = hide,
                            )
                        }
                    },
                    onShow = {
                        Control.showApp(ctx, app.pkg)
                        refresh()
                    },
                )
            }
        }
    }
    ConfirmDialog(confirm) { confirm = null }
}

@Composable
private fun AppRow(app: AppEntry, canLoosen: Boolean, onHide: () -> Unit, onShow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.pkg)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted(app.managedBy ?: if (app.hidden) "Hidden" else "Visible")
        }
        when {
            app.managedBy != null -> Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            app.hidden -> TextButton(onClick = onShow, enabled = canLoosen) { Text("Show") }
            else -> FilledTonalButton(onClick = onHide) { Text("Hide") }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching {
            val pm = ctx.packageManager
            pm.getApplicationIcon(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES))
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(Modifier.size(40.dp))
    }
}
