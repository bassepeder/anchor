package app.anchor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RulesTab(version: Int, refresh: () -> Unit, onRunSetup: () -> Unit) {
    val ctx = LocalContext.current
    val canLoosen = remember(version) { Control.canLoosen(ctx) }
    val enforcing = remember(version) { Policies.isEnforcing(ctx) }
    val restarts = remember(version) { Rules.restarts(ctx) }
    val gapHours = remember(version) { Rules.gapHours(ctx) }
    val windowMinutes = remember(version) { Rules.windowMinutes(ctx) }
    val youtubeStrict = remember(version) { Rules.youtubeStrict(ctx) }
    val freezeInstalls = remember(version) { Rules.freezeInstalls(ctx) }
    val dnsFilter = remember(version) { Rules.dnsFilter(ctx) }
    val privateDnsHost = remember(version) { Rules.privateDnsHost(ctx) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // A stricter change can't be undone without an unlock, so it's confirmed first.
    fun stricter(title: String, action: () -> Unit) {
        val apply = {
            action()
            refresh()
        }
        if (canLoosen) apply() else confirm = Confirm(title, "Undoing this will need an unlock.", "Apply", action = apply)
    }

    fun looser(action: () -> Unit) {
        action()
        refresh()
    }

    val gapUnit = Rules.gapUnit

    TabColumn {
        item { SectionHeader("Rules", "Making a rule stricter is instant. Making it looser needs an unlock.") }
        if (!enforcing) {
            item {
                AnchorCard("Start from a preset") {
                    Muted("Anchor isn't on yet, so you can run the setup again and pick a different preset.")
                    OutlinedButton(onClick = onRunSetup) { Text("Run setup again") }
                }
            }
        }
        item {
            ChoiceCard(
                title = "Restarts needed to unlock",
                options = Rules.RESTART_OPTIONS,
                selected = restarts,
                label = { "$it" },
                enabled = { it >= restarts || canLoosen },
            ) { n ->
                if (n > restarts) stricter("Require $n restarts?") { Control.setRestarts(ctx, n) }
                else looser { Control.setRestarts(ctx, n) }
            }
        }
        item {
            ChoiceCard(
                title = "Time between restarts",
                options = Rules.GAP_HOUR_OPTIONS,
                selected = gapHours,
                label = { "$it$gapUnit" },
                enabled = { it >= gapHours || canLoosen },
            ) { h ->
                if (h > gapHours) stricter("Require $h$gapUnit between restarts?") { Control.setGapHours(ctx, h) }
                else looser { Control.setGapHours(ctx, h) }
            }
        }
        item {
            ChoiceCard(
                title = "Unlock stays open for",
                options = Rules.WINDOW_MINUTE_OPTIONS,
                selected = windowMinutes,
                label = { "$it min" },
                enabled = { it <= windowMinutes || canLoosen },
            ) { m ->
                if (m < windowMinutes) stricter("Keep unlocks open for only $m minutes?") { Control.setWindowMinutes(ctx, m) }
                else looser { Control.setWindowMinutes(ctx, m) }
            }
        }
        item {
            SwitchCard(
                title = "Strict YouTube",
                description = "Restricted mode set to strict instead of moderate, in Chrome and every app.",
                checked = youtubeStrict,
                enabled = !youtubeStrict || canLoosen,
            ) { on ->
                if (on) stricter("Turn on strict YouTube?") { Control.setYoutubeStrict(ctx, true) }
                else looser { Control.setYoutubeStrict(ctx, false) }
            }
        }
        item {
            SwitchCard(
                title = "Freeze apps",
                description = "Nothing new can be installed, not even from the Play Store. App updates may stop too.",
                checked = freezeInstalls,
                enabled = !freezeInstalls || canLoosen,
            ) { on ->
                if (on) stricter("Freeze apps?") { Control.setFreezeInstalls(ctx, true) }
                else looser { Control.setFreezeInstalls(ctx, false) }
            }
        }
        item {
            SectionHeader(
                "DNS filtering",
                "Anchor's filter blocks sites for every app, uses the adult-site list and forces SafeSearch. " +
                    "Turn it off to use your own filter, like AdGuard. Chrome stays locked down either way.",
            )
        }
        item {
            SwitchCard(
                title = "Anchor DNS filter",
                description = if (dnsFilter) "On: runs as an always-on VPN." else "Off: other apps only get your own DNS filter's protection.",
                checked = dnsFilter,
                enabled = !dnsFilter || canLoosen,
            ) { on ->
                if (on) {
                    stricter("Turn on the DNS filter?") {
                        Control.setDnsFilter(ctx, true)
                        (ctx as? MainActivity)?.ensureFilterRunning()
                    }
                } else {
                    looser { Control.setDnsFilter(ctx, false) }
                }
            }
        }
        if (!dnsFilter) {
            item {
                AnchorCard("Lock Private DNS") {
                    Muted(
                        "Pin the phone to your own DNS filter so it can't be switched off in Settings. " +
                            "For AdGuard DNS, use the hostname from your AdGuard dashboard, like xxxxxxxx.d.adguard-dns.com."
                    )
                    if (privateDnsHost == null) {
                        HostField { host -> stricter("Lock Private DNS to $host?") { Control.setPrivateDnsHost(ctx, host) } }
                    } else {
                        EntryRow(privateDnsHost, "Remove", enabled = canLoosen) {
                            looser { Control.setPrivateDnsHost(ctx, null) }
                        }
                    }
                }
            }
        }
        if (!BuildConfig.STRICT) {
            item { Muted("Debug build: the time between restarts counts in minutes, not hours.") }
        }
    }
    ConfirmDialog(confirm) { confirm = null }
}

@Composable
private fun HostField(onLock: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = false
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("dns.example.com") },
            isError = error,
            supportingText = if (error) {
                { Text("That doesn't look like a hostname.") }
            } else {
                null
            },
        )
        Button(
            onClick = {
                val host = Rules.normalizeHost(input)
                if (host == null) error = true else onLock(host)
            },
            enabled = input.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Lock") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceCard(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    AnchorCard(title) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { if (option != selected) onSelect(option) },
                    label = { Text(label(option)) },
                    enabled = option == selected || enabled(option),
                )
            }
        }
    }
}
