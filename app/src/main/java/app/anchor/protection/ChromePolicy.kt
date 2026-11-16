package app.anchor.protection

import android.content.Context
import android.os.Bundle
import android.util.Log
import app.anchor.data.Rules
import app.anchor.filter.BlockList

object ChromePolicy {
    fun apply(ctx: Context) {
        if (!Policies.isEnforcing(ctx)) return
        BlockList.reload(ctx)
        val policy = Bundle().apply {
            putStringArray("URLBlocklist", BlockList.chromeBlocklist().take(1000).toTypedArray())
            putStringArray("URLAllowlist", BlockList.chromeAllowlist().toTypedArray())
            putInt("IncognitoModeAvailability", 1)           // 1 = incognito unavailable
            putString("DnsOverHttpsMode", "off")             // Chrome must use the phone's DNS, i.e. our filter
            putBoolean("ForceGoogleSafeSearch", true)
            putInt("ForceYouTubeRestrict", if (Rules.youtubeStrict(ctx)) 2 else 1) // 1 = moderate, 2 = strict
            putInt("SafeSitesFilterBehavior", 1)             // 1 = Google's adult-content filter on every site
            putBoolean("NTPContentSuggestionsEnabled", false) // no article feed on the new tab page
        }
        try {
            Policies.dpm(ctx).setApplicationRestrictions(Policies.admin(ctx), Policies.CHROME, policy)
        } catch (e: Exception) {
            Log.w("Anchor", "Failed to set Chrome policy", e)
        }
    }
}
