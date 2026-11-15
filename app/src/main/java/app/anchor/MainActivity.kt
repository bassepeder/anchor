package app.anchor

import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        FilterVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
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
