package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.FinderLauf
import de.troetenrepublik.app.RadarSpeicher
import de.troetenrepublik.app.engine.ComexLager
import de.troetenrepublik.app.engine.Tagesrueckblick
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.LagerDatei
import de.troetenrepublik.app.market.LmeLagerAbruf
import de.troetenrepublik.app.plattform.rememberOeffnenRoh
import de.troetenrepublik.app.radarLaden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Laeufe der Sammelstelle, die weiterlaufen, wenn die Seite geschlossen wird.
private object AnstossenLauf {
    val kongress = MutableStateFlow<Float?>(null)
    val radar = MutableStateFlow<Float?>(null)
    val rueckblick = MutableStateFlow(false)
}

// Sammelstelle fuer alles, was die Person selbst anstossen muss: vollstaendig laden, Dateien einlesen, Quellen nur im
// Browser. Jede Zeile ruft dieselbe Funktion auf wie die urspruengliche Stelle in der App.
@Composable
fun AnstossenScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    katalogBereit()

    val lesen by state.meldungenStand.collectAsStateWithLifecycle()
    val offen by produceState<Int?>(null, lesen?.fertig) { value = withContext(Dispatchers.IO) { runCatching { state.meldungenOffen() }.getOrNull() } }
    val finder by FinderLauf.zustand.collectAsStateWithLifecycle()
    val kongressLauf by AnstossenLauf.kongress.collectAsStateWithLifecycle()
    val radarLauf by AnstossenLauf.radar.collectAsStateWithLifecycle()
    val rueckblickLauf by AnstossenLauf.rueckblick.collectAsStateWithLifecycle()
    val archiv by state.rueckblicke.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { state.rueckblickeLaden() } }

    var lme by remember { mutableStateOf<LmeLagerAbruf.Ergebnis?>(null) }
    var comex by remember { mutableStateOf<ComexLager.Stand?>(null) }
    LaunchedEffect(Unit) {
        lme = state.lmeLagerStand()
        comex = withContext(Dispatchers.Default) { state.comexLagerStand() }
    }

    // Selbst heruntergeladene Registerdaten uebernehmen, wie unter Profil, Erweitert
    val registerImport = rememberOeffnenRoh { _, daten ->
        scope.launch { state.showToast(if (daten == null) S.imUnbekannt else state.registerImport(daten)) }
    }
    // Lagerdatei der LME oder COMEX einlesen; die Art erkennt die App am Inhalt
    val lagerEinlesen = rememberOeffnenRoh { _, daten ->
        scope.launch {
            val art = if (daten == null) LagerDatei.Art.KEINE else withContext(Dispatchers.Default) { LagerDatei.einlesen(daten, state.lmeLager, state.comexLager).art }
            when (art) {
                LagerDatei.Art.LME -> lme = state.lmeLagerStand()
                LagerDatei.Art.COMEX -> comex = withContext(Dispatchers.Default) { state.comexLagerStand() }
                LagerDatei.Art.KEINE -> state.showToast(S.lmeUnbekannt)
            }
        }
    }
    val depotImport = rememberPortfolioImport(state)

    val heute = Tagesrueckblick.tag(System.currentTimeMillis())
    val rueckblickHeute = archiv?.tage?.any { it.tag == heute } == true
    val letzterComex = comex?.tag
    val radarAm = RadarSpeicher.stand?.am
    val kongressAm = state.kongressFertig()?.am

    fun stand(e: Anstossen.Eintrag): Anstossen.Stand = when (e) {
        Anstossen.Eintrag.MELDUNGEN -> Anstossen.meldungen(lesen?.fertig == false, lesen?.gelesen ?: 0, lesen?.gesamt ?: 0, offen)
        Anstossen.Eintrag.KONGRESS -> Anstossen.geladen(kongressLauf, kongressAm)
        Anstossen.Eintrag.FINDER -> Anstossen.geladen(finder.laedt, finder.stand.takeIf { finder.daten.isNotEmpty() })
        Anstossen.Eintrag.RADAR -> Anstossen.geladen(radarLauf, radarAm)
        Anstossen.Eintrag.RUECKBLICK -> Anstossen.rueckblick(rueckblickLauf, rueckblickHeute)
        Anstossen.Eintrag.LME_DATEI -> Anstossen.lme(lme?.tage?.map { it.tag }.orEmpty())
        Anstossen.Eintrag.COMEX_DATEI -> Anstossen.comex(letzterComex)
        else -> Anstossen.Stand.Keiner
    }

    fun name(e: Anstossen.Eintrag): String = when (e) {
        Anstossen.Eintrag.MELDUNGEN -> S.anMeldungen
        Anstossen.Eintrag.KONGRESS -> S.anKongress
        Anstossen.Eintrag.FINDER -> S.anFinder
        Anstossen.Eintrag.RADAR -> S.anRadar
        Anstossen.Eintrag.RUECKBLICK -> S.rbTitel
        Anstossen.Eintrag.REGISTER -> S.imTitel
        Anstossen.Eintrag.LME_DATEI -> S.lmeTitel
        Anstossen.Eintrag.COMEX_DATEI -> S.comexTitel
        Anstossen.Eintrag.DEPOT -> S.importPortfolioJson
        Anstossen.Eintrag.POSITION -> S.importPosition
        Anstossen.Eintrag.SELBSTABRUF -> S.saTitel
    }

    fun tippen(e: Anstossen.Eintrag) {
        if (stand(e) is Anstossen.Stand.Laeuft) return
        when (e) {
            Anstossen.Eintrag.MELDUNGEN -> state.alleMeldungenStarten()
            Anstossen.Eintrag.KONGRESS -> state.hintergrundScope.launch(Dispatchers.Default) {
                AnstossenLauf.kongress.value = 0f
                runCatching { state.kongressDaten { AnstossenLauf.kongress.value = it } }
                AnstossenLauf.kongress.value = null
            }
            Anstossen.Eintrag.FINDER -> finderAnstossen(state)
            Anstossen.Eintrag.RADAR -> state.hintergrundScope.launch {
                AnstossenLauf.radar.value = 0f
                runCatching { state.radarLaden { AnstossenLauf.radar.value = it } }
                AnstossenLauf.radar.value = null
            }
            Anstossen.Eintrag.RUECKBLICK -> state.hintergrundScope.launch {
                AnstossenLauf.rueckblick.value = true
                runCatching { state.rueckblickJetzt() }
                runCatching { state.rueckblickeLaden() }
                AnstossenLauf.rueckblick.value = false
            }
            Anstossen.Eintrag.REGISTER -> registerImport()
            Anstossen.Eintrag.LME_DATEI, Anstossen.Eintrag.COMEX_DATEI -> lagerEinlesen()
            Anstossen.Eintrag.DEPOT -> depotImport()
            Anstossen.Eintrag.POSITION -> oeffnen("import")
            Anstossen.Eintrag.SELBSTABRUF -> oeffnen("selbstabruf")
        }
    }

    val gruppen = listOf(
        Anstossen.Gruppe.LADEN to S.anGruppeLaden,
        Anstossen.Gruppe.DATEIEN to S.anGruppeDateien,
        Anstossen.Gruppe.BROWSER to S.anGruppeBrowser,
    )
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item(key = "kopf", contentType = "kopf") { ScreenHeader(S.anTitel, null, onBack) }
        gruppen.forEach { (g, titel) ->
            item(key = "gruppe-$g", contentType = "gruppe") { SectionHeader(titel) }
            items(Anstossen.eintraege(g), key = { it.name }, contentType = { "zeile" }) { e ->
                NavRow(name(e), Anstossen.text(stand(e)) { Fmt.date(it) }) { tippen(e) }
            }
        }
        item(key = "ende", contentType = "ende") { Spacer(Modifier.height(40.dp)) }
    }
}
