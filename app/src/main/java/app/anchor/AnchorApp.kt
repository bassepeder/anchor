package app.anchor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    SITES("Sites", Icons.Filled.Lock),
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    RULES("Rules", Icons.Filled.Settings),
}

/**
 * State lives in SharedPreferences, not in Compose. Every change bumps [version], and each tab
 * re-reads what it shows when the version changes.
 */
@Composable
fun AnchorApp(onActivate: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { version++ }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var onboarded by remember { mutableStateOf(Store.isOnboarded(ctx) || Policies.isEnforcing(ctx)) }

    if (!onboarded) {
        OnboardingWizard(
            onActivate = onActivate,
            onDone = {
                onboarded = true
                tab = Tab.HOME
                refresh()
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeTab(version, refresh, onActivate)
                Tab.SITES -> SitesTab(version, refresh)
                Tab.APPS -> AppsTab(version, refresh)
                Tab.RULES -> RulesTab(version, refresh, onRunSetup = {
                    Store.setOnboarded(ctx, false)
                    onboarded = false
                })
            }
        }
    }
}
