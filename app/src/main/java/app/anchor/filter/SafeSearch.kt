package app.anchor.filter
/**
 * The search engines' own DNS-level SafeSearch: pointing their hostnames at these addresses
 * forces SafeSearch / restricted mode in every app, not just in Chrome.
 */
object SafeSearch {
    private val GOOGLE = Regex("^(www\\.)?google(\\.[a-z]{2,3}){1,2}$")
    private val YOUTUBE = setOf(
        "www.youtube.com",
        "m.youtube.com",
        "youtubei.googleapis.com",
        "youtube.googleapis.com",
        "www.youtube-nocookie.com",
    )

    fun target(host: String, youtubeStrict: Boolean): String? = when {
        GOOGLE.matches(host) -> "forcesafesearch.google.com"
        host == "bing.com" || host == "www.bing.com" -> "strict.bing.com"
        host == "duckduckgo.com" || host == "www.duckduckgo.com" -> "safe.duckduckgo.com"
        host in YOUTUBE -> if (youtubeStrict) "restrict.youtube.com" else "restrictmoderate.youtube.com"
        else -> null
    }
}
