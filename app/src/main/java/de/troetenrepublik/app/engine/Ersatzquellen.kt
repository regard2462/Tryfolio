package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.YearMonth

// Ersatzquellen: freie, offen nutzbare Daten zu Bewertung, Margin-Krediten, Lieferketten, Fracht, Halbleitern und
// Erwaehnungen im Netz. Hier stehen Adressen, Leser und Einordnung, die Abrufe in market/Ersatzabrufe.kt. Alles wird
// nur zur Laufzeit geladen, nichts davon liegt der App bei.
object Ersatzquellen {

    // ------------------------------------------------------------ Adressen

    // FRED-CSV einer Reihe ab einem Tag. Alle hier genutzten Reihen sind laut FRED gemeinfrei (Quelle nennen).
    fun fredAdresse(reihe: String, von: LocalDate) = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$reihe&cosd=$von"

    // Kredite der Wertpapierhaendler an ihre Kunden (Margin-Kredite und uebrige Forderungen), Finanzierungsrechnung
    // der Fed (Z.1), Quartale, Millionen Dollar. Ersatz fuer die Margin Debt der FINRA.
    const val MARGIN_REIHE = "BOGZ1FL663067003Q"

    // Seite von Robert Shiller mit der laufend fortgeschriebenen Datei ie_data.xls (die alte Yale-Datei endet 2023).
    const val SHILLER_SEITE = "https://shillerdata.com/"

    // Global Supply Chain Pressure Index der Federal Reserve Bank of New York, Monatswerte ab Januar 1998.
    const val GSCPI_DATEI = "https://www.newyorkfed.org/medialibrary/research/interactives/gscpi/downloads/gscpi_data.xlsx"
    const val GSCPI_SEITE = "https://www.newyorkfed.org/research/policy/gscpi"
    val GSCPI_START: YearMonth = YearMonth.of(1998, 1)

    // Suche der Hacker-News-Beitraege (Algolia, offene Schnittstelle ohne Schluessel): Treffer seit seit (Unix-Sekunden).
    fun hnAdresse(begriff: String, seit: Long) =
        "https://hn.algolia.com/api/v1/search_by_date?query=" + java.net.URLEncoder.encode("\"$begriff\"", "UTF-8") +
            "&tags=story&numericFilters=created_at_i%3E$seit&hitsPerPage=0"

    // Nutzung eines Hashtags auf mastodon.social in den letzten sieben Tagen (nur Zahlen, keine Beitraege).
    fun mastodonAdresse(tag: String) = "https://mastodon.social/api/v1/tags/" + java.net.URLEncoder.encode(tag, "UTF-8")

    // ------------------------------------------------------------ Signale zu Fracht, Lieferketten und Halbleitern

    const val LIEFERKETTEN = "lieferketten"
    const val FRACHT = "fracht"
    const val BAHN = "bahn"
    const val HALBLEITER = "halbleiter"

    // Reihenfolge der Anzeige
    val SIGNALE = listOf(LIEFERKETTEN, FRACHT, BAHN, HALBLEITER)

    // Monatsreihen bei FRED: Frachtindex des US-Verkehrsministeriums (BTS, Lkw, Bahn, Schiff, Pipeline, Luftfracht),
    // Gueterwagen der Bahn (BTS, saisonbereinigt) und Produktion von Halbleitern und Bauteilen (Fed).
    val FRED_SIGNALE = mapOf(
        FRACHT to "TSIFRGHT",
        BAHN to "RAILFRTCARLOADSD11",
        HALBLEITER to "IPG3344S",
    )

    // Seiten fuer Menschen
    fun seite(k: String): String = when (k) {
        LIEFERKETTEN -> GSCPI_SEITE
        else -> "https://fred.stlouisfed.org/series/" + (FRED_SIGNALE[k] ?: "")
    }

    // Ein Signal: wert in der Einheit der Quelle, stand als Tag seit 1970 (Monatsende bzw. Monatsanfang),
    // jahr: Veraenderung zum Vorjahresmonat in % (beim GSCPI in Punkten), vormonat ebenso zum Vormonat.
    data class Signal(
        val schluessel: String,
        val ampel: Ampel,
        val wert: Double,
        val stand: Long,
        val jahr: Double?,
        val vormonat: Double?,
    )

    // Monatsreihe gegen den Vorjahresmonat. Ab 3 % weniger rot, weniger als im Vorjahr gelb. Null ohne
    // Vorjahresmonat oder mit weniger als 13 Monaten.
    fun jahresvergleich(schluessel: String, reihe: List<Pair<Long, Double>>): Signal? {
        val r = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        if (r.size < 13) return null
        val letzter = r.last()
        val monat = YearMonth.from(LocalDate.ofEpochDay(letzter.first))
        fun wertIm(m: YearMonth) = r.lastOrNull { YearMonth.from(LocalDate.ofEpochDay(it.first)) == m }?.second
        val vorjahr = wertIm(monat.minusYears(1)) ?: return null
        val vormonat = wertIm(monat.minusMonths(1))
        val jahr = (letzter.second / vorjahr - 1) * 100
        val ampel = when {
            jahr <= -3 -> Ampel.ROT
            jahr < 0 -> Ampel.GELB
            else -> Ampel.GRUEN
        }
        return Signal(schluessel, ampel, letzter.second, letzter.first, jahr, vormonat?.let { (letzter.second / it - 1) * 100 })
    }

    // Lieferketten: der GSCPI misst in Standardabweichungen, wie weit der Druck vom langjaehrigen Schnitt abweicht.
    // Ab 0,5 gelb, ab 1,5 rot (2021 lag er bei gut 4). Veraenderungen in Punkten.
    fun lieferketten(reihe: List<Pair<Long, Double>>): Signal? {
        val r = reihe.filter { it.second.isFinite() }.sortedBy { it.first }
        val letzter = r.lastOrNull() ?: return null
        val ampel = when {
            letzter.second >= 1.5 -> Ampel.ROT
            letzter.second >= 0.5 -> Ampel.GELB
            else -> Ampel.GRUEN
        }
        val vorjahr = r.getOrNull(r.size - 13)
        val vormonat = r.getOrNull(r.size - 2)
        return Signal(LIEFERKETTEN, ampel, letzter.second, letzter.first, vorjahr?.let { letzter.second - it.second }, vormonat?.let { letzter.second - it.second })
    }

    // GSCPI aus der Excel-Datei der New York Fed. Das Datum steht dort als Text, den XlsLeser nicht liest; die
    // Monatswerte stehen aber lueckenlos untereinander ab Januar 1998. Gezaehlt wird deshalb ab dem ersten Wert. Passt
    // der letzte Monat nicht in die Zeit (mehr als ein halbes Jahr alt oder in der Zukunft), ist die Datei anders
    // aufgebaut und das Ergebnis leer. Ergebnis: (Monatsende als Tag seit 1970, Wert), aelteste zuerst.
    fun gscpiAusZahlen(blaetter: List<Map<Int, Map<Int, Double>>>, heute: LocalDate = LocalDate.now()): List<Pair<Long, Double>> {
        fun werte(b: Map<Int, Map<Int, Double>>) = b.entries.filter { (_, z) -> z[1]?.let { it.isFinite() && it in -10.0..20.0 } == true && z[0] == null }
            .sortedBy { it.key }
        val blatt = blaetter.maxByOrNull { werte(it).size } ?: return emptyList()
        val zeilen = werte(blatt)
        if (zeilen.size < 24) return emptyList()
        // Lueckenlos: jede Zeile folgt direkt auf die vorige
        if (zeilen.zipWithNext().any { (a, b) -> b.key != a.key + 1 }) return emptyList()
        val letzter = GSCPI_START.plusMonths((zeilen.size - 1).toLong())
        val jetzt = YearMonth.from(heute)
        if (letzter.isAfter(jetzt) || letzter.isBefore(jetzt.minusMonths(6))) return emptyList()
        return zeilen.mapIndexed { i, e -> GSCPI_START.plusMonths(i.toLong()).atEndOfMonth().toEpochDay() to e.value[1]!! }
    }

    fun gscpiLesen(datei: ByteArray, heute: LocalDate = LocalDate.now()): List<Pair<Long, Double>> =
        XlsLeser.arbeitsmappe(datei)?.let { gscpiAusZahlen(XlsLeser.zahlen(it), heute) }.orEmpty()

    // ------------------------------------------------------------ Bewertung (Blasen-Detektor)

    // Shiller-KGV (CAPE): Kurs des S&P 500 geteilt durch den inflationsbereinigten Gewinn der letzten zehn Jahre.
    // rang: Anteil der Monate seit 1881, in denen er niedriger stand, in %. schnitt: Mittel aller Monate. reihe: alle
    // Monate als (erster Tag des Monats seit 1970, Wert), aelteste zuerst, fuer den Verlauf auf der Karte.
    data class Cape(val wert: Double, val jahr: Int, val monat: Int, val rang: Double, val schnitt: Double, val reihe: List<Pair<Long, Double>> = emptyList()) {
        val stand: Long get() = YearMonth.of(jahr, monat).atDay(1).toEpochDay()
    }

    // Link auf ie_data.xls in der Seite von shillerdata.com (die Datei liegt bei einem Speicherdienst, die Adresse
    // aendert sich mit jeder neuen Fassung).
    fun shillerLink(html: String): String? =
        Regex("""href="((?:https?:)?//[^"]+/ie_data\.xls[^"]*)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.replace("&amp;", "&")

    // CAPE aus ie_data.xls: Blatt „Data“, Spalte 0 das Datum als Jahr.Monat (1871.01 bis 2026.1 fuer Oktober), Spalte
    // 12 der CAPE. Gesucht wird das Blatt mit den meisten solchen Zeilen; Werte ausserhalb von 3 bis 100 zaehlen nicht.
    fun capeAusZahlen(blaetter: List<Map<Int, Map<Int, Double>>>, spalte: Int = 12): Cape? {
        fun zeilen(b: Map<Int, Map<Int, Double>>) = b.values.mapNotNull { z ->
            val d = z[0]?.takeIf { it in 1871.0..2200.0 } ?: return@mapNotNull null
            val c = z[spalte]?.takeIf { it.isFinite() && it in 3.0..100.0 } ?: return@mapNotNull null
            val jahr = kotlin.math.floor(d).toInt()
            val monat = Math.round((d - jahr) * 100).toInt()
            if (monat !in 1..12) return@mapNotNull null
            Triple(jahr, monat, c)
        }
        val liste = blaetter.map { zeilen(it) }.maxByOrNull { it.size }?.takeIf { it.size >= 120 } ?: return null
        val letzte = liste.maxBy { it.first * 12 + it.second }
        val darunter = liste.count { it.third < letzte.third }
        val reihe = liste.sortedBy { it.first * 12 + it.second }.distinctBy { it.first * 12 + it.second }
            .map { YearMonth.of(it.first, it.second).atDay(1).toEpochDay() to it.third }
        return Cape(letzte.third, letzte.first, letzte.second, darunter * 100.0 / (liste.size - 1).coerceAtLeast(1), liste.map { it.third }.average(), reihe)
    }

    fun capeLesen(datei: ByteArray): Cape? = XlsLeser.arbeitsmappe(datei)?.let { capeAusZahlen(XlsLeser.zahlen(it)) }

    // Margin-Kredite: Stand des juengsten Quartals und Wachstum gegen das Vorjahresquartal in %. verlauf: dieses
    // Wachstum je Quartal als (Tag seit 1970, %), aelteste zuerst.
    data class Margin(val stand: Long, val milliarden: Double, val wachstum: Double, val verlauf: List<Pair<Long, Double>> = emptyList())

    fun margin(reihe: List<Pair<Long, Double>>): Margin? {
        val r = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val letzter = r.lastOrNull() ?: return null
        fun vorjahr(t: Long): Pair<Long, Double>? {
            val ziel = LocalDate.ofEpochDay(t).minusYears(1).toEpochDay()
            return r.lastOrNull { kotlin.math.abs(it.first - ziel) <= 10 }
        }
        val v = vorjahr(letzter.first) ?: return null
        val verlauf = r.mapNotNull { p -> vorjahr(p.first)?.let { p.first to (p.second / it.second - 1) * 100 } }
        return Margin(letzter.first, letzter.second / 1000, (letzter.second / v.second - 1) * 100, verlauf)
    }

    // ------------------------------------------------------------ Erwaehnungen in offenen Netzen

    private val RECHTSFORM = Regex(
        "(?i)[,.]?\\s+(inc|incorporated|corp|corporation|co|company|ltd|limited|plc|ag|se|sa|s\\.a|nv|n\\.v|holdings?|group|" +
            "class [a-c]|common stock|ordinary shares|adr|the)\\.?\$",
    )

    // Name eines Unternehmens ohne Rechtsform und Zusaetze, fuer die Suche („Alphabet Inc. Class A“ wird „Alphabet“).
    fun suchname(name: String): String {
        var n = name.replace(Regex("\\(.*?\\)"), " ").replace(Regex("\\s+"), " ").trim()
        while (true) {
            val kuerzer = RECHTSFORM.replace(n, "").trim().trimEnd(',', '.', '-').trim()
            if (kuerzer == n || kuerzer.isEmpty()) break
            n = kuerzer
        }
        return n
    }

    // Hashtag aus dem Suchnamen: nur Buchstaben und Ziffern, klein. Null, wenn zu kurz oder zu lang.
    fun hashtag(name: String): String? =
        suchname(name).lowercase().filter { it.isLetterOrDigit() }.takeIf { it.length in 3..24 }

    // Zahl der Treffer in der Antwort der Hacker-News-Suche.
    fun hnTreffer(json: String): Int? = runCatching {
        ((Json.parseToJsonElement(json) as? JsonObject)?.get("nbHits") as? JsonPrimitive)?.content?.toIntOrNull()
    }.getOrNull()

    // Tag (seit 1970), Beitraege und Konten je Tag aus der Antwort von Mastodon, aelteste zuerst.
    data class TagNutzung(val tag: Long, val beitraege: Int, val konten: Int)

    fun mastodonVerlauf(json: String): List<TagNutzung> = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
        (o["history"] as? JsonArray).orEmpty().mapNotNull { e ->
            val h = e as? JsonObject ?: return@mapNotNull null
            fun zahl(k: String) = (h[k] as? JsonPrimitive)?.content?.toLongOrNull()
            val tag = zahl("day") ?: return@mapNotNull null
            TagNutzung(Math.floorDiv(tag, 86_400L), (zahl("uses") ?: 0L).toInt(), (zahl("accounts") ?: 0L).toInt())
        }.sortedBy { it.tag }
    }.getOrDefault(emptyList())

    // Beitraege auf Hacker News: letzte sieben Tage und Wochenschnitt der vier Wochen davor (aus den Treffern der
    // letzten 35 Tage). veraenderung in %, null ohne Treffer davor.
    data class HackerNews(val woche: Int, val schnittVorher: Double) {
        val veraenderung: Double? get() = if (schnittVorher > 0) (woche / schnittVorher - 1) * 100 else null
    }

    fun hackerNews(woche: Int, fuenfWochen: Int): HackerNews =
        HackerNews(woche, (fuenfWochen - woche).coerceAtLeast(0) / 4.0)
}
