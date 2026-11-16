package app.anchor.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.anchor.data.Rules
import app.anchor.protection.Policies
import app.anchor.ui.components.Confirm
import app.anchor.ui.components.ConfirmDialog
import app.anchor.ui.components.formatDuration
import kotlinx.coroutines.delay

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
