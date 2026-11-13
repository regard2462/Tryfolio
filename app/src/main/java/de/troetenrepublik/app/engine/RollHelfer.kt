package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import kotlin.math.abs

// Rollen eines verkauften Puts (0.78): Geraet der Put unter Druck (Kurs hoechstens NAHE ueber dem Basispreis oder
// darunter), wird er zum Briefkurs zurueckgekauft und ein Put mit TAGE_MIN bis TAGE_MAX Tagen Laufzeit zum Geldkurs neu
// verkauft, zum selben oder zu einem tieferen Basispreis. Die Nettopraemie ist die neue Praemie minus der Rueckkauf,
// je Stueck in der Waehrung der Kette: positiv eine Gutschrift, negativ Kosten.
object RollHelfer {

    const val NAHE = 0.03
    const val TAGE_MIN = 45
    const val TAGE_MAX = 90

    // Laufzeit des gedeckten Calls nach einer Andienung.
    const val CALL_TAGE_MIN = 20
    const val CALL_TAGE_MAX = 60
    const val CALL_TAGE_ZIEL = 45

    private const val TAG_S = 86_400L

    enum class Druck { KEINER, NAHE, IM_GELD }

    fun druck(kurs: Double, strike: Double): Druck = when {
        !(kurs > 0) || !(strike > 0) -> Druck.KEINER
        kurs < strike -> Druck.IM_GELD
        kurs <= strike * (1 + NAHE) -> Druck.NAHE
        else -> Druck.KEINER
    }

    // Verfallstermine, die fuer die Vorschlaege gebraucht werden und in der geladenen Kette fehlen: der erste und der
    // letzte Termin im Fenster der neuen Laufzeit nach dem bisherigen Verfall, dazu der Termin fuer den gedeckten Call.
    // Die Kette traegt bei Werten mit Wochen- und Tagesoptionen nur die naechsten Termine. termine, vorhanden: Sekunden.
    fun fehlendeTermine(termine: List<Long>, vorhanden: Set<Long>, verfallTag: Long, jetztS: Long): List<Long> {
        fun tage(v: Long) = ((v - jetztS) / TAG_S).toInt()
        val neu = termine.filter { it / TAG_S > verfallTag && tage(it) in TAGE_MIN..TAGE_MAX }.sorted()
        val call = termine.filter { tage(it) in CALL_TAGE_MIN..CALL_TAGE_MAX }.minByOrNull { abs(tage(it) - CALL_TAGE_ZIEL) }
        return listOfNotNull(neu.firstOrNull(), neu.lastOrNull(), call).distinct().filter { it !in vorhanden }
    }

    // tiefer: Basispreis unter dem bisherigen. praemie: Geldkurs des neuen Puts.
    data class Vorschlag(val kontrakt: Kontrakt, val tage: Int, val praemie: Double, val netto: Double, val tiefer: Boolean)

    // call: gedeckter Call fuer den Fall der Andienung (nur wenn der Put im Geld liegt), Basispreis mindestens der des Puts.
    class Ergebnis(val druck: Druck, val rueckkauf: Double, val vorschlaege: List<Vorschlag>, val call: Kontrakt?)

    // strike und verfallTag (Tage seit 1970) des verkauften Puts. Der Rueckkauf kostet den Briefkurs desselben Kontrakts
    // in der Kette; fehlt er dort, gilt rueckkaufErsatz. null ohne Druck oder ohne Rueckkaufpreis.
    fun rechnen(kurs: Double, strike: Double, verfallTag: Long, kontrakte: List<Kontrakt>, jetztS: Long, rueckkaufErsatz: Double? = null): Ergebnis? {
        val d = druck(kurs, strike)
        if (d == Druck.KEINER) return null
        fun gleich(a: Double, b: Double) = abs(a - b) < 0.005
        val rueckkauf = kontrakte.firstOrNull { !it.call && gleich(it.strike, strike) && it.verfall / TAG_S == verfallTag && it.brief > 0 }?.brief
            ?: rueckkaufErsatz?.takeIf { it.isFinite() && it > 0 } ?: return null
        fun tage(v: Long) = ((v - jetztS) / TAG_S).toInt()
        val puts = kontrakte.filter {
            !it.call && it.geld > 0 && it.verfall / TAG_S > verfallTag && tage(it.verfall) in TAGE_MIN..TAGE_MAX && it.strike <= strike + 0.005
        }
        val termine = puts.map { it.verfall }.distinct().sorted()
        val vorschlaege = listOfNotNull(termine.firstOrNull(), termine.lastOrNull()).distinct().flatMap { termin ->
            val am = puts.filter { it.verfall == termin }
            val selber = am.firstOrNull { gleich(it.strike, strike) }
            val darunter = am.filter { it.strike < strike - 0.005 }
            // Der tiefste Basispreis, der noch eine Gutschrift bringt; gibt es keinen, der naechste unter dem bisherigen
            val tiefer = darunter.filter { it.geld - rueckkauf >= 0 }.minByOrNull { it.strike } ?: darunter.maxByOrNull { it.strike }
            listOfNotNull(selber, tiefer).map { Vorschlag(it, tage(termin), it.geld, it.geld - rueckkauf, it.strike < strike - 0.005) }
        }
        val call = if (d != Druck.IM_GELD) null else kontrakte
            .filter { it.call && it.geld > 0 && it.strike >= strike - 0.005 && tage(it.verfall) in CALL_TAGE_MIN..CALL_TAGE_MAX }
            .minWithOrNull(compareBy<Kontrakt> { abs(tage(it.verfall) - CALL_TAGE_ZIEL) }.thenBy { it.strike })
        return Ergebnis(d, rueckkauf, vorschlaege, call)
    }
}
