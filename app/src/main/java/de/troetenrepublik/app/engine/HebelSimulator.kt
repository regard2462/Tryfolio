package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow

// Hebel-Simulator (0.78): Was ein taeglich zurueckgesetzter Hebel aus einer Kursreihe gemacht haette. Modellrechnung,
// historisch und hypothetisch. Je Abschnitt zwischen zwei Kurstagen:
//   Hebelrendite = Hebel x Rendite - Finanzierung - Kosten
//   Finanzierung = ((Hebel - 1) x Zins + Geliehenes x Aufschlag) x Kalendertage / 360
//   Kosten       = Kostenquote x Kalendertage / 365
// Geliehenes ist das Geld ueber dem eigenen Einsatz (Hebel ueber 1) oder die leer verkauften Stuecke (Hebel unter 0).
// Unter Hebel 1 liegt ein Teil des Einsatzes als Geld da und bringt den Zins, deshalb wird die Finanzierung dort negativ.
// Faellt der Wert an einem Tag auf null oder darunter, ist er verloren und bleibt null.
object HebelSimulator {
    private const val TAG = 86_400_000L
    const val ZINS_TAGE = 360.0
    const val KOSTEN_TAGE = 365.0
    private const val JAHR_TAGE = 365.25

    // Annahmen, in der Anzeige als solche gekennzeichnet (Prozent pro Jahr)
    const val AUFSCHLAG_STANDARD = 0.5
    const val KOSTEN_STANDARD = 0.75
    // Zins ohne Zinsreihe der Handelswaehrung
    const val ZINS_ANNAHME = 3.0

    // Weniger Kurstage ergeben keine Aussage
    const val MIN_PUNKTE = 21

    val HEBEL = listOf(-3.0, -2.0, -1.0, 1.0, 2.0, 3.0, 4.0, 5.0)
    const val HEBEL_STANDARD = 2.0

    // Kurve „Rendite je Hebel“: der ganze Bereich lueckenlos, auch zwischen -1 und 1 (Hebel 0 = nur Zins)
    const val KURVE_VON = -3.0
    const val KURVE_BIS = 5.0
    const val KURVE_SCHRITT = 0.25

    // zinsreihe: Tag seit 1970 und Zins in Prozent pro Jahr, aufsteigend; leer = zinsFest gilt durchgehend.
    class Annahmen(
        val aufschlag: Double = AUFSCHLAG_STANDARD, val kosten: Double = KOSTEN_STANDARD,
        val zinsreihe: List<Pair<Long, Double>> = emptyList(), val zinsFest: Double = ZINS_ANNAHME,
    ) {
        private val tage: List<Long> = zinsreihe.map { it.first }

        // Letzter Zins bis zu diesem Tag, vor Beginn der Reihe ihr erster Wert
        fun zinsAm(tag: Long): Double {
            if (tage.isEmpty()) return zinsFest
            val i = tage.binarySearch(tag).let { if (it >= 0) it else -it - 2 }
            return zinsreihe[i.coerceAtLeast(0)].second
        }
    }

    // Rendite pro Jahr, Hebel mal Rendite, und was davon abgeht (Anteile, 0,1 = 10 Punkte). Es gilt
    // naiv - pfad - finanzierung - kosten = tatsaechlich. Negative Posten erhoehen die Rendite (Zins auf Bargeld
    // bei Short, Pfadgewinn bei stetigem Trend).
    class Zerlegung(val naiv: Double, val pfad: Double, val finanzierung: Double, val kosten: Double, val tatsaechlich: Double)

    class Ergebnis(
        val hebel: Double,
        // Zeitpunkte und beide Verlaeufe ab 1,0
        val zeiten: LongArray, val ohne: DoubleArray, val mit: DoubleArray,
        val jahre: Double,
        // false unter einem Jahr: Dann sind die Renditen die des Zeitraums, nicht hochgerechnet
        val proJahr: Boolean,
        val renditeOhne: Double, val renditeMit: Double,
        // Groesster Rueckgang vom Hoch, positiv (0,35 = 35 %)
        val rueckgangOhne: Double, val rueckgangMit: Double,
        // Zeitpunkt des Totalverlusts oder null
        val totalverlustAm: Long?,
        val zerlegung: Zerlegung,
        // Groesste Tagesbewegung gegen den Hebel im Zeitraum, positiv (Verlust bei Long, Anstieg bei Short)
        val schlimmsterTag: Double,
        // Mittlerer Zins im Zeitraum, Prozent pro Jahr
        val zinsMittel: Double,
    ) {
        val endOhne: Double get() = ohne.last()
        val endMit: Double get() = mit.last()
    }

    private fun geliehen(hebel: Double): Double = maxOf(hebel - 1.0, 0.0) + maxOf(-hebel, 0.0)

    private fun tag(t: Long): Long = Math.floorDiv(t, TAG)

    // Ein Kurs je Kalendertag, nur positive Kurse, aufsteigend
    internal fun kurse(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> =
        Risiko.taeglich(reihe.filter { it.second.isFinite() && it.second > 0.0 })

    // Verlauf ab 1,0; der zweite Wert ist der Index des Totalverlusts oder -1.
    private fun lauf(p: List<Pair<Long, Double>>, hebel: Double, a: Annahmen, mitFinanzierung: Boolean, mitKosten: Boolean): Pair<DoubleArray, Int> {
        val aus = DoubleArray(p.size)
        aus[0] = 1.0
        var wert = 1.0
        var verloren = -1
        val leihe = geliehen(hebel)
        for (i in 1 until p.size) {
            if (verloren < 0) {
                val tage = (tag(p[i].first) - tag(p[i - 1].first)).coerceAtLeast(1L).toDouble()
                val r = p[i].second / p[i - 1].second - 1.0
                val fin = if (mitFinanzierung) ((hebel - 1.0) * a.zinsAm(tag(p[i - 1].first)) + leihe * a.aufschlag) / 100.0 * tage / ZINS_TAGE else 0.0
                val kost = if (mitKosten) a.kosten / 100.0 * tage / KOSTEN_TAGE else 0.0
                val faktor = 1.0 + hebel * r - fin - kost
                if (faktor <= 0.0) { wert = 0.0; verloren = i } else wert *= faktor
            }
            aus[i] = wert
        }
        return aus to verloren
    }

    private fun rendite(end: Double, jahre: Double, proJahr: Boolean): Double =
        if (end <= 0.0) -1.0 else if (proJahr) end.pow(1.0 / jahre) - 1.0 else end - 1.0

    private fun rueckgang(werte: DoubleArray): Double {
        var hoch = werte[0]
        var max = 0.0
        for (v in werte) {
            if (v > hoch) hoch = v
            if (hoch > 0.0) max = maxOf(max, 1.0 - v / hoch)
        }
        return max
    }

    // reihe: Zeitpunkt (ms) und Kurs. Null bei weniger als MIN_PUNKTE Kurstagen.
    fun rechnen(reihe: List<Pair<Long, Double>>, hebel: Double, a: Annahmen = Annahmen()): Ergebnis? {
        val p = kurse(reihe)
        if (p.size < MIN_PUNKTE || !hebel.isFinite()) return null
        val jahre = (p.last().first - p.first().first) / (JAHR_TAGE * TAG)
        if (!(jahre > 0.0)) return null
        val proJahr = jahre >= 1.0 - 1e-9
        val ohne = DoubleArray(p.size) { p[it].second / p[0].second }
        val (mit, verloren) = lauf(p, hebel, a, mitFinanzierung = true, mitKosten = true)
        // Stufenweise: erst nur der Hebel auf die Tagesrenditen, dann die Finanzierung, dann die Kosten
        val nurHebel = rendite(lauf(p, hebel, a, mitFinanzierung = false, mitKosten = false).first.last(), jahre, proJahr)
        val mitFin = rendite(lauf(p, hebel, a, mitFinanzierung = true, mitKosten = false).first.last(), jahre, proJahr)
        val rOhne = rendite(ohne.last(), jahre, proJahr)
        val rMit = rendite(mit.last(), jahre, proJahr)
        val naiv = hebel * rOhne
        var schlimm = 0.0
        var zinsSumme = 0.0
        for (i in 1 until p.size) {
            val r = p[i].second / p[i - 1].second - 1.0
            schlimm = maxOf(schlimm, if (hebel >= 0.0) -r else r)
            zinsSumme += a.zinsAm(tag(p[i - 1].first))
        }
        return Ergebnis(
            hebel = hebel, zeiten = LongArray(p.size) { p[it].first }, ohne = ohne, mit = mit, jahre = jahre, proJahr = proJahr,
            renditeOhne = rOhne, renditeMit = rMit, rueckgangOhne = rueckgang(ohne), rueckgangMit = rueckgang(mit),
            totalverlustAm = if (verloren >= 0) p[verloren].first else null,
            zerlegung = Zerlegung(naiv, naiv - nurHebel, nurHebel - mitFin, mitFin - rMit, rMit),
            schlimmsterTag = schlimm, zinsMittel = zinsSumme / (p.size - 1),
        )
    }

    // Rendite (pro Jahr ab einem Jahr Laenge) je Hebel von KURVE_VON bis KURVE_BIS lueckenlos in Schritten von KURVE_SCHRITT; leer bei zu kurzer Reihe.
    fun kurve(reihe: List<Pair<Long, Double>>, a: Annahmen = Annahmen()): List<Pair<Double, Double>> {
        val p = kurse(reihe)
        if (p.size < MIN_PUNKTE) return emptyList()
        val jahre = (p.last().first - p.first().first) / (JAHR_TAGE * TAG)
        if (!(jahre > 0.0)) return emptyList()
        val proJahr = jahre >= 1.0 - 1e-9
        val schritte = ((KURVE_BIS - KURVE_VON) / KURVE_SCHRITT + 0.5).toInt()
        return (0..schritte).map { k -> KURVE_VON + k * KURVE_SCHRITT }.map { h ->
            h to rendite(lauf(p, h, a, mitFinanzierung = true, mitKosten = true).first.last(), jahre, proJahr)
        }
    }

    // ---- Gleicher Hebel in den grossen Krisen: Hoch bis Tief des S&P 500 (PortfolioTools.CRASHES), gerechnet auf den Tageskursen des
    // Werts, wo seine Reihe die Krise abdeckt, sonst auf denen des Index. Historisch und hypothetisch.
    val KRISEN = listOf("DOTCOM", "FINANCIAL", "COVID", "RATES")

    // aus: false = Kurse des Werts, true = Kurse des Index. Ergebnis: Verlauf ab 1,0 vom Hoch bis zum Tief.
    class Krise(val schluessel: String, val von: Long, val bis: Long, val aus: Boolean, val ergebnis: Ergebnis)

    // Schlusskurse vom Hochtag bis zum Tieftag; null, wenn die Reihe das Fenster nicht abdeckt.
    fun krisenReihe(kerzen: List<Candle>, von: Long, bis: Long): List<Pair<Long, Double>>? {
        if (PortfolioTools.windowReturn(kerzen, von, bis) == null) return null
        // Stichzeit der Tage ist 23 Uhr UTC, die Kerze des Hochtags liegt weniger als einen Tag davor
        return kerzen.filter { it.t > von - TAG && it.t <= bis }.map { it.t to it.close }.takeIf { it.size >= MIN_PUNKTE }
    }

    fun krisen(wert: List<Candle>, index: List<Candle>, hebel: Double, a: Annahmen): List<Krise> =
        KRISEN.mapNotNull { k ->
            val c = PortfolioTools.CRASHES.firstOrNull { it.key == k } ?: return@mapNotNull null
            val eigene = krisenReihe(wert, c.from, c.to)
            val reihe = eigene ?: krisenReihe(index, c.from, c.to) ?: return@mapNotNull null
            rechnen(reihe, hebel, a)?.let { Krise(k, c.from, c.to, eigene == null, it) }
        }

    // Hebel mit der hoechsten Rendite der Kurve: nur ein Rueckblick auf diesen Zeitraum.
    fun bester(kurve: List<Pair<Double, Double>>): Pair<Double, Double>? = kurve.maxByOrNull { it.second }

    // Tagesbewegung gegen den Hebel, ab der der Einsatz an einem Tag verloren ist (1 / Hebel, vor Zins und Kosten);
    // null bei Hebel 1 und zwischen -1 und 1.
    fun totalverlustSchwelle(hebel: Double): Double? = if (hebel == 1.0 || abs(hebel) < 1.0) null else 1.0 / abs(hebel)

    // ---- Zinsreihe der Handelswaehrung: Tagesgeldsatz, wo es einen frei nutzbaren gibt, sonst der Leitzins (BIZ)

    // Eine Reihe mit ihrer Nennung im Quellenanhang (text: Schluessel in HebelGrenzeTexte). fred: Antwort im Format
    // „Datum,Wert“ mit Kopfzeile, sonst CSV mit den Spalten TIME_PERIOD und OBS_VALUE.
    class Zinsquelle(val schluessel: String, val text: String, val seite: String, val fred: Boolean, val adresse: (ab: Int) -> String) {
        fun kuerzen(antwort: String): String? = if (fred) fredKuerzen(antwort) else zinsKuerzen(antwort)
    }

    // Was der Abruf liefert: die verkettete Reihe und die Quellen, aus denen sie stammt
    class Zinsdaten(val reihe: List<Pair<Long, Double>>, val quellen: List<Zinsquelle>)

    // Tagesgeld in Dollar: effektiver Satz der Fed Funds
    val DFF = Zinsquelle("fred-dff", "h.quelle.dff", "https://fred.stlouisfed.org/series/DFF", fred = true) { ab ->
        "https://fred.stlouisfed.org/graph/fredgraph.csv?id=DFF&cosd=$ab-01-01"
    }
    // Tagesgeld in Euro bis 2021 (eingestellt), danach der Euro-Tagesgeldsatz der EZB (ab Oktober 2019)
    val EONIA = Zinsquelle("fred-eonia", "h.quelle.eonia", "https://fred.stlouisfed.org/series/EONIARATE", fred = true) { ab ->
        "https://fred.stlouisfed.org/graph/fredgraph.csv?id=EONIARATE&cosd=$ab-01-01"
    }
    val ESTR = Zinsquelle(
        "ezb-estr", "h.quelle.estr", "https://www.ecb.europa.eu/stats/financial_markets_and_interest_rates/euro_short-term_rate/html/index.en.html", fred = false,
    ) { ab -> "https://data-api.ecb.europa.eu/service/data/EST/B.EU000A2X2A25.WT?startPeriod=$ab-01-01&detail=dataonly&format=csvdata" }

    // Tagesgeldreihen der Waehrung in zeitlicher Folge; eine spaetere loest die fruehere ab ihrem ersten Tag ab.
    fun tagesgeld(waehrung: String): List<Zinsquelle> = when (ExchangeRates.hauptwaehrung(waehrung)) {
        "USD" -> listOf(DFF)
        "EUR" -> listOf(EONIA, ESTR)
        else -> emptyList()
    }

    // Leitzins des Waehrungsraums als Ersatz und fuer alle anderen Waehrungen; null, wo die BIZ keinen fuehrt.
    fun leitzins(waehrung: String): Zinsquelle? = zinsGebiet(waehrung)?.let { g ->
        Zinsquelle("bis-verlauf-$g", "h.quelle.bis", BIS_SEITE, fred = false) { ab -> bisAdresse(g, ab) }
    }

    // Reihen in zeitlicher Folge zu einer: Von der frueheren bleibt, was vor dem ersten Tag der spaeteren liegt.
    fun verketten(reihen: List<List<Pair<Long, Double>>>): List<Pair<Long, Double>> {
        var aus = emptyList<Pair<Long, Double>>()
        for (r in reihen) {
            if (r.isEmpty()) continue
            val beginn = r.first().first
            aus = aus.filter { it.first < beginn } + r
        }
        return aus
    }

    // Speicherform: „Tag,Zins“ je Aenderung des Zinses; null ohne Wert
    private fun kurz(werte: List<Pair<Long, Double>>): String? {
        if (werte.isEmpty()) return null
        val aus = StringBuilder()
        var letzter: Double? = null
        for ((t, w) in werte.sortedBy { it.first }) {
            if (w != letzter) { aus.append(t).append(',').append(w).append('\n'); letzter = w }
        }
        return aus.toString().trimEnd()
    }

    // Antwort von FRED („observation_date,<Reihe>“, fehlende Werte als Punkt) in der Speicherform
    fun fredKuerzen(csv: String): String? = kurz(Fruehindikatoren.fredLesen(csv))

    // Waehrungsraum der BIZ-Statistik zur Handelswaehrung; null, wo sie keinen Leitzins fuehrt.
    fun zinsGebiet(waehrung: String): String? = when (ExchangeRates.hauptwaehrung(waehrung)) {
        "EUR" -> LaenderMarkt.EURORAUM
        "USD" -> "US"; "GBP" -> "GB"; "CHF" -> "CH"; "JPY" -> "JP"; "CAD" -> "CA"; "AUD" -> "AU"; "NZD" -> "NZ"
        "SEK" -> "SE"; "NOK" -> "NO"; "DKK" -> "DK"; "ISK" -> "IS"; "PLN" -> "PL"; "CZK" -> "CZ"; "HUF" -> "HU"; "RON" -> "RO"
        "HKD" -> "HK"; "CNY" -> "CN"; "INR" -> "IN"; "KRW" -> "KR"; "IDR" -> "ID"; "MYR" -> "MY"; "THB" -> "TH"; "PHP" -> "PH"
        "BRL" -> "BR"; "MXN" -> "MX"; "CLP" -> "CL"; "COP" -> "CO"; "PEN" -> "PE"
        "ZAR" -> "ZA"; "ILS" -> "IL"; "TRY" -> "TR"; "SAR" -> "SA"
        else -> null
    }

    const val BIS_SEITE = "https://data.bis.org/topics/CBPOL"

    // Tageswerte eines Waehrungsraums seit dem Jahr ab (CSV: FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE)
    fun bisAdresse(gebiet: String, ab: Int): String =
        "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.$gebiet?startPeriod=$ab-01-01&detail=dataonly&format=csv"

    // Verkleinert die Antwort fuer die Speicherung auf „Tag,Zins“ je Aenderung des Zinses; null ohne lesbare Zeile.
    fun zinsKuerzen(csv: String): String? {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.size < 2) return null
        val kopf = zeilen[0].split(',').map { it.trim().trim('"') }
        val iTag = kopf.indexOf("TIME_PERIOD")
        val iWert = kopf.indexOf("OBS_VALUE")
        if (iTag < 0 || iWert < 0) return null
        val werte = zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',')
            val t = runCatching { LocalDate.parse(f.getOrNull(iTag)?.trim()?.trim('"').orEmpty()).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            val w = f.getOrNull(iWert)?.trim()?.trim('"')?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            t to w
        }
        return kurz(werte)
    }

    fun zinsLesen(text: String): List<Pair<Long, Double>> =
        text.lineSequence().mapNotNull { z ->
            val t = z.substringBefore(',').trim().toLongOrNull() ?: return@mapNotNull null
            val w = z.substringAfter(',', "").trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            t to w
        }.sortedBy { it.first }.toList()
}
