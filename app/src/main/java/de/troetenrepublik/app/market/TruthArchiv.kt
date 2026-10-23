package de.troetenrepublik.app.market

// Beitraege des Praesidenten auf Truth Social ueber das Archiv Trump's Truth (trumpstruth.org), ein Projekt von
// Defending Democracy Together, nicht verbunden mit Truth Social oder Donald Trump. Offener RSS-Feed (bis 500
// Beitraege, Filter nach Datum, alle paar Minuten aktuell), robots.txt erlaubt alles. Abgerufen wird nur das Archiv:
// Bilder und Videos liegen in seinem Speicher, Links fuehren zur Archivseite. Vorschaubilder von truthsocial.com
// bleiben unbenutzt.
object TruthArchiv {

    const val FEED = "https://www.trumpstruth.org/feed"
    private const val SPEICHER = "linodeobjects.com/"

    enum class Art { EIGEN, ZITAT, WEITERGETEILT }

    data class Beitrag(
        // Archivseite des Beitrags, zugleich Schluessel.
        val link: String,
        val zeit: Long,
        val text: String,
        val bilder: List<String>,
        val videos: Int,
        val art: Art,
        val geloescht: Boolean,
    )

    // Die neuesten anzahl Beitraege; bis (JJJJ-MM-TT) begrenzt auf diesen Tag.
    fun adresse(anzahl: Int = 100, bis: String? = null): String =
        "$FEED?limit=${anzahl.coerceIn(1, 500)}" + (bis?.let { "&end_date=$it" } ?: "")

    private val itemRx = Regex("<item(?:\\s[^>]*)?>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun feld(block: String, name: String): String? =
        Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)?.groupValues?.get(1)

    // Text des Beitrags: ohne die Verweise auf Bilder und Videos und ohne Adressen von truthsocial.com.
    fun text(beschreibung: String): String {
        var t = beschreibung.trim().removePrefix("<![CDATA[").removeSuffix("]]>")
        t = t.replace(Regex("<a [^>]*>\\s*View (image|video)\\s*</a>", RegexOption.IGNORE_CASE), " ")
        t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("</p>\\s*<p>", RegexOption.IGNORE_CASE), "\n")
        // Jede Zeile einzeln, damit die Absaetze erhalten bleiben
        t = t.split("\n").joinToString("\n") { Rss.klartext(it) }
        t = t.replace(Regex("(RT:\\s*)?https?://(www\\.)?truthsocial\\.com/\\S*", RegexOption.IGNORE_CASE), " ")
        return t.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun lesen(xml: String): List<Beitrag> = itemRx.findAll(xml).mapNotNull { m ->
        val b = m.groupValues[1]
        val link = feld(b, "link")?.trim()?.takeIf { it.startsWith("https://www.trumpstruth.org/") || it.startsWith("https://trumpstruth.org/") }
            ?: return@mapNotNull null
        val medien = Regex("<media:content[^>]*url=\"([^\"]+)\"[^>]*medium=\"(\\w+)\"", RegexOption.IGNORE_CASE).findAll(b)
            .map { it.groupValues[1] to it.groupValues[2].lowercase() }.filter { SPEICHER in it.first }.toList()
        val art = when (feld(b, "archive:postType")?.trim()?.lowercase()) {
            "quote" -> Art.ZITAT
            "repost" -> Art.WEITERGETEILT
            else -> Art.EIGEN
        }
        Beitrag(
            link = link,
            zeit = Rss.zeit(feld(b, "pubDate")),
            text = text(feld(b, "description").orEmpty()),
            bilder = medien.filter { it.second == "image" }.map { it.first }.distinct(),
            videos = medien.count { it.second == "video" },
            art = art,
            geloescht = feld(b, "archive:removed")?.trim().equals("true", ignoreCase = true),
        )
    }.toList()

    // Zwei Seiten zusammen, neueste zuerst, ohne doppelte Beitraege.
    fun zusammen(alt: List<Beitrag>, neu: List<Beitrag>): List<Beitrag> =
        (alt + neu).distinctBy { it.link }.sortedByDescending { it.zeit }
}
