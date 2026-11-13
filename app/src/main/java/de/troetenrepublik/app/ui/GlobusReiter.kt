package de.troetenrepublik.app.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.engine.Boersenzeiten
import de.troetenrepublik.app.engine.Fruehindikator
import de.troetenrepublik.app.engine.Konjunkturphase
import de.troetenrepublik.app.engine.GlobusEbenen
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.WeltRezession
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusGesamt
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.engine.Hauspreise
import de.troetenrepublik.app.engine.HistorieJst
import de.troetenrepublik.app.engine.GlobusPfad
import de.troetenrepublik.app.engine.GlobusZeichner
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.r2
import de.troetenrepublik.app.market.GlobusIndizes
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

// Compose-Path als Ziel der gemeinsamen Zeichenfunktionen
private class ComposePfad(val pfad: Path) : GlobusPfad {
    override fun nach(x: Float, y: Float) = pfad.moveTo(x, y)
    override fun linie(x: Float, y: Float) = pfad.lineTo(x, y)
    override fun schliessen() = pfad.close()
}

private const val ZOOM_MIN = 0.85f
private const val ZOOM_MAX = 8f
private const val BREITE_MAX = 85.0

private fun modusTitel(m: GlobusModus) = when (m) {
    GlobusModus.GESAMT -> S.globusGesamt
    GlobusModus.BOERSE -> S.globusBoerse
    GlobusModus.WACHSTUM -> S.globusWachstum
    GlobusModus.AUSBLICK -> S.globusAusblick
    GlobusModus.INFLATION -> S.globusInflation
    GlobusModus.ARBEITSLOS -> S.globusArbeitslos
    GlobusModus.SCHULDEN -> S.globusSchulden
    GlobusModus.LEISTUNG -> S.globusLeistung
    GlobusModus.PHASE -> S.globusPhase
    GlobusModus.BOERSENZEIT -> S.globusBoerseZeit
    GlobusModus.DEPOT -> S.globusDepot
    GlobusModus.ENGPAESSE -> S.globusEngpaesse
    GlobusModus.HAUSPREISE -> S.globusHaus
    GlobusModus.AKTIEN1870 -> S.globusAktien1870
    GlobusModus.AKTIEN1950 -> S.globusAktien1950
    GlobusModus.PREISNIVEAU -> S.kkPreisniveau
    GlobusModus.KKP -> S.globusKkp
    GlobusModus.HDI -> S.globusHdi
    GlobusModus.GINI -> S.globusGini
}

// Reiter „Globus“ der Maerkte: drehbarer Globus, eingefaerbt nach Boerse heute, Wachstum, Ausblick oder Inflation, mit
// Laenderblatt beim Antippen und einer Weltuebersicht darunter. Geladen wird erst, wenn der Reiter gewaehlt ist (aktiv).
@Composable
fun GlobusReiter(
    state: AppState, liste: LazyListState, aktiv: Boolean, onOpen: (String) -> Unit, zuStimmung: () -> Unit, startEbene: String? = null, startLand: String? = null, startAbschnitt: String? = null,
    // Abschnitt im Reiter Ausblick oeffnen (Verweis der Rezessionszeichen)
    zuAusblick: (String) -> Unit = {},
) {
    var modus by rememberSaveable { mutableIntStateOf(GlobusModus.entries.indexOfFirst { it.name.equals(startEbene, ignoreCase = true) }.coerceAtLeast(0)) }
    val m = GlobusModus.entries[modus]
    var karte by remember { mutableStateOf<GlobusKarte?>(null) }
    var imf by remember { mutableStateOf(state.imfGespeichert()) }
    // [Globus Boersen 0.73] Staende je Land fuellen sich nach: Yahoo sofort, direkte Boersen im Hintergrund; gefaerbt wird nur Frisches
    val staende by state.globusStaende.collectAsStateWithLifecycle()
    val indizes = remember(staende) { de.troetenrepublik.app.market.IndexStand.frischeProzente(staende, java.time.LocalDate.now()) }
    var fehlt by remember { mutableStateOf(false) }
    var land by remember { mutableStateOf<GlobusLand?>(null) }
    val jahr = remember { java.time.LocalDate.now().year }
    // [Globus Trend und Ebenen] gewaehltes Jahr der IWF-Ebenen, Fruehindikator, Rezessionszeichen, Depot, Uhrzeit
    var waehlJahr by rememberSaveable { mutableIntStateOf(jahr) }
    var cli by remember { mutableStateOf(state.fruehindikatorGespeichert()) }
    var rez by remember { mutableStateOf(state.rezessionGespeichert()) }
    // Zinskurve und Sahm-Regel der Rezessionszeichen kommen aus den Fruehwarnzeichen der Stimmung
    var fruehwarn by remember { mutableStateOf(state.fruehwarnungBekannt()) }
    // Weltbank-Schätzung der Arbeitslosigkeit (ILO) als Ersatz für Länder ohne IWF-Wert
    var wbAlos by remember { mutableStateOf<de.troetenrepublik.app.engine.Weltbank.Reihe?>(null) }
    var jetztMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // [Engpaesse] Lage der Engpaesse (erst geladen, wenn die Ebene gewaehlt ist) und der geoeffnete Engpass
    var engpass by remember { mutableStateOf<Engpaesse.Uebersicht?>(null) }
    // [Globus Gesamtbild 0.73] Zahlen des Gesamtbilds ausser dem IWF (Weltbank, Konflikte, Sanktionen, Reisehinweise)
    var gesamtZusatz by remember { mutableStateOf(state.globusGesamtGespeichert()) }
    // [Laender Historie 0.78] Daten der Ebenen Hauspreise und Aktien real, erst geladen, wenn eine davon gewaehlt ist
    var hist by remember { mutableStateOf(HistorieLader.gespeichert()) }
    LaunchedEffect(m) { if (hist == null && (m == GlobusModus.HAUSPREISE || m == GlobusModus.AKTIEN1870 || m == GlobusModus.AKTIEN1950)) hist = HistorieLader.laden() }
    // [Waehrung und Kaufkraft 0.78] Ranglisten der Ebenen HDI und Gini, Basisland der Ebene Preisniveau
    var ranglisten by remember { mutableStateOf(WaehrungLader.gespeichert()?.rang) }
    LaunchedEffect(m) { if (ranglisten == null && (m == GlobusModus.HDI || m == GlobusModus.GINI)) ranglisten = WaehrungLader.laden().rang }
    var gesamtOffen by rememberSaveable { mutableStateOf(false) }
    // Rangliste der Ebene: zuerst fuenf hoechste und fuenf niedrigste, auf Wunsch alle Laender
    var rangAlle by rememberSaveable(modus) { mutableStateOf(false) }
    var gesamtGebiete by rememberSaveable { mutableStateOf(false) }
    var stelle by rememberSaveable { mutableStateOf<String?>(null) }
    val bewertung by state.valuation.collectAsStateWithLifecycle()
    val katalog = rememberCatalog()
    val scope = rememberCoroutineScope()
    val monat = remember { java.time.YearMonth.now() }

    LaunchedEffect(aktiv) {
        if (!aktiv) return@LaunchedEffect
        if (karte == null) karte = ohneAbbruch { state.globusKarte() }.getOrNull()
        val d = ohneAbbruch { state.imfDaten(erzwingen = true) }.getOrNull()
        if (d != null) imf = d
        fehlt = d == null && imf == null
        ohneAbbruch { state.globusIndizes() }
        ohneAbbruch { state.fruehindikatorDaten() }.getOrNull()?.let { cli = it }
        ohneAbbruch { state.rezessionsDaten() }.getOrNull()?.let { rez = it }
        ohneAbbruch { state.fruehwarnung() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { fruehwarn = it }
        wbAlos = ohneAbbruch { state.weltbank("SL.UEM.TOTL.ZS") }.getOrNull()
    }
    // [Globus Gesamtbild 0.73] erst der gebuendelte Teil, der Rest kommt nach und vervollstaendigt das Bild
    LaunchedEffect(aktiv) {
        if (!aktiv) return@LaunchedEffect
        ohneAbbruch { state.globusGesamtZusatz { gesamtZusatz = it } }
        // Rueckfallkette fuer Laender ohne IWF-Reihen (Weltbank, UN, Factbook) nachladen
        ohneAbbruch { state.imfDatenVoll() }.getOrNull()?.let { imf = it }
    }
    // [Engpaesse] erst der gespeicherte Stand, dann der Abruf
    LaunchedEffect(aktiv, m == GlobusModus.ENGPAESSE) {
        if (!aktiv || m != GlobusModus.ENGPAESSE) return@LaunchedEffect
        if (engpass == null) engpass = ohneAbbruch { state.engpassGespeichert() }.getOrNull()
        ohneAbbruch { state.engpassUebersicht() }.getOrNull()?.let { engpass = it }
    }
    // Ziel von aussen: Weltlage unter der Ebene Engpaesse (ENSO und Spannungsgebiete)
    var sprungErledigt by remember { mutableStateOf(startAbschnitt == null) }
    LaunchedEffect(aktiv, m, sprungErledigt) {
        if (!aktiv || sprungErledigt || m != GlobusModus.ENGPAESSE || startAbschnitt != "weltlage") return@LaunchedEffect
        sprungErledigt = true
        liste.zuSchluessel("globus-weltlage")
    }
    // Die Boersenzeiten rechnen mit der Uhr: jede Minute neu, solange die Ebene offen ist
    LaunchedEffect(aktiv, m) {
        if (!aktiv || m != GlobusModus.BOERSENZEIT) return@LaunchedEffect
        while (true) { jetztMs = System.currentTimeMillis(); delay(60_000) }
    }

    val jahre = remember(imf, jahr) { imfJahre(imf, jahr) }
    val wj = waehlJahr.coerceIn(jahre)
    val laenderDepot = remember(katalog) {
        val aus = HashMap<String, String>()
        katalog?.aktien?.forEach { if (it.c.isNotBlank()) aus[it.s] = it.c }
        katalog?.basiswerte?.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
        katalog?.ipos?.abgeschlossen?.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
        aus
    }
    // [Globus Depot 0.70] Taetigkeitsland statt Sitz, ETFs nach Index auf Wunsch
    var etfEin by rememberSaveable { mutableStateOf(true) }
    var herkunft by remember { mutableStateOf<Map<String, de.troetenrepublik.app.engine.Taetigkeit.Herkunft>>(emptyMap()) }
    var etfDaten by remember { mutableStateOf<Pair<Map<String, Map<String, Double>>, Map<String, Double>>?>(null) }
    val herkunftWahl = state.account.collectAsStateWithLifecycle().value.herkunftWahl
    LaunchedEffect(aktiv, m == GlobusModus.DEPOT, bewertung.positions.map { it.position.symbol }.toSet(), herkunftWahl) {
        if (!aktiv || m != GlobusModus.DEPOT) return@LaunchedEffect
        ohneAbbruch { state.depotHerkunft() }.getOrNull()?.let { herkunft = it }
        ohneAbbruch { state.etfLaender() }.getOrNull()?.let { etfDaten = it }
    }
    val depot = remember(bewertung.positions, laenderDepot, herkunft, etfEin, etfDaten) {
        val etfs = etfDaten?.first
        de.troetenrepublik.app.engine.DepotStreuung.anteile(
            // Verkaufte Optionen (negativer Wert) sind keine Anlage in einem Land
            bewertung.positions.filter { it.value > 0 }.map { Triple(it.position.symbol, it.value, etfs?.containsKey(it.position.symbol) == true || de.troetenrepublik.app.market.Symbols.typeOf(it.position.symbol) == de.troetenrepublik.app.data.AssetType.ETF) },
            { herkunft[it]?.land ?: laenderDepot[it] }, if (etfEin && etfs != null) { s -> etfs[s] } else null,
        )
    }
    val bilanz = remember(depot, etfDaten) {
        de.troetenrepublik.app.engine.DepotStreuung.bilanz(depot, etfDaten?.second.orEmpty(), de.troetenrepublik.app.engine.Regionen.aktuell)
    }
    val minute = jetztMs / 60_000L

    val signal = LocalSignal.current
    val ocker = Kategorie.ocker
    val werte = remember(karte, m, indizes, imf, wj, cli, depot, monat, wbAlos, gesamtZusatz, hist, ranglisten) {
        val k = karte
        val basis = k?.nachA2(de.troetenrepublik.app.engine.Regionen.aktuell)?.a3
        if (k == null) emptyList() else k.laender.map { wertVon(m, it, indizes, imf, wj, cli, monat, depot, wbAlos, jahr, gesamtZusatz, hist, ranglisten, basis) }
    }
    val rangSortiert = remember(karte, werte) { rangWerte(karte, werte) }
    val gp = signal.globus
    val farben = remember(karte, m, werte, cli, imf, jahr, depot, monat, minute, signal) {
        val k = karte
        val up = signal.up.toArgb(); val down = signal.down.toArgb(); val neutral = ocker.toArgb()
        val jetzt = java.time.Instant.ofEpochMilli(minute * 60_000L)
        if (k == null) emptyArray<Color>() else Array(k.laender.size) { i ->
            val l = k.laender[i]
            Color(when (m) {
                GlobusModus.PHASE -> GlobusFarbe.phase(Konjunkturphase.von(cli, imf, l.a3, monat, jahr)?.phase, up, down, neutral, gp)
                GlobusModus.BOERSENZEIT -> GlobusFarbe.handel(Boersenzeiten.offen(l.a2, jetzt), up, gp)
                GlobusModus.DEPOT -> GlobusFarbe.anteil(depot.nachLand[l.a2], neutral, gp)
                GlobusModus.ENGPAESSE -> gp.gedimmt
                else -> GlobusFarbe.farbe(m, werte[i], up, down, neutral, gp)
            })
        }
    }
    val punkte = remember(engpass, m, signal) {
        if (m != GlobusModus.ENGPAESSE) emptyList() else {
            val up = signal.up.toArgb(); val down = signal.down.toArgb()
            Engpaesse.STELLEN.map { GlobusEbenen.Punkt(it.id, it.lon, it.lat, GlobusFarbe.engpass(engpass?.lage?.get(it.id)?.abw, up, down, gp)) }
        }
    }
    val sonne = remember(m, minute) {
        if (m == GlobusModus.BOERSENZEIT) Boersenzeiten.sonne(java.time.Instant.ofEpochMilli(minute * 60_000L)) else null
    }

    // [Globus Gesamtbild 0.73] Rangfolge des laufenden Jahres, wird mit den nachgeladenen Zahlen vollstaendiger
    val gesamtAlle = remember(imf, gesamtZusatz, wj) { imf?.let { GlobusGesamt.rangliste(it, GlobusGesamt.laender(it) + gesamtZusatz.orEmpty().keys, wj, gesamtZusatz.orEmpty()) } }.orEmpty()
    val gesamtRang = remember(gesamtAlle, gesamtGebiete) { if (gesamtGebiete) gesamtAlle else GlobusGesamt.nurStaaten(gesamtAlle) }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "globus-wahl") {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlobusModus.entries.forEachIndexed { i, x -> Chip(modusTitel(x), i == modus) { modus = i } }
            }
        }
        item(key = "globus-kugel") {
            val k = karte
            if (k == null) Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(gp.hintergrund)), contentAlignment = Alignment.Center) {
                Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = Color(gp.ladeSchrift))
            } else GlobusAnsichtFlaeche(k, farben, sonne, punkte, { stelle = it }) { land = it }
        }
        item(key = "globus-legende") { GlobusLegende(m, Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        if (m == GlobusModus.ENGPAESSE) {
            item(key = "globus-engpass-liste") { EngpassListe(engpass) { stelle = it } }
            item(key = "globus-weltlage") { WeltlageKasten(state, onOpen) }
        }
        // [0.73] Fehlen die IWF-Daten ganz oder teilweise, steht das da, mit erneutem Versuch
        val imfLuecke = imf?.let { de.troetenrepublik.app.engine.Imf.unvollstaendig(it) } == true
        if ((fehlt || imfLuecke) && m.mitJahr) item(key = "globus-fehlt") {
            EmptyHint(if (fehlt) S.globusFehlt else S.globusFehltTeil, S.globusNeuLaden to {
                scope.launch {
                    val n = ohneAbbruch { state.imfDaten(erzwingen = true) }.getOrNull()
                    if (n != null) imf = n
                    fehlt = n == null && imf == null
                }
            })
        }
        val d = imf
        val k = karte
        if (m.mitJahr && d != null) item(key = "globus-jahr") { JahrRegler(wj, jahre, jahr) { waehlJahr = it } }
        if (m == GlobusModus.DEPOT) item(key = "globus-depot") {
            DepotHinweis(depot, k, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (m == GlobusModus.DEPOT) item(key = "globus-depot-streuung") {
            DepotStreuungBereich(etfEin, { etfEin = it }, bilanz, depot.unzuordenbar, herkunft, bewertung.positions.filter { it.value > 0 }, k, herkunftWahl) { s, l -> state.herkunftWaehlen(s, l) }
        }
        if (k != null && m != GlobusModus.BOERSENZEIT && m != GlobusModus.PHASE && m != GlobusModus.ENGPAESSE) {
            rangListe(modusTitel(m), m, rangSortiert, k, rangAlle, { rangAlle = it }, { wertText(m, it) }) { land = it }
        }
        // [Globus Gesamtbild 0.73] Staerkste Laender insgesamt (Gesamtbild des laufenden Jahres) unter jeder Ebene, vollstaendige Liste aufklappbar
        if (k != null && gesamtRang.isNotEmpty()) {
            item(key = "globus-gesamtrang") { GesamtRangOben(gesamtRang, k, gesamtOffen, { gesamtOffen = it }, gesamtGebiete, { gesamtGebiete = it }) { land = it } }
            if (gesamtOffen) itemsIndexed(gesamtRang.drop(GESAMT_OBEN), key = { _, r -> "globus-gesamtrang-" + r.code }) { i, r ->
                GesamtZeile(GESAMT_OBEN + i + 1, r, k) { land = it }
            }
        }
        if (d != null && k != null) item(key = "globus-welt") { WeltUebersicht(d, k, jahr, cli, rez, fruehwarn, monat, zuStimmung, zuAusblick) { land = it } }
        item(key = "globus-quelle") {
            // [Globus Gesten 0.70] Quellen gesammelt am Ende des Reiters
            QuellenAnhang(
                listOf(weltAusblickQuelle(), QuelleLink("OECD (CC BY 4.0)", "https://data-explorer.oecd.org/")) +
                    (if (m == GlobusModus.ENGPAESSE) listOf(QuelleLink(S.quelleIwfPortWatch, Engpaesse.SEITE)) else emptyList()) +
                    (if (m == GlobusModus.HAUSPREISE) listOfNotNull(pflichtQuelle(LaenderQuellen.dallas().first, LaenderQuellen.dallas().second)) else emptyList()) +
                    (if (m == GlobusModus.AKTIEN1870 || m == GlobusModus.AKTIEN1950) listOfNotNull(pflichtQuelle(LaenderQuellen.jst().first, LaenderQuellen.jst().second)) else emptyList()) +
                    (if (m == GlobusModus.HDI) listOf(WaehrungQuellen.undp()) else emptyList()) +
                    (if (m == GlobusModus.GINI) listOf(WaehrungQuellen.weltbank()) else emptyList()) +
                    if (gesamtZusatz == null) emptyList() else listOf(
                        LaenderQuellen.weltbank("").let { QuelleLink(it.first + " (CC BY 4.0)", it.second) },
                        LaenderQuellen.ucdp().let { QuelleLink(it.first + " (CC BY 4.0)", it.second) },
                    ),
                Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
    // [0.73] Oeffnet ein Papier aus dem Laenderblatt, schliesst sich das Blatt (sonst bliebe die Aktie unsichtbar dahinter
    // liegen) und kommt nach dem Zurueck an derselben Stelle wieder: Land, Scrollstand und aufgeklappte Abschnitte bleiben.
    var landA3 by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(land) { land?.let { landA3 = it.a3 } }
    LaunchedEffect(karte) { if (land == null) landA3?.let { a -> land = karte?.nachA3(a) } }
    // [0.73] Sprung aus Wirkungsnetz und Karte "Abhaengigkeiten": das Land (alpha-2) einmal oeffnen
    var startLandOffen by rememberSaveable { mutableStateOf(startLand) }
    LaunchedEffect(karte) { startLandOffen?.let { a2 -> karte?.nachA2(a2)?.let { land = it; startLandOffen = null } } }
    val landRueck = rememberWiederkehr()
    val landHalter = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val landScroll = rememberSaveable(land?.a3, saver = LazyListState.Saver) { LazyListState() }
    land?.let { l ->
        val schluessel = "laender-" + l.a3
        if (!landRueck.wartet) landHalter.SaveableStateProvider(schluessel) {
            LaenderBlatt(
                state, l, staende, imf, wj, landScroll, landRueck.sofort,
                onOpen = { s -> landRueck.verlassen(); onOpen(s) },
            ) { land = null; landA3 = null; landRueck.vergessen(); landHalter.removeState(schluessel) }
        }
        BlattBild(landRueck, "laender") { LaenderBlattInhalt(state, l, staende, imf, wj, landScroll, {}) }
    }
    // Ein Papier aus einem Engpassblatt: Das Blatt schliesst sich (sonst bliebe die Aktie unsichtbar dahinter liegen) und
    // kommt nach dem Zurueck an derselben Stelle wieder
    stelle?.let { id -> WiederkehrBlatt("engpass-$id") { w -> EngpassBlatt(state, id, engpass, karte, { s -> w.verlassen(); onOpen(s) }) { stelle = null } } }
}

private fun wertVon(
    m: GlobusModus, l: GlobusLand, indizes: Map<String, Double>, d: Imf.Datensatz?, jahr: Int,
    cli: Fruehindikator.Datensatz?, monat: java.time.YearMonth, depot: GlobusEbenen.Anteile,
    alos: de.troetenrepublik.app.engine.Weltbank.Reihe?, heute: Int, zusatz: Map<String, GlobusGesamt.Zusatz>?,
    hist: HistorieLader.Stand? = null, ranglisten: de.troetenrepublik.app.engine.Ranglisten.Datei? = null, basis: String? = null,
): Double? = when (m) {
    GlobusModus.PREISNIVEAU -> d?.let { de.troetenrepublik.app.engine.Preisniveau.preisniveau(it, l.a3, de.troetenrepublik.app.engine.Preisniveau.basis(it, basis, jahr), jahr) }
    GlobusModus.KKP -> d?.let { de.troetenrepublik.app.engine.Preisniveau.preisniveau(it, l.a3, de.troetenrepublik.app.engine.Preisniveau.USA, jahr) }
        ?.let { de.troetenrepublik.app.engine.Preisniveau.bewertung(it) }
    GlobusModus.HDI -> ranglisten?.let { de.troetenrepublik.app.engine.Ranglisten.wert(it, de.troetenrepublik.app.engine.Ranglisten.Index.HDI, l.a3) }
    GlobusModus.GINI -> ranglisten?.let { de.troetenrepublik.app.engine.Ranglisten.wert(it, de.troetenrepublik.app.engine.Ranglisten.Index.GINI, l.a3) }
    GlobusModus.HAUSPREISE -> hist?.haus?.let { h -> h.laender[l.a3]?.let { Hauspreise.kern(h, it).abweichung } }
    GlobusModus.AKTIEN1870 -> hist?.jst?.laender?.get(l.a3)?.let { HistorieJst.aktienRendite(it) }
    GlobusModus.AKTIEN1950 -> hist?.jst?.laender?.get(l.a3)?.let { HistorieJst.aktienRendite(it, 1950) }
    GlobusModus.GESAMT -> d?.let { GlobusGesamt.rechnen(it, l.a3, jahr, zusatz?.get(l.a3))?.punkte }
    GlobusModus.BOERSE -> indizes[l.a2]
    // Phase und Börsenzeiten haben keine Zahl für eine Rangliste
    GlobusModus.PHASE, GlobusModus.BOERSENZEIT, GlobusModus.ENGPAESSE -> null
    GlobusModus.DEPOT -> depot.nachLand[l.a2]?.times(100.0)
    GlobusModus.ARBEITSLOS -> d?.let { Imf.land(it, l.a3, jahr) }?.arbeitslosigkeit
        // Ersatz ohne IWF-Wert: letzte Weltbank-Schätzung, im laufenden Jahr oder im Jahr der Schätzung
        ?: alos?.werte?.get(l.a3)?.takeIf { jahr == heute || it.jahr == jahr }?.wert
    else -> d?.let { Imf.land(it, l.a3, jahr) }?.let {
        when (m) {
            GlobusModus.WACHSTUM -> it.wachstum
            GlobusModus.AUSBLICK -> it.ausblick
            GlobusModus.ARBEITSLOS -> it.arbeitslosigkeit
            GlobusModus.SCHULDEN -> it.schulden
            GlobusModus.LEISTUNG -> it.leistungsbilanz
            else -> it.inflation
        }
    }
}

// [Globus Trend und Ebenen] Jahre des Reglers: ab 2000 (soweit der IWF-Datensatz so weit zurückreicht) bis zum letzten Prognosejahr.
private fun imfJahre(d: Imf.Datensatz?, jahr: Int): IntRange {
    val alle = d?.werte?.get(Imf.WACHSTUM)?.values?.flatMap { it.keys }.orEmpty()
    if (alle.isEmpty()) return jahr..jahr
    return maxOf(2000, alle.min())..alle.max()
}

// Zahl einer Ebene für die Rangliste
private fun wertText(m: GlobusModus, v: Double): String = when (m) {
    // Gesamtpunkte von 0 bis 100
    GlobusModus.GESAMT -> v.roundToInt().toString()
    GlobusModus.BOERSE, GlobusModus.WACHSTUM, GlobusModus.AUSBLICK, GlobusModus.LEISTUNG, GlobusModus.AKTIEN1870, GlobusModus.AKTIEN1950 -> Fmt.signedPct(v, 1)
    GlobusModus.HAUSPREISE, GlobusModus.KKP -> Fmt.signedPct(v, 0)
    GlobusModus.PHASE, GlobusModus.GINI -> Fmt.num(v, 1)
    GlobusModus.HDI -> Fmt.num(v, 3)
    GlobusModus.SCHULDEN, GlobusModus.PREISNIVEAU -> Fmt.pct(v, 0)
    else -> Fmt.pct(v, 1)
}

// Der Globus selbst: Ziehen dreht (mit Schwung), zwei Finger oder Mausrad zoomen, ein Tipp waehlt ein Land.
@Composable
internal fun GlobusAnsichtFlaeche(
    karte: GlobusKarte, farben: Array<Color>, sonne: Boersenzeiten.Sonne?, punkte: List<GlobusEbenen.Punkt>, onPunkt: (String) -> Unit, onLand: (GlobusLand) -> Unit,
) {
    var lon by remember { mutableDoubleStateOf(10.0) }
    var lat by remember { mutableDoubleStateOf(28.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var groesse by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var schwung by remember { mutableStateOf<Job?>(null) }
    val dichte = LocalDensity.current.density
    val gp = LocalSignal.current.globus
    val ansicht = remember { GlobusAnsicht() }
    val pfade = remember(karte) { Array(karte.laender.size) { Path().also { p -> p.fillType = PathFillType.EvenOdd } } }
    val adapter = remember(pfade) { Array(pfade.size) { ComposePfad(pfade[it]) } }
    val grenzen = remember(karte) { Path() }
    val grenzAdapter = remember(grenzen) { ComposePfad(grenzen) }
    val tmp = remember { DoubleArray(2) }
    val bild = remember { FloatArray(2) }
    val nachtPfad = remember { Path() }
    val nachtAdapter = remember(nachtPfad) { ComposePfad(nachtPfad) }
    // [Globus Gesten 0.70] Laendernamen ab einer Zoomstufe
    val deutsch = I18n.locale.language == "de"
    val namen = remember(karte, deutsch) { GlobusNamen(karte, deutsch) }
    val messer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 256)

    fun radius(): Float = min(groesse.width, groesse.height) / 2f * 0.94f * zoom

    fun drehen(dx: Float, dy: Float) {
        val r = radius()
        if (r <= 1f) return
        val grad = Math.toDegrees((1.0 / r))
        var l = lon - dx * grad
        if (l > 180.0) l -= 360.0 else if (l < -180.0) l += 360.0
        lon = l
        lat = (lat + dy * grad).coerceIn(-BREITE_MAX, BREITE_MAX)
    }

    fun antippen(p: Offset) {
        if (groesse.width == 0) return
        ansicht.setze(lon, lat, groesse.width / 2f, groesse.height / 2f, radius())
        // Ebene Engpaesse: der naechste Punkt im Umkreis von etwa einem Daumen gewinnt vor dem Land
        if (punkte.isNotEmpty()) {
            var bester: String? = null
            var abstand = 34f * dichte
            for (pt in punkte) {
                if (!ansicht.bildpunkt(pt.lon, pt.lat, bild)) continue
                val d = kotlin.math.hypot(bild[0] - p.x, bild[1] - p.y)
                if (d < abstand) { abstand = d; bester = pt.id }
            }
            if (bester != null) { onPunkt(bester); return }
        }
        if (!ansicht.rueck(p.x, p.y, tmp)) return
        // Etwa ein Fingerbreit Spielraum fuer kleine Staaten
        val tol = Math.toDegrees(10.0 * dichte / radius().toDouble()).coerceAtMost(4.0)
        karte.landBei(tmp[0], tmp[1], tol)?.let(onLand)
    }

    // [Globus Gesten 0.73] Ziehen auf dem Globus schliesst die eigene Seite (Maerkte) nie
    Box(Modifier.fillMaxWidth().aspectRatio(1f).wischSperre()) {
    Canvas(
        // Eigene Ebene: Die Kugel wird nur neu gezeichnet, wenn sie sich selbst aendert, nicht wenn daneben etwas rollt.
        Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.clipToBounds().background(Color(gp.hintergrund)).onSizeChanged { groesse = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            zoom = (zoom * exp(-dy * 0.12f)).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    schwung?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var bewegt = false
                    var gesamt = Offset.Zero
                    while (true) {
                        val ev = awaitPointerEvent()
                        val offen = ev.changes.filter { it.pressed }
                        if (offen.isEmpty()) {
                            val up = ev.changes.firstOrNull()
                            if (!bewegt && up != null && up.changedToUp()) antippen(down.position)
                            else if (bewegt) {
                                val v = tracker.calculateVelocity()
                                if (abs(v.x) + abs(v.y) > 200f) {
                                    schwung = scope.launch {
                                        var vx = v.x; var vy = v.y
                                        var zuletzt = System.nanoTime()
                                        while (abs(vx) + abs(vy) > 25f) {
                                            delay(16)
                                            val jetzt = System.nanoTime()
                                            val dt = (jetzt - zuletzt) / 1e9f
                                            zuletzt = jetzt
                                            drehen(vx * dt, vy * dt)
                                            val f = exp(-3.2f * dt)
                                            vx *= f; vy *= f
                                        }
                                    }
                                }
                            }
                            break
                        }
                        if (offen.size >= 2) {
                            bewegt = true
                            val a = offen[0].position; val b = offen[1].position
                            val abstand = (a - b).getDistance()
                            val pa = offen[0].previousPosition; val pb = offen[1].previousPosition
                            val vorher = (pa - pb).getDistance()
                            if (vorher > 1f && abstand > 1f) zoom = (zoom * abstand / vorher).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            offen.forEach { it.consume() }
                            continue
                        }
                        val c = offen[0]
                        tracker.addPosition(c.uptimeMillis, c.position)
                        if (c.positionChanged()) {
                            val delta = c.position - c.previousPosition
                            gesamt += delta
                            if (!bewegt && gesamt.getDistance() > slop) bewegt = true
                            if (bewegt) { drehen(delta.x, delta.y); c.consume() }
                        }
                    }
                }
            },
    ) {
        val r = min(size.width, size.height) / 2f * 0.94f * zoom
        ansicht.setze(lon, lat, size.width / 2f, size.height / 2f, r)
        val mitte = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(gp.kugel), r, mitte)
        val mindest = 0.8f
        for (i in pfade.indices) {
            val p = pfade[i]
            p.rewind()
            if (GlobusZeichner.flaeche(karte.laender[i], ansicht, adapter[i], mindest)) {
                drawPath(p, farben.getOrElse(i) { Color(gp.ohneDaten) })
            }
        }
        // Nachtseite (Ebene Börsenzeiten): dunklere Halbkugel aus dem Sonnenstand
        if (sonne != null) {
            nachtPfad.rewind()
            if (Boersenzeiten.nacht(sonne, ansicht, nachtAdapter)) drawPath(nachtPfad, Color(gp.nacht))
        }
        grenzen.rewind()
        GlobusZeichner.linien(karte.linien, ansicht, grenzAdapter, mindest)
        drawPath(
            grenzen, Color(gp.grenze).copy(alpha = 0.75f),
            style = Stroke(width = 0.6f * dichte, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Punkte der Ebene Engpaesse: Ring und gefuellter Punkt
        for (pt in punkte) {
            if (!ansicht.bildpunkt(pt.lon, pt.lat, bild)) continue
            val o = Offset(bild[0], bild[1])
            drawCircle(Color(pt.farbe).copy(alpha = 0.3f), 11f * dichte, o)
            drawCircle(Color(pt.farbe), 6f * dichte, o)
            drawCircle(Color.White.copy(alpha = 0.85f), 6f * dichte, o, style = Stroke(width = 1.2f * dichte))
        }
        namen.zeichnen(this, messer, ansicht, zoom, r, Color(gp.schrift), Color(gp.schriftRand))
        drawCircle(Color(gp.rand), r, mitte, style = Stroke(width = 1f * dichte))
    }
    }
}

// Kleine Legende je Ebene: Verlauf von schwach nach stark, Farbfelder (Phasen, Börse) und „Keine Daten“.
@Composable
private fun GlobusLegende(m: GlobusModus, modifier: Modifier) {
    val signal = LocalSignal.current
    val gp = signal.globus
    val grau = Color(gp.nullwert)
    val schrift = MaterialTheme.typography.labelSmall
    val keine = Color(gp.ohneDaten)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (m) {
            GlobusModus.PHASE -> {
                val phasen = Fruehindikator.Phase.entries
                val farbe = { p: Fruehindikator.Phase ->
                    Color(GlobusFarbe.phase(p, signal.up.toArgb(), signal.down.toArgb(), Kategorie.ocker.toArgb(), gp))
                }
                for (zeile in phasen.chunked(2)) Row(Modifier.fillMaxWidth()) {
                    for (p in zeile) Box(Modifier.weight(1f)) { Farbfeld(farbe(p), S.trendPhase(p)) }
                }
                Farbfeld(keine, S.globusKeineDaten)
                Hinweis(S.globusLegPhase)
            }
            GlobusModus.BOERSENZEIT -> {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Farbfeld(signal.up, S.globusOffen) }
                    Box(Modifier.weight(1f)) { Farbfeld(grau, S.globusGeschlossen) }
                    Box(Modifier.weight(1f)) { Farbfeld(keine, S.globusKeineDaten) }
                }
                Hinweis(S.globusLegBoerse)
            }
            GlobusModus.ENGPAESSE -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.signedPct(-Engpaesse.VOLL_PROZENT, 0), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(signal.down, Color(gp.punktOhne), signal.up))))
                    Text(Fmt.signedPct(Engpaesse.VOLL_PROZENT, 0), style = schrift, maxLines = 1, softWrap = false)
                }
                Farbfeld(Color(gp.punktOhne), S.engOhneVergleich)
                Hinweis(S.globusLegEngpaesse)
            }
            GlobusModus.DEPOT -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.pct(0.0, 0), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(grau, Kategorie.ocker))))
                    Text(Fmt.pct(GlobusFarbe.ANTEIL_VOLL * 100, 0), style = schrift, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(16.dp))
                    Farbfeld(keine, S.globusKeineDaten)
                }
                Hinweis(S.globusLegDepot)
            }
            GlobusModus.GESAMT -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("0", style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(signal.down, grau, signal.up))))
                    Text("100", style = schrift, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(16.dp))
                    Farbfeld(keine, S.globusKeineDaten)
                }
            }
            else -> {
                val voll = GlobusFarbe.voll(m)
                val mitte = GlobusFarbe.mitte(m)
                val farben = when {
                    m == GlobusModus.INFLATION -> listOf(grau, Kategorie.ocker)
                    GlobusFarbe.ohneUrteil(m) -> listOf(Color(GlobusFarbe.ERHOLUNG), grau, Kategorie.ocker)
                    GlobusFarbe.niedrigGut(m) -> listOf(signal.up, grau, signal.down)
                    else -> listOf(signal.down, grau, signal.up)
                }
                val vorzeichen = m == GlobusModus.BOERSE || m == GlobusModus.WACHSTUM || m == GlobusModus.AUSBLICK || m == GlobusModus.LEISTUNG ||
                    m == GlobusModus.HAUSPREISE || m == GlobusModus.AKTIEN1870 || m == GlobusModus.AKTIEN1950 || m == GlobusModus.KKP
                fun text(v: Double) = when {
                    m == GlobusModus.HDI -> Fmt.num(v, 1)
                    m == GlobusModus.GINI -> Fmt.num(v, 0)
                    vorzeichen -> Fmt.signedPct(v, 0)
                    else -> Fmt.pct(v, 0)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m == GlobusModus.INFLATION) text(0.0) else text(mitte - voll), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(farben)))
                    Text(if (m == GlobusModus.INFLATION) text(voll) else text(mitte + voll), style = schrift, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(16.dp))
                    Farbfeld(keine, S.globusKeineDaten)
                }
                when (m) {
                    GlobusModus.BOERSE -> Hinweis(S.globusLegBoerseHeute)
                    GlobusModus.ARBEITSLOS -> Hinweis(S.globusLegArbeitslos)
                    GlobusModus.SCHULDEN -> Hinweis(S.globusLegSchulden)
                    GlobusModus.LEISTUNG -> Hinweis(S.globusLegLeistung)
                    GlobusModus.HAUSPREISE -> Hinweis(S.globusLegHaus)
                    GlobusModus.AKTIEN1870 -> Hinweis(S.globusLegAktien1870)
                    GlobusModus.AKTIEN1950 -> Hinweis(S.globusLegAktien1950)
                    GlobusModus.PREISNIVEAU -> Hinweis(S.globusLegPreisniveau)
                    GlobusModus.KKP -> Hinweis(S.globusLegKkp)
                    GlobusModus.HDI -> Hinweis(S.globusLegHdi)
                    GlobusModus.GINI -> Hinweis(S.globusLegGini)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun Farbfeld(farbe: Color, text: String) {
    // Im hellen Design bekommt das Feld einen feinen Rand, sonst verschwindet ein helles Feld auf hellem Grund
    val rand = if (LocalSignal.current.dark) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).then(rand).background(farbe))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Zeitregler der IWF-Ebenen: Jahr von 2000 (soweit vorhanden) bis zum letzten Prognosejahr, Prognosejahre in der Beschriftung markiert.
// [0.71] Eingeklappt, weil ein Wisch ueber den Globus sonst gern am Regler haengen bleibt; ein Tipp auf die Zeile klappt
// ihn auf, und abseits des aktuellen Jahres setzt ein Tipp auf "Zurueck auf ..." das Jahr zurueck.
@Composable
private fun JahrRegler(wahl: Int, jahre: IntRange, aktuell: Int, onWahl: (Int) -> Unit) {
    if (jahre.first >= jahre.last) return
    var offen by rememberSaveable { mutableStateOf(false) }
    val schrift = MaterialTheme.typography.labelSmall
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val bez = S.globusJahrRegler
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().zeilenKlick { offen = !offen }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.globusJahr(wahl, wahl >= aktuell), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (wahl != aktuell) {
                Text(
                    S.globusJahrZurueck(aktuell), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
                    modifier = Modifier.textKlick(8.dp) { onWahl(aktuell) }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            AufklappPfeil(offen)
        }
        if (offen) {
            Slider(
                value = wahl.toFloat(), onValueChange = { onWahl(it.roundToInt().coerceIn(jahre)) },
                valueRange = jahre.first.toFloat()..jahre.last.toFloat(), steps = (jahre.last - jahre.first - 1).coerceAtLeast(0),
                modifier = Modifier.semantics { contentDescription = bez },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${jahre.first}", style = schrift, color = grau, maxLines = 1, softWrap = false)
                Text(S.globusJahr(jahre.last, jahre.last >= aktuell), style = schrift, color = grau, maxLines = 1, softWrap = false)
            }
        }
    }
}

// Zur Ebene Mein Depot: Anteil ohne Land und Hinweis bei einem Land über der Hälfte.
@Composable
private fun DepotHinweis(depot: GlobusEbenen.Anteile, karte: GlobusKarte?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (depot.leer) {
            Hinweis(S.globusDepotLeer)
            return@Column
        }
        if (depot.unzuordenbar > 0.0) Hinweis(S.globusDepotNichtZuordenbar(Fmt.pct(depot.unzuordenbar * 100, 0)))
        val g = depot.groesstes
        if (g != null && g.value > GlobusEbenen.KLUMPEN) {
            val name = karte?.nachA2(g.key)?.name(I18n.locale.language == "de") ?: g.key
            Text(S.globusKlumpen(name, Fmt.pct(g.value * 100, 0)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Alle Laender der aktiven Ebene mit Wert, absteigend sortiert (ohne Doppelte und ohne ungueltige Werte).
private fun rangWerte(karte: GlobusKarte?, werte: List<Double?>): List<GlobusEbenen.Rang> {
    val k = karte ?: return emptyList()
    return werte.mapIndexedNotNull { i, w -> w?.takeIf { it.isFinite() }?.let { GlobusEbenen.Rang(k.laender[i].a3, it) } }
        .distinctBy { it.code }.sortedByDescending { it.wert }
}

// So viele hoechste und niedrigste Laender stehen zuerst da; der Rest hinter "Alle n".
private const val RANG_KURZ = 5

// Farbe eines Balkens der Rangliste: gut gruen, schlecht rot; Ebenen ohne Urteil und Anteile neutral.
@Composable
private fun rangFarbe(m: GlobusModus, wert: Double): Color {
    val signal = LocalSignal.current
    if (GlobusFarbe.ohneUrteil(m) || m == GlobusModus.INFLATION || m == GlobusModus.DEPOT) return AnteilsFarben.folge(0)
    val gut = if (GlobusFarbe.niedrigGut(m)) wert < GlobusFarbe.mitte(m) else wert > GlobusFarbe.mitte(m)
    return if (gut) signal.up else signal.down
}

// Die fuenf hoechsten und fuenf niedrigsten Laender der aktiven Ebene als Balken, Tippen oeffnet das Laenderblatt. "Alle n"
// zeigt die ganze Liste; jede Zeile ist ein Eintrag der LazyColumn.
private fun LazyListScope.rangListe(
    ebene: String, m: GlobusModus, sortiert: List<GlobusEbenen.Rang>, karte: GlobusKarte, alle: Boolean, onAlle: (Boolean) -> Unit,
    text: (Double) -> String, onLand: (GlobusLand) -> Unit,
) {
    if (sortiert.isEmpty()) return
    val (oben, unten) = GlobusEbenen.rangliste(sortiert, RANG_KURZ)
    val gruppen: List<Pair<String?, List<GlobusEbenen.Rang>>> =
        if (alle) listOf(null to sortiert) else listOf(S.globusHoechste to oben, S.globusNiedrigste to unten)
    val groesste = sortiert.maxOf { abs(it.wert) }.takeIf { it > 0.0 } ?: 1.0
    item(key = "globus-rang", contentType = "rang-kopf") {
        Text(S.globusRangliste(ebene), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp))
    }
    gruppen.forEachIndexed { gi, (titel, liste) ->
        if (liste.isEmpty()) return@forEachIndexed
        if (titel != null) item(key = "globus-rang-t$gi", contentType = "rang-titel") {
            Text(titel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp))
        }
        items(liste.size, key = { "globus-rang-$gi-" + liste[it].code }, contentType = { "rang-zeile" }) { i ->
            val r = liste[i]
            val l = karte.nachA3(r.code)
            if (l != null) Column {
                if (i > 0) Trennlinie()
                ShareBar(l.name(I18n.locale.language == "de"), text(r.wert), abs(r.wert) / groesste, rangFarbe(m, r.wert)) { onLand(l) }
            }
        }
    }
    if (sortiert.size > 2 * RANG_KURZ) item(key = "globus-rang-mehr", contentType = "rang-mehr") {
        ListenPfeil(alle, label = if (alle) S.less else S.bbAlle(sortiert.size)) { onAlle(!alle) }
    }
}

private fun landName(k: GlobusKarte, code: String) = k.nachA3(code)?.name(I18n.locale.language == "de") ?: code

// Blatt zu einem Land (0.70): Kopf mit Kennzahlen, dann Land, Wirtschaft, Struktur, Markt, Trend, Unternehmen, Handel,
// Gesellschaft, Risiken und Meldungen. Seltenes ist aufklappbar, die Quellen stehen gesammelt am Ende.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LaenderBlatt(
    state: AppState, land: GlobusLand, staende: Map<String, de.troetenrepublik.app.market.IndexStand.Wert>, imf: Imf.Datensatz?, jahr: Int, scroll: LazyListState, sofort: Boolean,
    onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LaenderBlattInhalt(state, land, staende, imf, jahr, scroll, onOpen)
    }
}

// Inhalt des Laenderblatts ohne Fenster; auch als Bild unter der Seite, die aus dem Blatt geoeffnet wurde (BlattBild).
@Composable
internal fun LaenderBlattInhalt(
    state: AppState, land: GlobusLand, staende: Map<String, de.troetenrepublik.app.market.IndexStand.Wert>, imf: Imf.Datensatz?, jahr: Int, scroll: LazyListState,
    onOpen: (String) -> Unit,
) {
    val signal = LocalSignal.current
    val z = remember(land, imf) { imf?.let { Imf.land(it, land.a3, jahr) } }
    val index = GlobusIndizes.NACH_LAND[land.a2] ?: GlobusIndizes.DIREKT[land.a2]
    val stand = staende[land.a2]
    val geladen = rememberLaenderGeladen(state, land)
    var indexInfo by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    val sammler = remember(land) { androidx.compose.runtime.mutableStateListOf<QuelleLink>() }
    androidx.compose.runtime.CompositionLocalProvider(LocalQuellenSammler provides sammler) {
        RollstandHalten(scroll)
        // [R25] Jeder Abschnitt ist ein eigener Eintrag mit key und contentType; gerollt wird ueber den LazyListState
        LazyColumn(Modifier.fillMaxWidth(), state = scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "kopf", contentType = "kopf") {
                Column(Modifier.fillMaxWidth()) {
                    Text(land.name(I18n.locale.language == "de"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    IndexZeile(state, land.a2, index, stand) { indexInfo = it }
                }
            }
            // [Globus Gesamtbild 0.73] Punkte je Land mit den fuenf Bereichen
            item(key = "gesamt", contentType = "gesamt") { Column(Modifier.fillMaxWidth()) { LaenderGesamt(state, land, imf, jahr) } }
            item(key = "land-kopf", contentType = "land-kopf") { Column(Modifier.fillMaxWidth()) { LaenderKopf(land, z, imf, jahr, geladen) } }
            item(key = "steckbrief", contentType = "steckbrief") { Column(Modifier.fillMaxWidth()) { LaenderSteckbrief(state, land, geladen) } }
            item(key = "wirtschaft", contentType = "wirtschaft") { Column(Modifier.fillMaxWidth()) { LaenderWirtschaft(land, z, imf, jahr) } }
            item(key = "schulden", contentType = "schulden") { Column(Modifier.fillMaxWidth()) { LaenderSchulden(land, imf) } } // [0.78] Schuldentragfaehigkeit
            item(key = "waehrung", contentType = "waehrung") { Column(Modifier.fillMaxWidth()) { LaenderWaehrung(state, land, imf, jahr) } } // [Waehrung und Kaufkraft 0.78]
            item(key = "hauspreise", contentType = "hauspreise") { Column(Modifier.fillMaxWidth()) { LaenderHauspreise(land) } } // [Laender Historie 0.78]
            item(key = "historie", contentType = "historie") { Column(Modifier.fillMaxWidth()) { LaenderHistorie(land) } }
            item(key = "langreihen", contentType = "langreihen") { Column(Modifier.fillMaxWidth()) { LaenderLangreihen(land) } } // [Lange Reihen 0.78]
            item(key = "konflikt-reihe", contentType = "konflikt-reihe") { Column(Modifier.fillMaxWidth()) { LaenderKonfliktReihe(land) } } // [Lange Lage] Konflikte seit 1946
            item(key = "gpr-land", contentType = "gpr-land") { Column(Modifier.fillMaxWidth()) { LaenderGprLand(land) } } // [Lange Lage] Geopolitisches Risiko seit 1900
            item(key = "struktur", contentType = "struktur") { Column(Modifier.fillMaxWidth()) { LaenderStruktur(land, jahr, geladen) } }
            item(key = "leitzins", contentType = "leitzins") { Column(Modifier.fillMaxWidth()) { LaenderLeitzins(state, land) } } // [Leitzins 0.78]
            item(key = "markt", contentType = "markt") { Column(Modifier.fillMaxWidth()) { LaenderMarktAbschnitt(land, jahr, onOpen, geladen) } }
            item(key = "trend", contentType = "trend") { Column(Modifier.fillMaxWidth()) { LaenderTrend(state, land, imf, jahr) } }
            item(key = "unternehmen", contentType = "unternehmen") { Column(Modifier.fillMaxWidth()) { LaenderUnternehmen(state, land, onOpen) } }
            item(key = "handel", contentType = "handel") { Column(Modifier.fillMaxWidth()) { LaenderHandel(land, geladen) } }
            item(key = "risiken", contentType = "risiken") { Column(Modifier.fillMaxWidth()) { LaenderRisiken(state, land) } }
            // [Laender Strukturen 0.70], mit den strukturellen Kennzahlen aus den Risiken
            item(key = "strukturen", contentType = "strukturen") { Column(Modifier.fillMaxWidth()) { LaenderStrukturen(state, land) } }
            if (land.a2.isNotEmpty()) item(key = "wirkung", contentType = "wirkung") {
                NavRow(S.wnTitel, S.abhWirkungUnter) { onOpen(WIRKUNG_PRAEFIX + "land:" + land.a2) }
            }
            // [Laenderblatt 0.70] Hier folgt der Abschnitt "Strukturelle Probleme"
            // Strom: Grosshandelspreis (ENTSO-E, mit Token) und fuer Deutschland Erzeugung und Verbrauch (SMARD)
            if (de.troetenrepublik.app.engine.StromMarkt.zone(land.a2) != null) item(key = "strom", contentType = "strom") { Column(Modifier.fillMaxWidth()) { LaenderStrom(state, land, onOpen) } }
            item(key = "mehr", contentType = "mehr") { Column(Modifier.fillMaxWidth()) { LaenderMehr(land, geladen) } }
            item(key = "quellen", contentType = "quellen") {
                Column(Modifier.fillMaxWidth()) {
                    QuellenAnhang(sammler.toList())
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    indexInfo?.let { (titel, text) -> MarktInfoBlatt(titel, text) { indexInfo = null } }
}

// Einklappbare Weltuebersicht: Weltwachstum mit Pfad, Rezessionen, Aufsteiger und Abschwaecher, Risiken.
@Composable
private fun WeltUebersicht(
    d: Imf.Datensatz, karte: GlobusKarte, jahr: Int, cli: Fruehindikator.Datensatz?, rez: WeltRezession.Daten?,
    fruehwarn: List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen>,
    monat: java.time.YearMonth, zuStimmung: () -> Unit, zuAusblick: (String) -> Unit, onLand: (GlobusLand) -> Unit,
) {
    var offen by rememberSaveable { mutableStateOf(true) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val welt = remember(cli, d, monat, jahr) { Konjunkturphase.welt(cli, d, monat, jahr) }
    val u = remember(d, jahr) { Imf.ueberblick(d, jahr) }
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.globusWelt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
    if (!offen) return
    // [Globus Gesten 0.70] Wachstum je Jahr, Treiber, Anteile und Aufsteiger in eigener Datei
    WeltAusblickKasten(d, karte, jahr, onLand)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (u.laender > 0) {
            // [0.73] Eigene Ueberschrift, Zeilen und Stichpunkte stehen gleich eingerueckt
            LbUnterkopf(S.wwRezTitel)
            LbZeile(S.lbRezLaender, S.lbAnteilVon(u.rezession, u.laender, Fmt.pct(u.rezession * 100.0 / u.laender, 0))) { info = S.rezTitelWachstum to S.rezInfoWachstum }
            u.rezessionAnteil?.let { LbZeile(S.lbRezBip, Fmt.pct(it * 100, 0)) { info = S.rezTitelWachstum to S.rezInfoWachstum } }
        }
        if (u.abschwaecher.isNotEmpty()) RangZeile(S.globusAbschwaecher, u.abschwaecher.map { rangWeg(landName(karte, it.code), it) }, signal.down)
        val risiken = ArrayList<String>()
        if (u.hoheInflationAnzahl > 0) risiken += S.globusHoheInflation(u.hoheInflationAnzahl, u.hoheInflation.joinToString(", ") { landName(karte, it.code) })
        if (u.schuldenSchwachAnzahl > 0) risiken += S.globusSchuldenSchwach(u.schuldenSchwachAnzahl, u.schuldenSchwach.joinToString(", ") { landName(karte, it.code) })
        if (risiken.isNotEmpty()) {
            LbUnterkopf(S.globusRisiken)
            for (r in risiken) LbStichpunkt(r)
        }
    }
    RezessionsZeichen(cli, rez, fruehwarn, welt, monat, zuAusblick) { info = it }
    NavRow(S.globusFruehwarn, "", zuStimmung)
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// [Globus Trend und Ebenen] Rezession Welt: vier Zahlen mit Quelle und Stand, jede mit Erklärung, ohne Gesamturteil.
// Zinskurve und Sahm-Regel zeigen Wert und Ampelfarbe der Fruehwarnzeichen (Stimmung), ohne sie den gemerkten FRED-Wert.
// Die Wahrscheinlichkeit des laufenden Monats fuehrt per Tipp zur Rezessionswahrscheinlichkeit in zwoelf Monaten im Ausblick.
@Composable
private fun RezessionsZeichen(
    cli: Fruehindikator.Datensatz?, rez: WeltRezession.Daten?, fruehwarn: List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen>,
    welt: Konjunkturphase.Welt, monat: java.time.YearMonth, zuAusblick: (String) -> Unit, onInfo: (Pair<String, String>) -> Unit,
) {
    val anteil = Fruehindikator.anteilAbschwung(cli, monat)
    val zins = remember(fruehwarn, rez) { WeltRezession.zinskurveAnzeige(fruehwarn, rez) }
    val sahm = remember(fruehwarn, rez) { WeltRezession.sahmAnzeige(fruehwarn, rez) }
    if (anteil.mitDaten == 0 && welt.mitPhase == 0 && (rez == null || rez.leer) && zins == null && sahm == null) return
    fun punkte(v: Double, vorzeichen: Boolean): String =
        (if (vorzeichen) (if (v >= 0) "+" else "−") else "") + Fmt.num(kotlin.math.abs(v), 2) + " " + S.fwPunkte
    fun tag(t: Long) = java.time.LocalDate.ofEpochDay(t).let { "%02d.%02d.".format(it.dayOfMonth, it.monthValue) }
    fun monatText(t: Long) = java.time.LocalDate.ofEpochDay(t).let { "%02d/%d".format(it.monthValue, it.year) }
    LbUnterkopf(S.rezTitel)
    if (anteil.mitDaten > 0) LbZeile(
        S.rezAbschwung, S.rezAbschwungWert(anteil.abschwung, anteil.mitDaten, Fmt.pct(anteil.abschwung * 100.0 / anteil.mitDaten, 0)),
    ) { onInfo(S.rezAbschwung to S.rezInfoAbschwung) }
    if (welt.mitPhase > 0) {
        LbZeile(
            S.rezPhaseWelt, S.rezPhaseWeltWert(welt.abschwung, welt.mitPhase, Fmt.pct(welt.abschwung * 100.0 / welt.mitPhase, 0), null),
        ) { onInfo(S.rezPhaseWelt to S.rezInfoPhaseWelt) }
        welt.bipAnteil?.let { LbZeile(S.lbRezBip, Fmt.pct(it * 100, 0)) { onInfo(S.rezPhaseWelt to S.rezInfoPhaseWelt) } }
    }
    zins?.let { LbZeile(S.rezZins, punkte(it.wert, true) + PUNKT + tag(it.tag), it.ampel?.let { a -> fwAmpelFarbe(a) }) { onInfo(S.rezZins to S.rezInfoZins) } }
    sahm?.let { LbZeile(S.rezSahm, punkte(it.wert, true) + PUNKT + monatText(it.tag), it.ampel?.let { a -> fwAmpelFarbe(a) }) { onInfo(S.rezSahm to S.fwErklaerung("sahm")) } }
    rez?.wahrscheinlichkeit?.let { LbZeile(S.rezWahrsch, Fmt.pct(it.wert, 1) + PUNKT + monatText(it.tag)) { zuAusblick("rezession") } }
}

// Wachstum im Jahr und im Folgejahr, etwa „Irak −20,0 %, danach +15,0 %“.
private fun rangWeg(land: String, r: Imf.Rang): String =
    if (r.von != null && r.bis != null) land + " " + Fmt.signedPct(r.von, 1) + ", " + r2("rang.danach") + " " + Fmt.signedPct(r.bis, 1)
    else land + " " + Fmt.signedPct(r.wert, 1)

// [0.71] Stichpunkte mit haengendem Einzug statt einem zusammenhaengenden Fliesstext
@Composable
private fun RangZeile(titel: String, eintraege: List<String>, farbe: Color) {
    Column(Modifier.padding(horizontal = 0.dp)) {
        Text(titel, style = MaterialTheme.typography.labelLarge, color = farbe, modifier = Modifier.padding(horizontal = 20.dp))
        for (e in eintraege) LbStichpunkt(e)
    }
}

// Kleine Linie des Weltwachstums ueber die Jahre, mit Anfangs- und Endjahr.
@Composable
private fun Pfadlinie(werte: List<Double>, jahr: Int, modifier: Modifier, farbe: Color) {
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = 20.dp)) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val lo = werte.min(); val hi = werte.max()
            val spanne = (hi - lo).coerceAtLeast(0.5)
            val pad = 4f
            fun pos(i: Int) = Offset(pad + (size.width - 2 * pad) * i / (werte.size - 1), pad + (size.height - 2 * pad) * (1f - ((werte[i] - lo) / spanne).toFloat()))
            val p = Path()
            werte.indices.forEach { i -> val o = pos(i); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            drawPath(p, farbe, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            werte.indices.forEach { i -> drawCircle(farbe, 2.5f * density, pos(i)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$jahr", style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
            Text("${jahr + werte.size - 1}", style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
        }
    }
}
