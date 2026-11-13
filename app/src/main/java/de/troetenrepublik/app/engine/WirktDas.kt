package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max

// Lernseiten „Wirkt das?“ [0.76]: Rueckschau der Insider-Cluster-Kaeufe (gebuendelte Rechnung), Protokoll der
// Stillhalter-Puts mit der hoechsten Rendite (nominal gegen realisiert, ab Installation auf dem Geraet), Zahl zu
// „Kongress kopieren“ und die IPO-Statistik von Jay R. Ritter. Reine Rechnung und Lesen, Anzeige in WirktDasScreen.
object WirktDas {

    private val json = Json { ignoreUnknownKeys = true }
    private const val TAG_S = 86_400L

    // ------------------------------------------------------------ Insider-Cluster

    @Serializable
    data class ClusterJahr(val j: Int, val n: Int, val r: Double, val i: Double, val t: Double)

    @Serializable
    data class Extrem(val k: String, val f: String, val r: Double)

    // Ergebnis der Rechnung tools/sec/cluster_rueckschau.py. Renditen als Anteil (0,04 = 4 %). kurve: je Handelstag
    // [Tag, Cluster, Index] im Mittel aller Cluster.
    @Serializable
    data class Cluster(
        val stand: String, val ab: String, val halten: Int, val fenster: Int, val mindest: Int,
        val cluster: Int, val ausgewertet: Int, val ohneKurse: Int = 0, val offen: Int = 0,
        val rendite: Double, val index: Double, val median: Double, val indexMedian: Double, val treffer: Double,
        val schlechtester: Extrem, val bester: Extrem,
        val jahre: List<ClusterJahr>, val kurve: List<List<Double>>,
    )

    fun clusterLesen(text: String): Cluster? = runCatching {
        json.decodeFromString(Cluster.serializer(), text).takeIf { it.ausgewertet > 0 && it.jahre.isNotEmpty() && it.kurve.size >= 2 && it.kurve.all { p -> p.size == 3 } }
    }.getOrNull()

    // ------------------------------------------------------------ Stillhalter-Puts

    // Ein protokollierter Put: Aktie, Zeitpunkt (ms), Verfall (Sekunden), Basispreis, Praemie (Geldkurs), Kurs der Aktie
    // und Rendite aufs Jahr bei der Aufnahme; schluss ist der Schlusskurs am Verfall, null solange er aussteht.
    @Serializable
    data class Put(
        val s: String, val am: Long, val verfall: Long, val strike: Double, val praemie: Double, val kurs: Double,
        val jahr: Double, val schluss: Double? = null,
    ) {
        val tage: Int get() = max(1, ((verfall - am / 1000) / TAG_S).toInt())

        // Praemie je Basispreis bis zum Verfall.
        val nominal: Double get() = praemie / strike

        // Praemie minus Verlust bei Ausuebung (Basispreis minus Schlusskurs, nie unter null) je Basispreis.
        val realisiert: Double? get() = schluss?.let { (praemie - max(0.0, strike - it)) / strike }
    }

    @Serializable
    private data class Datei(val format: Int = 1, val eintraege: List<Put> = emptyList())

    const val HOECHSTENS = 3000
    const val JE_LAUF = 5

    fun protokollLesen(text: String): List<Put>? = runCatching {
        json.decodeFromString(Datei.serializer(), text).takeIf { it.format == 1 }?.eintraege
    }.getOrNull()

    fun protokollSchreiben(liste: List<Put>): String =
        json.encodeToString(Datei.serializer(), Datei(1, liste.sortedBy { it.am }.takeLast(HOECHSTENS)))

    // Der Put mit der hoechsten Rendite aufs Jahr aus der Kette einer Aktie (Auswahl und Rechnung wie im
    // Stillhalter-Rechner der Optionskarte); null ohne Kette oder Put mit Geldkurs.
    fun besterPut(symbol: String, kurs: Double, kontrakte: List<Optionsanalyse.Kontrakt>, jetztMs: Long): Put? {
        val st = Optionsanalyse.stillhalter(kurs, kontrakte, jetztMs / 1000) ?: return null
        val z = st.puts.maxByOrNull { it.jahr } ?: return null
        if (!(z.strike > 0 && z.praemie > 0)) return null
        return Put(symbol, jetztMs, st.verfall, z.strike, z.praemie, kurs, z.jahr)
    }

    // Die JE_LAUF Puts mit hoechster Jahresrendite aus den Treffern je Aktie, ohne bereits protokollierte
    // (gleiche Aktie, Verfall und Basispreis).
    fun auswaehlen(kandidaten: List<Put>, vorhanden: List<Put>): List<Put> =
        kandidaten.filter { k -> vorhanden.none { it.s == k.s && it.verfall == k.verfall && it.strike == k.strike } }
            .sortedByDescending { it.jahr }.take(JE_LAUF)

    // Am Verfall faellig und noch ohne Schlusskurs: erst eineinhalb Tage nach dem Verfallstag.
    fun faellig(p: Put, jetztMs: Long): Boolean = p.schluss == null && jetztMs >= p.verfall * 1000 + 36 * 3_600_000L

    data class Bilanz(
        val gesamt: Int, val offen: Int, val fertig: Int,
        // Mittel der Rendite aufs Jahr, nominal bei der Aufnahme gegen realisiert am Verfall (nur abgeschlossene)
        val nominalJahr: Double?, val realisiertJahr: Double?,
        // Mittel je Put ueber die Laufzeit
        val nominal: Double?, val realisiert: Double?,
        // Anteil der abgeschlossenen Puts, die netto Verlust brachten, und der schlechteste
        val verlustAnteil: Double?, val schlechtester: Double?,
    )

    fun bilanz(liste: List<Put>): Bilanz {
        val fertig = liste.filter { it.realisiert != null }
        if (fertig.isEmpty()) return Bilanz(liste.size, liste.size, 0, null, null, null, null, null, null)
        fun jahr(r: Double, p: Put) = r * 365.0 / p.tage
        val real = fertig.map { it.realisiert!! }
        return Bilanz(
            liste.size, liste.size - fertig.size, fertig.size,
            fertig.map { jahr(it.nominal, it) }.average(), fertig.map { jahr(it.realisiert!!, it) }.average(),
            fertig.map { it.nominal }.average(), real.average(),
            real.count { it < 0 }.toDouble() / real.size, real.min(),
        )
    }

    // Die Aktien, deren Puts protokolliert werden: liquide US-Werte mit breiter Optionskette.
    val UNIVERSUM = listOf(
        "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "AMD", "NFLX", "AVGO", "JPM", "BAC", "XOM", "CVX", "PFE",
        "KO", "PEP", "WMT", "DIS", "INTC", "CSCO", "ORCL", "CRM", "UBER", "PLTR", "COIN", "SOFI", "F", "T", "MU",
    )

    // Nach so vielen Stunden darf die Seite wieder neue Puts aufnehmen.
    const val ABSTAND_STUNDEN = 20

    fun neuAufnehmen(liste: List<Put>, jetztMs: Long): Boolean {
        val letzte = liste.maxOfOrNull { it.am } ?: return true
        return jetztMs - letzte >= ABSTAND_STUNDEN * 3_600_000L || letzte > jetztMs + 60_000L
    }

    // ------------------------------------------------------------ Kongress kopieren

    // Unterschied der jaehrlichen Rendite der Strategie „Kongress kauft“ zum S&P 500 in Prozentpunkten; null, wenn eine
    // der beiden Renditen fehlt.
    fun kongressAbstand(s: KongressAuswertung.Strategie): Double? {
        val a = s.renditePa ?: return null
        val b = s.vergleichPa ?: return null
        return (a - b) * 100.0
    }

    // ------------------------------------------------------------ IPO-Statistik

    // Eine Zeile je Jahr aus den Tabellen von Jay R. Ritter. Prozentwerte als Prozent (14,3 = 14,3 %): t1 mittlere
    // Rendite am ersten Tag, neg Anteil mit Verlust am ersten Tag, j1 und j3 mittlere Rendite nach ein und drei Jahren ab
    // dem ersten Schluss, m3 die drei Jahre abzueglich Markt; null, wo die Jahre noch nicht vorbei sind.
    @Serializable
    data class IpoJahr(val j: Int, val n: Int, val t1: Double, val neg: Double, val j1: Double? = null, val j3: Double? = null, val m3: Double? = null)

    @Serializable
    data class Ipo(val quelle: String = "", val jahre: List<IpoJahr>)

    fun ipoLesen(text: String): Ipo? = runCatching { json.decodeFromString(Ipo.serializer(), text).takeIf { it.jahre.isNotEmpty() } }.getOrNull()

    // Gesamtwerte ueber alle Jahre, nach Zahl der Boersengaenge gewichtet; m3 nur ueber Jahre mit Wert, m3Anteil ist der
    // Anteil dieser Jahre mit negativem Wert.
    data class IpoGesamt(val von: Int, val bis: Int, val n: Int, val t1: Double, val neg: Double, val m3: Double?, val m3Anteil: Double?)

    fun ipoGesamt(i: Ipo): IpoGesamt {
        val n = i.jahre.sumOf { it.n }
        val mit = i.jahre.filter { it.m3 != null }
        val nm = mit.sumOf { it.n }
        return IpoGesamt(
            i.jahre.minOf { it.j }, i.jahre.maxOf { it.j }, n,
            i.jahre.sumOf { it.t1 * it.n } / n, i.jahre.sumOf { it.neg * it.n } / n,
            if (nm > 0) mit.sumOf { it.m3!! * it.n } / nm else null,
            if (mit.isNotEmpty()) mit.count { it.m3!! < 0 }.toDouble() / mit.size else null,
        )
    }

    // Groesster Betrag einer Reihe, fuer die Laenge der Balken.
    fun spanne(werte: List<Double>): Double = max(1e-9, werte.maxOfOrNull { abs(it) } ?: 0.0)
}
