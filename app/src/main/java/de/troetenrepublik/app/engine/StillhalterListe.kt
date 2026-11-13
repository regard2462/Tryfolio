package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import kotlin.math.abs

// Stillhalter je Wert (0.76): der Call und der Put, die zu gewaehltem Abstand vom Kurs und gewaehlter Laufzeit am
// besten passen. Gerechnet mit dem Geldkurs, wie in der Stillhalter-Tabelle der Optionenkarte.
object StillhalterListe {

    val ABSTAENDE = listOf(3, 5, 10, 15)
    val LAUFZEITEN = listOf(30, 45, 60)

    data class Vorschlag(
        val call: Boolean,
        val strike: Double,
        val verfall: Long,
        val tage: Int,
        val praemie: Double,
        val renditeJahr: Double,
        val ausserhalb: Double?,
        val spread: Double?,
        // null: Zahlentermin unbekannt
        val zahlenVorVerfall: Boolean?,
    )

    data class Ergebnis(val call: Vorschlag?, val put: Vorschlag?)

    private const val TAG_S = 86_400L

    // Hoechste Abweichung des Verfalls von der gewuenschten Laufzeit in Tagen
    const val TOLERANZ = 15

    // Der Verfallstermin, der zur Laufzeit am besten passt, wenn er in der geladenen Kette fehlt; null, wenn er schon da
    // ist oder keiner passt. Die Kette traegt bei Werten mit Wochen- und Tagesoptionen nur die naechsten Termine.
    fun fehlenderTermin(termine: List<Long>, vorhanden: Set<Long>, laufzeit: Int, jetztS: Long): Long? {
        fun tage(v: Long) = ((v - jetztS) / TAG_S).toInt()
        val bester = termine.filter { tage(it) >= 5 }.minByOrNull { abs(tage(it) - laufzeit) }?.takeIf { abs(tage(it) - laufzeit) <= TOLERANZ } ?: return null
        return bester.takeIf { it !in vorhanden }
    }

    // abstandPct: gewuenschter Abstand des Basispreises vom Kurs in Prozent, laufzeit: gewuenschte Restlaufzeit in
    // Tagen. zahlenS: naechster Zahlentermin in Sekunden. Der Verfall darf hoechstens 15 Tage von der Laufzeit
    // abweichen, der Abstand muss zwischen der Haelfte und dem Eineinhalbfachen des gewuenschten liegen.
    fun waehlen(kurs: Double, kontrakte: List<Kontrakt>, abstandPct: Int, laufzeit: Int, jetztS: Long, zahlenS: Long?, zins: Double = Pricing.RATE): Ergebnis {
        if (kurs <= 0) return Ergebnis(null, null)
        fun tage(v: Long) = ((v - jetztS) / TAG_S).toInt()
        val verfall = kontrakte.map { it.verfall }.distinct().filter { tage(it) >= 5 }
            .minByOrNull { abs(tage(it) - laufzeit) }?.takeIf { abs(tage(it) - laufzeit) <= TOLERANZ } ?: return Ergebnis(null, null)
        val t = tage(verfall)
        val a = abstandPct / 100.0
        val am = kontrakte.filter { it.verfall == verfall && it.geld > 0 }
        fun vorschlag(call: Boolean): Vorschlag? {
            val ziel = if (call) kurs * (1 + a) else kurs * (1 - a)
            val k = am.filter { it.call == call && (if (call) it.strike > kurs else it.strike < kurs) }
                .filter { abs(it.strike / kurs - 1) in a * 0.5..a * 1.5 }
                .minByOrNull { abs(it.strike - ziel) } ?: return null
            val r = k.geld / (if (call) kurs else k.strike)
            val chance = Optionsanalyse.ausuebung(kurs, k.strike, k.iv, call, t / 365.0, zins)?.let { 1 - it }
            val spread = if (k.brief >= k.geld && k.brief > 0) (k.brief - k.geld) / ((k.brief + k.geld) / 2) else null
            val zahlen = zahlenS?.let { it > jetztS && it <= verfall + TAG_S }
            return Vorschlag(call, k.strike, verfall, t, k.geld, r * 365 / t, chance, spread, zahlen)
        }
        return Ergebnis(vorschlag(true), vorschlag(false))
    }
}
