package de.troetenrepublik.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.UnternehmenTexte
import de.troetenrepublik.app.market.FirmaEmissionen
import de.troetenrepublik.app.market.FirmaEntlassungen
import de.troetenrepublik.app.market.FirmaPatente
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

// Block „Unternehmen“ auf der Wertpapierseite einer Aktie: eingeklappt eine Kopfzeile mit den Themen, die es gibt, aufgeklappt
// die Bausteine untereinander. Jeder Baustein hat einen eigenen Ladeeintrag direkt vor seinen Zeilen. Ein gemeinsamer Ladeeintrag
// oben im Block wuerde beim Scrollen aus dem Bild verworfen, und Karten, die spaeter fertig werden, meldeten ihr Modell nie mehr.

// Reihenfolge der Bausteine im Block
internal enum class UnternehmenThema { STANDORTE, GESCHICHTE, AUFTRAG, PRUEFER, RECHT, ENTLASSUNGEN, PATENTE, EMISSIONEN, STAATSGELD }

// Startet einen Abruf im Bereich der Seite, solange er nicht fertig ist. Er laeuft weiter, wenn der ladende Eintrag aus dem
// Bild scrollt. Ist er ohne Ergebnis beendet (kein Netz), startet der naechste Aufruf ihn neu; ein laufender startet nicht doppelt.
internal fun SeitenMerker.abrufBisFertig(schluessel: String, fertig: () -> Boolean, block: suspend CoroutineScope.() -> Unit) {
    if (fertig() || bereich == null) return
    val laeuft = zustand<Boolean>("$schluessel/laeuft") { false }
    if (laeuft.value) return
    val zaehler = zustand<Int>("$schluessel/n") { 0 }
    laeuft.value = true
    zaehler.value = zaehler.value + 1
    starte("$schluessel#${zaehler.value}") {
        try {
            coroutineScope { block() }
        } finally {
            laeuft.value = false
        }
    }
}

// Themen, die der Block nennt: die, die es fuer diese Aktie geben kann, ohne die, bei denen ein fertiger Abruf nichts fand.
// Entlassungen und Emissionen liegen mitgeliefert vor und sind schon zugeklappt genau bekannt (ohne Netz); die Abrufe der
// anderen starten erst im aufgeklappten Block.
@Composable
internal fun rememberUnternehmenThemen(
    state: AppState, symbol: String, name: String, langerName: String?,
    aktie: Boolean, usWert: Boolean, extern: Boolean, merker: SeitenMerker,
    // false: alle moeglichen Themen, auch die, deren Abruf leer ausging (fuer die Ladeeintraege)
    pruefeLeer: Boolean = true,
): List<UnternehmenThema> {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val moeglich = aktie && !extern

    val entl = merker.zustand<FirmaEntlassungen.Auswertung?>("fu/entl") { null }
    val entlGelesen = merker.zustand("fu/entl/gelesen") { false }
    val emi = merker.zustand<FirmaEmissionen.Stand?>("fu/emi") { null }
    val emiGelesen = merker.zustand("fu/emi/gelesen") { false }
    LaunchedEffect(symbol, moeglich) {
        if (!moeglich) return@LaunchedEffect
        if (!entlGelesen.value) {
            entl.value = entlassungenLesen(state, symbol)
            entlGelesen.value = true
        }
        if (!emiGelesen.value) {
            emi.value = emissionenLesen(symbol, name, langerName)
            emiGelesen.value = true
        }
    }
    if (!moeglich) return emptyList()

    // Ein Baustein ohne Daten: Abruf fertig, aber kein Modell gemeldet
    fun leer(geladen: String, modell: String): Boolean =
        pruefeLeer && merker.zustand(geladen) { false }.value && merker.zustand<ListenModell?>("modell/$modell") { null }.value == null

    val sec = usWert && SecInsider.kennung(settings.secKontakt) != null
    val letztes = Uhr.heute().year - 1
    val patNamen = merker.zustand<List<String>?>("fu/pat/namen") { null }.value
    val patJahre = merker.zustand<Map<Int, Int>>("fu/pat/jahre") { emptyMap() }.value
    return buildList {
        if (!leer("standorte/geladen", "standorte")) add(UnternehmenThema.STANDORTE)
        if (!leer("geschichte/geladen", "geschichte")) add(UnternehmenThema.GESCHICHTE)
        if (sec && !leer("fa-ab/$symbol/geladen", "fa-auftrag")) add(UnternehmenThema.AUFTRAG)
        if (sec && !leer("fa-pr/$symbol/geladen", "fa-pruefer")) add(UnternehmenThema.PRUEFER)
        // Klagen und Urteile (EU-Gerichte, CMA, Justizministerium) gibt es auch ohne SEC-Zugang und fuer Firmen ausserhalb der USA
        if (!leer("fa-rs/$symbol/geladen", "fa-recht")) add(UnternehmenThema.RECHT)
        if (!(entlGelesen.value && entl.value == null)) add(UnternehmenThema.ENTLASSUNGEN)
        if (settings.keyUspto.isNotBlank() && !(patNamen != null && FirmaPatente.kern(patJahre.toList(), letztes) == null)) add(UnternehmenThema.PATENTE)
        if (!(emiGelesen.value && emi.value == null)) add(UnternehmenThema.EMISSIONEN)
        if (!leer("fa-sg/$symbol/geladen", "fa-staatsgeld")) add(UnternehmenThema.STAATSGELD)
    }
}

// Kopf und Bausteine des Blocks als Eintraege der Seite. Ohne Thema entfaellt der Block.
internal fun LazyListScope.unternehmenAbschnitt(
    themen: List<UnternehmenThema>, moegliche: List<UnternehmenThema>, offen: Boolean, umschalten: () -> Unit,
    state: AppState, symbol: String, firma: String, name: String, langerName: String?,
    onOpenRoute: (String) -> Unit, merker: SeitenMerker,
    profil: de.troetenrepublik.app.market.MarketApi.Profile? = null,
) {
    if (themen.isEmpty()) return
    item(key = "unternehmen") {
        AufklappKopf(
            UnternehmenTexte.t("titel"),
            UnternehmenTexte.unter(themen.map { UnternehmenTexte.t("t." + it.name) }),
            offen, onClick = umschalten,
        )
    }
    if (!offen) return
    // Ladeeintraege fuer alle moeglichen Themen: Eine Karte meldet ihr Modell erst nach dem fertigen Abruf. Fiele ihr Eintrag
    // weg, sobald der Abruf fertig ist und noch kein Modell da ist, meldete sie es nie. Ohne Modell bleibt der Eintrag leer.
    for (thema in moegliche) {
        when (thema) {
            UnternehmenThema.STANDORTE -> {
                item(key = "standorte-lader", contentType = "lader") { StandorteKarte(state, symbol, firma, onOpenRoute, merker) }
                listenAbschnitt(merker, "standorte")
            }
            UnternehmenThema.GESCHICHTE -> {
                item(key = "geschichte-lader", contentType = "lader") { GeschichteKarte(state, symbol, firma, merker) }
                listenAbschnitt(merker, "geschichte")
            }
            UnternehmenThema.AUFTRAG -> {
                item(key = "auftrag-lader", contentType = "lader") { AuftragsbestandKarte(state, symbol, merker) }
                listenAbschnitt(merker, "fa-auftrag")
            }
            UnternehmenThema.PRUEFER -> {
                item(key = "pruefer-lader", contentType = "lader") { PrueferKarte(state, symbol, merker) }
                listenAbschnitt(merker, "fa-pruefer")
            }
            UnternehmenThema.RECHT -> {
                item(key = "recht-lader", contentType = "lader") { RechtsstreitKarte(state, symbol, firma, merker) }
                listenAbschnitt(merker, "fa-recht")
            }
            // Kopf und Zeilen dieser drei stehen im selben Eintrag wie ihr Laden
            UnternehmenThema.ENTLASSUNGEN -> if (thema in themen) item(key = "firma-entlassungen", contentType = "firma") { EntlassungenKarte(state, symbol, merker) }
            UnternehmenThema.PATENTE -> if (thema in themen) item(key = "firma-patente", contentType = "firma") { PatenteKarte(state, symbol, name, langerName, merker) }
            UnternehmenThema.EMISSIONEN -> if (thema in themen) item(key = "firma-emissionen", contentType = "firma") { EmissionenKarte(symbol, name, langerName, merker) }
            UnternehmenThema.STAATSGELD -> {
                item(key = "staatsgeld-lader", contentType = "lader") { StaatsgeldKarte(state, symbol, name, langerName, profil, merker) }
                listenAbschnitt(merker, "fa-staatsgeld")
            }
        }
    }
}
