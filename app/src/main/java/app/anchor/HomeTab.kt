package app.anchor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private class HomeData(
    val isOwner: Boolean,
    val enforcing: Boolean,
    val days: Long,
    val blockedToday: Int,
    val blockedTotal: Long,
    val hiddenApps: Int,
    val vpnOn: Boolean,
    val listSize: Int,
    val categoriesOn: Int,
    val dnsFilter: Boolean,
    val privateDnsHost: String?,
)

private fun loadHome(ctx: Context): HomeData {
    val enforcing = Policies.isEnforcing(ctx)
    val since = Store.activatedAt(ctx)
    return HomeData(
        isOwner = Policies.isDeviceOwner(ctx),
        enforcing = enforcing,
        days = if (since > 0) (System.currentTimeMillis() - since) / 86_400_000 else 0,
        blockedToday = Stats.blockedToday(ctx),
        blockedTotal = Stats.blockedTotal(ctx),
        hiddenApps = Store.stringSet(ctx, Store.HIDDEN).size,
        vpnOn = enforcing && runCatching {
            Policies.dpm(ctx).getAlwaysOnVpnPackage(Policies.admin(ctx)) == ctx.packageName
        }.getOrDefault(false),
        listSize = ListUpdater.size(ctx),
        categoriesOn = Categories.enabledIds(ctx).size,
        dnsFilter = Rules.dnsFilter(ctx),
        privateDnsHost = Rules.privateDnsHost(ctx),
    )
}

@Composable
fun HomeTab(version: Int, refresh: () -> Unit, onActivate: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val data = remember(version, now / 15_000) { loadHome(ctx) }
    val unlock = remember(version, now) { Unlock.state(ctx) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    TabColumn {
        item { Hero(data.enforcing, data.days) }
        when {
            !data.isOwner -> item { SetupCard() }
            !data.enforcing -> item {
                ActivateCard(onClick = {
                    confirm = Confirm(
                        title = "Turn on Anchor?",
                        text = "From now on, loosening anything takes an unlock: ${Rules.restarts(ctx)} restarts, " +
                            "each at least ${formatDuration(Rules.gapMs(ctx))} apart. Adding blocks stays instant.",
                        confirmLabel = "Turn on",
                    ) {
                        onActivate()
                        refresh()
                    }
                })
            }
            else -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(formatCount(data.blockedToday.toLong()), "blocked today", Modifier.weight(1f))
                        StatTile(formatCount(data.blockedTotal), "blocked in total", Modifier.weight(1f))
                        StatTile(data.hiddenApps.toString(), "apps hidden", Modifier.weight(1f))
                    }
                }
                item { ProtectionCard(data) }
                item {
                    UnlockCard(unlock, now, refresh, onTurnOff = {
                        confirm = Confirm(
                            title = "Turn Anchor off completely?",
                            text = "Every block is lifted and Anchor stops being device owner. You can then uninstall it.",
                            confirmLabel = "Turn off",
                            destructive = true,
                        ) {
                            Control.turnOff(ctx)
                            refresh()
                        }
                    })
                }
            }
        }
    }
    ConfirmDialog(confirm) { confirm = null }
}

@Composable
private fun Hero(enforcing: Boolean, days: Long) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_anchor),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                if (enforcing) "Day ${days + 1}" else "Anchor",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                if (enforcing) "Your phone is a tool again." else "Turn your smartphone back into a phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Card(modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Muted(label)
        }
    }
}

@Composable
private fun ProtectionCard(data: HomeData) {
    AnchorCard("Protection") {
        StatusLine(true, "Locked in: can't be uninstalled or switched off")
        if (data.dnsFilter) {
            StatusLine(data.vpnOn, if (data.vpnOn) "Filter runs for every app, always" else "Filter isn't the always-on VPN yet")
            StatusLine(
                data.listSize > 0,
                if (data.listSize > 0) "${formatCount(data.listSize.toLong())} known adult sites blocked"
                else "Adult-site list not downloaded yet",
            )
            StatusLine(true, "SafeSearch forced on Google, Bing, DuckDuckGo and YouTube")
        } else {
            StatusLine(
                data.privateDnsHost != null,
                data.privateDnsHost?.let { "Anchor's DNS filter off. Private DNS locked to $it" }
                    ?: "Anchor's DNS filter off. Only Chrome is filtered by Anchor",
            )
        }
        StatusLine(true, "${data.categoriesOn} categories blocked in Chrome, other browsers hidden")
        if (!BuildConfig.STRICT) {
            Muted("Debug build: adb, safe mode and sideloading still work, and hours between restarts count as minutes.")
        }
    }
}

@Composable
private fun UnlockCard(state: UnlockState, now: Long, refresh: () -> Unit, onTurnOff: () -> Unit) {
    val ctx = LocalContext.current
    AnchorCard("Unlock") {
        when (state) {
            UnlockState.LOCKED -> {
                Text(
                    "To remove a block, show an app or loosen a rule, request an unlock. It opens after " +
                        "${Rules.restarts(ctx)} restarts, each at least ${formatDuration(Rules.gapMs(ctx))} after the " +
                        "previous one. The first counts from the request."
                )
                Muted("Adding blocks never needs an unlock.")
                OutlinedButton(onClick = {
                    Unlock.request(ctx)
                    refresh()
                }) { Text("Request unlock") }
            }
            UnlockState.PENDING -> {
                val counted = Unlock.counted(ctx)
                val required = Rules.restarts(ctx)
                val next = Unlock.nextCountableAt(ctx)
                LinearProgressIndicator(
                    progress = { counted / required.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Restarts counted: $counted of $required")
                Text(if (now >= next) "Your next restart will count." else "A restart counts if it happens after ${formatTime(next)}.")
                Muted("Urges pass. If this one has, cancel the request.")
                OutlinedButton(onClick = {
                    Unlock.lock(ctx)
                    refresh()
                }) { Text("Cancel request") }
            }
            UnlockState.OPEN -> {
                Text(
                    "Unlocked for ${formatCountdown(Unlock.openUntil(ctx) - now)}. " +
                        "You can remove blocks, show apps and loosen rules."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        Unlock.lock(ctx)
                        refresh()
                    }) { Text("Lock now") }
                    Button(
                        onClick = onTurnOff,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("Turn Anchor off") }
                }
            }
        }
    }
}

@Composable
private fun SetupCard() {
    AnchorCard("Setup") {
        Text("Anchor has to be the phone's device owner. That's what stops it from being uninstalled or switched off. Do this once, from a computer:")
        Text("1. Settings › Accounts and backup › Manage accounts: remove every account. Remove Secure Folder if you have one.")
        Text("2. With USB debugging on, run:")
        SelectionContainer {
            Text(DEVICE_OWNER_COMMAND, fontFamily = FontFamily.Monospace)
        }
        Text("3. Add your accounts back and reopen Anchor.")
        Muted("You can already pick categories, apps and rules in the other tabs. If you just turned Anchor off, you can uninstall it now.")
    }
}

@Composable
private fun ActivateCard(onClick: () -> Unit) {
    AnchorCard("Ready to turn on") {
        Text(
            "• Blocks adult content and your chosen categories, in Chrome and every app\n" +
                "• Forces SafeSearch and turns off incognito\n" +
                "• Hides every other browser and the apps you picked\n" +
                "• Locks VPN, private DNS, date and time, extra users and factory reset" +
                if (BuildConfig.STRICT) "\n• Turns off safe mode, USB debugging and sideloading" else ""
        )
        Muted("Set up Sites, Apps and Rules first: before Anchor is on, everything can still be changed freely.")
        Button(onClick = onClick) { Text("Turn on protection") }
    }
}
