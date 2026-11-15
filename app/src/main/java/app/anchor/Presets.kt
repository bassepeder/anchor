package app.anchor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Friction(
    val name: String,
    val description: String,
    val restarts: Int,
    val gapHours: Int,
    val windowMinutes: Int,
) {
    // The first restart only counts after one full gap, so the minimum wait is restarts × gap.
    val minimumWaitMs get() = restarts * Rules.gapMs(gapHours)
}

class Preset(
    val id: String,
    val name: String,
    val tagline: String,
    val level: Int,
    val categories: Set<String>,
    val friction: Friction,
    val youtubeStrict: Boolean,
    val freezeInstalls: Boolean,
    val recommended: Boolean = false,
)

object Presets {
    val GENTLE = Friction("Gentle", "A pause to stop and think.", restarts = 2, gapHours = 6, windowMinutes = 30)
    val FIRM = Friction("Firm", "Long enough for an urge to pass.", restarts = 3, gapHours = 12, windowMinutes = 30)
    val IRON = Friction("Iron", "For when you really mean it.", restarts = 5, gapHours = 24, windowMinutes = 10)
    val FRICTIONS = listOf(GENTLE, FIRM, IRON)

    private val BASE = setOf("adult", "bypass")

    val ALL = listOf(
        Preset(
            id = "essentials",
            name = "Essentials",
            tagline = "Adult content off the phone. Everything else stays as it is.",
            level = 1,
            categories = BASE,
            friction = GENTLE,
            youtubeStrict = false,
            freezeInstalls = false,
        ),
        Preset(
            id = "balanced",
            name = "Balanced",
            tagline = "No adult content, no endless feeds or Shorts. YouTube and messaging keep working.",
            level = 2,
            categories = BASE + setOf("social", "shorts"),
            friction = FIRM,
            youtubeStrict = false,
            freezeInstalls = false,
            recommended = true,
        ),
        Preset(
            id = "focused",
            name = "Focused",
            tagline = "Balanced, plus gambling and dating blocked and strict YouTube.",
            level = 3,
            categories = BASE + setOf("social", "shorts", "gambling", "dating"),
            friction = FIRM,
            youtubeStrict = true,
            freezeInstalls = false,
        ),
        Preset(
            id = "phone",
            name = "Just a phone",
            tagline = "Every category blocked and no new apps. Calls, messages, maps and banking.",
            level = 4,
            categories = BASE + setOf("social", "shorts", "video", "gambling", "dating"),
            friction = IRON,
            youtubeStrict = true,
            freezeInstalls = true,
        ),
    )

    val DEFAULT = ALL.first { it.recommended }
}

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
