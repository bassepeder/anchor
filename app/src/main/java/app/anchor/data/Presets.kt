package app.anchor.data
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
