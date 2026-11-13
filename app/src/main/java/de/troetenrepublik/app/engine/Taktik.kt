package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Catalog

// Taktische Regeln im Strategietest (0.78): das Signal einer Regel auf einem anderen Wert als dem gehandelten und ein
// Ausweichwert, der bei „Signal aus“ statt Bargeld gehalten wird. Die Rechnung selbst steht in StrategieLabor
// (Daten.signal, Daten.ausweich); hier werden die Kursreihen auf gemeinsame Handelstage gebracht.
object Taktik {

    private const val TAG_MS = 86_400_000L

    // Die Reihen auf die Handelstage gekuerzt, die alle haben (Kalendertag in UTC), in zeitlicher Folge. Je Reihe und
    // Tag zaehlt die letzte Kerze. Alle Ergebnisse sind gleich lang; Index i meint in jeder denselben Tag.
    fun gemeinsam(reihen: List<List<Candle>>): List<List<Candle>> {
        if (reihen.isEmpty()) return emptyList()
        val jeTag = reihen.map { r -> HashMap<Long, Candle>(r.size * 2).also { m -> r.forEach { c -> m[c.t.floorDiv(TAG_MS)] = c } } }
        val kleinste = jeTag.minBy { it.size }
        val tage = kleinste.keys.filter { t -> jeTag.all { it.containsKey(t) } }.sorted()
        return jeTag.map { m -> tage.map { m.getValue(it) } }
    }

    // Gehandelter Wert, Signalwert und Ausweichwert auf gemeinsamen Handelstagen; signal und ausweich null ohne Wahl.
    class Reihen(val handel: List<Candle>, val signal: List<Candle>?, val ausweich: List<Candle>?) {
        // Daten fuer die ersten n gemeinsamen Tage (ein eigener Zeitraum endet vor der letzten Kerze).
        fun daten(n: Int = handel.size): StrategieLabor.Daten = StrategieLabor.Daten(
            handel.subList(0, n),
            signal = signal?.let { StrategieLabor.Daten(it.subList(0, n)) },
            ausweich = ausweich?.let { StrategieLabor.Daten(it.subList(0, n)) },
        )
    }

    fun reihen(handel: List<Candle>, signal: List<Candle>?, ausweich: List<Candle>?): Reihen {
        val g = gemeinsam(listOfNotNull(handel, signal, ausweich))
        var i = 1
        return Reihen(g[0], signal?.let { g[i++] }, ausweich?.let { g[i] })
    }

    private fun erster(etfs: List<Catalog.Etf>, art: String, region: String? = null, wort: String? = null): String? =
        etfs.firstOrNull { it.i == art && (region == null || it.r == region) && (wort == null || it.n.contains(wort, ignoreCase = true)) }?.s

    // Vorschlaege fuer den Ausweichwert aus dem Katalog: Geldmarkt, Staatsanleihen Europa und Nordamerika,
    // Unternehmensanleihen, Gold; je Gruppe der erste Eintrag.
    fun ausweichVorschlaege(etfs: List<Catalog.Etf>): List<String> = listOfNotNull(
        erster(etfs, "GELDMARKT"),
        erster(etfs, "STAATSANLEIHEN", "EUROPA"),
        erster(etfs, "STAATSANLEIHEN", "NORDAMERIKA"),
        erster(etfs, "UNTERNEHMENSANLEIHEN"),
        erster(etfs, "ROHSTOFFE", wort = "Gold"),
    ).distinct()

    // Kleine ETF-Auswahl fuer die Rotation: je Region der erste breite Aktien-ETF, dazu Staatsanleihen und Gold.
    fun rotationAuswahl(etfs: List<Catalog.Etf>): List<String> = (
        listOf("NORDAMERIKA", "EUROPA", "SCHWELLENLAENDER", "ASIEN_PAZIFIK").mapNotNull { erster(etfs, "SEKTORUEBERGREIFEND", it) } +
            listOfNotNull(erster(etfs, "STAATSANLEIHEN", "EUROPA"), erster(etfs, "ROHSTOFFE", wort = "Gold"))
        ).distinct()

    // Vorbelegung der Rotation: Depot, dann Watchlist, ohne Doppelte; mit weniger als zwei Werten die ETF-Auswahl.
    fun vorbelegung(depot: List<String>, watchlist: List<String>, auswahl: List<String>): List<String> =
        (depot + watchlist).distinct().take(Rotation.MAX_WERTE).takeIf { it.size >= Rotation.MIN_WERTE } ?: auswahl.take(Rotation.MAX_WERTE)
}
