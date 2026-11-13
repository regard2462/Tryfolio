package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender

// Angaben aus dem Prospekt eines Boersengangs (S-1, F-1, 424B4): Anteilseigner vor dem Angebot aus der Tabelle
// „Principal Stockholders“, die Banken aus der Tabelle unter „Underwriting“ und die Sperrfrist der Altaktionaere.
// Reine Funktionen auf dem HTML des Prospekts; was nicht sicher lesbar ist, fehlt im Ergebnis.
object IpoProspekt {

    // anteil in Prozent vor dem Angebot; gesellschaft: Fonds oder Firma, keine Person.
    data class Halter(val name: String, val anteil: Double, val gesellschaft: Boolean)

    // halter absteigend nach Anteil. stimmrechte: Die Tabelle nennt nur den Anteil an den Stimmrechten.
    // gruppe: Vorstand und Aufsichtsrat zusammen in Prozent.
    data class Eigner(val halter: List<Halter>, val stimmrechte: Boolean, val gruppe: Double?)

    // aktien: zugeteilte Stueckzahl laut Tabelle, null im Prospekt vor der Preisfestsetzung.
    data class Bank(val name: String, val aktien: Long?)

    // Anteil der Aktien unter Sperrvereinbarung in Prozent. nachAngebot: bezogen auf alle Aktien nach dem Angebot,
    // sonst auf die Aktien vor dem Angebot (so, wie es der Prospekt nennt).
    data class Gesperrt(val anteil: Double, val nachAngebot: Boolean)

    // Aktien einer Gattung (Class A, Class B) nach dem Angebot; name ist der Buchstabe, stimmen die Stimmen je Aktie.
    data class Gattung(val name: String, val aktien: Long, val stimmen: Int?)

    // Angaben aus dem Abschnitt „The Offering“, jeweils in Aktien. angeboten ohne Mehrzuteilung, danach ohne
    // Mehrzuteilung. neu: Aktien aus einer Kapitalerhoehung (Geld an die Firma), verkauf: Aktien der Altaktionaere.
    // gattungen nur bei mehreren Gattungen. stimmenAnteil: Anteil der angebotenen Aktien an allen Stimmen in Prozent.
    data class Angebot(
        val angeboten: Long?,
        val neu: Long?,
        val verkauf: Long?,
        val mehrzuteilung: Long?,
        val danach: Long?,
        val gattungen: List<Gattung> = emptyList(),
        val stimmenAnteil: Double? = null,
    ) {
        // Frei handelbarer Anteil in Prozent: angebotene Aktien gegen alle Aktien danach
        val frei: Double? get() {
            val a = angeboten ?: return null
            val d = danach ?: return null
            return if (a > 0 && a <= d) a * 100.0 / d else null
        }
    }

    // Eine Stufe einer gestaffelten Freigabe: nach so vielen Tagen werden bis zu anteil Prozent der gesperrten Aktien frei.
    data class Stufe(val tage: Int, val anteil: Double)

    data class Auszug(
        val eigner: Eigner?, val banken: List<Bank>, val sperrfristTage: Int?, val gesperrt: Gesperrt? = null, val angebot: Angebot? = null,
        val stufen: List<Stufe> = emptyList(),
    ) {
        val leer get() = eigner == null && banken.isEmpty() && sperrfristTage == null && angebot == null && stufen.isEmpty()
    }

    // So viele Zeilen stehen offen, der Rest ist eingeklappt.
    const val HALTER_OFFEN = 5
    const val BANKEN_OFFEN = 3

    private val ENDGUELTIG = setOf("424B4", "424B1")
    private val VORLAEUFIG = setOf("S-1/A", "F-1/A", "S-1", "F-1")

    // Prospekt, aus dem gelesen wird: der erste endgueltige (der des Boersengangs, spaetere gehoeren zu Folgeangeboten),
    // sonst der neueste eingereichte. Nur HTML-Dokumente.
    fun wahl(doks: List<IpoKalender.Dokument>): IpoKalender.Dokument? {
        val html = doks.filter { it.link.endsWith(".htm", true) || it.link.endsWith(".html", true) }
        return html.filter { it.formular in ENDGUELTIG }.minByOrNull { it.tag }
            ?: html.filter { it.formular in VORLAEUFIG }.maxByOrNull { it.tag }
    }

    fun lesen(html: String): Auszug = Auszug(eigner(html), banken(html), sperrfrist(html), gesperrt(html), angebot(html), sperrstufen(html))

    // ------------------------------------------------------------ Tabellen

    // Leerraum im rohen HTML
    private const val L = "(?:\\s|&nbsp;|&#160;|&#xa0;|&#xA0;)+"
    private const val L0 = "(?:\\s|&nbsp;|&#160;|&#xa0;|&#xA0;)*"

    private class Zelle(val spalte: Int, val breite: Int, val text: String)

    private val ZEILE = Regex("(?is)<tr\\b.*?</tr>")
    private val ZELLE = Regex("(?is)<t[dh]\\b([^>]*)>(.*?)</t[dh]>")
    private val COLSPAN = Regex("(?i)colspan\\s*=\\s*[\"']?(\\d+)")
    private val TAG = Regex("(?s)<[^>]*>")
    private val RAUM = Regex("\\s+")

    private fun klartext(html: String): String =
        Zahlenmitteilung.entitaeten(html.replace(TAG, " ")).replace('\u00a0', ' ').replace(RAUM, " ").trim()

    // Zeilen einer Tabelle mit der Spalte jeder Zelle (colspan zaehlt mit).
    private fun gitter(tabelle: String): List<List<Zelle>> = ZEILE.findAll(tabelle).map { z ->
        var spalte = 0
        ZELLE.findAll(z.value).map { m ->
            val breite = COLSPAN.find(m.groupValues[1])?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 100) ?: 1
            Zelle(spalte, breite, klartext(m.groupValues[2])).also { spalte += breite }
        }.toList()
    }.toList()

    // Die Tabelle, in der die Stelle liegt; null ausserhalb einer Tabelle.
    private fun tabelleUm(html: String, stelle: Int): String? {
        val von = html.lastIndexOf("<table", stelle, ignoreCase = true)
        if (von < 0) return null
        val zuVorher = html.lastIndexOf("</table", stelle, ignoreCase = true)
        if (zuVorher > von) return null
        val bis = html.indexOf("</table", stelle, ignoreCase = true)
        if (bis < 0 || bis - von > 1_500_000) return null
        return html.substring(von, bis)
    }

    // ------------------------------------------------------------ Anteilseigner

    private enum class Art { ZAHL, PROZENT, DEZIMAL, STERN, STRICH }
    private class Wert(val spalte: Int, var art: Art, val zahl: Double, val text: String)

    private val GANZ = Regex("^\\d{1,3}(,\\d{3})+$|^\\d+$")
    private val DEZIMAL = Regex("^\\d+\\.\\d+$")
    private val PROZENT = Regex("^(\\d+(?:\\.\\d+)?)\\s*%$")
    private val STERN = Regex("^\\*+\\s*%?$|^<\\s*1(\\.0)?\\s*%?$|^(?i:less than (1|one)\\s*%?)$")
    private val STRICH = Regex("^[-\u2010\u2011\u2012\u2013\u2014\u2212\uFFFD]+\\s*%?$")

    // Fussnotenzeichen hinter einem Wert („1,415,826,462 (2)“, „9.63%(3)“, „*(4)“); die Fussnote allein bleibt stehen
    private val FUSS_WERT = Regex("(?<=[\\d%*])\\s*(?:\\(\\d{1,3}\\)|\\([a-z]\\)|[†‡§])+$")

    // Werte einer Zeile hinter dem Namen; ein „%“ in eigener Zelle gehoert zur Zahl davor.
    private fun werte(zeile: List<Zelle>): List<Wert> {
        val aus = ArrayList<Wert>()
        for (z in zeile) {
            val t = FUSS_WERT.replace(z.text.removePrefix("$").trim(), "").trim()
            if (t.isEmpty()) continue
            if (t == "%") {
                aus.lastOrNull()?.takeIf { it.art == Art.ZAHL || it.art == Art.DEZIMAL }?.let { it.art = Art.PROZENT }
                continue
            }
            val p = PROZENT.matchEntire(t)
            when {
                p != null -> aus += Wert(z.spalte, Art.PROZENT, p.groupValues[1].toDoubleOrNull() ?: continue, t)
                GANZ.matches(t) -> aus += Wert(z.spalte, Art.ZAHL, t.replace(",", "").toDoubleOrNull() ?: continue, t)
                DEZIMAL.matches(t) -> aus += Wert(z.spalte, Art.DEZIMAL, t.toDoubleOrNull() ?: continue, t)
                STERN.matches(t) -> aus += Wert(z.spalte, Art.STERN, 0.0, t)
                STRICH.matches(t) -> aus += Wert(z.spalte, Art.STRICH, 0.0, t)
            }
        }
        return aus
    }

    private val FUSSNOTE = Regex("\\s*\\(\\s*\\d{1,3}\\s*\\)|[\u2020\u2021\u00a7*]+$")
    private val VERBUND = Regex("(?i)^(?:(?:entities|funds|persons|investment funds|funds and accounts|accounts)\\s+(?:affiliated|associated)\\s+with|funds\\s+(?:managed|advised)\\s+by)\\s+")
    private val FIRMA = Regex(
        "(?i)\\b(l\\.?p\\.?|l\\.?l\\.?c\\.?|llp|inc\\.?|incorporated|corp\\.?|corporation|ltd\\.?|limited|plc|gmbh|ag|s\\.?a\\.?|n\\.?v\\.?|b\\.?v\\.?|a/s|" +
            "fund|funds|capital|ventures?|partners|holdings?|trust|management|investments?|authority|company|co\\.|group|foundation|bank|advisors|associates|university)\\b",
    )
    private val KEIN_HALTER = Regex("(?i)^(total|shares\\s|all\\s+other|other\\s+selling|selling\\s+(stock|share)holders)|as\\s+a\\s+group")
    private val NUR_FUSSNOTE = Regex("^(\\(\\d{1,3}\\))+$")
    private val GRUPPE_ZEILE = Regex("(?i)as\\s+a\\s+group")

    private fun halterName(roh: String): Pair<String, Boolean> {
        var s = roh
        while (true) {
            val t = s.replace(FUSSNOTE, "").trim().trimEnd(':', ',', ' ')
            if (t == s) break
            s = t
        }
        val ohne = s.replace(VERBUND, "")
        return ohne to (ohne != s || FIRMA.containsMatchIn(ohne))
    }

    // Spaltenbereich der Prozentangabe vor dem Angebot: aus den Kopfzeilen. Bevorzugt der Anteil an allen Aktien,
    // bei mehreren Gattungen sonst die Stimmrechte. null, wenn die Kopfzeilen nichts hergeben.
    private class Ziel(val von: Int, val bis: Int, val stimmrechte: Boolean)

    private fun ziel(kopf: List<List<Zelle>>, daten: List<List<Wert>>): Ziel? {
        fun kopfText(spalte: Int) = kopf.joinToString(" ") { z -> z.firstOrNull { spalte >= it.spalte && spalte < it.spalte + it.breite }?.text.orEmpty() }.lowercase()
        // unterste beschriftete Kopfzelle ueber der Spalte
        fun kopfZelle(spalte: Int): Zelle? = kopf.asReversed().firstNotNullOfOrNull { z -> z.firstOrNull { spalte >= it.spalte && spalte < it.spalte + it.breite && it.text.isNotBlank() } }
        val prozentSpalten = daten.flatMap { z -> z.filter { it.art == Art.PROZENT }.map { it.spalte } }.distinct().sorted()
        if (prozentSpalten.isEmpty()) return null
        val nachher = Regex("\\bafter\\b|\\boffered\\b|being sold|to be sold|following (this|the) offering")
        val grenze = prozentSpalten.firstOrNull { nachher.containsMatchIn(kopfText(it)) } ?: Int.MAX_VALUE
        // Bereiche: je Kopfzelle einer, Spalten ohne Kopfzelle einzeln
        val bereiche = LinkedHashMap<Pair<Int, Int>, String>()
        for (s in prozentSpalten) {
            if (s >= grenze) break
            val k = kopfZelle(s)
            val b = if (k == null) s to s else k.spalte to k.spalte + k.breite - 1
            bereiche.getOrPut(b) { kopfText(s) }
        }
        if (bereiche.isEmpty()) return null
        val liste = bereiche.entries.toList()
        val gesamt = liste.firstOrNull { it.value.contains("total") && !it.value.contains("voting") && !it.value.contains("class") }
        val stimmen = liste.firstOrNull { it.value.contains("voting") }
        val wahl = gesamt ?: liste.singleOrNull() ?: stimmen ?: liste.first()
        return Ziel(wahl.key.first, wahl.key.second, wahl.value.contains("voting"))
    }

    private fun eignerAus(tabelle: String): Eigner? {
        val zeilen = gitter(tabelle)
        class Daten(val name: String, val werte: List<Wert>)
        val kopf = ArrayList<List<Zelle>>()
        val daten = ArrayList<Daten>()
        for (z in zeilen) {
            val erste = z.firstOrNull { it.text.isNotBlank() } ?: continue
            val rest = z.filter { it.spalte > erste.spalte }
            val w = werte(rest)
            val gefuellt = rest.count { it.text.isNotBlank() && it.text != "%" && it.text != "$" && !NUR_FUSSNOTE.matches(it.text) }
            val istDaten = erste.text.any { it.isLetter() } && w.isNotEmpty() && w.size == gefuellt
            // Zwischenzeilen („5% Stockholders:“) mit nur einer Zelle am Zeilenanfang sind keine Kopfzeilen
            if (istDaten) daten += Daten(erste.text, w) else if (daten.isEmpty() && !(gefuellt == 0 && erste.spalte == 0)) kopf += z
        }
        if (daten.size < 2) return null
        val ziel = ziel(kopf, daten.map { it.werte })
        // Prozent vor dem Angebot je Zeile; ohne brauchbare Kopfzeilen die erste Prozentangabe
        fun anteil(w: List<Wert>): Double? {
            val v = if (ziel != null) w.firstOrNull { it.spalte >= ziel.von && it.spalte <= ziel.bis && it.art != Art.ZAHL }
            else w.firstOrNull { it.art == Art.PROZENT || it.art == Art.STERN }
            return v?.takeIf { it.art == Art.PROZENT || it.art == Art.DEZIMAL }?.zahl?.takeIf { it > 0 && it <= 100.0 }
        }
        class Roh(val halter: Halter, val aktien: String?)
        val roh = daten.filter { !KEIN_HALTER.containsMatchIn(it.name) }.mapNotNull { d ->
            val a = anteil(d.werte) ?: return@mapNotNull null
            val (name, firma) = halterName(d.name)
            if (name.length < 2) return@mapNotNull null
            Roh(Halter(name, a, firma), d.werte.firstOrNull { it.art == Art.ZAHL }?.text)
        }
        // Ein Aufsichtsrat, dem die Aktien seines Fonds zugerechnet werden, steht mit derselben Stueckzahl noch einmal da
        val firmenAktien = roh.filter { it.halter.gesellschaft && it.aktien != null }.map { it.aktien to it.halter.anteil }.toSet()
        val halter = roh.filter { it.halter.gesellschaft || (it.aktien to it.halter.anteil) !in firmenAktien }
            .map { it.halter }.distinctBy { it.name.lowercase() }.sortedWith(compareByDescending<Halter> { it.anteil }.thenBy { it.name })
        if (halter.isEmpty()) return null
        val gruppe = daten.firstOrNull { GRUPPE_ZEILE.containsMatchIn(it.name) }?.let { anteil(it.werte) }
        return Eigner(halter, ziel?.stimmrechte == true, gruppe)
    }

    private val GRUPPE = Regex("(?i)as${L}a${L}group")
    private val UEBERSCHRIFT = Regex("(?i)principal$L(?:and${L}selling$L)?(?:stock|share)holders|security${L}ownership${L}of${L}certain${L}beneficial${L}owners")
    private val WIRTSCHAFTLICH = Regex("(?i)benefici")

    // Tabelle der wirtschaftlich Berechtigten: die mit der Zeile „as a group“, sonst die erste lesbare hinter der
    // Ueberschrift des Abschnitts.
    fun eigner(html: String): Eigner? {
        for (m in GRUPPE.findAll(html)) {
            val t = tabelleUm(html, m.range.first) ?: continue
            val davor = html.substring(maxOf(0, m.range.first - t.length - 20_000), m.range.first)
            if (!WIRTSCHAFTLICH.containsMatchIn(davor)) continue
            eignerAus(t)?.let { return it }
        }
        for (m in UEBERSCHRIFT.findAll(html).toList().asReversed()) {
            var ab = m.range.last
            val ende = minOf(html.length, ab + 300_000)
            repeat(6) {
                val von = html.indexOf("<table", ab, ignoreCase = true)
                if (von < 0 || von >= ende) return@repeat
                val bis = html.indexOf("</table", von, ignoreCase = true)
                if (bis < 0) return@repeat
                val t = html.substring(von, bis)
                if (WIRTSCHAFTLICH.containsMatchIn(t)) eignerAus(t)?.takeIf { it.halter.size >= 2 }?.let { return it }
                ab = bis + 1
            }
        }
        return null
    }

    // ------------------------------------------------------------ Anteilseigner mit allen Prozentspalten

    // Eine Zeile der Tabelle mit jeder Prozentangabe und dem Kopftext ihrer Spalte (kleingeschrieben, etwa
    // "class a shares", "% of total voting power"). gruppe: die Zeile "as a group".
    class SpaltenZeile(val name: String, val gesellschaft: Boolean, val gruppe: Boolean, val spalten: List<Pair<String, Double>>)

    private fun spaltenAus(tabelle: String): List<SpaltenZeile>? {
        val zeilen = gitter(tabelle)
        class Daten(val name: String, val werte: List<Wert>)
        val kopf = ArrayList<List<Zelle>>()
        val daten = ArrayList<Daten>()
        for (z in zeilen) {
            val erste = z.firstOrNull { it.text.isNotBlank() } ?: continue
            val rest = z.filter { it.spalte > erste.spalte }
            val w = werte(rest)
            val gefuellt = rest.count { it.text.isNotBlank() && it.text != "%" && it.text != "$" && !NUR_FUSSNOTE.matches(it.text) }
            val istDaten = erste.text.any { it.isLetter() } && w.isNotEmpty() && w.size == gefuellt
            if (istDaten) daten += Daten(erste.text, w) else if (daten.isEmpty() && !(gefuellt == 0 && erste.spalte == 0)) kopf += z
        }
        if (daten.size < 2) return null
        fun kopfText(spalte: Int) = kopf.joinToString(" ") { z -> z.firstOrNull { spalte >= it.spalte && spalte < it.spalte + it.breite }?.text.orEmpty() }.lowercase()
        val aus = daten.mapNotNull { d ->
            val gruppe = GRUPPE_ZEILE.containsMatchIn(d.name)
            if (!gruppe && KEIN_HALTER.containsMatchIn(d.name)) return@mapNotNull null
            val (name, firma) = halterName(d.name)
            if (!gruppe && name.length < 2) return@mapNotNull null
            val sp = d.werte.filter { (it.art == Art.PROZENT || it.art == Art.DEZIMAL) && it.zahl > 0 && it.zahl <= 100.0 }.map { kopfText(it.spalte) to it.zahl }
            if (sp.isEmpty()) null else SpaltenZeile(if (gruppe) "" else name, firma, gruppe, sp)
        }
        return aus.takeIf { it.any { z -> !z.gruppe } }
    }

    // Wie eigner, aber mit allen Prozentspalten je Zeile (Mehrklassentabellen: Anteil je Klasse und an den Stimmen)
    fun eignerSpalten(html: String): List<SpaltenZeile>? {
        for (m in GRUPPE.findAll(html)) {
            val t = tabelleUm(html, m.range.first) ?: continue
            val davor = html.substring(maxOf(0, m.range.first - t.length - 20_000), m.range.first)
            if (!WIRTSCHAFTLICH.containsMatchIn(davor)) continue
            spaltenAus(t)?.let { return it }
        }
        for (m in UEBERSCHRIFT.findAll(html).toList().asReversed()) {
            var ab = m.range.last
            val ende = minOf(html.length, ab + 300_000)
            repeat(6) {
                val von = html.indexOf("<table", ab, ignoreCase = true)
                if (von < 0 || von >= ende) return@repeat
                val bis = html.indexOf("</table", von, ignoreCase = true)
                if (bis < 0) return@repeat
                val t = html.substring(von, bis)
                if (WIRTSCHAFTLICH.containsMatchIn(t)) spaltenAus(t)?.takeIf { it.size >= 2 }?.let { return it }
                ab = bis + 1
            }
        }
        return null
    }

    // ------------------------------------------------------------ Banken

    // Kopfzelle „Underwriter“ in der Tabelle oder die Ueberschrift „Underwriting“ kurz vor ihr
    private val BANK_KOPF = Regex("(?i)>$L0(?:(?:Name${L}of$L)?Underwriters?|Underwriting(?:$L\\([^)<]{0,60}\\))?)$L0<")
    private val BANK_SPALTE = Regex("(?i)^(name|(name of )?underwriters?)$")
    private val BANK_MENGE = Regex("(?i)number|shares|amount|units|ADSs")

    // Die erste Tabelle, die hoechstens weite Zeichen hinter der Stelle beginnt.
    private fun tabelleNach(html: String, stelle: Int, weite: Int): String? {
        val von = html.indexOf("<table", stelle, ignoreCase = true)
        if (von < 0 || von - stelle > weite) return null
        val bis = html.indexOf("</table", von, ignoreCase = true)
        if (bis < 0 || bis - von > 1_500_000) return null
        return html.substring(von, bis)
    }

    private fun bankenAus(tabelle: String): List<Bank>? {
        val aus = ArrayList<Bank>()
        var kopf = false
        var summe = false
        for (z in gitter(tabelle)) {
            val erste = z.firstOrNull { it.text.isNotBlank() } ?: continue
            val name = erste.text
            if (!kopf) {
                val spalte = name.trimEnd(':', ' ')
                kopf = BANK_SPALTE.matches(spalte) && (spalte.contains("underwriter", true) || z.any { it !== erste && BANK_MENGE.containsMatchIn(it.text) })
                if (!kopf) return null
                continue
            }
            if (name.startsWith("Total", true)) { summe = true; break }
            if (!name.any { it.isLetter() }) continue
            val aktien = werte(z.filter { it.spalte > erste.spalte }).firstOrNull { it.art == Art.ZAHL }?.zahl?.toLong()?.takeIf { it > 0 }
            aus += Bank(halterName(name).first, aktien)
        }
        return aus.distinctBy { it.name.lowercase() }.takeIf { kopf && summe && it.size in 1..40 }
    }

    // Tabelle unter „Underwriting“: Kopf „Underwriter“ oder „Name“, je Bank eine Zeile, am Ende „Total“. Reihenfolge
    // wie im Prospekt (der Konsortialfuehrer steht oben).
    fun banken(html: String): List<Bank> {
        for (m in BANK_KOPF.findAll(html)) {
            val t = tabelleUm(html, m.range.first) ?: tabelleNach(html, m.range.last, 30_000) ?: continue
            bankenAus(t)?.let { return it }
        }
        return emptyList()
    }

    // ------------------------------------------------------------ Sperrfrist

    private val FRIST = Regex("(?i)(\\d{2,3})${L}days$L(?:after|following|from)${L}the${L}date${L}of${L}this$L(?:final$L)?prospectus")
    private val SPERRE = Regex("(?i)lock-?\\s?up|market\\s+stand-?\\s?off|not to (offer|sell|transfer|dispose)|may not|will not")
    private val ANDERE_FRIST = Regex("(?i)rule\\s+144|rule\\s+701|over-?allotment|exercisable|option to purchase")
    private val VORBEHALT = Regex("(?i)earlier of|earliest of|later of|earlier to occur")
    // Teilfreigaben: Anteil („up to 20%“), Termin „on or after the date that is“, Kursschwelle oder Zahlen als Bedingung
    private val STAFFEL = Regex(
        "(?i)up\\s+to\\s+\\d{1,3}(?:\\.\\d+)?\\s?%|on\\s+or\\s+after\\s+the\\s+date\\s+that\\s+is|(?:closing|last\\s+reported)\\s+(?:sale\\s+)?price|earnings|" +
            "tranche|installment|staggered|early\\s+release|partial(?:ly)?\\s+releas",
    )
    private val STAFFEL_ANTEIL = Regex("(?i)up\\s+to\\s+(\\d{1,3}(?:\\.\\d+)?)\\s?%")

    // Fristen aus allen Stellen „N days after the date of this prospectus“ mit Bezug zur Sperrfrist: einfache (feste
    // Zahl), gestaffelte (Teilfreigabe oder Bedingung, anteil null ohne Prozentangabe) und ob eine Bedingung
    // („earlier of“) den Wert unsicher macht.
    private class Fristen(val einfach: List<Int>, val gestaffelt: List<Pair<Int, Double?>>, val unsicher: Boolean)

    private fun fristenLesen(html: String): Fristen {
        val einfach = ArrayList<Int>()
        val staffel = ArrayList<Pair<Int, Double?>>()
        for (m in FRIST.findAll(html)) {
            val davor = klartext(html.substring(maxOf(0, m.range.first - 2_500), m.range.first)).takeLast(350)
            val danach = klartext(html.substring(m.range.last + 1, minOf(html.length, m.range.last + 800))).take(220)
            // Mehrzuteilung und die Fristen der Regeln 144 und 701 sind keine Sperrfrist
            if (ANDERE_FRIST.containsMatchIn(davor.takeLast(150) + " " + danach.take(60))) continue
            if (!SPERRE.containsMatchIn(davor + " " + danach)) continue
            if (VORBEHALT.containsMatchIn(davor.takeLast(300))) return Fristen(emptyList(), emptyList(), true)
            val tage = m.groupValues[1].toIntOrNull() ?: continue
            // Nur der Satz der Fundstelle: Die Teilfreigabe im Nachbarsatz macht diese Frist nicht zur Staffel
            val fenster = davor.takeLast(300).substringAfterLast(". ") + " " + danach.substringBefore(". ")
            if (STAFFEL.containsMatchIn(fenster)) staffel += tage to STAFFEL_ANTEIL.find(fenster)?.groupValues?.get(1)?.toDoubleOrNull()
            else einfach += tage
        }
        return Fristen(einfach, staffel, false)
    }

    // Tage der Sperrfrist (Lock-up oder Market Standoff) ab dem Datum des Prospekts. Nur wenn alle Stellen mit Bezug
    // zur Sperrfrist dieselbe feste Zahl nennen; eine Frist mit Bedingung („earlier of“) oder mit Teilfreigaben gilt
    // als nicht sicher lesbar (dann gibt es hoechstens sperrstufen).
    fun sperrfrist(html: String): Int? {
        val f = fristenLesen(html)
        if (f.unsicher || f.gestaffelt.isNotEmpty()) return null
        val t = f.einfach.distinct().singleOrNull() ?: return null
        return t.takeIf { it in 30..540 }
    }

    // Gestaffelte Freigabe als Zeitpunkte mit Anteil: nur wenn mindestens zwei verschiedene Termine je einen
    // Anteil nennen und keine Stelle eine Bedingung oder einen Termin ohne Anteil hat. Sonst leer.
    fun sperrstufen(html: String): List<Stufe> {
        val f = fristenLesen(html)
        if (f.unsicher || f.gestaffelt.isEmpty() || f.gestaffelt.any { it.second == null || it.second!! <= 0.0 || it.second!! > 100.0 }) return emptyList()
        val stufen = f.gestaffelt.map { Stufe(it.first, it.second!!) }.distinct().sortedBy { it.tage }
        if (stufen.map { it.tage }.distinct().size != stufen.size || stufen.size < 2 || stufen.any { it.tage !in 30..540 }) return emptyList()
        return stufen
    }

    // ------------------------------------------------------------ Gesperrte Aktien

    private val SPERRWORT = Regex("(?i)lock-?\\s?up|market\\s+stand-?\\s?off")
    private const val STUECK = "(\\d{1,3}(?:,\\d{3})+)"
    private const val GATTUNG = "(?:shares\\s+of\\s+(?:our\\s+)?(?:Class\\s+A\\s+)?(?:common\\s+stock|ordinary\\s+shares|common\\s+shares)|(?:ordinary|common)\\s+shares)"

    // „holders of approximately 97% of our outstanding shares“ mit Sperrvereinbarung im selben Satz. Erst ab 50 %:
    // kleinere Zahlen beschreiben meist die Gruppe („holders of more than 5%“), nicht den gesperrten Anteil.
    private val HALTER_ANTEIL = Regex(
        "(?i)(?:holders|owners)\\s+of\\s+(?:approximately\\s+|more\\s+than\\s+|over\\s+|at\\s+least\\s+)?(\\d{2,3}(?:\\.\\d+)?)\\s?%\\s+of\\s+(?:our|the)\\s+(?:[A-Za-z,-]+\\s+){0,8}?" +
            "(?:shares|stock|securities)",
    )

    // Aktien nach dem Angebot: „we will have 100,000,000 shares of common stock outstanding“
    private val AUSSTEHEND = listOf(
        Regex("(?i)$STUECK\\s+$GATTUNG[^.;]{0,60}?\\s(?:will\\s+be\\s+)?outstanding"),
        Regex("(?i)will\\s+have\\s+(?:outstanding\\s+)?(?:an\\s+aggregate\\s+of\\s+|a\\s+total\\s+of\\s+)?(?:approximately\\s+)?$STUECK\\s+(?:outstanding\\s+)?$GATTUNG"),
    )

    // „80,000,000 shares … are subject to lock-up agreements“ innerhalb eines Satzes
    private val STUECK_GESPERRT = Regex("(?i)$STUECK\\s+(?:additional\\s+|remaining\\s+)?shares[^.;]{0,200}?(?:subject\\s+to|covered\\s+by)\\s[^.;]{0,60}?lock-?\\s?up")

    private val FREI = Regex("(?i)freely|sold\\s+in\\s+this\\s+offering|except|\\bnot\\b")
    private val MEHR_GATTUNGEN = Regex("(?i)class\\s+[B-Z]\\b")
    private val BEDINGT = Regex("(?i)option|warrant|issuable|reserved|convertible")
    private val ABSCHNITT_VERKAUF = Regex("(?i)shares${L}eligible${L}for${L}future${L}sales?")

    private fun stueck(s: String): Double? = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    // Aus einem Textstueck des Abschnitts „Shares Eligible for Future Sale“; null ohne eindeutige Angabe.
    private fun gesperrtAus(text: String): Gesperrt? {
        val anteile = HALTER_ANTEIL.findAll(text).mapNotNull { m ->
            val satz = text.substring(m.range.first, minOf(text.length, m.range.last + 500)).substringBefore(". ")
            val davor = text.substring(maxOf(0, m.range.first - 300), m.range.first).substringAfterLast(". ")
            if (!SPERRWORT.containsMatchIn(davor + " " + satz)) return@mapNotNull null
            m.groupValues[1].toDoubleOrNull()?.takeIf { it >= 50.0 && it <= 100.0 }
        }.distinct().toList()
        if (anteile.size > 1) return null
        anteile.singleOrNull()?.let { return Gesperrt(it, nachAngebot = false) }
        // „20,000,000 shares sold in this offering will be freely tradable, except … subject to lock-up“ zaehlt nicht
        val gesperrt = STUECK_GESPERRT.findAll(text).filter { !FREI.containsMatchIn(it.value) }.mapNotNull { stueck(it.groupValues[1]) }.distinct().toList().singleOrNull() ?: return null
        val alle = AUSSTEHEND.flatMap { r ->
            r.findAll(text).mapNotNull { m ->
                // Mehrere Gattungen im selben Satz: die Summe steht nicht da
                val satz = text.substring(m.range.first, minOf(text.length, m.range.last + 200)).substringBefore(". ")
                if (MEHR_GATTUNGEN.containsMatchIn(satz) || BEDINGT.containsMatchIn(m.value)) null else stueck(m.groupValues[1])
            }.toList()
        }.distinct().singleOrNull() ?: return null
        val anteil = gesperrt / alle * 100
        return if (anteil >= 20.0 && anteil <= 100.0) Gesperrt(anteil, nachAngebot = true) else null
    }

    // Anteil der Aktien unter Sperrvereinbarung, soweit der Abschnitt „Shares Eligible for Future Sale“ ihn nennt:
    // als Prozentsatz der Halter oder als Stueckzahl gegen die Aktien nach dem Angebot. Nennen mehrere Stellen
    // Verschiedenes, gilt die Angabe als nicht sicher lesbar.
    fun gesperrt(html: String): Gesperrt? {
        val funde = ABSCHNITT_VERKAUF.findAll(html).mapNotNull { m ->
            gesperrtAus(klartext(html.substring(m.range.last + 1, minOf(html.length, m.range.last + 40_000))).take(12_000))
        }.distinct().toList()
        return funde.singleOrNull()
    }

    // ------------------------------------------------------------ Angebot („The Offering“)

    // Stueckzahl: „10,000,000“, „10000000“ oder „5.0 million shares“; nie hinter einem Dollarzeichen („$ 150.0 million“)
    private const val ZAHL = "(?<![\\d\$.,])(?<!\\\$\\s)(?:\\d{1,3}(?:,\\d{3})+|\\d{6,}|\\d+(?:\\.\\d+)?\\s?million(?=\\s+(?:shares|ordinary|common|class|additional|ads)))(?!\\d)"
    private val ZAHL_RE = Regex(ZAHL)
    private val UP_TO = Regex("(?i)up\\s+to\\s+($ZAHL)")
    private val KLASSE = Regex("[Cc]lass\\s+([A-Z])\\b")
    private val KLASSE_ZAHL = Regex("($ZAHL)\\s+(?:[Aa]dditional\\s+)?[Ss]hares\\s+of\\s+(?:our\\s+)?[Cc]lass\\s+([A-Z])\\b")
    private val STIMMEN = Regex(
        "[Cc]lass\\s+([A-Z])\\b[^.;]{0,120}?\\b(one|two|three|four|five|ten|twenty|fifty|\\d{1,3})\\s+votes?\\s+per\\s+share", RegexOption.IGNORE_CASE,
    )
    private val STIMME_WORT = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "ten" to 10, "twenty" to 20, "fifty" to 50)
    private val FUSSNOTE_ANGEBOT = Regex("\\(\\d{1,2}\\)|\\*+")

    private val OFFERING_KOPF = Regex("(?i)>$L0(?:Summary${L}of${L}the${L}|The${L})Offering$L0<")
    private val ANGEBOT_ZEILE = Regex("(?i)offered${L}by$L(?:us|the${L}company|the${L}selling)")
    private val ZEILENENDE = Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>")

    private val NACH = Regex("\\b(?:after|following|upon)\\b")
    private val MEHRZUTEILUNG = Regex("(?:option|over-?allotment).*(?:additional|underwriters)|underwriters.{0,40}option|additional\\s+shares.*option")
    private val FREMD = Regex("proceeds|use of|directed\\s+share|reserved|dividend|lock-?up|symbol|listing|risk factors")
    private val ANGEBOTEN_VON = Regex("offered\\s+by\\b")
    private val ANGEBOTEN_BEIDE = Regex("by\\s+(?:us|the\\s+company)\\s+and\\s+(?:the\\s+)?selling")
    private val ADS = Regex("\\bads\\b|\\badss\\b|american\\s+depositary")

    private val KLEIN = Regex("(?<![\\d,.\$])(\\d{1,5})\\s+shares?\\b")
    private val KEINE = Regex("(?i)\\b(?:no|zero)\\s+shares?\\b|\\bnone\\b")
    private val FOLGE_ENDE = Regex("(?i)summary\\s+(?:consolidated|historical|financial|selected)|risk\\s+factors|use\\s+of\\s+proceeds|capitalization|dilution")

    private fun kleineZahl(voll: String): Long? =
        KLEIN.find(voll)?.groupValues?.get(1)?.toLongOrNull() ?: if (KEINE.containsMatchIn(voll)) 0L else null

    // Tabelle an der Stelle (oder die naechste hoechstens weite Zeichen dahinter) samt den Folgetabellen: Ein Seitenumbruch
    // teilt „The Offering“ oft in zwei Tabellen. Eine Folgetabelle gehoert dazu, wenn dazwischen nur kurzer Text
    // (Seitenzahl, „Table of Contents“) und keine neue Ueberschrift steht; hoechstens vier Tabellen und 120.000 Zeichen.
    private fun tabellenFolge(html: String, stelle: Int, weite: Int): List<String> {
        var von = html.lastIndexOf("<table", stelle, ignoreCase = true)
        if (von >= 0 && html.lastIndexOf("</table", stelle, ignoreCase = true) > von) von = -1
        if (von < 0) {
            von = html.indexOf("<table", stelle, ignoreCase = true)
            if (von < 0 || von - stelle > weite) return emptyList()
        }
        val aus = ArrayList<String>()
        var summe = 0
        while (aus.size < 4) {
            val bis = html.indexOf("</table", von, ignoreCase = true)
            if (bis < 0 || bis - von > 1_500_000) break
            aus += html.substring(von, bis)
            summe += bis - von
            val naechste = html.indexOf("<table", bis, ignoreCase = true)
            if (naechste < 0 || summe > 120_000) break
            val dazwischen = klartext(html.substring(bis, naechste))
            if (dazwischen.length > 300 || FOLGE_ENDE.containsMatchIn(dazwischen)) break
            von = naechste
        }
        return aus
    }

    private class AngebotZeile(val label: String, val orig: String, val voll: String, val zahlen: List<Long>)

    private fun zahlWert(s: String): Long? {
        val n = if (s.contains("million")) s.substringBefore("million").trim().toDoubleOrNull()?.let { Math.round(it * 1e6) } else s.replace(",", "").toLongOrNull()
        return n?.takeIf { it in 1..100_000_000_000L }
    }

    // Eine Zeile aus Bezeichnung und Wert; die Bezeichnung endet vor der ersten Stueckzahl.
    private fun angebotZeile(flat: String): AngebotZeile? {
        val t = flat.replace(FUSSNOTE_ANGEBOT, " ").replace(RAUM, " ").trim()
        if (t.isEmpty()) return null
        val erste = ZAHL_RE.find(t)
        val label = (if (erste != null) t.substring(0, erste.range.first) else t).trim()
        return AngebotZeile(label.lowercase(), label, t, ZAHL_RE.findAll(t).mapNotNull { zahlWert(it.value) }.toList())
    }

    private fun zeilenAusTabelle(t: String): List<String> =
        gitter(t).map { z -> z.map { it.text }.filter { it.isNotBlank() }.joinToString(" ") }.filter { it.isNotBlank() }

    private fun zeilenAusText(html: String): List<String> = ZEILENENDE.split(html).map { klartext(it) }.filter { it.isNotBlank() && it.length < 3_000 }

    private fun angebotAus(zeilen: List<String>): Angebot? {
        var gesamt: Long? = null
        var neu: Long? = null
        var verkauf: Long? = null
        var mehr: Long? = null
        var danachTotal: Long? = null
        var danachGenerisch: Long? = null
        var firmaErwaehnt = false
        var verkaufErwaehnt = false
        val klassen = LinkedHashMap<String, Long>()
        val angebotKlassen = LinkedHashSet<String>()
        val stimmen = LinkedHashMap<String, Int>()
        zeilen@ for (flat in zeilen) {
            val z = angebotZeile(flat) ?: continue
            // Stimmen je Aktie stehen in der Zeile „Voting rights“ oder im Satz zur Gattung
            if (z.voll.length < 2_000 && z.voll.contains("vote", ignoreCase = true)) {
                for (m in STIMMEN.findAll(z.voll)) {
                    val w = m.groupValues[2].lowercase()
                    val n = STIMME_WORT[w] ?: w.toIntOrNull() ?: continue
                    if (n > 0) stimmen.putIfAbsent(m.groupValues[1].uppercase(), n)
                }
            }
            val l = z.label
            if (l.length > 300 || ADS.containsMatchIn(l) || FREMD.containsMatchIn(l)) continue
            val klasse = KLASSE.find(z.orig)?.groupValues?.get(1)
            when {
                MEHRZUTEILUNG.containsMatchIn(l) -> {
                    if (mehr == null) mehr = UP_TO.find(z.voll)?.let { zahlWert(it.groupValues[1]) } ?: z.zahlen.firstOrNull()
                }
                l.contains("outstanding") -> {
                    // Vor dem Angebot zaehlt nicht
                    if (!NACH.containsMatchIn(l)) continue@zeilen
                    val paare = KLASSE_ZAHL.findAll(z.voll).toList()
                    if (paare.isNotEmpty()) {
                        for (m in paare) zahlWert(m.groupValues[1])?.let { klassen.putIfAbsent(m.groupValues[2], it) }
                    } else {
                        // „1 share“ oder „no shares“ (Gattungen mit nur einer Stimmrechtsaktie) fallen sonst durch
                        val n = z.zahlen.firstOrNull() ?: (if (klasse != null) kleineZahl(z.voll) else null) ?: continue@zeilen
                        val klassenImLabel = KLASSE.findAll(z.orig).map { it.groupValues[1] }.toSet()
                        when {
                            // „Class A, Class N and Class F common stock to be outstanding“ ist die Summe
                            klassenImLabel.size >= 2 -> if (danachTotal == null) danachTotal = n
                            klasse != null -> klassen.putIfAbsent(klasse, n)
                            l.contains("total") -> if (danachTotal == null) danachTotal = n
                            danachGenerisch == null -> danachGenerisch = n
                        }
                    }
                }
                l.contains("offered") -> {
                    val n = z.zahlen.firstOrNull()
                    when {
                        ANGEBOTEN_BEIDE.containsMatchIn(l) -> if (n != null && gesamt == null) { gesamt = n; klasse?.let(angebotKlassen::add) }
                        l.contains("selling") -> {
                            verkaufErwaehnt = true
                            if (n != null && verkauf == null) { verkauf = n; klasse?.let(angebotKlassen::add) }
                        }
                        ANGEBOTEN_VON.containsMatchIn(l) -> {
                            firmaErwaehnt = true
                            if (n != null && neu == null) { neu = n; klasse?.let(angebotKlassen::add) }
                        }
                        n != null && gesamt == null -> { gesamt = n; klasse?.let(angebotKlassen::add) }
                    }
                }
            }
        }
        var angeboten = gesamt
        var n = neu
        var v = verkauf
        if (angeboten != null) {
            when {
                n != null && v != null -> if (n + v != angeboten) { n = null; v = null }
                n != null -> if (n <= angeboten) v = angeboten - n else n = null
                v != null -> if (v <= angeboten) n = angeboten - v else v = null
            }
        } else if (n != null || v != null) {
            if ((n != null && v == null && verkaufErwaehnt) || (v != null && n == null && firmaErwaehnt)) {
                n = null
                v = null
            } else {
                angeboten = (n ?: 0L) + (v ?: 0L)
                // Ohne Zeile zu den Altaktionaeren (bzw. zur Firma) gibt es diesen Teil nicht
                if (n == null) n = 0L
                if (v == null) v = 0L
            }
        }
        if (mehr != null && angeboten != null && mehr > angeboten) mehr = null
        val summe = if (klassen.size >= 2) klassen.values.sum() else null
        val danach = danachTotal ?: summe ?: danachGenerisch
        val gattungen = if (klassen.size >= 2) klassen.map { Gattung(it.key, it.value, stimmen[it.key]) } else emptyList()
        val anteil = if (gattungen.size >= 2 && angeboten != null && angebotKlassen.size == 1 && gattungen.all { it.stimmen != null }) {
            val g = gattungen.firstOrNull { it.name == angebotKlassen.first() }
            val alle = gattungen.sumOf { it.aktien.toDouble() * (it.stimmen ?: 0) }
            if (g != null && alle > 0) angeboten.toDouble() * (g.stimmen ?: 0) / alle * 100.0 else null
        } else null
        if (angeboten == null && danach == null && mehr == null) return null
        return Angebot(angeboten, n, v, mehr, danach, gattungen, anteil)
    }

    // Abschnitt „The Offering“ (Tabelle neben der Ueberschrift, sonst Tabelle mit „offered by us“, sonst Textzeilen):
    // angebotene Aktien, ihre Aufteilung, Mehrzuteilungsoption, Aktien danach je Gattung und Stimmen je Aktie.
    // Was nicht sicher lesbar ist, fehlt.
    fun angebot(html: String): Angebot? {
        val kandidaten = sequence<List<String>> {
            for (m in OFFERING_KOPF.findAll(html).take(8)) {
                val tabellen = tabellenFolge(html, m.range.first, 30_000)
                if (tabellen.isNotEmpty()) yield(tabellen.flatMap { zeilenAusTabelle(it) })
                yield(zeilenAusText(html.substring(m.range.last, minOf(html.length, m.range.last + 40_000))))
            }
            val gesehen = HashSet<Int>()
            for (m in ANGEBOT_ZEILE.findAll(html).take(6)) {
                val tabellen = tabellenFolge(html, m.range.first, 0)
                if (tabellen.isEmpty()) continue
                if (gesehen.add(tabellen.first().length * 31 + tabellen.first().hashCode())) yield(tabellen.flatMap { zeilenAusTabelle(it) })
            }
        }
        for (zeilen in kandidaten) angebotAus(zeilen)?.let { return it }
        return null
    }

    // ------------------------------------------------------------ Grosse Prospekte lesen

    // Stellen, an denen die Auszuege ansetzen (Ueberschriften und Saetze), je mit den Zeichen davor und dahinter, die der
    // jeweilige Auszug braucht: Tabelle „The Offering“ (auch geteilt), Anteilseigner, Banken, Abschnitt zu kuenftigen
    // Verkaeufen (gesperrter Anteil), Saetze zur Sperrfrist.
    private val KOPF_HALTER = Regex(
        "(?i)>$L0(?:Principal(?:${L}and${L}Selling)?$L(?:Stock|Share)holders|Security${L}Ownership${L}of${L}Certain${L}Beneficial${L}Owners(?:${L}and${L}Management)?)$L0<",
    )
    private val ANGEBOTEN_ZELLE = Regex("(?i)>[^<>]{0,60}offered${L}by$L(?:us|the${L}company)$L0(?:\\(\\d{1,2}\\))?$L0<")

    private class Anker(val regex: Regex, val vor: Int, val nach: Int) { var letzte = -1L }
    private class Stueck(val start: Long, var ende: Long, var wunsch: Long, val text: StringBuilder = StringBuilder())

    // Anzahl Bytes am Ende, die ein angefangenes UTF-8-Zeichen bilden (0, wenn der Block sauber endet).
    private fun angefangen(b: ByteArray, n: Int): Int {
        for (k in 1..3) {
            if (n - k < 0) return 0
            val x = b[n - k].toInt() and 0xFF
            if ((x and 0xC0) == 0x80) continue
            if (x < 0xC0) return 0
            val noetig = if (x >= 0xF0) 4 else if (x >= 0xE0) 3 else 2
            return if (noetig > k) k else 0
        }
        return 0
    }

    // Liest einen Prospekt als Strom und behaelt nur die Stellen, aus denen die Auszuege lesen (bei den Fundstellen
    // einige zehn- bis hunderttausend Zeichen). Im Speicher liegen nie mehr als ein Block plus ein Rueckblick und
    // hoechstens maxBehalten Zeichen; ein Prospekt mit 15 MB (Anthropic) wird so ganz durchsucht. Das Ergebnis geht an lesen().
    fun verdichten(strom: java.io.InputStream, block: Int = 400_000, rueck: Int = 64_000, maxBytes: Long = 60_000_000L, maxBehalten: Int = 8_000_000): String {
        val anker = listOf(
            Anker(OFFERING_KOPF, 2_000, 150_000), Anker(KOPF_HALTER, 2_000, 400_000), Anker(BANK_KOPF, 60_000, 60_000),
            Anker(ABSCHNITT_VERKAUF, 0, 45_000), Anker(FRIST, 3_000, 1_500), Anker(ANGEBOTEN_ZELLE, 60_000, 60_000),
        )
        val stuecke = ArrayList<Stueck>()
        var behalten = 0
        val fenster = StringBuilder()
        var fs = 0L
        val puffer = ByteArray(block + 4)
        var uebrig = 0
        var gelesen = 0L
        var ende = false
        fun vormerken(von0: Long, bis: Long) {
            val von = maxOf(von0, fs)
            val letztes = stuecke.lastOrNull()
            if (letztes != null && von <= letztes.wunsch) {
                if (bis > letztes.wunsch) letztes.wunsch = bis
            } else if (behalten < maxBehalten) stuecke += Stueck(von, von, bis)
        }
        while (!ende && gelesen < maxBytes) {
            var n = uebrig
            while (n < block) {
                val r = strom.read(puffer, n, block - n)
                if (r < 0) { ende = true; break }
                n += r
            }
            gelesen += n - uebrig
            if (n == 0) break
            val offen = if (ende) 0 else angefangen(puffer, n)
            val gueltig = n - offen
            val neuAb = fs + fenster.length
            fenster.append(String(puffer, 0, gueltig, Charsets.UTF_8))
            for (i in 0 until offen) puffer[i] = puffer[gueltig + i]
            uebrig = offen
            val fe = fs + fenster.length
            val suchAb = (maxOf(fs, neuAb - 400) - fs).toInt()
            for (a in anker) {
                var m = a.regex.find(fenster, suchAb)
                while (m != null) {
                    val p = fs + m.range.first
                    if (p > a.letzte) {
                        a.letzte = p
                        vormerken(p - a.vor, p + a.nach)
                    }
                    m = m.next()
                }
            }
            for (s in stuecke) {
                if (s.ende >= s.wunsch) continue
                val von = maxOf(s.ende, s.start, fs)
                val bis = minOf(s.wunsch, fe)
                if (bis > von) {
                    s.text.append(fenster.substring((von - fs).toInt(), (bis - fs).toInt()))
                    behalten += (bis - von).toInt()
                    s.ende = bis
                }
            }
            if (fenster.length > rueck) {
                val ueber = fenster.length - rueck
                fenster.deleteRange(0, ueber)
                fs += ueber
            }
        }
        return stuecke.joinToString("\n") { it.text }
    }

    // Aktien, die mit dem Ende der Sperrfrist frei werden: gesperrter Anteil mal Aktien (danach, bei Bezug auf die
    // Aktien vor dem Angebot ohne die neuen). null ohne Frist-Anteil oder Zahlen, oder wenn mehr herauskaeme als
    // ueberhaupt Altaktien da sind.
    fun freigabe(angebot: Angebot?, gesperrt: Gesperrt?): Long? {
        val a = angebot ?: return null
        val g = gesperrt ?: return null
        val danach = a.danach ?: return null
        val basis = if (g.nachAngebot) danach else danach - (a.neu ?: return null)
        if (basis <= 0) return null
        val n = Math.round(basis * g.anteil / 100.0)
        return n.takeIf { it > 0 && it <= danach - (a.angeboten ?: 0L) }
    }
}
