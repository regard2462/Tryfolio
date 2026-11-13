package de.troetenrepublik.app.engine

import kotlin.math.ln
import kotlin.math.sqrt

// Marktmacht (0.77): wie gut eine Firma ihre Stellung gegen Wettbewerber haelt, genaehert aus Kennzahlen. Drei Bausteine,
// jeder 0 bis 100 Punkte, aus den Jahresabschluessen im Speicher des Aktienfinders (FinderBilanz) und dem Katalog:
//  - Marktstellung: Umsatzanteil an der Branche des Katalogs (Rang und Konzentration der drei Groessten);
//  - Preismacht: Bruttomarge, sonst operative Marge, gegen den Median der Branche;
//  - Bestaendigkeit: niedrigste Kapitalrendite der Jahre und Schwankung der operativen Marge.
// Die Hoehe von Eigenkapitalrendite und Gewinnmarge im letzten Jahr misst die Qualitaet; der Marktmacht misst Stellung in
// der Branche und Dauer. Fehlt die Grundlage eines Bausteins, zaehlt er nicht (nichts wird geraten). Reine Rechnung.
object Marktmacht {

    enum class Teil { STELLUNG, PREISMACHT, BESTAENDIGKEIT }

    enum class Stufe { HOCH, MITTEL, GERING }

    // Stufen nach dem Schnitt der vorhandenen Bausteine; ab zwei Bausteinen.
    const val HOCH_AB = 62.0
    const val MITTEL_AB = 42.0

    // Mindestens so viele Werte einer Branche braucht ein Vergleich (Median, Umsatzanteil).
    const val MIN_VERGLEICH = 5

    // Der Umsatzanteil gilt, wenn der Umsatz fuer mindestens diesen Anteil der Katalogwerte der Branche vorliegt:
    // sonst waere der Anteil am Rest groesser als an der ganzen Branche.
    const val ABDECKUNG = 0.6

    // Die Bestaendigkeit braucht mindestens drei Jahre.
    const val MIN_JAHRE = 3

    // Steuersatz der Kapitalrendite, wenn der Abschluss keinen plausiblen nennt.
    private const val STEUER = 0.21

    // Ein Wert mit den Reihen aus seinen Jahresabschluessen: Umsatz des juengsten Jahres in Euro, je Jahr Bruttomarge,
    // operative Marge und Kapitalrendite nach Steuern (alle in Prozent, aelteste zuerst).
    class Eingabe(
        val symbol: String,
        val branche: String,
        val umsatz: Double?,
        val brutto: List<Double>,
        val betrieb: List<Double>,
        val rendite: List<Double>,
    )

    data class Stellung(val anteil: Double, val rang: Int, val von: Int)

    // Zahlen hinter den Punkten, fuer die Begruendung.
    data class Angaben(
        val stellung: Stellung? = null,
        // Preismacht: Marge (Mittel der Jahre) und Median der Branche; brutto sagt, welche Marge
        val brutto: Boolean = true,
        val marge: Double? = null,
        val branchenMarge: Double? = null,
        val jahreUeber: Int = 0,
        val jahre: Int = 0,
        // Bestaendigkeit: niedrigste Kapitalrendite, Zahl der Jahre, ob die Marge ruhig blieb
        val renditeMin: Double? = null,
        val renditeJahre: Int = 0,
        val margeRuhig: Boolean? = null,
    )

    data class Ergebnis(val punkte: Double?, val stufe: Stufe?, val teile: Map<Teil, Double>, val angaben: Angaben)

    private fun begrenzen(v: Double) = v.coerceIn(0.0, 100.0)

    // Marktstellung: 10 % Umsatzanteil geben 50 Punkte, jede Verdopplung 22 mehr; als Oligopol (unter den drei Groessten,
    // die drei zusammen mit mindestens 60 %) acht Punkte dazu.
    fun stellungPunkte(anteil: Double, rang: Int, spitze3: Double): Double {
        if (!(anteil > 0)) return 0.0
        val oligopol = if (rang <= 3 && spitze3 >= 60.0) 8.0 else 0.0
        return begrenzen(50.0 + 22.0 * ln(anteil / 10.0) / ln(2.0) + oligopol)
    }

    // Preismacht: gleich dem Median 50 Punkte, je Prozentpunkt darueber zwei mehr.
    fun preisPunkte(marge: Double, median: Double): Double = begrenzen(50.0 + (marge - median) * 2.0)

    // Kapitalrendite: niedrigste Jahresrendite 20 % und mehr geben 100 Punkte, 0 oder weniger null.
    fun renditePunkte(niedrigste: Double): Double = begrenzen(niedrigste * 5.0)

    // Schwankung der operativen Marge: Variationskoeffizient 0 gibt 100 Punkte, 0,5 und mehr null; ohne Gewinn null.
    fun ruhePunkte(marge: List<Double>): Double {
        val m = marge.average()
        if (!(m > 0)) return 0.0
        val streu = sqrt(marge.sumOf { (it - m) * (it - m) } / marge.size)
        return begrenzen(100.0 - streu / m * 200.0)
    }

    // Reihen aus den Jahresabschluessen. euroJe: Einheiten der Berichtswaehrung je Euro (null: Umsatz unbekannt).
    fun eingabe(symbol: String, branche: String, jahre: List<Qualitaet.Jahr>, euroJe: Double?): Eingabe {
        val mitUmsatz = jahre.filter { (it.umsatz ?: 0.0) > 0 }
        val brutto = mitUmsatz.mapNotNull { j -> j.bruttogewinn?.let { it / j.umsatz!! * 100.0 } }.filter { it.isFinite() && it <= 100.0 }
        val betrieb = mitUmsatz.mapNotNull { j -> j.ebit?.let { it / j.umsatz!! * 100.0 } }.filter { it.isFinite() }
        val rendite = jahre.mapNotNull { j ->
            val eingesetzt = (j.bilanzsumme ?: return@mapNotNull null) - (j.kurzfristSchulden ?: return@mapNotNull null)
            val ebit = j.ebit ?: return@mapNotNull null
            val steuer = j.steuersatz?.takeIf { it in 0.0..0.5 } ?: STEUER
            if (eingesetzt > 0) ebit * (1.0 - steuer) / eingesetzt * 100.0 else null
        }.filter { it.isFinite() }
        val umsatz = jahre.lastOrNull { (it.umsatz ?: 0.0) > 0 }?.umsatz?.let { u -> euroJe?.takeIf { it > 0 }?.let { u / it } }
        return Eingabe(symbol, branche, umsatz?.takeIf { it.isFinite() }, brutto, betrieb, rendite)
    }

    private fun median(werte: List<Double>): Double? {
        if (werte.size < MIN_VERGLEICH) return null
        val s = werte.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private class Branche(val stellung: Map<String, Stellung>, val spitze3: Double, val medianBrutto: Double?, val medianBetrieb: Double?)

    // Bewertet alle Werte. mitglieder: Zahl der Katalogwerte je Branche (fuer die Abdeckung des Umsatzanteils); fehlt eine
    // Branche, zaehlen die Werte mit Umsatz selbst. Der Vergleich gilt je Branche ueber alle uebergebenen Werte, deshalb
    // das ganze bekannte Universum uebergeben, nicht nur den gewaehlten Markt.
    fun auswerten(eingaben: Collection<Eingabe>, mitglieder: Map<String, Int> = emptyMap()): Map<String, Ergebnis> {
        val branchen = eingaben.filter { it.branche.isNotBlank() }.groupBy { it.branche }.mapValues { (b, l) ->
            val umsaetze = l.filter { (it.umsatz ?: 0.0) > 0 }.sortedByDescending { it.umsatz }
            val alle = mitglieder[b] ?: umsaetze.size
            val summe = umsaetze.sumOf { it.umsatz!! }
            val traegt = umsaetze.size >= MIN_VERGLEICH && umsaetze.size >= ABDECKUNG * alle && summe > 0
            val stellung = if (traegt) umsaetze.mapIndexed { i, e -> e.symbol to Stellung(e.umsatz!! / summe * 100.0, i + 1, umsaetze.size) }.toMap() else emptyMap()
            val spitze = if (traegt) umsaetze.take(3).sumOf { it.umsatz!! } / summe * 100.0 else 0.0
            Branche(
                stellung, spitze,
                median(l.filter { it.brutto.size >= 2 }.map { it.brutto.average() }),
                median(l.filter { it.betrieb.size >= 2 }.map { it.betrieb.average() }),
            )
        }
        return eingaben.mapNotNull { e ->
            val b = branchen[e.branche] ?: return@mapNotNull null
            val teile = LinkedHashMap<Teil, Double>()
            var angaben = Angaben()
            b.stellung[e.symbol]?.let { s ->
                teile[Teil.STELLUNG] = stellungPunkte(s.anteil, s.rang, b.spitze3)
                angaben = angaben.copy(stellung = s)
            }
            // Bruttomarge, wo Wert und Branche sie fuehren, sonst die operative Marge
            val vergleich: Triple<List<Double>, Double?, Boolean> = when {
                e.brutto.size >= 2 && b.medianBrutto != null -> Triple(e.brutto, b.medianBrutto, true)
                e.betrieb.size >= 2 && b.medianBetrieb != null -> Triple(e.betrieb, b.medianBetrieb, false)
                else -> Triple(emptyList(), null, true)
            }
            val (reihe, median, brutto) = vergleich
            if (median != null && reihe.isNotEmpty()) {
                val mittel = reihe.average()
                teile[Teil.PREISMACHT] = preisPunkte(mittel, median)
                angaben = angaben.copy(brutto = brutto, marge = mittel, branchenMarge = median, jahreUeber = reihe.count { it > median }, jahre = reihe.size)
            }
            if (e.rendite.size >= MIN_JAHRE && e.betrieb.size >= MIN_JAHRE) {
                val tief = e.rendite.min()
                val ruhe = ruhePunkte(e.betrieb)
                teile[Teil.BESTAENDIGKEIT] = (renditePunkte(tief) + ruhe) / 2
                angaben = angaben.copy(renditeMin = tief, renditeJahre = e.rendite.size, margeRuhig = ruhe >= 50.0)
            }
            if (teile.isEmpty()) return@mapNotNull null
            val punkte = teile.values.average()
            e.symbol to Ergebnis(punkte, stufe(punkte, teile.size), teile, angaben)
        }.toMap()
    }

    // Stufe aus dem Schnitt; mit nur einem Baustein keine.
    fun stufe(punkte: Double, bausteine: Int): Stufe? = when {
        bausteine < 2 -> null
        punkte >= HOCH_AB -> Stufe.HOCH
        punkte >= MITTEL_AB -> Stufe.MITTEL
        else -> Stufe.GERING
    }
}
