package de.troetenrepublik.app.market

// Text von Nachrichten, deren Seite sich nicht auslesen laesst, auf allen Plattformen gleich.
// EU-Kommission: Die Seite entsteht erst per Skript; der Text kommt ueber die Schnittstelle des Pressecorners.
// Seiten, die nur Titel und Link eines Dokuments zeigen (SNB, Bank of England): Die App liest das verlinkte PDF.
object Sonderleser {

    private val api by lazy { MarketApi() }

    // Pressecorner: Der Link endet auf ip_26_2186, abgefragt wird IP/26/2186.
    fun euAdresse(link: String): String? {
        val m = Regex("""ec\.europa\.eu/commission/presscorner/detail/([a-z]{2})/([a-z]+)_(\d{2})_(\d+)""", RegexOption.IGNORE_CASE).find(link) ?: return null
        val (sprache, art, jahr, nummer) = m.destructured
        return "https://ec.europa.eu/commission/presscorner/api/documents?reference=${art.uppercase()}/$jahr/$nummer&language=${sprache.lowercase()}"
    }

    // Absaetze aus der Antwort des Pressecorners (htmlContent).
    fun euText(json: String): List<String> {
        val html = runCatching {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
            val r = o["docuLanguageResource"] as? kotlinx.serialization.json.JsonObject ?: return emptyList()
            (r["htmlContent"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        }.getOrNull() ?: return emptyList()
        return htmlAbsaetze(html)
    }

    // Absaetze und Aufzaehlungspunkte aus einfachem HTML, ohne Tags und mit aufgeloesten Zeichen.
    fun htmlAbsaetze(html: String): List<String> =
        Regex("""<(p|li|h[2-4])\b[^>]*>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html)
            .map { klartext(it.groupValues[2]) }
            .filter { it.length >= 3 }
            .distinct().take(80).toList()

    private fun klartext(s: String): String =
        Rss.klartext(entitaeten(s.replace(Regex("<[^>]+>"), " "))).replace(Regex("\\s+"), " ").trim()

    // Benannte HTML-Entitaeten (Latin-1, Gedanken- und Anfuehrungszeichen); &amp; und die uebrigen loest Rss.klartext.
    private val ENTITAETEN: Map<String, String> by lazy {
        val latin1 = ("nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn sup2 sup3 acute micro " +
            "para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute " +
            "Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN " +
            "szlig agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml eth ntilde ograve oacute " +
            "ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml").split(' ')
        latin1.mapIndexed { i, n -> n to (160 + i).toChar().toString() }.toMap() + mapOf(
            "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“",
            "rdquo" to "”", "bdquo" to "„", "hellip" to "…", "euro" to "€", "bull" to "•", "trade" to "™",
        )
    }

    fun entitaeten(s: String): String = s.replace(Regex("&([A-Za-z][A-Za-z0-9]{1,7});")) { m -> ENTITAETEN[m.groupValues[1]] ?: m.value }

    // Erstes PDF auf demselben Server, auf das die Seite verlinkt; null ohne.
    fun pdfLink(html: String, seite: String): String? {
        val basis = Regex("^(https?://[^/]+)").find(seite)?.value ?: return null
        val links = Regex("""href\s*=\s*["']([^"']+\.pdf(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1].replace("&amp;", "&") }
        return links.map { l -> if (l.startsWith("http")) l else if (l.startsWith("/")) basis + l else null }
            .firstOrNull { it != null && it.startsWith(basis) }
    }

    // Der PDF-Leser liefert kurze Schnipsel; sie werden zu Saetzen verbunden. Ein Absatz endet nach einem Satzende,
    // wenn er lang genug ist, oder vor einem Aufzaehlungspunkt. Seitenzahlen fallen weg.
    fun pdfAbsaetze(zeilen: List<String>): List<String> {
        val aus = ArrayList<String>()
        val sb = StringBuilder()
        fun ende() {
            val t = sb.toString().replace(Regex("\\s+"), " ").replace(Regex(" ([,.;:!?])"), "$1").trim()
            if (t.length >= 3) aus += t
            sb.setLength(0)
        }
        for (roh in zeilen) {
            val z = roh.trim()
            if (z.isEmpty() || Regex("^\\d{1,3}$").matches(z)) continue
            if (z.startsWith("•") || z.startsWith("–") && sb.length > 40) ende()
            if (sb.isNotEmpty() && sb.endsWith("-") && z.firstOrNull()?.isLowerCase() == true) sb.setLength(sb.length - 1) else if (sb.isNotEmpty()) sb.append(' ')
            sb.append(z)
            if (sb.length >= 280 && z.last() in ".!?") ende()
            if (sb.length >= 1400) ende()
            if (aus.size >= 60) break
        }
        if (sb.isNotEmpty() && aus.size < 60) ende()
        return aus
    }

    // Text der EU-Kommission ueber die Schnittstelle; null, wenn der Link nicht dorthin gehoert oder nichts kommt.
    suspend fun eu(link: String): List<String>? {
        val adresse = euAdresse(link) ?: return null
        val a = api.anfrage(adresse, max = 2_000_000)
        return if (a.ok) euText(a.text.orEmpty()).takeIf { it.isNotEmpty() } else null
    }

    // Text des Dokuments, auf das die Seite verweist; null ohne Dokument oder wenn es sich nicht lesen laesst.
    suspend fun dokument(link: String): List<String>? {
        if (!link.startsWith("http")) return null
        val roh = api.datei(link, 15_000_000) ?: return null
        // Der Link ist selbst ein PDF
        if (roh.size > 4 && roh[0] == '%'.code.toByte() && roh[1] == 'P'.code.toByte() && roh[2] == 'D'.code.toByte() && roh[3] == 'F'.code.toByte()) {
            return pdfAbsaetze(PdfText.zeilen(roh)).takeIf { l -> l.sumOf { it.length } >= 200 }
        }
        val html = roh.toString(Charsets.UTF_8)
        // Mehrsprachige Seiten ohne Dokument in dieser Sprache verweisen auf die englische Fassung (etwa die SNB)
        val pdf = pdfLink(html, link) ?: Regex("/(de|fr|it)/").find(link)?.let { m ->
            val en = link.replaceRange(m.range, "/en/")
            api.datei(en, 3_000_000)?.toString(Charsets.UTF_8)?.let { pdfLink(it, en) }
        } ?: return null
        val bytes = api.datei(pdf, 15_000_000) ?: return null
        return pdfAbsaetze(PdfText.zeilen(bytes)).takeIf { l -> l.sumOf { it.length } >= 200 }
    }
}
