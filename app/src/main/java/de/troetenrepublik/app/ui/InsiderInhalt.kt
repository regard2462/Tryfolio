package de.troetenrepublik.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Signale
import de.troetenrepublik.app.i18n.S

// Inhalt des Insider-Themas einer Aktie, einmal fuer alle drei Einstiege: das Blatt hinter der Signale-Zeile, das Blatt
// hinter dem Punkt in „Chancen und Risiken“ und das Thema im Hintergrund. Reihenfolge ueberall gleich: Meldungen
// (BaFin, Register weiterer Laender, SEC mit Bestaenden), „Im Preis?“ zum juengsten Insiderkauf, angekuendigte
// Verkaeufe, Verguetung. Mit Merker (Hintergrund) geben die Karten ihre Zeilen an die Liste der Seite
// (insiderAbschnitte), ohne Merker zeichnen sie im Blatt selbst. zeile: Das Blatt haengt an einer 90-Tage-Zeile und
// zeigt deren Trend zuerst, darunter die zwoelf Monate. Grossaktionaere stehen nur auf der Seite, dort unter
// „Grosse Halter“ (grossaktionaerAbschnitte).
@Composable
internal fun InsiderInhalt(
    state: AppState, symbol: String, isin: String, name: String, usWert: Boolean, euInsider: Boolean,
    oeffnen: (String) -> Unit, merker: SeitenMerker? = null, zeile: Boolean = false,
) {
    val wirktDas = { oeffnen(CopyRouten.WIRKT_DAS) }
    BafinKarte(state, symbol, isin, name, merker = merker, zeile = zeile, mitGross = merker != null, onCluster = wirktDas)
    if (euInsider) EuInsiderKarte(state, symbol, isin, name, merker = merker, zeile = zeile, onCluster = wirktDas)
    if (usWert) SecKarte(state, symbol, merker = merker, zeile = zeile, onCluster = wirktDas)
    if (merker == null) InsiderImPreis(state, symbol, isin, oeffnen = oeffnen)
    if (usWert) {
        SecFormenKarte(state, symbol, merker = merker, mitHaltern = merker != null)
        VerguetungKarte(state, symbol, merker = merker)
    }
}

// Die Zeilen des Insider-Themas in der Liste der Seite, in der Reihenfolge von InsiderInhalt.
internal fun LazyListScope.insiderAbschnitte(
    merker: SeitenMerker, state: AppState, symbol: String, isin: String, usWert: Boolean, euInsider: Boolean, oeffnen: (String) -> Unit,
) {
    listenAbschnitt(merker, "bafin-eigen")
    listenAbschnitt(merker, "bafin-einstieg")
    listenAbschnitt(merker, "bafin-verkauft")
    if (euInsider) euInsiderAbschnitt(merker)
    if (usWert) secAbschnitte(merker)
    item(key = "insider-impreis", contentType = "impreis") { InsiderImPreis(state, symbol, isin, oeffnen = oeffnen) }
    if (usWert) {
        listenAbschnitt(merker, "f144")
        verguetungAbschnitt(merker)
    }
}

// Grossaktionaere aus den Stimmrechtsmeldungen der BaFin und aus Schedule 13D/13G, im Thema „Grosse Halter“.
internal fun LazyListScope.grossaktionaerAbschnitte(merker: SeitenMerker, usWert: Boolean) {
    listenAbschnitt(merker, "bafin-gross")
    if (usWert) listenAbschnitt(merker, "f13")
}

// Blatt hinter der Insider-Zeile der Signale und dem Insider-Punkt in „Chancen und Risiken“.
@Composable
internal fun InsiderBlatt(
    state: AppState, symbol: String, isin: String, name: String, usWert: Boolean, euInsider: Boolean, oeffnen: (String) -> Unit, onDismiss: () -> Unit,
) {
    DetailBlatt(S.bwThema("FUEHRUNG"), name, onDismiss) {
        InsiderInhalt(state, symbol, isin, name, usWert, euInsider, oeffnen, zeile = true)
    }
}

// Blatt hinter der Kongress-Zeile der Signale und dem Kongress-Punkt in „Chancen und Risiken“: zuerst die Zahl der
// Zeile (90 Tage nach dem Tag der Meldung, Haus und Senat), darunter alle Gruppen ueber zwoelf Monate.
@Composable
internal fun KongressBlatt(
    state: AppState, symbol: String, name: String, usWert: Boolean, zeile: Signale.Kongress?, oeffnen: (String) -> Unit, onDismiss: () -> Unit,
) {
    DetailBlatt(S.copyWhoTitle, name, onDismiss) {
        zeile?.let { KeyValueRow(S.sgKongress, S.sgKaeufeVerkaeufe(it.kaeufe, it.verkaeufe)) }
        HandelndeAbschnitt(state, symbol, oeffnen, usWert = usWert, imBlatt = true)
    }
}
