package de.troetenrepublik.app.engine

// Rueckblick des Put/Call-Verhaeltnisses der Aktienoptionen [0.78]: Archivdatei der Cboe mit einem Wert je Handelstag
// (November 2006 bis Oktober 2019). Die Bedingungen der Cboe erlauben eine Kopie fuer den persoenlichen, nicht
// kommerziellen Gebrauch, aber weder Weitergabe noch Ablage in einem Datenbestand: die App laedt die Datei deshalb erst
// auf dem Geraet, haelt sie nur im Arbeitsspeicher und liefert nichts davon mit. Hier stehen Adresse, Leser und Rechnung.
object PutCallRueckblick {

    const val ADRESSE = "https://cdn.cboe.com/resources/options/volume_and_call_put_ratios/equitypc.csv"
    const val SEITE = "https://www.cboe.com/us/options/market_statistics/historical_data/"

    // Breite einer Klasse der Verteilung und ihr Bereich (Werte darunter und darueber zaehlen zur ersten und letzten Klasse)
    const val SCHRITT = 0.05
    const val UNTEN = 0.35
    const val OBEN = 1.10
    const val MINDEST_TAGE = 250

    // werte: aufsteigend sortiert; vonJahr und bisJahr: erstes und letztes Jahr mit Wert
    class Archiv(val werte: List<Double>, val vonJahr: Int, val bisJahr: Int)

    // Zeilen "M/T/JJJJ,CALL,PUT,TOTAL,P/C Ratio"; alles davor (Hinweistext, Kopf) und unlesbare Zeilen werden uebergangen.
    // null bei weniger als MINDEST_TAGE Tagen.
    fun lesen(csv: String): Archiv? {
        val werte = ArrayList<Double>()
        var von = Int.MAX_VALUE
        var bis = Int.MIN_VALUE
        for (zeile in csv.lineSequence()) {
            val t = zeile.split(',')
            if (t.size < 5) continue
            val datum = t[0].trim().split('/')
            if (datum.size != 3) continue
            val monat = datum[0].toIntOrNull() ?: continue
            val tag = datum[1].toIntOrNull() ?: continue
            val jahr = datum[2].toIntOrNull() ?: continue
            if (monat !in 1..12 || tag !in 1..31 || jahr !in 1990..2100) continue
            val v = t[t.size - 1].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it < 10.0 } ?: continue
            werte += v
            if (jahr < von) von = jahr
            if (jahr > bis) bis = jahr
        }
        if (werte.size < MINDEST_TAGE) return null
        werte.sort()
        return Archiv(werte, von, bis)
    }

    // Anteil der Tage im Archiv mit einem kleineren Wert, Prozent
    fun rang(a: Archiv, wert: Double): Double {
        var lo = 0
        var hi = a.werte.size
        while (lo < hi) {
            val mitte = (lo + hi) ushr 1
            if (a.werte[mitte] < wert) lo = mitte + 1 else hi = mitte
        }
        return lo * 100.0 / a.werte.size
    }

    fun median(a: Archiv): Double {
        val n = a.werte.size
        return if (n % 2 == 1) a.werte[n / 2] else (a.werte[n / 2 - 1] + a.werte[n / 2]) / 2.0
    }

    // Zahl der Klassen der Verteilung
    val KLASSEN: Int = Math.round((OBEN - UNTEN) / SCHRITT).toInt()

    // Klasse eines Werts (0 bis KLASSEN - 1)
    fun klasse(wert: Double): Int = Math.floor((wert - UNTEN) / SCHRITT + 1e-9).toInt().coerceIn(0, KLASSEN - 1)

    // Anteil der Tage je Klasse, Prozent
    fun verteilung(a: Archiv): List<Double> {
        val n = IntArray(KLASSEN)
        for (v in a.werte) n[klasse(v)]++
        return n.map { it * 100.0 / a.werte.size }
    }
}
