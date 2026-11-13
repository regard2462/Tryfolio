package de.troetenrepublik.app.engine

// Vierer-Indikator nach Gebert (0.78): einmal im Monat vier Fragen, je Ja ein Punkt.
// 1. Zins: Die letzte Aenderung des EZB-Einlagesatzes war eine Senkung.
// 2. Inflation: Die Jahresrate im Euroraum (HVPI) liegt unter der des Vorjahresmonats.
// 3. Dollar: Der Dollar steht zum Euro hoeher als vor einem Jahr (ein Euro kostet weniger Dollar).
// 4. Saison: Der Monat liegt zwischen November und April.
// Ab drei Ja gilt „investiert“, ab drei Nein „nicht investiert“, bei zwei zu zwei bleibt das letzte Signal.
// Stichtag ist der erste Tag des Monats; fuer die Inflation zaehlt der juengste Monat davor, der in der Reihe steht.
// Alle Tage als Tagesnummer seit 1970.
object Vierer {

    const val ZINS = "zins"
    const val INFLATION = "inflation"
    const val DOLLAR = "dollar"
    const val SAISON = "saison"

    // Einlagesatz der EZB je Tag (dieselbe Reihe wie der Zins des Verrechnungskontos), ab 2015 fuer die Richtung
    // der letzten Aenderung auch in frueheren Monaten.
    const val ZINS_ADRESSE = "https://data-api.ecb.europa.eu/service/data/FM/D.U2.EUR.4F.KR.DFR.LEV?startPeriod=2015-01-01&format=csvdata&detail=dataonly"
    const val EURUSD = "EURUSD=X"
    const val EZB_SEITE = "https://data.ecb.europa.eu"

    private const val TAG = 86_400_000L

    // Tagesnummer des ersten Tags eines Monats (monat 1 bis 12).
    fun monatsTag(jahr: Int, monat: Int): Long {
        val y = if (monat <= 2) jahr - 1 else jahr
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * ((monat + 9) % 12) + 2) / 5
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    // Tagesnummer aus „2019-09-18“; null bei anderem Format.
    fun tagLesen(text: String): Long? {
        val t = text.trim().split('-')
        if (t.size != 3) return null
        val j = t[0].toIntOrNull() ?: return null
        val m = t[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val d = t[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        return monatsTag(j, m) + d - 1
    }

    // Stufen des Einlagesatzes aus der CSV der EZB (Spalten TIME_PERIOD und OBS_VALUE): der erste Wert und jeder Tag,
    // an dem sich der Satz aendert, aelteste zuerst.
    fun zinsLesen(csv: String): List<Pair<Long, Double>> {
        val zeilen = csv.lines().filter { it.isNotBlank() }
        if (zeilen.size < 2) return emptyList()
        val kopf = zeilen[0].split(',').map { it.trim().trim('"') }
        val iZeit = kopf.indexOf("TIME_PERIOD")
        val iWert = kopf.indexOf("OBS_VALUE")
        if (iZeit < 0 || iWert < 0) return emptyList()
        val alle = zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',')
            val t = f.getOrNull(iZeit)?.trim('"', ' ')?.let(::tagLesen) ?: return@mapNotNull null
            val w = f.getOrNull(iWert)?.trim('"', ' ')?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            t to w
        }.sortedBy { it.first }
        val aus = ArrayList<Pair<Long, Double>>()
        for (p in alle) if (aus.isEmpty() || aus.last().second != p.second) aus += p
        return aus
    }

    // Kurzform zum Speichern: je Zeile „Schluessel,Wert“.
    fun stufenText(stufen: List<Pair<Long, Double>>): String = stufen.joinToString("\n") { "${it.first},${it.second}" }

    fun stufenLesen(text: String): List<Pair<Long, Double>> = text.lines().mapNotNull { z ->
        val f = z.split(',')
        val t = f.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val w = f.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        t to w
    }

    fun indexText(monate: List<Kaufkraft.Monat>): String = monate.joinToString("\n") { "${it.m},${it.w}" }

    // Preisindex je Monat, Schluessel Jahr mal zwoelf plus Monat minus eins.
    fun indexLesen(text: String): Map<Int, Double> = text.lines().mapNotNull { z ->
        val f = z.split(',')
        val m = f.getOrNull(0)?.trim()?.split('-')?.takeIf { it.size == 2 } ?: return@mapNotNull null
        val j = m[0].toIntOrNull() ?: return@mapNotNull null
        val mo = m[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
        val w = f.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        (j * 12 + mo - 1) to w
    }.toMap()

    fun index(monate: List<Kaufkraft.Monat>): Map<Int, Double> = indexLesen(indexText(monate))

    // Eine Frage mit Antwort. wert und vergleich: Zins nach und vor der Aenderung in Prozent, Jahresrate der Inflation
    // jetzt und im Vorjahresmonat in Prozent, Dollar je Euro jetzt und vor einem Jahr, bei der Saison der Monat.
    // stand: Tag der Zinsaenderung, erster Tag des Inflationsmonats, Tag des Wechselkurses, Stichtag.
    data class Frage(val schluessel: String, val ja: Boolean, val wert: Double, val vergleich: Double, val stand: Long)

    enum class Signal { INVESTIERT, NICHT_INVESTIERT }

    // Ergebnis fuer einen Monat. signal: null, solange es bei zwei zu zwei kein frueheres Signal gibt. seitJahr und
    // seitMonat: Monat, in dem das Signal zuletzt gewechselt hat (0 ohne Signal).
    data class Ergebnis(
        val jahr: Int, val monat: Int, val fragen: List<Frage>, val signal: Signal?, val seitJahr: Int, val seitMonat: Int,
    ) {
        val ja: Int get() = fragen.count { it.ja }
        val uebernommen: Boolean get() = ja == 2
    }

    // Signal aus der Zahl der Ja: ab drei investiert, hoechstens eins nicht investiert, bei zwei das bisherige.
    fun signal(ja: Int, bisher: Signal?): Signal? = when {
        ja >= 3 -> Signal.INVESTIERT
        ja <= 1 -> Signal.NICHT_INVESTIERT
        else -> bisher
    }

    // Letzter Kurs bis zum Tag, hoechstens zehn Tage alt.
    private fun kursBis(kurse: List<Pair<Long, Double>>, tag: Long): Pair<Long, Double>? {
        var lo = 0
        var hi = kurse.size - 1
        var treffer = -1
        while (lo <= hi) {
            val mitte = (lo + hi) ushr 1
            if (kurse[mitte].first <= tag) { treffer = mitte; lo = mitte + 1 } else hi = mitte - 1
        }
        return kurse.getOrNull(treffer)?.takeIf { tag - it.first <= 10 && it.second > 0 }
    }

    // Die vier Fragen fuer einen Monat; null, wenn eine Groesse fehlt.
    fun fragen(jahr: Int, monat: Int, stufen: List<Pair<Long, Double>>, index: Map<Int, Double>, kurse: List<Pair<Long, Double>>): List<Frage>? {
        val tag = monatsTag(jahr, monat)
        val i = stufen.indexOfLast { it.first <= tag }
        if (i < 1) return null
        val zins = Frage(ZINS, stufen[i].second < stufen[i - 1].second, stufen[i].second, stufen[i - 1].second, stufen[i].first)
        val k = jahr * 12 + monat - 1
        val mi = (k - 1 downTo k - 3).firstOrNull { index[it] != null && index[it - 12] != null && index[it - 24] != null } ?: return null
        val rate = (index.getValue(mi) / index.getValue(mi - 12) - 1) * 100
        val vorjahr = (index.getValue(mi - 12) / index.getValue(mi - 24) - 1) * 100
        val inflation = Frage(INFLATION, rate < vorjahr, rate, vorjahr, monatsTag(mi / 12, mi % 12 + 1))
        val jetzt = kursBis(kurse, tag) ?: return null
        val alt = kursBis(kurse, monatsTag(jahr - 1, monat)) ?: return null
        val dollar = Frage(DOLLAR, jetzt.second < alt.second, jetzt.second, alt.second, jetzt.first)
        val saison = Frage(SAISON, monat >= 11 || monat <= 4, monat.toDouble(), 0.0, tag)
        return listOf(zins, inflation, dollar, saison)
    }

    // Wie viele Monate zurueck das letzte klare Signal gesucht wird.
    const val RUECKBLICK = 180

    // Ergebnis fuer den Monat des Tags heute. stufen: Stufen des Einlagesatzes, index: Preisindex je Monat,
    // kurse: Dollar je Euro als (Tag, Schluss), aelteste zuerst. Die Monate davor liefern das Signal, das bei zwei zu
    // zwei weiter gilt; Monate ohne vollstaendige Daten aendern nichts. Null, wenn fuer den laufenden Monat etwas fehlt.
    fun rechnen(stufen: List<Pair<Long, Double>>, index: Map<Int, Double>, kurse: List<Pair<Long, Double>>, heute: Long): Ergebnis? {
        val (j, m) = Fruehwarnung.jahrMonat(heute * TAG)
        val ende = j * 12 + m - 1
        val sortiert = kurse.sortedBy { it.first }
        var signal: Signal? = null
        var seit = 0
        var letzte: List<Frage>? = null
        for (k in ende - RUECKBLICK..ende) {
            val f = fragen(k / 12, k % 12 + 1, stufen, index, sortiert)
            if (k == ende) letzte = f
            if (f == null) continue
            val neu = signal(f.count { it.ja }, signal)
            if (neu != signal) { signal = neu; seit = k }
        }
        val f = letzte ?: return null
        return Ergebnis(j, m, f, signal, if (signal == null) 0 else seit / 12, if (signal == null) 0 else seit % 12 + 1)
    }
}
