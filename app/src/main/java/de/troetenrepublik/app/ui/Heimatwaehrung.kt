package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.ExchangeRates

// Kurs, Kennzahlen und eigene Position eines Werts in der Waehrung seiner Heimatboerse, etwa Yuan bei einer
// Aktie aus Shanghai. Ein Tipp auf den Kurs schaltet durch die Waehrungen der App (Fmt.stufen), dann die
// Heimatwaehrung und wieder die gewaehlte. Die Heimatwaehrung gilt nur auf der Seite des Werts.
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
        if (w in Fmt.stufen) return null
        return w.takeIf { ExchangeRates.rate(it, kurse) != null }
    }

    // Naechste Anzeige nach stufe von stufen Waehrungen der App: die naechste Stufe, nach der letzten die
    // Heimatwaehrung (wenn es eine gibt), danach wieder die erste. Ergebnis: Stufe und ob die Heimatwaehrung gilt.
    fun naechste(stufe: Int, stufen: Int, heimatMoeglich: Boolean, inHeimat: Boolean): Pair<Int, Boolean> = when {
        inHeimat -> 0 to false
        stufe >= stufen - 1 && heimatMoeglich -> stufe to true
        else -> (stufe + 1) % stufen to false
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
