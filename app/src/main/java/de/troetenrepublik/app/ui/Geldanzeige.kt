package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.ExchangeRates

// Betraege in fremder Waehrung per Tippen zwischen Originalwaehrung, US-Dollar und der gewaehlten Waehrung
// umschalten. Umgerechnet wird mit den Wechselkursen der App (Euro-Referenzkurse von Yahoo, stuendlich erneuert).
// Fehlt ein Kurs, bleibt der Betrag in der Originalwaehrung.
class Geldanzeige(private val kurse: Map<String, Double>, private val wahl: Int, private val setzen: (Int) -> Unit) {

    // 2 = gewaehlte Waehrung; ist das der US-Dollar, Euro, damit sich die Stufen unterscheiden
    private fun zielFuer(w: Int, waehrung: String) = when (w) {
        1 -> "USD"
        2 -> if (Fmt.basis == "USD") "EUR" else Fmt.basis
        else -> waehrung
    }

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

    // Naechste Stufe, die sich fuer waehrung sichtbar unterscheidet (Original, Dollar, Euro, wieder Original).
    fun weiter(waehrung: String) {
        val jetzt = ziel(waehrung)
        var w = wahl
        repeat(3) {
            w = (w + 1) % 3
            val z = zielFuer(w, waehrung)
            if (z != jetzt && (z == waehrung || umrechenbar(waehrung, z))) { setzen(w); return }
        }
    }
}

@Composable
fun rememberGeldanzeige(state: AppState, schluessel: String, start: Int = 0): Geldanzeige {
    val kurse by state.market.fxKurse.collectAsStateWithLifecycle()
    var wahl by rememberSaveable(schluessel, start) { mutableStateOf(start) }
    return Geldanzeige(kurse, wahl) { wahl = it }
}
