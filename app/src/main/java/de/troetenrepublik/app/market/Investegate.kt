package de.troetenrepublik.app.market

import java.time.LocalDateTime
import java.time.ZoneId

// Investegate (www.investegate.co.uk, seit 5.95): Pflichtmeldungen britischer Firmen, hier die Director
// Dealings. Nur Adressen und reine Leser, die Abrufe laufen spaeter ueber MarketApi.anfrage. Uhrzeiten gelten
// in Europe/London, uebernommen werden nur Links auf eigene Meldungsseiten, Entitaeten werden dekodiert.
object Investegate {

    const val BASIS = "https://www.investegate.co.uk"
    const val SEITE_LISTE = BASIS + "/category/directors-dealings"

    fun listeAdresse(seite: Int): String = if (seite <= 1) SEITE_LISTE else "$SEITE_LISTE?page=$seite"

    fun firmaAdresse(kuerzel: String): String = "$BASIS/company/$kuerzel"

    // Yahoo-Symbol auf Investegate-Kuerzel: nur .L und .IL, Bindestrich wird Punkt, sonst null.
    fun ticker(symbol: String): String? {
        val s = symbol.trim().uppercase()
        val rumpf = when {
            s.endsWith(".IL") -> s.dropLast(3)
            s.endsWith(".L") -> s.dropLast(2)
            else -> return null
        }
        if (rumpf.isEmpty()) return null
        return rumpf.replace('-', '.')
    }

    // Eine Meldung mit Zeit in Millisekunden, Firma, Kuerzel, Quellenangabe (etwa RNS) und Link.
    data class Meldung(val zeit: Long, val firma: String, val kuerzel: String, val titel: String, val quelle: String, val link: String)

    private val ZEILEN = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ZELLE = Regex("<t[dh]\\b[^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ANKER = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val MERKMAL = Regex("([a-zA-Z-]+)\\s*=\\s*\"([^\"]*)\"")
    private val FIRMA = Regex("""/company/([A-Za-z0-9.-]+)/?${'$'}""")

    private val MONATE = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)

    private class Verweis(val klasse: String, val ziel: String, val text: String)

    // Sichtbarer Text: Tags weg, Entitaeten aufgeloest, geschuetzte Leerzeichen zu Leerzeichen, Leerraum zusammen.
    private fun klartext(html: String): String =
        InsiderQuellen.entitaeten(html.replace(Regex("<[^>]+>"), " "))
            .replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun verweise(html: String): List<Verweis> = ANKER.findAll(html).map {
        val merkmale = MERKMAL.findAll(it.groupValues[1]).associate { m -> m.groupValues[1].lowercase() to m.groupValues[2] }
        Verweis(merkmale["class"].orEmpty(), merkmale["href"].orEmpty(), klartext(it.groupValues[2]))
    }.toList()

    // Nur Links auf eigene Meldungsseiten; relative Links werden ergaenzt, alles andere verworfen.
    private fun link(href: String): String {
        val l = if (href.startsWith("/")) BASIS + href else href
        return if (l.startsWith("$BASIS/announcement/")) l else ""
    }

    // "03 Nov 2026 03:31 PM" in Europe/London als Millisekunden; 12:xx AM ist 0 Uhr, 12:xx PM 12 Uhr.
    private fun zeit(text: String): Long? {
        val m = Regex("""(\d{1,2}) ([A-Za-z]{3}) (\d{4}) (\d{1,2}):(\d{2}) (AM|PM)""", RegexOption.IGNORE_CASE).matchEntire(text.trim()) ?: return null
        val monat = MONATE[m.groupValues[2].lowercase()] ?: return null
        val stunde = m.groupValues[4].toIntOrNull() ?: return null
        val minute = m.groupValues[5].toIntOrNull() ?: return null
        if (stunde !in 1..12 || minute !in 0..59) return null
        val vormittag = m.groupValues[6].equals("AM", true)
        val h = when {
            vormittag && stunde == 12 -> 0
            !vormittag && stunde == 12 -> 12
            vormittag -> stunde
            else -> stunde + 12
        }
        return runCatching {
            LocalDateTime.of(m.groupValues[3].toInt(), monat, m.groupValues[1].toInt(), h, minute)
                .atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        }.getOrNull()
    }

    // Kategorieseite Director Dealings: je Zeile Datum mit Uhrzeit, Quelle, Firma mit Kuerzel
    // (Firmenlink mit Text; ein Link nur mit Bild wird uebergangen), Titel und Link. Kein Dealing-Filter, die
    // Seite zeigt nur Director Dealings. Doppelte Links fallen weg.
    fun listeLesen(html: String): List<Meldung> {
        val aus = LinkedHashMap<String, Meldung>()
        for (z in ZEILEN.findAll(html)) {
            val zeile = z.groupValues[1]
            val zeitWert = zeit(klartext(ZELLE.find(zeile)?.groupValues?.get(1).orEmpty())) ?: continue
            val menue = verweise(zeile)
            val quelle = menue.firstOrNull { it.klasse.contains("regulatory") }?.text.orEmpty()
            val firma = menue.firstOrNull { FIRMA.containsMatchIn(it.ziel) && it.text.isNotBlank() } ?: continue
            val kuerzel = FIRMA.find(firma.ziel)!!.groupValues[1].uppercase()
            val meldung = menue.firstOrNull { it.klasse.contains("announcement-link") } ?: continue
            val l = link(meldung.ziel)
            if (l.isEmpty() || meldung.text.isBlank() || l in aus) continue
            val name = if (firma.text.endsWith("($kuerzel)")) firma.text.dropLast(kuerzel.length + 2).trim() else firma.text
            aus[l] = Meldung(zeitWert, name, kuerzel, meldung.text, quelle, l)
        }
        return aus.values.toList()
    }

    // Firmenseite: Spalten Datum, Uhrzeit, Quelle, Titel-Link. Alle Meldungen ohne Filter nach Art. Firma aus h1
    // oder Seitentitel, falls einfach greifbar, sonst das Kuerzel. Doppelte Links fallen weg.
    fun firmaAlleLesen(html: String, kuerzel: String): List<Meldung> {
        val name = Regex("<h1\\b[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?.let { klartext(it.groupValues[1]) }?.takeIf { it.isNotBlank() }
            ?: Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL)).find(html)
                ?.let { klartext(it.groupValues[1]) }?.takeIf { it.isNotBlank() }
            ?: kuerzel
        val aus = LinkedHashMap<String, Meldung>()
        for (z in ZEILEN.findAll(html)) {
            val zeile = z.groupValues[1]
            val zellen = ZELLE.findAll(zeile).map { klartext(it.groupValues[1]) }.toList()
            val menue = verweise(zeile)
            val quelle = menue.firstOrNull { it.klasse.contains("regulatory") }?.text.orEmpty()
            val meldung = menue.firstOrNull { it.klasse.contains("announcement-link") } ?: continue
            val l = link(meldung.ziel)
            if (l.isEmpty() || l in aus || zellen.size < 2) continue
            val zeitWert = zeit(zellen[0] + " " + zellen[1]) ?: continue
            aus[l] = Meldung(zeitWert, name, kuerzel, meldung.text, quelle, l)
        }
        return aus.values.toList()
    }

    // Nur die Meldungen mit Dealing-Titel aus der Firmenseite
    fun firmaLesen(html: String, kuerzel: String): List<Meldung> = firmaAlleLesen(html, kuerzel).filter { istDealing(it.titel) }

    // Titel einer Meldung ueber Eigengeschaefte von Fuehrungskraeften: PDMR, managerial responsibility oder
    // Director zusammen mit Shareholding, Dealing, Kauf oder Verkauf. Director Declaration, Directorate change,
    // Transaction in Own Shares, Total Voting Rights und Director appointments fallen heraus.
    fun istDealing(titel: String): Boolean {
        val t = titel.lowercase()
        if (t.contains("pdmr") || t.contains("managerial responsib") || t.contains("dealings in securities")) return true
        if (!t.contains("director")) return false
        return t.contains("shareholding") || t.contains("dealing") || t.contains("share purchase") ||
            t.contains("share sale") || t.contains("holding")
    }

    // Eine Einzelmeldung als Zeilen.
    data class Bogen(val titel: String, val zeilen: List<List<String>>)

    // Eine Tabellenebene beim Zerlegen der Einzelmeldung.
    private class Rahmen {
        val zellen = ArrayList<String>()
        var zelle: StringBuilder? = null
    }

    // Einzelmeldung: Titel aus dem h1 mit id main-title, Text nur aus dem template mit shadowrootmode (fehlt
    // es, null). Darin verschwinden style, script, head und Bilder. Ausserhalb von Tabellen beenden p, br,
    // div, h1 bis h6 und li eine Textzeile; Tabellen werden zu Zeilen aus Zellen, beginnt eine Tabelle in
    // einer Zelle, kommt zuerst die angefangene aeussere Zeile. Der Text bleibt unveraendert, nur Markup
    // faellt weg.
    fun meldungLesen(html: String): Bogen? {
        val titel = Regex("<h1\\b[^>]*id=\"main-title\"[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { klartext(it) }.orEmpty()
        val koerper = Regex("<template\\b[^>]*shadowrootmode=\"open\"[^>]*>(.*?)</template>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1) ?: return null
        val inhalt = koerper
            .replace(Regex("(?s)<!--.*?-->"), " ")
            .replace(Regex("(?is)<(style|script|head)\\b.*?</\\1>"), " ")
            .replace(Regex("(?i)<![^>]*>"), " ")
            .replace(Regex("(?i)<img\\b[^>]*>"), " ")

        fun reinigen(roh: String): String =
            InsiderQuellen.entitaeten(roh).replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

        val zeilen = ArrayList<List<String>>()
        val zeile = StringBuilder()
        val rahmen = ArrayList<Rahmen>()
        val tag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>")

        fun zeileAusgeben() {
            val s = reinigen(zeile.toString())
            if (s.isNotEmpty()) zeilen.add(listOf(s))
            zeile.setLength(0)
        }

        fun zellenDesRahmens(r: Rahmen): List<String> {
            val rohe = ArrayList(r.zellen)
            r.zelle?.let { rohe.add(it.toString()) }
            return rohe.map { reinigen(it) }.filter { it.isNotEmpty() }
        }

        var pos = 0
        while (true) {
            val naechster = tag.find(inhalt, pos)
            val text = if (naechster == null) inhalt.substring(pos) else inhalt.substring(pos, naechster.range.first)
            val ziel = if (rahmen.isEmpty()) zeile else rahmen.last().zelle
            if (text.isNotEmpty()) ziel?.append(text)
            if (naechster == null) break
            ziel?.append(' ')
            val schliessend = naechster.groupValues[1] == "/"
            val name = naechster.groupValues[2].lowercase()
            when (name) {
                "table" -> {
                    if (!schliessend) {
                        rahmen.lastOrNull()?.let { aeusserer ->
                            val angefangen = zellenDesRahmens(aeusserer)
                            if (angefangen.isNotEmpty()) zeilen.add(angefangen)
                            aeusserer.zellen.clear()
                            aeusserer.zelle = StringBuilder()
                        }
                        rahmen.add(Rahmen())
                    } else if (rahmen.isNotEmpty()) {
                        val r = rahmen.removeAt(rahmen.size - 1)
                        val rest = zellenDesRahmens(r)
                        if (rest.isNotEmpty()) zeilen.add(rest)
                    }
                }
                "td", "th" -> {
                    val r = rahmen.lastOrNull()
                    if (r != null) {
                        if (schliessend) {
                            r.zelle?.let { r.zellen.add(it.toString()) }
                            r.zelle = null
                        } else {
                            r.zelle?.let { r.zellen.add(it.toString()) }
                            r.zelle = StringBuilder()
                        }
                    }
                }
                "tr" -> {
                    val r = rahmen.lastOrNull()
                    if (r != null && schliessend) {
                        r.zelle?.let { r.zellen.add(it.toString()) }
                        r.zelle = null
                        val voll = zellenDesRahmens(r)
                        if (voll.isNotEmpty()) zeilen.add(voll)
                        r.zellen.clear()
                    }
                }
                else -> {
                    if (rahmen.isEmpty()) {
                        val ende = name == "br" || (schliessend && (name == "p" || name == "div" || name == "li" ||
                            (name.length == 2 && name[0] == 'h' && name[1] in '1'..'6')))
                        if (ende) zeileAusgeben()
                    } else if (name == "br" || (schliessend && (name == "p" || name == "div"))) {
                        rahmen.last().zelle?.append(' ')
                    }
                }
            }
            pos = naechster.range.last + 1
        }
        zeileAusgeben()
        return Bogen(titel, zeilen)
    }
}
