package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Rechnungen fuer Paket q78h (0.76): Beitraege der Bestaende zur Tagesbewegung von ETFs und Indizes, Risikokennzahlen
// fuer den Aktienvergleich, Kursziel-Spanne der Analysten, Abweichung im Wirtschaftskalender, Breite der Watchlist,
// Paare je Branche und der Filter fuer die Fuehrung bei den Insidern. Alles Lehrbuchrechnung, ohne Wertung.
object Beitraege {
    // anteil: Anteil an der Bezugsgroesse (0,05 = 5 %), aenderung: Tagesaenderung des Bestands in Prozent. beitrag in
    // Prozentpunkten der Bezugsgroesse (Anteil mal Aenderung).
    data class Eintrag(val symbol: String, val name: String, val anteil: Double, val aenderung: Double) {
        val beitrag: Double get() = anteil * aenderung
    }

    // Anteile auf Summe 1 bringen (Indexgewicht nach Boersenwert); Werte ohne Gewicht bleiben 0.
    fun normiert(gewichte: List<Double>): List<Double> {
        val s = gewichte.filter { it > 0 && it.isFinite() }.sum()
        return if (s <= 0) gewichte.map { 0.0 } else gewichte.map { if (it > 0 && it.isFinite()) it / s else 0.0 }
    }

    fun summe(l: List<Eintrag>): Double = l.sumOf { it.beitrag }

    // Die Eintraege mit dem groessten Betrag des Beitrags, davon hoechstens je Richtung n, nach Beitrag sortiert.
    fun groesste(l: List<Eintrag>, n: Int): List<Eintrag> {
        val plus = l.filter { it.beitrag > 0 }.sortedByDescending { it.beitrag }.take(n)
        val minus = l.filter { it.beitrag < 0 }.sortedBy { it.beitrag }.take(n)
        return (plus + minus).sortedByDescending { it.beitrag }
    }

    // Kuerzel der Indizes in Yahoo-Schreibweise zum Schluessel der Mitglieder im Katalog.
    private val INDEX = mapOf(
        "^GDAXI" to "DAX", "^MDAXI" to "MDAX", "^TECDAX" to "TECDAX", "^SDAXI" to "SDAX", "^GSPC" to "SP500", "^NDX" to "NDX",
        "^DJI" to "DJIA", "^STOXX50E" to "ESTX50", "^FTSE" to "FTSE100", "^FCHI" to "CAC40", "^SSMI" to "SMI", "^AEX" to "AEX",
        "^IBEX" to "IBEX35", "FTSEMIB.MI" to "FTSEMIB", "^ATX" to "ATX", "^OMXS30" to "OMXS30", "^OMXC25" to "OMXC25",
        "^OMXH25" to "OMXH25", "OBX.OL" to "OBX", "^N225" to "NIKKEI225",
    )

    fun indexSchluessel(symbol: String): String? = INDEX[symbol.uppercase()]
}

object Risikokennzahlen {
    data class Ergebnis(val volatilitaet: Double, val maxRueckgang: Double)

    // Aus Tagesschlusskursen (aelteste zuerst) der letzten bis zu 252 Handelstage: Schwankung als Standardabweichung der
    // logarithmischen Tagesrenditen mal Wurzel aus 252, in Prozent; groesster Rueckgang vom Hoch zum folgenden Tief in
    // Prozent (negativ). Null bei weniger als 30 Kursen.
    fun berechne(schluss: List<Double>): Ergebnis? {
        val k = schluss.filter { it > 0 && it.isFinite() }.takeLast(253)
        if (k.size < 30) return null
        val r = (1 until k.size).map { ln(k[it] / k[it - 1]) }
        val m = r.average()
        val v = r.sumOf { (it - m) * (it - m) } / (r.size - 1)
        var hoch = k[0]
        var tief = 0.0
        for (c in k) {
            if (c > hoch) hoch = c
            val d = (c / hoch - 1.0) * 100.0
            if (d < tief) tief = d
        }
        return Ergebnis(sqrt(v) * sqrt(252.0) * 100.0, tief)
    }
}

object KurszielLage {
    // Lage von Kurs und Zielwerten auf einer Skala von 0 bis 1 ueber die Spanne aller Werte (mit etwas Rand).
    data class Lage(val tief: Double, val hoch: Double, val skalaVon: Double, val skalaBis: Double, val kurs: Double, val median: Double?) {
        private fun f(x: Double) = ((x - skalaVon) / (skalaBis - skalaVon)).coerceIn(0.0, 1.0).toFloat()
        val tiefPos: Float get() = f(tief)
        val hochPos: Float get() = f(hoch)
        val kursPos: Float get() = f(kurs)
        val medianPos: Float? get() = median?.let { f(it) }
        // Abstand des Kurses zum Median in Prozent des Medians (negativ: Kurs unter dem Median)
        val abweichung: Double? get() = median?.takeIf { it > 0 }?.let { (kurs / it - 1.0) * 100.0 }
    }

    // Null ohne gueltige Spanne oder Kurs.
    fun lage(kurs: Double, tief: Double, hoch: Double, median: Double?): Lage? {
        if (kurs <= 0 || tief <= 0 || hoch < tief || !kurs.isFinite()) return null
        val alle = listOfNotNull(kurs, tief, hoch, median?.takeIf { it > 0 })
        val von = alle.min()
        val bis = alle.max()
        val rand = (bis - von).takeIf { it > 0 }?.times(0.06) ?: bis * 0.05
        return Lage(tief, hoch, von - rand, bis + rand, kurs, median?.takeIf { it > 0 })
    }

    // Hoch- und Abstufungen innerhalb der letzten tage Tage.
    fun stufen(urteile: List<Analystenbilanz.Urteil>, jetzt: Long, tage: Int): Pair<Int, Int> {
        val ab = jetzt - tage * 86_400_000L
        val l = urteile.filter { it.zeit >= ab }
        return l.count { it.aktion == "up" } to l.count { it.aktion == "down" }
    }
}

object Abweichung {
    // Zahl und Zusatz aus einer Angabe wie „3.2%“, „$1.2B“, „-0.5%“, „250K“, „(0.3)%“; Tausendertrenner Komma.
    private val ZAHL = Regex("^\\(?([-+]?)\\$?([-+]?[0-9][0-9,]*\\.?[0-9]*)\\)?\\s*(.*)$")

    private fun lesen(s: String): Pair<Double, String>? {
        val m = ZAHL.find(s.trim()) ?: return null
        val z = (m.groupValues[1] + m.groupValues[2]).replace(",", "").toDoubleOrNull() ?: return null
        return z to m.groupValues[3].trim()
    }

    // Unterschied zwischen gemeldetem Wert und Erwartung mit dem Zusatz des gemeldeten Werts; null, wenn eine Angabe keine
    // Zahl ist oder die Zusaetze verschieden sind.
    data class Diff(val betrag: Double, val zusatz: String, val nachkommastellen: Int)

    fun diff(ist: String?, erwartet: String?): Diff? {
        if (ist == null || erwartet == null) return null
        val a = lesen(ist) ?: return null
        val b = lesen(erwartet) ?: return null
        if (a.second != b.second) return null
        val stellen = maxOf(stellen(ist), stellen(erwartet))
        val d = a.first - b.first
        return Diff(if (abs(d) < 10.0.pow(-stellen) / 2) 0.0 else d, a.second, stellen)
    }

    private fun stellen(s: String): Int {
        val z = ZAHL.find(s.trim())?.groupValues?.get(2) ?: return 0
        return if ('.' in z) z.substringAfter('.').length else 0
    }
}

object WatchBreite {
    data class Ergebnis(val steigen: Int, val fallen: Int, val gleich: Int, val mittel: Double?)

    // Aus den Tagesaenderungen in Prozent (null = kein Kurs): wie viele steigen, fallen, bleiben, und das einfache Mittel.
    fun berechne(aenderungen: List<Double?>): Ergebnis {
        val w = aenderungen.filterNotNull().filter { it.isFinite() }
        return Ergebnis(w.count { it > 0.005 }, w.count { it < -0.005 }, w.count { it in -0.005..0.005 }, w.takeIf { it.isNotEmpty() }?.average())
    }
}

object Paare {
    // Paare aus den ersten vier Kandidaten (nach Boersenwert sortiert), je zwei benachbarte und die beiden Groessten:
    // bei vier Kandidaten a,b,c,d die Paare a-b, c-d, a-c. Hoechstens drei Paare.
    fun bilden(kandidaten: List<String>): List<Pair<String, String>> {
        val k = kandidaten.distinct().take(4)
        return buildList {
            if (k.size >= 2) add(k[0] to k[1])
            if (k.size >= 4) add(k[2] to k[3])
            if (k.size >= 3) add(k[0] to k[2])
        }
    }
}

object InsiderFuehrung {
    // Rollen der Fuehrung: Vorstand und andere Fuehrungskraefte, ohne Aufsicht und Grossaktionaere.
    val ROLLEN: Set<InsiderTrend.Rolle> = setOf(InsiderTrend.Rolle.SPITZE, InsiderTrend.Rolle.FUEHRUNG)

    fun aktiv(rollen: Set<InsiderTrend.Rolle>): Boolean = rollen == ROLLEN
}
