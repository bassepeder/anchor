@file:OptIn(ExperimentalMaterial3Api::class)

package app.anchor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val DEVICE_OWNER_COMMAND = "adb shell dpm set-device-owner app.anchor/.AdminReceiver"

private enum class Step { WELCOME, PRESET, CATEGORIES, FRICTION, DNS, FINISH }

@Composable
fun OnboardingWizard(onActivate: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val draft = remember { SetupDraft(ctx) }
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var isOwner by remember { mutableStateOf(Policies.isDeviceOwner(ctx)) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val progress by animateFloatAsState(
        targetValue = step.ordinal / (Step.entries.size - 1f),
        animationSpec = tween(400),
        label = "progress",
    )

    // Notices the moment `adb shell dpm set-device-owner` has run, so the last step updates by itself.
    LaunchedEffect(step) {
        while (step == Step.FINISH && !isOwner) {
            delay(2000)
            isOwner = Policies.isDeviceOwner(ctx)
        }
    }

    fun next() {
        step = Step.entries[step.ordinal + 1]
    }

    fun back() {
        step = Step.entries[step.ordinal - 1]
    }

    fun finish(activate: Boolean) {
        draft.commit(ctx)
        if (!activate) {
            onDone()
            return
        }
        confirm = Confirm(
            title = "Turn on Anchor?",
            text = "From now on, loosening anything takes ${draft.restarts} restarts, each at least " +
                "${formatDuration(Rules.gapMs(draft.gapHours))} apart. Adding blocks stays instant.",
            confirmLabel = "Turn on",
        ) {
            onActivate()
            onDone()
        }
    }

    BackHandler(enabled = step != Step.WELCOME) { back() }

    Scaffold(
        topBar = {
            if (step != Step.WELCOME) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        },
        bottomBar = {
            WizardBar(
                showBack = step != Step.WELCOME,
                nextLabel = when {
                    step == Step.WELCOME -> "Get started"
                    step == Step.FINISH && isOwner -> "Turn on protection"
                    step == Step.FINISH -> "Finish"
                    else -> "Next"
                },
                nextEnabled = step != Step.DNS || draft.dnsFilter || draft.privateDnsHostValid,
                onBack = ::back,
                onNext = { if (step == Step.FINISH) finish(activate = isOwner) else next() },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(tween(350)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(350))
                val exit = slideOutHorizontally(tween(250)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(250))
                enter togetherWith exit
            },
            label = "step",
        ) { current ->
            when (current) {
                Step.WELCOME -> WelcomeStep()
                Step.PRESET -> PresetStep(draft)
                Step.CATEGORIES -> CategoriesStep(draft)
                Step.FRICTION -> FrictionStep(draft)
                Step.DNS -> DnsStep(draft)
                Step.FINISH -> FinishStep(draft, isOwner, onSaveOnly = { finish(activate = false) })
            }
        }
    }
    ConfirmDialog(confirm) { confirm = null }
}

@Composable
private fun WizardBar(
    showBack: Boolean,
    nextLabel: String,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) { Text(nextLabel) }
    }
}

@Composable
private fun StepPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WelcomeStep() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
        label = "logo-scale",
    )
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "logo-fade")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_anchor),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("A phone. A tool.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Not a doomscrolling or adult-content machine. Anchor blocks what pulls you in, and makes undoing it slow on purpose.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Feature(Icons.Filled.Lock, "Blocks adult content and endless feeds", "In Chrome and in every other app.")
            Feature(Icons.Filled.DateRange, "Loosening takes time", "Restarts spread over hours, so the urge passes first.")
            Feature(Icons.Filled.CheckCircle, "Set up in a minute", "Pick a preset, then adjust anything you like.")
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PresetStep(draft: SetupDraft) {
    val ctx = LocalContext.current
    val names = remember { Categories.all(ctx).associate { it.id to it.name } }
    StepPage("Choose a starting point", "Pick the one closest to what you want. You can fine-tune it on the next screens.") {
        Presets.ALL.forEach { preset ->
            SelectableCard(selected = draft.presetId == preset.id, onClick = { draft.apply(preset) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (preset.recommended) Pill("Recommended")
                    StrictnessDots(preset.level)
                }
                Text(preset.tagline, style = MaterialTheme.typography.bodyMedium)
                Soft(
                    "Blocks " + preset.categories.mapNotNull { names[it] }.joinToString() +
                        ". Unlock: ${preset.friction.restarts} restarts, ${preset.friction.gapHours}${Rules.gapUnit} apart."
                )
            }
        }
        if (draft.presetId == null) {
            Muted("You've adjusted the settings, so they no longer match a preset. That's fine: your choices are kept.")
        }
    }
}

@Composable
private fun CategoriesStep(draft: SetupDraft) {
    val ctx = LocalContext.current
    val categories = remember { Categories.all(ctx) }
    StepPage("What should be blocked?", "Blocked in Chrome and every app. Matching apps, like Instagram for Social feeds, are hidden too.") {
        categories.forEach { category ->
            SwitchCard(
                title = category.name,
                description = category.description,
                checked = category.alwaysOn || category.id in draft.categories,
                enabled = true,
                locked = category.alwaysOn,
            ) { on -> draft.setCategory(category.id, on) }
        }
    }
}

@Composable
private fun FrictionStep(draft: SetupDraft) {
    StepPage(
        "How hard should undoing be?",
        "Adding blocks is always instant. Removing one, showing a hidden app or loosening a rule needs an unlock: a number of restarts, spread out over time.",
    ) {
        Presets.FRICTIONS.forEach { f ->
            SelectableCard(selected = draft.friction == f, onClick = { draft.setFriction(f) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (f == Presets.FIRM) Pill("Recommended")
                }
                Text(f.description, style = MaterialTheme.typography.bodyMedium)
                Soft(
                    "${f.restarts} restarts, at least ${f.gapHours}${Rules.gapUnit} apart. " +
                        "Takes at least ${formatDuration(f.minimumWaitMs)}. Stays open ${f.windowMinutes} min."
                )
            }
        }
        if (draft.friction == null) {
            Muted("Custom: ${draft.restarts} restarts, ${draft.gapHours}${Rules.gapUnit} apart, open ${draft.windowMinutes} min.")
        }
        Text("Extras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        SwitchCard(
            title = "Strict YouTube",
            description = "Restricted mode set to strict instead of moderate.",
            checked = draft.youtubeStrict,
            enabled = true,
        ) { draft.setYoutube(it) }
        SwitchCard(
            title = "Freeze apps",
            description = "Nothing new can be installed, not even from the Play Store. App updates may stop too.",
            checked = draft.freezeInstalls,
            enabled = true,
        ) { draft.setFreeze(it) }
    }
}

@Composable
private fun DnsStep(draft: SetupDraft) {
    StepPage("How should other apps be filtered?", "Chrome is locked down either way. This decides how everything else on the phone is filtered.") {
        SelectableCard(selected = draft.dnsFilter, onClick = { draft.dnsFilter = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anchor's filter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill("Recommended")
            }
            Text(
                "Blocks your categories and a large adult-site list in every app, and forces SafeSearch everywhere.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Soft("Runs on the phone as an always-on VPN that only handles DNS lookups.")
        }
        SelectableCard(selected = !draft.dnsFilter, onClick = { draft.dnsFilter = false }) {
            Text("My own DNS filter", style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep AdGuard, NextDNS or another filter. Anchor leaves the VPN slot free and can lock Private DNS so it can't be switched off.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!draft.dnsFilter) {
            OutlinedTextField(
                value = draft.privateDnsHost,
                onValueChange = { draft.privateDnsHost = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Private DNS hostname (optional)") },
                placeholder = { Text("xxxxxxxx.d.adguard-dns.com") },
                isError = !draft.privateDnsHostValid,
                supportingText = {
                    Text(
                        if (draft.privateDnsHostValid) "Leave empty if you use the AdGuard app instead of Private DNS."
                        else "That doesn't look like a hostname."
                    )
                },
            )
        }
    }
}

@Composable
private fun FinishStep(draft: SetupDraft, isOwner: Boolean, onSaveOnly: () -> Unit) {
    val ctx = LocalContext.current
    val names = remember { Categories.all(ctx).associate { it.id to it.name } }
    StepPage(
        if (isOwner) "Ready when you are" else "One last step",
        if (isOwner) "Turning on makes these rules stick. You can always make them stricter."
        else "Anchor needs to become the phone's device owner. That's what stops it from being switched off.",
    ) {
        AnchorCard("Your setup") {
            SummaryRow("Preset", draft.preset?.name ?: "Custom")
            SummaryRow("Blocking", draft.categories.mapNotNull { names[it] }.joinToString())
            SummaryRow(
                "Unlock",
                "${draft.restarts} restarts, ${draft.gapHours}${Rules.gapUnit} apart (at least ${formatDuration(draft.minimumWaitMs)})",
            )
            SummaryRow(
                "Filter",
                when {
                    draft.dnsFilter -> "Anchor's DNS filter"
                    draft.privateDnsHost.isNotBlank() -> "Your own, Private DNS locked to ${draft.privateDnsHost.trim()}"
                    else -> "Your own"
                },
            )
            val extras = listOfNotNull(
                "Strict YouTube".takeIf { draft.youtubeStrict },
                "Apps frozen".takeIf { draft.freezeInstalls },
            )
            if (extras.isNotEmpty()) SummaryRow("Extras", extras.joinToString())
        }
        AnimatedContent(targetState = isOwner, label = "owner") { owner ->
            if (owner) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusLine(true, "Anchor is the device owner")
                    TextButton(onClick = onSaveOnly) { Text("Save without turning on yet") }
                }
            } else {
                DeviceOwnerSetup()
            }
        }
    }
}

@Composable
private fun DeviceOwnerSetup() {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AnchorCard("Make Anchor the device owner") {
        Text("Do this once, from a computer with adb:")
        NumberedStep(1, "Settings › Accounts and backup › Manage accounts: remove every account. Remove Secure Folder if you have one.")
        NumberedStep(2, "Turn on USB debugging, connect the phone and run:")
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DEVICE_OWNER_COMMAND,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(DEVICE_OWNER_COMMAND))
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy") }
            }
        }
        NumberedStep(3, "Add your accounts back.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Muted("Waiting for Anchor to become device owner. This screen updates by itself.")
        }
        Muted("Rather do it later? Tap Finish. Your choices are saved, and Home shows these steps.")
    }
}

@Composable
private fun NumberedStep(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SelectableCard(selected: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "card-border",
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "card-container",
    )
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        colors = CardDefaults.outlinedCardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun Pill(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun StrictnessDots(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (i < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
    }
}

/** Secondary text that follows the card's content colour, so it reads well on a selected card too. */
@Composable
private fun Soft(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.75f))
}
