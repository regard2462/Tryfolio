package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.LaenderMarkt
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.Weltbank
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// [Laenderblatt 0.70] Was das Laenderblatt zu einem Land laedt, an einer Stelle: Kopf, Wirtschaft, Markt und die Abschnitte Land,
// Handel, Gesellschaft teilen sich dieselben Daten, jede Quelle wird einmal abgerufen (AppState speichert sie zwischen).

// Alles, was die Abschnitte Land, Handel, Gesellschaft, Staat, Energie, Feiertage und Schlagzeilen zeigen; ohne Netz sind Teile leer.
internal class MehrDaten(
    val steck: LaenderZusatz.Steckbrief?,
    val reihen: Map<String, Weltbank.Reihe?>,
    val handel: LaenderZusatz.Handel?,
    val karte: GlobusKarte?,
    val feiertage: List<LaenderZusatz.Feiertag>,
    val meldungen: List<LaenderZusatz.Meldung>,
)

internal class LaenderGeladen {
    var struktur by mutableStateOf<LaenderMarkt.Struktur?>(null)
    var struktGeladen by mutableStateOf(false)
    var markt by mutableStateOf<LaenderMarkt.Markt?>(null)
    var marktGeladen by mutableStateOf(false)
    var mehr by mutableStateOf<MehrDaten?>(null)
    var mehrFertig by mutableStateOf(false)
}

private suspend fun ladenMehr(state: AppState, land: GlobusLand, deutsch: Boolean): MehrDaten = coroutineScope {
    val feiertage = async { ohneAbbruch { state.globusFeiertage(land.a2) }.getOrDefault(emptyList()) }
    val meldungen = async { ohneAbbruch { state.globusMeldungen(land.a2, land.name(deutsch)) }.getOrDefault(emptyList()) }
    val steck = ohneAbbruch { state.globusSteckbrief() }.getOrNull()?.get(land.a2)
    val handel = ohneAbbruch { state.globusHandel() }.getOrNull()?.get(land.a3)
    val karte = ohneAbbruch { state.globusKarte() }.getOrNull()
    // Die Weltbank-Abrufe laufen gleichzeitig (hoechstens sechs), jeder ist eine Woche gespeichert.
    val reihen = ohneAbbruch { state.weltbankViele(LaenderZusatz.KENNUNGEN) }.getOrDefault(emptyMap())
    MehrDaten(steck, reihen, handel, karte, feiertage.await(), meldungen.await())
}

@Composable
internal fun rememberLaenderGeladen(state: AppState, land: GlobusLand): LaenderGeladen {
    val g = remember(land) { LaenderGeladen() }
    val deutsch = I18n.locale.language == "de"
    LaunchedEffect(land) {
        coroutineScope {
            launch { g.struktur = ohneAbbruch { state.globusStruktur(land.a3) }.getOrNull(); g.struktGeladen = true }
            launch { g.markt = ohneAbbruch { state.globusMarkt(land.a2) }.getOrNull(); g.marktGeladen = true }
            launch { g.mehr = ohneAbbruch { ladenMehr(state, land, deutsch) }.getOrNull(); g.mehrFertig = true }
        }
    }
    return g
}
