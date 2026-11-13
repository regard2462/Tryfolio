package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Empfindlichkeit.Faktor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// [Szenarien 0.74] "Was waere, wenn": Rechenbeispiele fuer das Depot aus gemessenen Kursempfindlichkeiten und dem
// Einflussverzeichnis, bei unveraendertem Gesamtmarkt. Beschreibung vergangener Kursbewegungen, keine Vorhersage.
// Verfahren:
// - Je Position und Groesse (Oel, Kupfer, Gold, Zinsen, Dollar) Log-Renditen ueber Bloecke von vier Kalenderwochen (letzter
//   Kurs je Block, nur aufeinanderfolgende Bloecke, die Position, Markt und Groesse haben; bei Zinsen Aenderung in Punkten),
//   Kleinste-Quadrate-Schaetzung der Position auf Achsenabschnitt, Markt und Groesse, bei Werten in anderer Waehrung
//   zusaetzlich auf den Wechselkurs zum Dollar. Mindestens MIN_BLOECKE Bloecke.
// - Belegt: |Steigung / Standardfehler| >= SCHWELLE_T und die Steigungen beider Haelften haben dasselbe Vorzeichen. Sonst Beitrag 0.
// - Spanne der Steigung: kleinste bis groesste Steigung aus Gesamt und beiden Haelften, je um zwei Standardfehler geweitet,
//   an der Null gekappt.
// - Gemessen an neun Preisbewegungen von Oel, Gold und Kupfer seit 2013 (Schaetzung nur aus den sechs Jahren davor): Richtung
//   in 25 von 25 belegten Faellen richtig, Bewegung der Position ohne Marktanteil in 18 von 25 in der Spanne. Bei Zinsen,
//   Dollar und Schwankungsindex stimmte die Richtung nur in 25 von 29 Faellen: Zinsen und Dollar stehen ohne Zahl, es
//   bleiben die Positionen mit dem bisherigen Zusammenhang und dem Dollar-Anteil.
// - Ereignisse (Taiwan, Hormus, Rohstoffembargo) haben keine Zahl: nur die betroffenen Positionen aus dem Verzeichnis.
object Szenarien {
    // Schock: Vorgabe in Prozent (Wert der Groesse), bei punkte in Prozentpunkten (Zinsen); berechenbar: false zeigt nur
    // die betroffenen Positionen
    // min, max und schritt: Grenzen und Raster des eigenen Werts (Regler), in Prozent bzw. Prozentpunkten
    enum class Szenario(
        val faktor: Faktor, val stufen: List<Double>, val standard: Double, val punkte: Boolean = false, val berechenbar: Boolean = true,
        val min: Double = -60.0, val max: Double = 100.0, val schritt: Double = 5.0,
    ) {
        OEL(Faktor.OEL, listOf(-30.0, 30.0, 60.0), 30.0),
        ZINS(Faktor.ZINS10J, listOf(-1.0, 1.0, 2.0), 1.0, punkte = true, berechenbar = false, min = -3.0, max = 3.0, schritt = 1.0),
        DOLLAR(Faktor.DOLLAR, listOf(-20.0, -10.0, 10.0), -10.0, berechenbar = false, min = -30.0, max = 30.0),
        KUPFER(Faktor.KUPFER, listOf(-30.0, 30.0), 30.0),
        GOLD(Faktor.GOLD, listOf(-30.0, 30.0), 30.0),
    }

    // Eigener Wert innerhalb der Grenzen des Szenarios
    fun begrenzen(s: Szenario, wert: Double): Double = if (wert.isFinite()) wert.coerceIn(s.min, s.max) else s.standard

    // Groessen, gegen die gemessen wird (ausser dem Markt)
    val GROESSEN: List<Faktor> = Szenario.entries.map { it.faktor }

    enum class Ereignis(val groessen: List<String>) {
        TAIWAN(listOf("engpass:chokepoint11", "engpass:chokepoint14", "spannung:TWN")),
        HORMUS(listOf("engpass:chokepoint6", "spannung:GOL")),
        EMBARGO(emptyList()),
    }

    const val MIN_BLOECKE = 40
    const val BLOCK_WOCHEN = 4L
    const val SCHWELLE_T = 3.0

    // Steigung einer Rendite je Rendite der Groesse (bei Zinsen je Prozentpunkt): ganzer Zeitraum mit Standardfehler und
    // beide Haelften
    class Steigung(val gesamt: Double, val se: Double, val haelften: Pair<Double, Double>) {
        // Grenzen der Steigung: kleinste und groesste Schaetzung, je um zwei Standardfehler geweitet, an der Null gekappt
        val grenzen: Pair<Double, Double> get() {
            var lo = min(gesamt, min(haelften.first, haelften.second)) - 2.0 * se
            var hi = max(gesamt, max(haelften.first, haelften.second)) + 2.0 * se
            if (gesamt > 0.0) lo = max(lo, 0.0)
            if (gesamt < 0.0) hi = min(hi, 0.0)
            return lo to hi
        }
    }

    // Belegte Steigungen eines Werts je Groesse; ohne Eintrag gilt der Zusammenhang als nicht belegt
    class Messung(val steigungen: Map<Faktor, Steigung>)

    private fun block(t: Long): Long = Math.floorDiv(Math.floorDiv(Tagesrueckblick.kerzenTag(t) + 3, 7L), BLOCK_WOCHEN)

    private fun blockKurse(k: List<Candle>): Map<Long, Double> {
        val m = HashMap<Long, Double>()
        for (c in k.sortedBy { it.t }) if (c.close > 0 && c.close.isFinite()) m[block(c.t)] = c.close
        return m
    }

    // Kleinste Quadrate auf Achsenabschnitt und die Reihen xs in den Zeilen von bis bis; Rueckgabe: Steigung und
    // Standardfehler der letzten Reihe, null bei zu wenig Zeilen oder singulaerer Matrix
    private fun schaetzen(y: DoubleArray, xs: List<DoubleArray>, von: Int, bis: Int): Pair<Double, Double>? {
        val n = bis - von
        val p = xs.size + 1
        if (n <= p + 10) return null
        fun x(j: Int, i: Int) = if (j == 0) 1.0 else xs[j - 1][i]
        val a = Array(p) { DoubleArray(p) }
        val b = DoubleArray(p)
        for (i in von until bis) {
            for (j in 0 until p) { b[j] += x(j, i) * y[i]; for (k in 0 until p) a[j][k] += x(j, i) * x(k, i) }
        }
        val inv = invertieren(a) ?: return null
        val beta = DoubleArray(p) { j -> (0 until p).sumOf { k -> inv[j][k] * b[k] } }
        var sse = 0.0
        for (i in von until bis) {
            var fit = 0.0
            for (j in 0 until p) fit += beta[j] * x(j, i)
            sse += (y[i] - fit) * (y[i] - fit)
        }
        val se = sqrt((sse / (n - p) * inv[p - 1][p - 1]).coerceAtLeast(0.0))
        return beta[p - 1] to se
    }

    private fun invertieren(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (abs(a[p][c]) < 1e-14) return null
            val t = a[c]; a[c] = a[p]; a[p] = t
            val d = a[c][c]
            for (j in 0 until 2 * n) a[c][j] /= d
            for (r in 0 until n) if (r != c) {
                val f = a[r][c]
                if (f != 0.0) for (j in 0 until 2 * n) a[r][j] -= f * a[c][j]
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][n + j] } }
    }

    // Messung eines Werts aus Tageskerzen: Markt und je Groesse; Groessen mit weniger als MIN_BLOECKE gemeinsamen
    // Bloecken oder ohne Beleg fehlen im Ergebnis
    // wechselkurs: Dollar je Einheit der Handelswaehrung bei Werten, die nicht in Dollar notieren. Markt und Groessen
    // notieren in Dollar; ohne den Wechselkurs als weitere Reihe liefe ein solcher Wert schon seinetwegen mit Gold oder Oel.
    fun messen(wert: List<Candle>, markt: List<Candle>, groessen: Map<Faktor, List<Candle>>, wechselkurs: List<Candle>? = null): Messung {
        val w = blockKurse(wert)
        val m = blockKurse(markt)
        val fx = wechselkurs?.let { blockKurse(it) }
        val aus = LinkedHashMap<Faktor, Steigung>()
        for ((f, kerzen) in groessen) {
            val g = blockKurse(kerzen)
            val gemeinsam = w.keys.intersect(m.keys).intersect(g.keys).let { k -> if (fx != null) k.intersect(fx.keys) else k }.sorted()
            val zeilen = gemeinsam.zipWithNext().filter { it.second - it.first == 1L }
            if (zeilen.size < MIN_BLOECKE) continue
            fun rendite(r: Map<Long, Double>, diff: Boolean) = DoubleArray(zeilen.size) { i ->
                val a = r.getValue(zeilen[i].first); val b = r.getValue(zeilen[i].second)
                if (diff) b - a else ln(b / a)
            }
            val y = rendite(w, false)
            val xs = listOfNotNull(rendite(m, false), fx?.let { rendite(it, false) }, rendite(g, f.differenz))
            val n = zeilen.size
            val voll = schaetzen(y, xs, 0, n) ?: continue
            val h1 = schaetzen(y, xs, 0, n / 2) ?: continue
            val h2 = schaetzen(y, xs, n / 2, n) ?: continue
            if (voll.second <= 0.0 || abs(voll.first / voll.second) < SCHWELLE_T) continue
            val vz = if (voll.first > 0) 1 else -1
            if ((if (h1.first > 0) 1 else -1) != vz || (if (h2.first > 0) 1 else -1) != vz) continue
            aus[f] = Steigung(voll.first, voll.second, h1.first to h2.first)
        }
        return Messung(aus)
    }

    // wert: Marktwert der Position; messbar: false bei Derivaten und Bargeld (nicht eingerechnet); waehrung: Anteile je
    // Waehrung (Waehrungsschock.verteilung)
    class Posten(
        val symbol: String, val name: String, val wert: Double, val messbar: Boolean = true,
        val waehrung: Map<String, Double> = emptyMap(),
    )

    // von und bis: Beitrag zum Depot als Anteil (von <= bis); gewicht: Anteil der Position am Depot
    class Beitrag(val posten: Posten, val gewicht: Double, val von: Double, val bis: Double) {
        val staerke: Double get() = max(abs(von), abs(bis))
    }

    // von und bis: Aenderung des Depots als Anteil; belegt: Anteil des Depots mit belegtem Zusammenhang (0 bis 1);
    // beitraege: nur Positionen mit Wirkung, groesste zuerst
    class Zeile(val szenario: Szenario, val schock: Double, val von: Double, val bis: Double, val belegt: Double, val beitraege: List<Beitrag>)

    private const val MIN_FAKTOR = 0.05

    // Aenderung des Faktors als Eingang der Rechnung: Log-Rendite bei Prozent, Punkte bei Zinsen
    fun eingang(s: Szenario, schock: Double): Double =
        if (s.punkte) schock else ln((1.0 + schock / 100.0).coerceAtLeast(MIN_FAKTOR))

    // Anteil einer Position in Dollar; ist der Dollar die Heimatwaehrung, der Anteil in anderen Waehrungen
    fun dollarAnteil(verteilung: Map<String, Double>, heim: String): Double {
        val summe = verteilung.values.sum()
        if (summe <= 0.0) return 0.0
        val h = Waehrungsschock.code(heim)
        return if (h == "USD") Waehrungsschock.fremdanteil(verteilung, h) else (verteilung["USD"] ?: 0.0) / summe
    }

    // Dollar-Szenario ohne gemessenen Zusammenhang (0.78): die reine Umrechnung wie beim Waehrungsschock im Crash-Test.
    // schockProzent: Aenderung des Dollars in Prozent. Bei einer anderen Heimatwaehrung aendert sich der Dollar-Anteil
    // jeder Position um den Schock; ist der Dollar die Heimatwaehrung, aendern sich die Anteile in anderen Waehrungen
    // um die Gegenbewegung (Waehrungsschock.umrechnung). Ergebnis als Anteil des Depots, Kurse sonst unveraendert;
    // null ohne Position mit betroffenem Anteil.
    fun dollarUmrechnung(posten: List<Posten>, heim: String, schockProzent: Double): Double? {
        val summe = posten.sumOf { it.wert.coerceAtLeast(0.0) }
        if (summe <= 0.0) return null
        val f = schockProzent / 100.0
        val wirkung = if (Waehrungsschock.code(heim) == "USD") Waehrungsschock.umrechnung(f) else f
        var anteil = 0.0
        for (p in posten) {
            if (!p.messbar) continue
            anteil += p.wert.coerceAtLeast(0.0) / summe * dollarAnteil(p.waehrung, heim)
        }
        return if (anteil < 0.0005) null else anteil * wirkung
    }

    // Eine Zeile der Liste: Szenario mit dem gewaehlten Schock fuer die Positionen des Depots. Nicht berechenbare
    // Szenarien liefern eine leere Zeile (siehe betroffene).
    fun rechnen(s: Szenario, schock: Double, posten: List<Posten>, messungen: Map<String, Messung>): Zeile {
        val summe = posten.sumOf { it.wert.coerceAtLeast(0.0) }
        if (summe <= 0.0 || !s.berechenbar) return Zeile(s, schock, 0.0, 0.0, 0.0, emptyList())
        val x = eingang(s, schock)
        var von = 0.0
        var bis = 0.0
        var belegt = 0.0
        val beitraege = ArrayList<Beitrag>()
        for (p in posten) {
            val gewicht = p.wert.coerceAtLeast(0.0) / summe
            if (gewicht <= 0.0 || !p.messbar) continue
            val steigung = messungen[p.symbol]?.steigungen?.get(s.faktor) ?: continue
            val (sLo, sHi) = steigung.grenzen
            val e1 = exp(sLo * x) - 1.0
            val e2 = exp(sHi * x) - 1.0
            val lo = min(e1, e2)
            val hi = max(e1, e2)
            belegt += gewicht
            von += gewicht * lo
            bis += gewicht * hi
            beitraege += Beitrag(p, gewicht, gewicht * lo, gewicht * hi)
        }
        return Zeile(s, schock, von, bis, belegt, beitraege.sortedByDescending { it.staerke })
    }

    // Positionen eines nicht berechenbaren Szenarios: bei Zinsen die mit belegtem bisherigem Zusammenhang (Richtung), beim
    // Dollar die mit Dollar-Anteil (und, wo gemessen, Richtung); groesster Depotanteil zuerst
    fun betroffene(s: Szenario, posten: List<Posten>, messungen: Map<String, Messung>, heim: String): EreignisZeile? {
        val summe = posten.sumOf { it.wert.coerceAtLeast(0.0) }
        if (s.berechenbar || summe <= 0.0) return null
        val aus = ArrayList<Betroffen>()
        for (p in posten) {
            val gewicht = p.wert.coerceAtLeast(0.0) / summe
            if (gewicht <= 0.0 || !p.messbar) continue
            val st = messungen[p.symbol]?.steigungen?.get(s.faktor)
            val richtung = if (st == null) 0 else if (st.gesamt > 0) 1 else -1
            val dollar = if (s == Szenario.DOLLAR) dollarAnteil(p.waehrung, heim) else 0.0
            if (st == null && dollar < 0.005) continue
            aus += Betroffen(p.symbol, p.name, gewicht, Einflussverzeichnis.Art.GEMESSEN, "", richtung = richtung, dollar = dollar)
        }
        return EreignisZeile(s.name, aus.sortedByDescending { it.gewicht })
    }

    // Von-bis-Paar fuer die Anzeige in der Reihenfolge der Beispiele (naeher an null zuerst): -2 bis -5, +2 bis +5, -1 bis +3
    fun anzeigeReihenfolge(von: Double, bis: Double): Pair<Double, Double> =
        if (bis <= 0.0) bis to von else von to bis

    // Betroffene Position eines Ereignisses; rohstoffe nur beim Embargo (Rohstoffe der Branche mit hoher Konzentration)
    // richtung und dollar nur bei Zinsen und Dollar: bisheriger Zusammenhang (+1 mit, -1 gegen, 0 ohne) und Dollar-Anteil
    class Betroffen(
        val symbol: String, val name: String, val gewicht: Double, val art: Einflussverzeichnis.Art, val beleg: String,
        val rohstoffe: List<Einflussverzeichnis.Rohstoff> = emptyList(), val richtung: Int = 0, val dollar: Double = 0.0,
    )

    // kennung: Name des Ereignisses oder des nicht berechenbaren Szenarios. [0.75] abgelaufen: Einordnungen des Ereignisses,
    // die Positionen nur noch ueber abgelaufene Verbindungen treffen (Anzahl, spaetester Ablauf in Tagen seit 1970); sie
    // fehlen in betroffen, die Anzeige nennt sie. null, wenn keine.
    class EreignisZeile(val kennung: String, val betroffen: List<Betroffen>, val abgelaufen: Pair<Int, Long>? = null) {
        val anteil: Double get() = betroffen.sumOf { it.gewicht }
    }

    // Positionen, die ein Ereignis trifft; groesster Depotanteil zuerst. Je Position zaehlt die staerkste Verbindung.
    fun ereignis(e: Ereignis, index: Einflussverzeichnis.Index, bestand: List<Bestand>, heute: Long): EreignisZeile {
        if (e == Ereignis.EMBARGO) return EreignisZeile(e.name, embargo(index, bestand))
        val je = LinkedHashMap<String, Betroffen>()
        val rang = HashMap<String, Int>()
        for (id in e.groessen) for (t in Einflussverzeichnis.imDepot(index, id, bestand, heute)) {
            val s = t.bestand.wert.symbol
            val r = (if (t.kante.ziel.art == Einflussverzeichnis.ZielArt.WERT) 0 else 3) + t.kante.art.ordinal
            if (s in je && (rang[s] ?: 99) <= r) continue
            rang[s] = r
            je[s] = Betroffen(s, t.bestand.name, t.bestand.gewicht, t.kante.art, t.kante.beleg)
        }
        val alt = Einflussverzeichnis.abgelaufen(index, bestand.map { it.wert }, heute) { it.groesse in e.groessen }
        return EreignisZeile(e.name, je.values.sortedByDescending { it.gewicht }, alt)
    }

    // Embargo auf Rohstoffe: Positionen, deren Unternehmen Rohstoffe braucht (Rohstoffprofil, sonst die Branche), bei denen die
    // drei groessten Foerderlaender mindestens HOHE_KONZENTRATION Prozent stellen (abgeleitet aus USGS) oder die als
    // embargoempfindlich gekennzeichnet sind (Uran)
    private fun embargo(index: Einflussverzeichnis.Index, bestand: List<Bestand>): List<Betroffen> {
        val aus = ArrayList<Betroffen>()
        for (b in bestand) {
            val knapp = Einflussverzeichnis.rohstoffeFuerWert(index, b.wert).filter { (it.konzentration ?: 0) >= Einflussverzeichnis.HOHE_KONZENTRATION || it.embargo }
            if (knapp.isEmpty()) continue
            aus += Betroffen(b.wert.symbol, b.name, b.gewicht, Einflussverzeichnis.Art.ABGELEITET, (listOf(index.rohstoffQuelle) + knapp.mapNotNull { it.quelle }).distinct().joinToString(", "), knapp)
        }
        return aus.sortedByDescending { it.gewicht }
    }
}
