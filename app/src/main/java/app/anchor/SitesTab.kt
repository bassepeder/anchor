package app.anchor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SitesTab(version: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = remember { Categories.all(ctx) }
    val enabled = remember(version) { Categories.enabledIds(ctx) }
    val canLoosen = remember(version) { Control.canLoosen(ctx) }
    val userSites = remember(version) { Store.stringSet(ctx, Store.USER_SITES).sorted() }
    val allowed = remember(version) { Store.stringSet(ctx, Store.ALLOWED).sorted() }
    val listSize = remember(version) { ListUpdater.size(ctx) }
    val listUpdatedAt = remember(version) { ListUpdater.updatedAt(ctx) }
    val dnsFilter = remember(version) { Rules.dnsFilter(ctx) }
    var updating by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    TabColumn {
        item { SectionHeader("Categories", "Turning a category on is instant. Turning one off needs an unlock.") }
        items(categories, key = { "cat:${it.id}" }) { category ->
            val on = category.id in enabled
            SwitchCard(
                title = category.name,
                description = category.description,
                checked = on,
                enabled = !on || canLoosen,
                locked = category.alwaysOn,
            ) { turnOn ->
                val apply = {
                    Control.setCategory(ctx, category, turnOn)
                    refresh()
                }
                if (turnOn && !canLoosen) {
                    confirm = Confirm(
                        title = "Block ${category.name}?",
                        text = "Turning it off again will need an unlock.",
                        confirmLabel = "Block",
                        action = apply,
                    )
                } else {
                    apply()
                }
            }
        }
        item {
            AnchorCard("Adult-site list") {
                Text(
                    if (listSize > 0) "${formatCount(listSize.toLong())} known adult sites, updated ${formatDate(listUpdatedAt)}."
                    else "Not downloaded yet. It downloads once Anchor is on, then refreshes every week."
                )
                Muted(
                    if (dnsFilter) "The HaGeZi NSFW list, blocked for every app on top of the categories above."
                    else "Only used by Anchor's DNS filter, which is off (see Rules). Categories still apply in Chrome."
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = !updating,
                        onClick = {
                            updating = true
                            updateFailed = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { ListUpdater.update(ctx) }
                                updating = false
                                updateFailed = !ok
                                refresh()
                            }
                        },
                    ) { Text(if (updating) "Updating…" else "Update now") }
                    if (updateFailed) Muted("Update failed. Check your connection.")
                }
            }
        }
        item { SectionHeader("Your blocked sites", "Blocking is instant. Removing a site needs an unlock.") }
        item {
            EntryField("example.com or youtube.com/shorts", "Block") { entry ->
                Control.addSite(ctx, entry)
                refresh()
            }
        }
        items(userSites, key = { "site:$it" }) { site ->
            EntryRow(site, "Remove", enabled = canLoosen) {
                Control.removeSite(ctx, site)
                refresh()
            }
        }
        item {
            SectionHeader(
                "Always allowed",
                "For a site blocked by mistake. Allowing one needs an unlock; removing it is instant.",
            )
        }
        item {
            EntryField("example.com", "Allow", enabled = canLoosen) { entry ->
                Control.allowSite(ctx, entry)
                refresh()
            }
        }
        items(allowed, key = { "allow:$it" }) { site ->
            EntryRow(site, "Remove", enabled = true) {
                Control.unallowSite(ctx, site)
                refresh()
            }
        }
    }
    ConfirmDialog(confirm) { confirm = null }
}
