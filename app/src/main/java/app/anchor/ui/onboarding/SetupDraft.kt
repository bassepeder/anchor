package app.anchor.ui.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.anchor.data.Friction
import app.anchor.data.Preset
import app.anchor.data.Presets
import app.anchor.data.Rules
import app.anchor.data.Store
import app.anchor.filter.BlockList
import app.anchor.protection.Policies

/** The wizard's choices, kept in memory until the last step saves them. */
class SetupDraft(ctx: Context) {
    var presetId by mutableStateOf<String?>(null)
    var categories by mutableStateOf<Set<String>>(emptySet())
    var restarts by mutableIntStateOf(3)
    var gapHours by mutableIntStateOf(12)
    var windowMinutes by mutableIntStateOf(30)
    var youtubeStrict by mutableStateOf(false)
    var freezeInstalls by mutableStateOf(false)
    var dnsFilter by mutableStateOf(Rules.dnsFilter(ctx))
    var privateDnsHost by mutableStateOf(Rules.privateDnsHost(ctx) ?: "")

    init {
        apply(Presets.DEFAULT)
    }

    val preset: Preset? get() = Presets.ALL.firstOrNull { it.id == presetId }
    val friction: Friction? get() = Presets.FRICTIONS.firstOrNull { matches(it) }
    val minimumWaitMs: Long get() = restarts * Rules.gapMs(gapHours)
    val privateDnsHostValid: Boolean get() = privateDnsHost.isBlank() || Rules.normalizeHost(privateDnsHost) != null

    fun apply(preset: Preset) {
        categories = preset.categories
        setFriction(preset.friction)
        youtubeStrict = preset.youtubeStrict
        freezeInstalls = preset.freezeInstalls
        presetId = preset.id
    }

    fun setCategory(id: String, on: Boolean) {
        categories = if (on) categories + id else categories - id
        rematch()
    }

    fun setFriction(f: Friction) {
        restarts = f.restarts
        gapHours = f.gapHours
        windowMinutes = f.windowMinutes
        rematch()
    }

    fun setYoutube(on: Boolean) {
        youtubeStrict = on
        rematch()
    }

    fun setFreeze(on: Boolean) {
        freezeInstalls = on
        rematch()
    }

    private fun matches(f: Friction) =
        f.restarts == restarts && f.gapHours == gapHours && f.windowMinutes == windowMinutes

    private fun rematch() {
        presetId = Presets.ALL.firstOrNull {
            it.categories == categories && matches(it.friction) &&
                it.youtubeStrict == youtubeStrict && it.freezeInstalls == freezeInstalls
        }?.id
    }

    fun commit(ctx: Context) {
        // Saving bypasses the "looser needs an unlock" checks, which is only fine before Anchor is on.
        if (Policies.isEnforcing(ctx)) return
        Store.edit(ctx) { putStringSet(Store.CATEGORIES, categories) }
        Rules.setRestarts(ctx, restarts)
        Rules.setGapHours(ctx, gapHours)
        Rules.setWindowMinutes(ctx, windowMinutes)
        Rules.setYoutubeStrict(ctx, youtubeStrict)
        Rules.setFreezeInstalls(ctx, freezeInstalls)
        Rules.setDnsFilter(ctx, dnsFilter)
        Rules.setPrivateDnsHost(ctx, if (dnsFilter) null else Rules.normalizeHost(privateDnsHost))
        Store.setOnboarded(ctx, true)
        BlockList.reload(ctx)
    }
}
