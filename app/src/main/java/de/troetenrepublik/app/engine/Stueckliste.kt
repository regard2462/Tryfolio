package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Stueckliste zu Auftraegen in Euro: aus Betrag und Kurs je Wert werden kaufbare Stueckzahlen. Verkaeufe werden auf
// ganze Stuecke abgerundet, Kaeufe ebenfalls; was vom Geld uebrig bleibt, geht Stueck fuer Stueck an den Wert mit dem
// groessten Abstand zum Zielbetrag, solange ein weiteres Stueck naeher an das Ziel fuehrt als keines. Der Rest bleibt
// als Restbetrag stehen. Mit Bruchstuecken wird jeder Betrag genau getroffen. Ordergebuehren sind nicht eingerechnet.
object Stueckliste {

    // betrag: Auftrag in Euro, positiv kaufen, negativ verkaufen. bestand: Stuecke im Depot (Grenze beim Verkauf),
    // null ohne Grenze.
    data class Posten(val key: String, val preis: Double, val betrag: Double, val bestand: Double? = null)

    // stuecke und betrag mit Vorzeichen wie der Auftrag; betrag ist stuecke mal Kurs.
    data class Zeile(val key: String, val preis: Double, val stuecke: Double, val betrag: Double)

    // budget: Geld fuer die Kaeufe (Kaufsumme abzueglich dessen, was die abgerundeten Verkaeufe weniger einbringen).
    // rest: nicht angelegter Teil des Budgets.
    data class Ergebnis(val zeilen: List<Zeile>, val budget: Double, val rest: Double)

    private const val EPS = 1e-9

    fun rechnen(posten: List<Posten>, bruchstuecke: Boolean): Ergebnis {
        val gueltig = posten.filter { it.preis.isFinite() && it.preis > 0 && it.betrag.isFinite() }
        val stuecke = HashMap<String, Double>()
        // Verkaeufe
        var sollErloes = 0.0
        var erloes = 0.0
        for (p in gueltig) {
            if (p.betrag >= 0) continue
            sollErloes += -p.betrag
            var n = -p.betrag / p.preis
            if (!bruchstuecke) n = floor(n + EPS)
            if (p.bestand != null) n = min(n, if (bruchstuecke) p.bestand else floor(p.bestand + EPS))
            n = max(0.0, n)
            stuecke[p.key] = -n
            erloes += n * p.preis
        }
        // Kaeufe
        val kaeufe = gueltig.filter { it.betrag > 0 }
        val budget = max(0.0, kaeufe.sumOf { it.betrag } - (sollErloes - erloes))
        var rest = budget
        if (bruchstuecke) {
            val summe = kaeufe.sumOf { it.betrag }
            val faktor = if (summe > budget && summe > 0) budget / summe else 1.0
            for (p in kaeufe) { stuecke[p.key] = p.betrag * faktor / p.preis; rest -= p.betrag * faktor }
        } else {
            for (p in kaeufe) { val n = floor(p.betrag / p.preis + EPS); stuecke[p.key] = n; rest -= n * p.preis }
            fun luecke(p: Posten) = p.betrag - (stuecke[p.key] ?: 0.0) * p.preis
            // Reicht das Budget nach abgerundeten Verkaeufen nicht: dort ein Stueck weniger, wo am wenigsten fehlt
            while (rest < -EPS) {
                val p = kaeufe.filter { (stuecke[it.key] ?: 0.0) >= 1.0 }.minWithOrNull(compareBy<Posten> { luecke(it) }.thenBy { it.key }) ?: break
                stuecke[p.key] = stuecke.getValue(p.key) - 1.0
                rest += p.preis
            }
            // Restgeld: ein weiteres Stueck dort, wo am meisten fehlt, wenn es bezahlbar ist und naeher ans Ziel fuehrt
            while (true) {
                val p = kaeufe.filter { it.preis <= rest + EPS && luecke(it) > it.preis / 2 }
                    .maxWithOrNull(compareBy<Posten> { luecke(it) }.thenByDescending { it.key }) ?: break
                stuecke[p.key] = stuecke.getValue(p.key) + 1.0
                rest -= p.preis
            }
        }
        val zeilen = gueltig.mapNotNull { p ->
            val n = stuecke[p.key] ?: 0.0
            if (abs(n) < EPS) null else Zeile(p.key, p.preis, n, n * p.preis)
        }
        return Ergebnis(zeilen, budget, max(0.0, rest))
    }

    // Aus Gewichten (beliebige nichtnegative Zahlen) und einem Betrag: Zielbetrag je Wert, dann wie oben.
    fun ausGewichten(werte: List<Triple<String, Double, Double>>, betrag: Double, bruchstuecke: Boolean): Ergebnis {
        val summe = werte.sumOf { max(0.0, it.third) }
        if (summe <= 0 || betrag <= 0) return Ergebnis(emptyList(), 0.0, 0.0)
        return rechnen(werte.map { (k, preis, g) -> Posten(k, preis, betrag * max(0.0, g) / summe) }, bruchstuecke)
    }
}
