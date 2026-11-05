package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.Waehrungsfolge

// Betraege in fremder Waehrung per Tippen umschalten, in der Reihenfolge der Einstellung „Waehrungen beim Antippen“
// (Vorgabe: Originalwaehrung, gewaehlte Waehrung, US-Dollar, Euro). wahl ist ein Eintrag dieser Folge, ORIGINAL steht
// fuer die Waehrung des Betrags. Umgerechnet wird mit den Wechselkursen der App (Euro-Referenzkurse von Yahoo,
// stuendlich erneuert). Fehlt ein Kurs, bleibt der Betrag in der Originalwaehrung.
class Geldanzeige(
    private val kurse: Map<String, Double>,
    private val wahl: String,
    private val setzen: (String) -> Unit,
    private val folge: List<String> = Waehrungsfolge.wirksam(Fmt.folge, Fmt.basis),
) {

    private fun zielFuer(eintrag: String, waehrung: String) = if (eintrag == Waehrungsfolge.ORIGINAL) waehrung else eintrag

    // Waehrung, in der ein Betrag in waehrung gerade angezeigt wird.
    fun ziel(waehrung: String): String {
        val z = zielFuer(wahl, waehrung)
        return if (z == waehrung || umrechenbar(waehrung, z)) z else waehrung
    }

    private fun umrechenbar(von: String, nach: String) = ExchangeRates.rate(von, kurse) != null && ExchangeRates.rate(nach, kurse) != null

    fun umrechnen(wert: Double, waehrung: String): Pair<Double, String> {
        val z = ziel(waehrung)
        if (z == waehrung) return wert to waehrung
        val von = ExchangeRates.rate(waehrung, kurse) ?: return wert to waehrung
        val nach = ExchangeRates.rate(z, kurse) ?: return wert to waehrung
        return wert / von * nach to z
    }

    fun preis(wert: Double, waehrung: String): String {
        val (v, w) = umrechnen(wert, waehrung)
        return Fmt.price(v, Derivatives.currencySymbol(w))
    }

    fun kompakt(wert: Double, waehrung: String): String {
        val (v, w) = umrechnen(wert, waehrung)
        return Fmt.compact(v) + " " + w
    }

    // Naechster Eintrag der Folge, der sich fuer waehrung sichtbar unterscheidet; nach dem letzten wieder der erste.
    fun weiter(waehrung: String) {
        if (folge.isEmpty()) return
        val jetzt = ziel(waehrung)
        val ab = folge.indexOf(wahl)
        for (k in 1..folge.size) {
            val e = folge[Math.floorMod(ab + k, folge.size)]
            val z = zielFuer(e, waehrung)
            if (z != jetzt && (z == waehrung || umrechenbar(waehrung, z))) { setzen(e); return }
        }
    }

    companion object {
        // Erster Eintrag zu den bisherigen Stufen: 0 Originalwaehrung, 1 US-Dollar, sonst die gerade angezeigte Waehrung.
        // Gehoert die Originalwaehrung nicht zur Folge, beginnt auch 0 mit der angezeigten Waehrung.
        fun startEintrag(start: Int): String = when {
            start == 0 && Fmt.mitOriginal -> Waehrungsfolge.ORIGINAL
            start == 1 -> "USD"
            else -> Fmt.anzeigeWaehrung
        }
    }
}

@Composable
fun rememberGeldanzeige(state: AppState, schluessel: String, start: Int = 0): Geldanzeige {
    val kurse by state.market.fxKurse.collectAsStateWithLifecycle()
    var wahl by rememberSaveable(schluessel, start) { mutableStateOf(Geldanzeige.startEintrag(start)) }
    return Geldanzeige(kurse, wahl, { wahl = it })
}

// Betrag eines Insidergeschaefts in der Anzeigewaehrung; zweite Angabe: Original klein dahinter, wenn umgerechnet wurde.
class InsiderGeld(private val anzeige: Geldanzeige) {
    val ziel: String get() = Fmt.anzeigeWaehrung
    fun umrechnen(wert: Double, w: String): Pair<Double, String> = anzeige.umrechnen(wert, w.ifBlank { ziel })
    fun text(wert: Double, w: String): Pair<String, String?> {
        val (v, z) = umrechnen(wert, w)
        fun f(x: Double, c: String) = Fmt.mitSymbol(Fmt.compact(x), Derivatives.currencySymbol(c))
        return f(v, z) to (if (w.isNotBlank() && z != w) f(wert, w) else null)
    }
}

@Composable
fun rememberInsiderGeld(state: AppState): InsiderGeld {
    val kurse by state.market.fxKurse.collectAsStateWithLifecycle()
    return remember(kurse, Fmt.anzeigeWaehrung) { InsiderGeld(Geldanzeige(kurse, Fmt.anzeigeWaehrung, {})) }
}
