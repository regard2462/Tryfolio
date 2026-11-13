package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.AtrAbstand
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ATR ueber 20 Tage in Prozent des Kurses aus den zwischengespeicherten Tageskerzen; 0, solange nichts da ist.
@Composable
internal fun rememberAtrProzent(state: AppState, symbol: String): Double {
    val atr by produceState(0.0, symbol) {
        val kerzen = state.dailyCloses(symbol)
        value = withContext(Dispatchers.Default) { AtrAbstand.prozent(kerzen) } ?: 0.0
    }
    return atr
}

// Stoppkurs im Abstand von zwei ATR; ein Tipp uebernimmt den Abstand in die Rechnung der Positionsgroesse.
// Ohne ATR erscheint nichts. kurs: aktueller Kurs in Euro.
@Composable
internal fun AtrStopp(state: AppState, symbol: String, kurs: Double, onWahl: (Double) -> Unit) {
    val faktor = AtrAbstand.FAKTOREN.first()
    val abstand = AtrAbstand.abstand(rememberAtrProzent(state, symbol), faktor) ?: return
    KeyValueRow(
        S.st("atr.stopp", listOf(faktor.toString())), Fmt.priceEuro(kurs * (1 - abstand / 100)),
        hint = S.st("atr.hinweis", listOf(Fmt.pct(abstand, 1))),
        onClick = { onWahl(abstand) },
    )
}
