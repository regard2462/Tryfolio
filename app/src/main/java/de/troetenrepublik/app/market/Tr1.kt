package de.troetenrepublik.app.market

import java.time.LocalDate
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Britische Pflichtmeldungen "Holding(s) in Company" (TR-1 nach DTR 5): Titelerkennung, Zerlegen eines
// Formulars in Zeilen und Lesen von Halter, Gruenden, Stichtagen und Stimmrechtsanteilen. Die Schwelle
// nennt das Formular nicht, sie wird aus vorher und nachher abgeleitet.
object Tr1 {

    // Eine Meldung in einer Liste: Zeit in ms, Titel, Quellenangabe (etwa RNS), Link zur Meldung
    data class Eintrag(val zeit: Long, val titel: String, val quelle: String, val link: String)

    // Anteile in Prozent; unter = Wert x, wenn statt einer Zahl "below x%" / "under x%" / "less than x%" / "<x%" steht
    data class Stand(val aktien: Double?, val instrumente: Double?, val gesamt: Double?, val unter: Double? = null)

    enum class Grund { STIMMRECHTE, INSTRUMENTE, AUFTEILUNG, ANDERES }

    // Abgeleitete Schwelle in ganzen Prozent; steigend = erreicht oder ueberschritten, sonst unterschritten
    data class Schwelle(val prozent: Int, val steigend: Boolean)

    data class Meldung(
        val halter: String,          // Abschnitt 3, Name; leer, wenn nicht lesbar
        val sitz: String,            // Abschnitt 3, Ort und Land mit ", " verbunden; leer moeglich
        val gruende: Set<Grund>,     // Abschnitt 2
        val hinweis: String,         // Abschnitt 2: "Comments" oder Text hinter "Other (please specify):"; sonst leer
        val stichtag: Long?,         // Abschnitt 5 als ms (12:00 UTC des Tages)
        val gemeldet: Long?,         // Abschnitt 6 als ms (12:00 UTC)
        val nachher: Stand,
        val vorher: Stand?,          // null, wenn alle Werte fehlen
        val stimmrechte: Long?,      // Abschnitt 7, Total number of voting rights held in issuer
        val nichtUk: Boolean,        // Abschnitt 1: Non-UK-Emittent
    ) {
        val schwelle: Schwelle? get() = schwelle(vorher, nachher, nichtUk)
    }

    // Titel einer Stimmrechtsmeldung; Meldungen zu Stimmrechtszahlen und Fuehrungskraeften fallen heraus
    fun istStimmrechte(titel: String): Boolean {
        val t = titel.lowercase()
        if (t.contains("total voting rights") || t.contains("director")) return false
        return t.contains("holding(s) in company") || t.contains("holdings in company") || t.contains("holding in company") ||
            t.contains("tr-1") || t.contains("tr1 ") || t.endsWith("tr1") || t.contains("major holding") ||
            t.contains("major interest") || t.contains("major shareholding") || t.contains("notification of major") ||
            t.contains("notification of significant")
    }

    // Einzelmeldung als Zeilen: Inhalt des template mit shadowrootmode, sonst body, sonst alles. Ausserhalb
    // von Tabellen beendet jedes schliessende p die Zeile (auch leer), br, div, li und h1 bis h6 nur mit
    // Text. In Tabellen wird jede tr zu einer Zeile aus allen Zellen, auch leeren; ganz leere Zeilen
    // entfallen. Eine Tabelle in einer Zelle gibt erst die angefangene aeussere Zeile aus, danach geht die
    // aeussere Zeile mit einer neuen Zelle weiter.
    fun zeilen(html: String): List<List<String>> {
        val koerper = TEMPLATE.find(html)?.groupValues?.get(1)
            ?: BODY.find(html)?.groupValues?.get(1)
            ?: html
        val inhalt = koerper
            .replace(Regex("(?s)<!--.*?-->"), " ")
            .replace(Regex("(?is)<(style|script|head)\\b.*?</\\1>"), " ")
            .replace(Regex("(?i)<![^>]*>"), " ")
            .replace(Regex("(?i)<img\\b[^>]*>"), " ")

        // Leerraum vor Komma, Semikolon und schliessender Klammer stammt aus Tag-Grenzen und faellt weg
        fun reinigen(roh: String): String =
            InsiderQuellen.entitaeten(roh).replace('\u00A0', ' ').replace(Regex("\\s+"), " ")
                .replace(Regex(" (?=[,;)])"), "").trim()

        val aus = ArrayList<List<String>>()
        val zeile = StringBuilder()
        val rahmen = ArrayList<Rahmen>()

        fun textZeile(immer: Boolean) {
            val s = reinigen(zeile.toString())
            if (immer || s.isNotEmpty()) aus.add(listOf(s))
            zeile.setLength(0)
        }

        fun reihe(r: Rahmen) {
            val rohe = ArrayList(r.zellen)
            r.zelle?.let { rohe.add(it.toString()) }
            val voll = rohe.map { reinigen(it) }
            if (voll.isNotEmpty() && voll.any { it.isNotEmpty() }) aus.add(voll)
            r.zellen.clear()
            r.zelle = null
        }

        var pos = 0
        while (true) {
            val marke = TAG_MUSTER.find(inhalt, pos)
            val text = if (marke == null) inhalt.substring(pos) else inhalt.substring(pos, marke.range.first)
            val ziel = if (rahmen.isEmpty()) zeile else rahmen.last().zelle
            if (text.isNotEmpty()) ziel?.append(text)
            if (marke == null) break
            ziel?.append(' ')
            val schliessend = marke.groupValues[1] == "/"
            val name = marke.groupValues[2].lowercase()
            when (name) {
                "table" -> {
                    if (!schliessend) {
                        if (rahmen.isEmpty()) textZeile(false) else reihe(rahmen.last())
                        rahmen.add(Rahmen())
                    } else if (rahmen.isNotEmpty()) {
                        reihe(rahmen.removeAt(rahmen.size - 1))
                        rahmen.lastOrNull()?.let { it.zelle = StringBuilder() }
                    }
                }
                "td", "th" -> {
                    val r = rahmen.lastOrNull()
                    if (r != null) {
                        r.zelle?.let { r.zellen.add(it.toString()) }
                        r.zelle = if (schliessend) null else StringBuilder()
                    }
                }
                "tr" -> {
                    val r = rahmen.lastOrNull()
                    if (r != null && (schliessend || r.zellen.isNotEmpty() || r.zelle != null)) reihe(r)
                }
                else -> {
                    if (rahmen.isEmpty()) {
                        if (name == "p" && schliessend) textZeile(true)
                        else if (name == "br" || (schliessend && (name == "div" || name == "li" ||
                                (name.length == 2 && name[0] == 'h' && name[1] in '1'..'6')))) textZeile(false)
                    } else if (name == "p" || name == "br" || name == "div") {
                        rahmen.last().zelle?.append(' ')
                    }
                }
            }
            pos = marke.range.last + 1
        }
        textZeile(false)
        while (rahmen.isNotEmpty()) reihe(rahmen.removeAt(rahmen.size - 1))
        return aus
    }

    // Ein Formular lesen; null, wenn weder Halter noch Gesamtanteil lesbar waren oder etwas schiefging
    fun lesen(html: String): Meldung? = runCatching {
        val z = zeilen(html)
        val halterName = halter(z)
        var nachherPaar: Pair<Stand, Long?> = Pair(Stand(null, null, null), null)
        var vorherPaar: Pair<Stand, Long?>? = null
        val bereich = abschnittBereich(z, 7)
        if (bereich != null) {
            for (i in bereich.first + 1..bereich.last) {
                val b = bezeichnung(z[i])
                if (b.startsWith("resulting situation")) nachherPaar = position(z, i)
                else if (b.startsWith("position of previous notification")) vorherPaar = position(z, i)
            }
        }
        var vorherStand = vorherPaar?.first
        if (vorherStand != null && vorherStand.aktien == null && vorherStand.instrumente == null && vorherStand.gesamt == null && vorherStand.unter == null)
            vorherStand = null
        if (halterName.isEmpty() && nachherPaar.first.gesamt == null) return@runCatching null
        Meldung(
            halterName,
            sitz(z),
            gruende(z),
            hinweis(z),
            abschnittsDatum(z, 5),
            abschnittsDatum(z, 6),
            nachherPaar.first,
            vorherStand,
            nachherPaar.second,
            nichtUk(z))
    }.getOrNull()

    // Erste Zahl im Text als Prozent; below und aehnliche Texte sowie Werte ausserhalb 0 bis 100 sind keine Zahl
    fun prozent(text: String): Double? {
        val t = text.lowercase()
        if (t.contains("below") || t.contains("under") || t.contains("less than") || t.contains("<")) return null
        val m = ZAHL.find(t) ?: return null
        val wert = zahl(m.value) ?: return null
        return if (wert in 0.0..100.0) wert else null
    }

    // Zahl hinter below, under, less than oder "<"; sonst null
    fun unterWert(text: String): Double? {
        val t = text.lowercase()
        if (!(t.contains("below") || t.contains("under") || t.contains("less than") || t.contains("<"))) return null
        val m = ZAHL.find(t) ?: return null
        val wert = zahl(m.value) ?: return null
        return if (wert in 0.0..100.0) wert else null
    }

    // Erste Zahl im Text als Stimmrechtsanzahl; Tausenderkommas und Nachkommastellen fallen weg
    fun anzahl(text: String): Long? {
        val m = ZAHL.find(text) ?: return null
        val w = m.value.replace(",", "")
        return if (w.contains('.')) w.toDoubleOrNull()?.roundToLong() else w.toLongOrNull()
    }

    // Datum als ms um 12:00 UTC; englische Monatsnamen (vor und nach dem Tag), Zahl- und ISO-Schreibweise
    fun tag(text: String): Long? {
        fun datum(tagWert: Int?, monat: Int?, jahr: Int?): Long? {
            if (tagWert == null || monat == null || jahr == null) return null
            return runCatching {
                LocalDate.of(jahr, monat, tagWert).toEpochDay() * 86_400_000L + 43_200_000L
            }.getOrNull()
        }
        DATUM_NAMEN.find(text)?.let {
            return datum(it.groupValues[1].toIntOrNull(), MONATE[it.groupValues[2].lowercase().take(3)], it.groupValues[3].toIntOrNull())
        }
        DATUM_NAME_VORNE.find(text)?.let {
            return datum(it.groupValues[2].toIntOrNull(), MONATE[it.groupValues[1].lowercase().take(3)], it.groupValues[3].toIntOrNull())
        }
        DATUM_ISO.find(text)?.let {
            return datum(it.groupValues[3].toIntOrNull(), it.groupValues[2].toIntOrNull(), it.groupValues[1].toIntOrNull())
        }
        DATUM_ZAHLEN.find(text)?.let {
            return datum(it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull(), it.groupValues[3].toIntOrNull())
        }
        return null
    }

    // Meldestufen: UK jede volle Prozentschwelle ab 3, Nicht-UK nur 5, 10, 15, 20, 25, 30, 50 und 75
    fun schwellen(nichtUk: Boolean): List<Int> =
        if (nichtUk) listOf(5, 10, 15, 20, 25, 30, 50, 75) else (3..100).toList()

    // Schwelle aus vorher und nachher ableiten; eine unten-Angabe zaehlt als Unterschreiten
    fun schwelle(vorher: Stand?, nachher: Stand, nichtUk: Boolean): Schwelle? {
        if (nachher.unter != null) return Schwelle(nachher.unter.roundToInt(), false)
        val stufen = schwellen(nichtUk)
        val kleinste = stufen.first()
        val nullAlsNull = vorher != null && vorher.aktien == null && vorher.instrumente == null &&
            vorher.gesamt == null && vorher.unter != null
        val paare = listOf(nachher.gesamt to vorher?.gesamt, nachher.aktien to vorher?.aktien, nachher.instrumente to vorher?.instrumente)
        for ((x, y) in paare) {
            if (x == null) continue
            val xx = runden(x)
            val yy = when {
                y != null -> runden(y)
                nullAlsNull -> 0.0
                else -> null
            }
            if (yy == null) {
                if (xx >= kleinste) return Schwelle(stufen.last { it <= xx }, true)
                return Schwelle(kleinste, false)
            }
            if (xx > yy) {
                val t = stufen.lastOrNull { it > yy && it <= xx }
                if (t != null) return Schwelle(t, true)
            } else if (xx < yy) {
                val t = stufen.firstOrNull { it > xx && it <= yy }
                if (t != null) return Schwelle(t, false)
            }
        }
        return null
    }

    // Eine Tabellenebene beim Zerlegen
    private class Rahmen {
        val zellen = ArrayList<String>()
        var zelle: StringBuilder? = null
    }

    private val TEMPLATE = Regex("<template\\b[^>]*shadowrootmode=\"open\"[^>]*>(.*?)</template>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BODY = Regex("<body\\b[^>]*>(.*?)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG_MUSTER = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>")
    private val ZAHL = Regex("\\d+(?:[.,]\\d+)*")
    private val KOPF = Regex("^(\\d{1,2})[a-z]?\\.\\s")
    private val DATUM_NAMEN = Regex("(\\d{1,2})(?:st|nd|rd|th)?[ -]([A-Za-z]{3,9})[ -](\\d{4})", RegexOption.IGNORE_CASE)
    private val DATUM_NAME_VORNE = Regex("([A-Za-z]{3,9}) (\\d{1,2})(?:st|nd|rd|th)?,? (\\d{4})", RegexOption.IGNORE_CASE)
    private val PLATZHALTER = Regex("^(n/?a|nil|none|-)$")
    private val DATUM_ISO = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val DATUM_ZAHLEN = Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{4})")
    private val MONATE = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)

    // Bezeichner, hinter denen in der naechsten Zeile kein Wert folgt
    private val BEKANNT = listOf("name", "city", "country", "full name", "isin", "issuer name", "uk or non-uk", "comments")

    // Zahl mit Punkt als Dezimalzeichen; ein einzelnes Komma zaehlt als Dezimalzeichen, mehrere als Trenner
    private fun zahl(wert: String): Double? = when {
        wert.contains('.') -> wert.replace(",", "").toDoubleOrNull()
        wert.count { it == ',' } == 1 -> wert.replace(',', '.').toDoubleOrNull()
        else -> wert.replace(",", "").toDoubleOrNull()
    }

    // Auf vier Nachkommastellen runden, damit Rundungsreste den Vergleich nicht stoeren
    private fun runden(wert: Double): Double = round(wert * 10_000.0) / 10_000.0

    // Erste nicht leere Zelle einer Zeile als Index
    private fun ersteZelle(zeile: List<String>): Int = zeile.indexOfFirst { it.isNotBlank() }

    // Erste nicht leere Zelle einer Zeile, klein und ohne Mehrfach-Leerraum
    private fun bezeichnung(zeile: List<String>): String =
        zeile.firstOrNull { it.isNotBlank() }?.lowercase()?.replace(Regex("\\s+"), " ")?.trim() ?: ""

    // Nummer eines Abschnittskopfs, sonst null
    private fun kopfNummer(zeile: List<String>): Int? =
        KOPF.find(bezeichnung(zeile))?.groupValues?.get(1)?.toIntOrNull()

    // Bereich eines Abschnitts: Kopf bis vor den naechsten Kopf mit hoeherer Nummer
    private fun abschnittBereich(z: List<List<String>>, nr: Int): IntRange? {
        val anfang = z.indices.firstOrNull { kopfNummer(z[it]) == nr } ?: return null
        var ende = z.size
        for (j in anfang + 1 until z.size) {
            val n = kopfNummer(z[j])
            if (n != null && n > nr) {
                ende = j
                break
            }
        }
        return anfang until ende
    }

    // Wert zu einer Bezeichnung: Teil hinter dem Doppelpunkt, erste nicht leere Folge-Zelle oder die
    // naechste Zeile, wenn beide einzellig sind und die Folge-Zeile kein Kopf und keine Bezeichnung ist
    private fun wert(z: List<List<String>>, i: Int, k: Int): String {
        val zelle = z[i].getOrNull(k).orEmpty()
        val doppelpunkt = zelle.indexOf(':')
        if (doppelpunkt >= 0) {
            val teil = zelle.substring(doppelpunkt + 1).trim()
            if (teil.isNotEmpty()) return teil
        }
        for (j in k + 1 until z[i].size) {
            val teil = z[i][j].trim()
            if (teil.isNotEmpty()) return teil
        }
        if (z[i].size == 1 && i + 1 < z.size) {
            val folge = z[i + 1]
            val b = bezeichnung(folge)
            if (folge.size == 1 && kopfNummer(folge) == null && BEKANNT.none { b.startsWith(it) })
                return folge.firstOrNull().orEmpty()
        }
        return ""
    }

    // Abschnitt 3: Name des Meldepflichtigen, ersatzweise der Kopf selbst
    private fun halter(z: List<List<String>>): String {
        val bereich = abschnittBereich(z, 3) ?: return ""
        for (i in bereich.first + 1..bereich.last) {
            val k = ersteZelle(z[i])
            val b = bezeichnung(z[i])
            if (k >= 0 && b.startsWith("name") && !b.startsWith("name of the proxy")) return wert(z, i, k)
        }
        val kopfText = bezeichnung(z[bereich.first])
        return if (KOPF.replace(kopfText, "").startsWith("full name of person"))
            wert(z, bereich.first, maxOf(0, ersteZelle(z[bereich.first]))) else ""
    }

    // Abschnitt 3: Ort und Land, verbunden mit ", "
    private fun sitz(z: List<List<String>>): String {
        val bereich = abschnittBereich(z, 3) ?: return ""
        var ort = ""
        var land = ""
        for (i in bereich.first + 1..bereich.last) {
            val k = ersteZelle(z[i])
            if (k < 0) continue
            val b = bezeichnung(z[i])
            when {
                b.startsWith("city and country") -> return wert(z, i, k)
                b.startsWith("city of registered office") -> ort = wert(z, i, k)
                b.startsWith("country of registered office") -> land = wert(z, i, k)
            }
        }
        return listOf(ort, land).filter { it.isNotEmpty() }.joinToString(", ")
    }

    // Abschnitt 2: Ankreuzzeilen mit X zaehlen, sonst alle einzelligen Zeilen bis zur Comments-Zeile; eine
    // Ankreuztabelle ohne X ergibt keine Gruende. Zuordnung je Semikolon-Stueck
    private fun gruende(z: List<List<String>>): Set<Grund> {
        val bereich = abschnittBereich(z, 2) ?: return emptySet()
        val bisKommentar = (bereich.first + 1..bereich.last).map { z[it] }.takeWhile { !bezeichnung(it).startsWith("comments") }
        val kreuz = { zeile: List<String> -> zeile.any { it.trim().equals("x", true) } }
        val nutzbare = when {
            bisKommentar.any(kreuz) -> bisKommentar.filter(kreuz)
            bisKommentar.any { zeile -> zeile.size > 1 } -> emptyList()
            else -> bisKommentar
        }
        val aus = LinkedHashSet<Grund>()
        for (zeile in nutzbare) {
            for (stueck in zeile.joinToString(" ").lowercase().split(";")) {
                val s = stueck.trim()
                when {
                    s.contains("disposal of voting rights") -> aus.add(Grund.STIMMRECHTE)
                    s.contains("disposal of financial instruments") -> aus.add(Grund.INSTRUMENTE)
                    s.contains("changing the breakdown") -> aus.add(Grund.AUFTEILUNG)
                    s.startsWith("other") -> aus.add(Grund.ANDERES)
                }
            }
        }
        return aus
    }

    // Abschnitt 2: Wert der Comments-Zeile oder Text hinter "Other (please specify):"
    private fun hinweis(z: List<List<String>>): String {
        val bereich = abschnittBereich(z, 2) ?: return ""
        for (i in bereich.first + 1..bereich.last) {
            val k = ersteZelle(z[i])
            if (k >= 0 && bezeichnung(z[i]).startsWith("comments")) return wert(z, i, k)
        }
        for (i in bereich.first + 1..bereich.last) {
            val k = ersteZelle(z[i])
            if (k >= 0 && bezeichnung(z[i]).startsWith("other (please specify)")) {
                val zelle = z[i][k]
                val p = zelle.indexOf(':')
                return if (p >= 0) zelle.substring(p + 1).trim() else ""
            }
        }
        return ""
    }

    // Datum hinter dem Kopf eines Abschnitts (5 = Stichtag, 6 = Meldetag)
    private fun abschnittsDatum(z: List<List<String>>, nr: Int): Long? {
        val i = z.indices.firstOrNull { kopfNummer(z[it]) == nr } ?: return null
        val k = ersteZelle(z[i])
        return if (k >= 0) tag(wert(z, i, k)) else null
    }

    // Abschnitt 7: eine Lage (nachher oder vorher) mit Stimmrechtsanzahl; Spalten ueber die Kopfzeile mit
    // "attached to shares" hoechstens drei Zeilen davor, sonst nach Position
    private fun position(z: List<List<String>>, i: Int): Pair<Stand, Long?> {
        val zeile = z[i]
        val b = ersteZelle(zeile)
        var aktienText: String? = null
        var instrumenteText: String? = null
        var gesamtText: String? = null
        var stimmenText: String? = null
        var kopfZeile: List<String>? = null
        for (j in i - 1 downTo maxOf(0, i - 3)) {
            if (z[j].any { it.lowercase().contains("attached to shares") }) {
                kopfZeile = z[j]
                break
            }
        }
        if (zeile.size > 1 && kopfZeile != null && kopfZeile.size == zeile.size) {
            for (k in kopfZeile.indices) {
                val t = kopfZeile[k].lowercase()
                when {
                    t.contains("attached to shares") -> aktienText = zeile.getOrNull(k)
                    t.contains("financial instruments") -> instrumenteText = zeile.getOrNull(k)
                    t.contains("total of both") -> gesamtText = zeile.getOrNull(k)
                    t.contains("total number") -> stimmenText = zeile.getOrNull(k)
                }
            }
        } else if (zeile.size > 1) {
            aktienText = zeile.getOrNull(b + 1)
            instrumenteText = zeile.getOrNull(b + 2)
            gesamtText = zeile.getOrNull(b + 3)
            stimmenText = zeile.getOrNull(b + 4)
        } else {
            val werte = ArrayList<String>()
            for (j in i + 1 until z.size) {
                if (werte.size >= 4 || z[j].size != 1) break
                val text = z[j][0]
                val b = bezeichnung(z[j])
                // Ein Abschnittskopf oder die naechste Lage beendet die Werte; Platzhalter wie N/A gelten als leer
                if (kopfNummer(z[j]) != null || b.startsWith("resulting situation") || b.startsWith("position of previous")) break
                if (PLATZHALTER.matches(b)) { werte.add(""); continue }
                if (text.isNotEmpty() && text.none { it.isDigit() }) break
                werte.add(text)
            }
            aktienText = werte.getOrNull(0)
            instrumenteText = werte.getOrNull(1)
            gesamtText = werte.getOrNull(2)
            stimmenText = werte.getOrNull(3)
        }
        val aktien = aktienText?.let { prozent(it) }
        val instrumente = instrumenteText?.let { prozent(it) }
        var gesamt = gesamtText?.let { prozent(it) }
        if (gesamt == null && (aktien != null || instrumente != null)) gesamt = (aktien ?: 0.0) + (instrumente ?: 0.0)
        val unter = listOfNotNull(gesamtText, aktienText).firstNotNullOfOrNull { t -> if (prozent(t) == null) unterWert(t) else null }
        val stimmen = stimmenText?.let { anzahl(it) }
        return Pair(Stand(aktien, instrumente, gesamt, unter), stimmen)
    }

    // Abschnitt 1: Nicht-UK-Emittent ueber den Wert der UK-Zeile oder ein angekreuztes Feld
    private fun nichtUk(z: List<List<String>>): Boolean {
        val bereich = abschnittBereich(z, 1) ?: return false
        for (i in bereich) {
            val k = ersteZelle(z[i])
            if (k < 0) continue
            val b = bezeichnung(z[i])
            if (b == "uk or non-uk issuer" && wert(z, i, k).lowercase().contains("non")) return true
            if (b.startsWith("non-uk issuer") && z[i].any { it.trim().equals("x", true) }) return true
        }
        return false
    }
}
