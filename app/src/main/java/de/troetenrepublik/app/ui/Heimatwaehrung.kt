package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.ExchangeRates

// Kurs, Kennzahlen und eigene Position eines Werts in der Waehrung seiner Heimatboerse, etwa Yuan bei einer
// Aktie aus Shanghai. Ein Tipp auf den Kurs schaltet durch die Waehrungen der App (Fmt.stufen) und die
// Heimatwaehrung an ihrer Stelle in der eigenen Folge (Fmt.originalNach, Vorgabe: nach der letzten Stufe).
// Die Heimatwaehrung gilt nur auf der Seite des Werts und nur, wenn die Originalwaehrung zur Folge gehoert.
// Umgerechnet wird mit den Wechselkursen, die die App fuer die Kurse ohnehin laedt.
object Heimatwaehrung {

    // Waehrung der Heimatboerse, wenn sie weder die gewaehlte noch die zweite Waehrung der Anzeige ist und die App einen
    // Wechselkurs dafuer hat; sonst null. Pence und US-Cent (Yahoo: GBp, GBX, USd, USX) zaehlen als Pfund und Dollar.
    fun waehrung(heimat: String?, kurse: Map<String, Double>): String? {
        val w = when (heimat) {
            null, "" -> return null
            "GBp", "GBX" -> "GBP"
            "USd", "USX" -> "USD"
            else -> heimat
        }
        if (!Fmt.mitOriginal || w in Fmt.stufen) return null
        return w.takeIf { ExchangeRates.rate(it, kurse) != null }
    }

    // Naechste Anzeige nach stufe von stufen Waehrungen der App: die naechste Stufe; steht die Heimatwaehrung (wenn es
    // eine gibt) hinter nach Stufen, kommt sie dazwischen. Ergebnis: Stufe und ob die Heimatwaehrung gilt.
    fun naechste(stufe: Int, stufen: Int, heimatMoeglich: Boolean, inHeimat: Boolean, nach: Int = Fmt.originalNach): Pair<Int, Boolean> {
        val n = stufen.coerceAtLeast(1)
        return when {
            inHeimat -> Math.floorMod(nach, n) to false
            heimatMoeglich && (stufe + 1) % n == Math.floorMod(nach, n) -> stufe to true
            else -> (stufe + 1) % n to false
        }
    }
}

// Betraege einer Wertpapierseite: in der Heimatwaehrung waehrung (Einheiten je Euro: jeEuro), sonst wie ueberall
// in Euro oder Dollar (Fmt.price, Fmt.money). Alle Werte kommen in Euro herein.
class KursFormat(private val waehrung: String?, private val jeEuro: Double?) {

    val inHeimat: Boolean get() = waehrung != null && jeEuro != null && jeEuro > 0

    private val zeichen: String get() = Derivatives.currencySymbol(waehrung.orEmpty())

    fun preis(euro: Double): String = if (inHeimat) Fmt.price(euro * jeEuro!!, zeichen) else Fmt.price(euro)

    fun geld(euro: Double): String = if (inHeimat) Fmt.moneyIn(euro * jeEuro!!, zeichen) else Fmt.money(euro)

    fun geldMitVorzeichen(euro: Double): String = (if (euro > 0) "+" else "") + geld(euro)

    fun kompakt(euro: Double): String = if (inHeimat) Fmt.compact(euro * jeEuro!!) + " " + waehrung else Fmt.compactMoney(euro)
}
