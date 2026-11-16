package app.anchor

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.anchor.data.Rules
import app.anchor.data.Store
import app.anchor.filter.FilterVpnService
import app.anchor.protection.Policies
import app.anchor.ui.AnchorApp
import app.anchor.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        FilterVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnchorTheme {
                AnchorApp(onActivate = ::activate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Policies.isEnforcing(this)) {
            Policies.applyAll(this)
            FilterVpnService.start(this)
        }
    }

    private fun activate() {
        Store.setActivated(this, true)
        Policies.applyAll(this)
        ensureFilterRunning()
    }

    fun ensureFilterRunning() {
        if (!Rules.dnsFilter(this)) return
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else FilterVpnService.start(this)
    }
}
