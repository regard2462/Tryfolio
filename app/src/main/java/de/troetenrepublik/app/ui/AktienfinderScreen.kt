package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.FINDER_DETAILS
import de.troetenrepublik.app.FINDER_REGIONEN
import de.troetenrepublik.app.FinderBilanzSpeicher
import de.troetenrepublik.app.FinderLauf
import de.troetenrepublik.app.RadarSpeicher
import de.troetenrepublik.app.finderAktienIm
import de.troetenrepublik.app.finderAlarmeLesen
import de.troetenrepublik.app.finderAlarmUmschalten
import de.troetenrepublik.app.finderAlarmePruefen
import de.troetenrepublik.app.finderOhneAntwortKuerzlich
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Kennzahl
import de.troetenrepublik.app.engine.FinderExport
import de.troetenrepublik.app.engine.FinderRueckblick
import de.troetenrepublik.app.engine.FinderVorlagen
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.Radar
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// Route des Aktienfinders (Kachel der Startseite); in Analytics steht er als Werkzeug.
const val AKTIENFINDER_ROUTE = "aktienfinder"

// Universen: alle Aktien, USA, die Laender und Regionen nach Land und die Indizes, deren
// Zusammensetzung der Katalog vollstaendig hat.
internal val UNIVERSEN = listOf(
    "ALLE", "USA", "EUROPA", "DEUTSCHLAND", "GROSSBRITANNIEN", "SCHWEIZ", "JAPAN", "KANADA", "ASIEN", "SCHWELLEN",
    "SP500", "NDX", "DJIA", "DAX", "MDAX", "SDAX", "TECDAX", "ESTX50", "FTSE100", "CAC40", "SMI",
    "AEX", "IBEX35", "FTSEMIB", "ATX", "OMXS30", "OMXC25", "OMXH25", "OBX", "NIKKEI225",
)

internal fun universumName(code: String): String =
    if (code in FINDER_REGIONEN) S.dfUniversumName(code) else Catalog.indexName(code)

// Wahl im Aktienfinder; bleibt erhalten, solange die App laeuft.
private object FinderWahl {
    var universum by mutableStateOf("ALLE")
    val gewichte = mutableStateMapOf<Bereich, Float>()
    var filter by mutableStateOf(Aktienfinder.Filter())
    var radar by mutableStateOf(false)
    // [0.77] Weg neben Liste und Radar: 1 Branchen, 2 Vorschlagsdepot, seit 0.78 3 Kursmuster
    var weg by mutableIntStateOf(0)
    var nurGespraech by mutableStateOf(false)
    var sortierung by mutableStateOf<Aktienfinder.Sortierung>(Aktienfinder.Sortierung.Gesamt)
}

// Aus der Liste „Glocken“ in den Aktienfinder: Markt, Vorlage und Grenzen des Alarms, Ansicht Liste.
internal fun finderAlarmOeffnen(a: de.troetenrepublik.app.FinderAlarm) {
    val v = FinderVorlagen.vorlage(a.vorlage) ?: return
    FinderWahl.universum = a.universum
    FinderWahl.filter = Aktienfinder.Filter(grenzen = de.troetenrepublik.app.finderAlarmGrenzen(v, a), vorlage = v.id, land = if (v.nurUs) "US" else null)
    FinderWahl.radar = false
    FinderWahl.weg = 0
}

// Von Kachel oder altem Radar-Weg in den Aktienfinder mit Radar-Ansicht (5.93).
internal fun finderRadarOeffnen() { FinderWahl.radar = true }

// Laedt den zuletzt gewaehlten Markt neu, aus der Sammelstelle „Selbst anstossen“; startet wie „Aktien einordnen“.
internal fun finderAnstossen(state: AppState) {
    val katalog = Catalog.loaded() ?: return
    val u = FinderLauf.zustand.value.universum ?: FinderWahl.universum
    FinderWahl.universum = u
    val gewichte = Bereich.entries.associateWith { (FinderWahl.gewichte[it] ?: it.gewicht.toFloat()).toDouble() }
    FinderLauf.starten(state, u, finderAktienIm(u, katalog), gewichte)
}

// Aktienfinder als eigene Seite.
@Composable
fun AktienfinderScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.afTitel, null, onBack)
        AktienfinderInhalt(state, onOpen)
        Spacer(Modifier.height(40.dp))
    }
}

// Inhalt des Aktienfinders, auch als Werkzeug in Analytics: Markt waehlen, einordnen, filtern und sortieren, je Aktie
// Punkte und Gruende; Gewichte, Erklaerung und Nachbilden darunter.
@Composable
internal fun AktienfinderInhalt(state: AppState, onOpen: (String) -> Unit) {
    katalogBereit()
    val w = FinderWahl
    val scope = rememberCoroutineScope()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Zustand des Hintergrund-Auftrags: nur was zum gewaehlten Markt gehoert, wird angezeigt
    val lauf by FinderLauf.zustand.collectAsState()
    val meins = lauf.universum == w.universum
    val daten = if (meins) lauf.daten else emptyList()
    val stand = if (meins) lauf.stand else 0L
    val laedt = if (meins) lauf.laedt else null
    val details = if (meins) lauf.details else null
    val bilanzAktiv = meins && lauf.bilanzAktiv
    // [0.75] Der letzte Lauf bekam keine Antwort: Hinweis mit Knopf, der Stand bleibt der vorherige
    val ohneAntwort = meins && lauf.ohneAntwort
    val bilanzNeu = lauf.bilanzNeu
    var offen by remember { mutableStateOf<String?>(null) }
    var zeigen by remember { mutableIntStateOf(20) }
    var gewichteOffen by remember { mutableStateOf(false) }
    var nachbilden by remember { mutableStateOf(false) }
    // Reihenfolge halten und neu ordnen, solange Bilanzdaten eintreffen
    var halten by remember { mutableStateOf(false) }
    var neuOrdnen by remember { mutableIntStateOf(0) }
    // Stand des Radars (5.93): kennzeichnet Werte im Gespraech und fuellt die Radar-Ansicht
    var radarStand by remember { mutableStateOf(RadarSpeicher.stand) }
    val gespraech = remember(radarStand) { radarStand?.treffer?.filter { Radar.imGespraech(it) }?.map { it.symbol }?.toSet().orEmpty() }
    val gewichte = Bereich.entries.associateWith { (w.gewichte[it] ?: it.gewicht.toFloat()).toDouble() }
    // Ergebnis mit den Daten, aus denen es gerechnet ist; neu gerechnet wird im Hintergrund
    val gerechnet by produceState(emptyList<Aktienfinder.Daten>() to emptyList<Aktienfinder.Ergebnis>(), daten, gewichte) {
        val d = daten
        value = d to withContext(Dispatchers.Default) { Aktienfinder.bewerten(d, gewichte) }
    }
    val ergebnisse = gerechnet.second
    val aktuell = gerechnet.first === daten
    // [0.78] Mit dem Pruefergebnis je Wert: Regeln ueber den ganzen Markt lassen sich spaeter nicht am einzelnen Wert nachpruefen
    val geprueft = remember(ergebnisse, w.filter, w.sortierung) {
        Aktienfinder.auswahlGeprueft(ergebnisse, w.filter, w.sortierung, regionVon = { Region.ofCountry(it) })
    }
    val sortiert = remember(geprueft, w.nurGespraech, gespraech) {
        geprueft.liste.let { l -> if (w.nurGespraech) l.filter { it.symbol in gespraech } else l }
    }
    // Solange Bilanzdaten eintreffen, die Reihenfolge halten; am Ende freigeben (ist eine Aktie aufgeklappt, erst auf Tipp).
    LaunchedEffect(bilanzAktiv) { if (bilanzAktiv) halten = true else if (offen == null) halten = false }
    // Solange Bilanzdaten eintreffen, bleibt die Reihenfolge stehen; neu geordnet wird bei jeder Wahl des Nutzers,
    // auf Tipp und am Ende des Durchlaufs (ist dann eine Aktie aufgeklappt, erst auf Tipp).
    val ordnung = remember(w.filter, w.sortierung, gewichte, neuOrdnen, w.universum, w.nurGespraech) { arrayOfNulls<List<String>>(1) }
    val auswahl = remember(sortiert, halten, ordnung, aktuell) {
        val alt = ordnung[0]
        val l = if (halten && alt != null) Aktienfinder.ordnungHalten(sortiert, alt) else sortiert
        // Festgehalten wird erst eine Reihenfolge aus den neuesten Daten, sonst stuende eine veraltete fest
        ordnung[0] = if (halten && (alt != null || aktuell)) l.map { it.symbol } else null
        l
    }

    // [0.76] Treffer als CSV: nur Werte, die alle eingeschalteten Grenzen sicher erfuellen
    var csvMeldung by remember { mutableStateOf<String?>(null) }
    val csvSpeichern = de.troetenrepublik.app.plattform.rememberSpeichern(
        inhalt = {
            val t = FinderExport.treffer(auswahl, geprueft.pruefung)
            FinderExport.csv(
                t, FinderExport.felder(w.filter.grenzen), S.afCsvSpalten,
                deutsch = de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE, branche = { b -> if (b.isBlank()) b else S.industryName(b) },
            ) { S.afFeld(it.name) }
        },
        mime = "text/csv",
    ) { text, ok -> csvMeldung = if (ok) S.afCsvFertig(text.lineSequence().count { it.isNotBlank() } - 1) else S.exportFailed }

    // Vorlagen (5.94): Werte, die die Grenzen des Sammelabrufs bestehen, aber noch auf Profil oder Tageskurse warten.
    // Erst die Tageskurse (20 Werte je Abfrage), das Profil nur fuer Werte, die danach noch passen. Drei Runden laufen
    // von selbst, weitere auf Tipp.
    val sortFeld = (w.sortierung as? Aktienfinder.Sortierung.NachFeld)?.feld
    val stufen = remember(w.filter.grenzen, sortFeld) { FinderVorlagen.stufen(w.filter.grenzen, sortFeld) }
    // Werte, die eben keine Antwort bekamen, warten eine Stunde, sonst stuenden sie in jeder Runde wieder vorn. Zaehler und
    // Abruf nutzen dieselbe Bedingung; der Minutentakt und das Ende eines Laufs rechnen sie neu.
    val takt by produceState(0L, stufen.isEmpty()) {
        if (stufen.isNotEmpty()) while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000L) }
    }
    fun kerzenOffen(e: Aktienfinder.Ergebnis, jetzt: Long) =
        FinderVorlagen.Stufe.KERZEN in stufen && !e.daten.mitKerzen && !finderOhneAntwortKuerzlich('k', e.symbol, jetzt)
    fun profilOffen(e: Aktienfinder.Ergebnis, jetzt: Long) =
        FinderVorlagen.Stufe.EINZEL in stufen && !e.daten.mitDetails &&
            (e.daten.mitKerzen || FinderVorlagen.Stufe.KERZEN !in stufen) && !finderOhneAntwortKuerzlich('d', e.symbol, jetzt)
    val ungeprueft = remember(auswahl, stufen, details == null, laedt == null, takt) {
        val jetzt = System.currentTimeMillis()
        if (stufen.isEmpty()) emptyList() else auswahl.filter { kerzenOffen(it, jetzt) || profilOffen(it, jetzt) }
    }
    fun vorlagePruefen() {
        val jetzt = System.currentTimeMillis()
        val kerzen = auswahl.filter { kerzenOffen(it, jetzt) }.take(VORLAGE_KERZEN).map { it.symbol }
        val einzeln = auswahl.filter { profilOffen(it, jetzt) }.take(VORLAGE_EINZELN).map { it.symbol }
        FinderLauf.vorlageStarten(state, einzeln, kerzen)
    }
    var runden by remember(w.filter.vorlage, w.universum, sortFeld) { mutableIntStateOf(0) }
    // laedt als Key: endet ein Lauf ohne neue ungepruefte Treffer, startet die naechste Runde trotzdem.
    // [0.72] Runden auch ohne Vorlage, wenn nach einem Feld der spaeteren Stufen sortiert wird (Waage, RSI)
    LaunchedEffect(ungeprueft.size, details == null, laedt == null, aktuell, w.filter.vorlage, sortFeld) {
        if (aktuell && (w.filter.vorlage != null || sortFeld != null) && details == null && laedt == null && ungeprueft.isNotEmpty() && runden < 3) { runden++; vorlagePruefen() }
    }

    // Fehlende Jahresabschluesse des Markts im Hintergrund-Auftrag nachholen
    fun bilanzLaden() = FinderLauf.bilanzStarten(state, w.universum, gewichte)

    // Beim Oeffnen mit gespeicherten Daten: Fehlendes nachholen; der Auftrag prueft selbst, ob er laufen darf
    LaunchedEffect(Unit) { if (daten.isNotEmpty()) bilanzLaden() }

    // Markt neu laden im Hintergrund-Auftrag, der beim Verlassen des Bildschirms weiterlaeuft
    fun laden() {
        val katalog = Catalog.loaded() ?: return
        if (laedt != null) return
        offen = null
        FinderLauf.starten(state, w.universum, finderAktienIm(w.universum, katalog), gewichte)
    }

    // Einzelabrufe fuer die sichtbaren besten Werte
    fun detailsLaden(symbole: List<String>) {
        if (symbole.isEmpty() || details != null) return
        FinderLauf.detailsStarten(state, symbole)
    }

    // Markt wechseln; war der alte geladen, laedt der neue gleich mit
    fun wechseln(u: String) {
        if (u == w.universum) return
        val geladen = daten.isNotEmpty()
        halten = false
        offen = null
        w.universum = u
        zeigen = 20
        // Hat der Hintergrund-Auftrag diesen Markt zuletzt geladen, dessen Daten uebernehmen
        FinderLauf.uebernehmen(u)
        // daten ergibt sich aus FinderLauf; hat der neue Markt keine, laedt er gleich mit, wenn der alte geladen war
        if (geladen && FinderLauf.zustand.value.universum != u) laden() else bilanzLaden()
    }

    // Maerkte als Reiter: Wischen ueber die Liste blaettert zum Nachbarmarkt, die Pille wandert mit.
    // Gewechselt wird erst, wenn die Seite steht, damit beim Durchwischen nichts unnoetig laedt.
    val pager = rememberPagerState(initialPage = UNIVERSEN.indexOf(w.universum).coerceAtLeast(0)) { UNIVERSEN.size }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { wechseln(UNIVERSEN[it]) } }
    Text(S.rbMarkt, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp))
    val wm = rememberReiterWidgetMenue(state) { i -> UNIVERSEN.getOrNull(i)?.let { de.troetenrepublik.app.data.WidgetVorgabe.aktienfinder(it) } }
    MitlaufendeReiter(UNIVERSEN.size, pager, titel = { universumName(UNIVERSEN[it]) }, langDruecken = wm.langDruecken, menue = { i -> wm.menue(i) }) { i -> scope.launch { pager.animateScrollToPage(i) } }

    // Nachbarmaerkte zeigen beim Wischen nur, was nach dem Wechsel kommt: Ladebalken oder den Start
    val vorher = daten.isNotEmpty()
    HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().mausWischen(pager), key = { UNIVERSEN[it] }) { seite ->
    Column(Modifier.fillMaxWidth()) {
    if (UNIVERSEN[seite] != w.universum) {
        if (vorher) {
            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp))
            Text(S.afLaedt, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else {
            Spacer(Modifier.height(8.dp))
            ActionLineWide(S.afStart) { scope.launch { pager.animateScrollToPage(seite) } }
        }
        return@Column
    }

    val f = laedt
    if (f != null) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp))
        Text(S.afLaedt, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    } else if (ohneAntwort) {
        EmptyHint(S.afOhneAntwort, S.erneutVersuchen to { laden() })
    } else if (daten.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        ActionLineWide(S.afStart) { laden() }
    }

    if (daten.isNotEmpty() && f == null) {
        // Ansicht wechseln (5.93): Liste mit Filtern oder Radar-Ansicht des Markts
        Row(Modifier.horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.aktienfinder(w.universum)) { Chip(S.afAnsichtListe, active = !w.radar && w.weg == 0) { w.radar = false; w.weg = 0 } }
            WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.aktienfinder(w.universum)) { Chip(S.afAnsichtRadar, active = w.radar) { w.radar = true; w.weg = 0 } }
            Chip(S.afAnsichtBranchen, active = !w.radar && w.weg == 1) { w.radar = false; w.weg = 1 }
            Chip(S.afAnsichtDepot, active = !w.radar && w.weg == 2) { w.radar = false; w.weg = 2 }
            // [0.78] Scan nach Kursmustern auf Tagesschlusskursen
            Chip(S.km("ansicht"), active = !w.radar && w.weg == 3) { w.radar = false; w.weg = 3 }
        }
        if (w.radar) FinderRadarAnsicht(
            state, ergebnisse, if (w.universum == "ALLE") null else ergebnisse.map { it.symbol }.toSet(), radarStand, { radarStand = it }, onOpen,
        )
        else if (w.weg == 3) FinderKursmusterAnsicht(state, w.universum, daten, onOpen)
        else if (w.weg == 1 || w.weg == 2) {
            BilanzFortschritt(daten, bilanzNeu, bilanzAktiv) { bilanzLaden() }
            if (w.weg == 1) FinderBranchenAnsicht(state, ergebnisse, onOpen) else FinderVorschlagAnsicht(state, ergebnisse, onOpen)
        }
        else {
        Row(
            Modifier.horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val vorlagen = listOf(null) + FinderVorlagen.VORLAGEN
            MenuePille(
                w.filter.vorlage?.let { S.afVorlage(it) } ?: S.afVorlageKeine, w.filter.vorlage != null,
                vorlagen.map { v -> v?.let { S.afVorlage(it.id) } ?: S.afVorlageKeine }, vorlagen.indexOfFirst { it?.id == w.filter.vorlage },
            ) { i ->
                val v = vorlagen[i]
                w.filter = if (v == null) Aktienfinder.Filter() else Aktienfinder.Filter(grenzen = v.grenzen, vorlage = v.id, land = if (v.nurUs) "US" else null)
                w.sortierung = v?.rang?.takeIf { it.isNotEmpty() }?.let { Aktienfinder.Sortierung.Rang(it) }
                    ?: v?.sortierung?.let { Aktienfinder.Sortierung.NachFeld(it, v.aufsteigend) } ?: Aktienfinder.Sortierung.Gesamt
                zeigen = 20
            }
            // [0.72] Waage und Indikatoren als eigene Sortierungen; sie ziehen Profil- und Tageskursabrufe nach sich
            val feldSort = listOf(FinderVorlagen.Feld.WAAGE, FinderVorlagen.Feld.RSI)
            val sortierungen = listOf<Aktienfinder.Sortierung>(Aktienfinder.Sortierung.Gesamt) + Bereich.entries.map { Aktienfinder.Sortierung.Teil(it) } +
                listOf(Aktienfinder.Sortierung.Dividende, Aktienfinder.Sortierung.Boersenwert) +
                feldSort.map { Aktienfinder.Sortierung.NachFeld(it) } +
                listOfNotNull((w.sortierung as? Aktienfinder.Sortierung.NachFeld)?.takeIf { it.feld !in feldSort }, w.sortierung as? Aktienfinder.Sortierung.Rang)
            MenuePille(S.afSortierung(sortName(w.sortierung)), true, sortierungen.map { sortName(it) }, sortierungen.indexOf(w.sortierung)) {
                w.sortierung = sortierungen[it]
            }
            val einordnungen = listOf(null) + Aktienfinder.Einordnung.entries.filter { it != Aktienfinder.Einordnung.UNKLAR }
            MenuePille(
                w.filter.einordnung?.let { S.afEinordnung(it.name) } ?: S.afJedeBewertung, w.filter.einordnung != null,
                einordnungen.map { e -> e?.let { S.afEinordnung(it.name) } ?: S.afJedeBewertung }, einordnungen.indexOf(w.filter.einordnung),
            ) { w.filter = w.filter.copy(einordnung = einordnungen[it]) }
            FilterPill(S.afNurSolide, active = w.filter.solideBilanz) { w.filter = w.filter.copy(solideBilanz = !w.filter.solideBilanz) }
            // Pille auch ohne Stand zeigen, wenn der Filter an ist, sonst liesse er sich nicht mehr ausschalten
            if (radarStand != null || w.nurGespraech) FilterPill(S.afImGespraech, active = w.nurGespraech) { w.nurGespraech = !w.nurGespraech }
            val branchen = listOf(null) + daten.map { it.branche }.filter { it.isNotBlank() }.distinct().sortedBy { S.industryName(it) }
            MenuePille(
                w.filter.branche?.let { S.industryName(it) } ?: S.afAlleBranchen, w.filter.branche != null,
                branchen.map { b -> b?.let { S.industryName(it) } ?: S.afAlleBranchen }, branchen.indexOf(w.filter.branche),
            ) { w.filter = w.filter.copy(branche = branchen[it]) }
            val regionen = listOf(null) + daten.map { Region.ofCountry(it.land) }.distinct().sortedBy { S.regionName(it) }
            if (regionen.size > 2) MenuePille(
                w.filter.region?.let { S.regionName(it) } ?: S.afAlleRegionen, w.filter.region != null,
                regionen.map { r -> r?.let { S.regionName(it) } ?: S.afAlleRegionen }, regionen.indexOf(w.filter.region),
            ) { w.filter = w.filter.copy(region = regionen[it], land = null) }
            val laender = listOf(null) + daten.filter { w.filter.region == null || Region.ofCountry(it.land) == w.filter.region }
                .map { it.land }.filter { it.isNotBlank() }.distinct().sortedBy { S.countryLabel(it) }
            if (laender.size > 2) MenuePille(
                w.filter.land?.let { S.countryLabel(it) } ?: S.afAlleLaender, w.filter.land != null,
                laender.map { l -> l?.let { S.countryLabel(it) } ?: S.afAlleLaender }, laender.indexOf(w.filter.land),
            ) { w.filter = w.filter.copy(land = laender[it]) }
            val groessen = listOf(null) + Aktienfinder.Groesse.entries
            MenuePille(
                w.filter.groesse?.let { S.afGroesse(it.name) } ?: S.afAlleGroessen, w.filter.groesse != null,
                groessen.map { g -> g?.let { S.afGroesse(it.name) } ?: S.afAlleGroessen }, groessen.indexOf(w.filter.groesse),
            ) { w.filter = w.filter.copy(groesse = groessen[it]) }
            val dividenden = listOf(null, 1.0, 2.0, 3.0, 4.0, 5.0)
            MenuePille(
                w.filter.dividende?.let { S.afDividendeAb(Fmt.pct(it, 0)) } ?: S.afJedeDividende, w.filter.dividende != null,
                dividenden.map { d -> d?.let { S.afDividendeAb(Fmt.pct(it, 0)) } ?: S.afJedeDividende }, dividenden.indexOf(w.filter.dividende),
            ) { w.filter = w.filter.copy(dividende = dividenden[it]) }
            val mindest = listOf(0, 50, 60, 70, 80)
            MenuePille(
                S.afMindest(w.filter.mindest.roundToInt()), w.filter.mindest > 0, mindest.map { S.afMindest(it) }, mindest.indexOf(w.filter.mindest.roundToInt()),
            ) { w.filter = w.filter.copy(mindest = mindest[it].toDouble()) }
        }
        FinderVorlagen.vorlage(w.filter.vorlage)?.let { v ->
            // Alarm auf neue Treffer dieser Vorlage im gewaehlten Markt (5.94)
            var alarmAn by remember(v.id, w.universum) { mutableStateOf(false) }
            LaunchedEffect(v.id, w.universum) { alarmAn = state.finderAlarmeLesen().any { it.universum == w.universum && it.vorlage == v.id } }
            VorlagenKasten(v, w.filter.grenzen, ungeprueft.size, details == null && laedt == null, onGrenze = { g ->
                val an = g in w.filter.grenzen
                w.filter = w.filter.copy(grenzen = if (an) w.filter.grenzen - g else v.grenzen.filter { it == g || it in w.filter.grenzen })
            }, onWeiter = { vorlagePruefen() }, alarm = alarmAn, onAlarm = {
                val nummern = w.filter.grenzen.map { v.grenzen.indexOf(it) }.filter { it >= 0 }
                scope.launch {
                    alarmAn = state.finderAlarmUmschalten(w.universum, v.id, nummern)
                    // Den heutigen Stand gleich merken, damit nur spaeter neue Treffer gemeldet werden
                    if (alarmAn) state.finderAlarmePruefen(w.universum, daten, mitAbruf = false)
                }
            })
        }
        PunktText(
            S.afTreffer(auswahl.size, ergebnisse.size) + (if (stand > 0) " · " + S.afStand(Fmt.relative(stand)) else ""),
            style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp),
        )
        // Dezenter Stand der Bilanzdaten, solange welche fehlen oder die Reihenfolge gehalten wird
        val (bilanzDa, bilanzAlle) = remember(daten, bilanzNeu) { FinderBilanzSpeicher.stand(daten) }
        if (bilanzAlle > 0 && (bilanzAktiv || halten || bilanzDa < bilanzAlle)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.afBilanzStand(bilanzDa, bilanzAlle), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
                if (!bilanzAktiv && bilanzDa < bilanzAlle) Text(
                    S.afBilanzWeiter, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link,
                    modifier = Modifier.textKlick(12.dp, 0.dp) { bilanzLaden() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
                if (halten) Text(
                    S.afNeuOrdnen, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link,
                    modifier = Modifier.textKlick(12.dp, 0.dp) { neuOrdnen++; if (!bilanzAktiv) halten = false }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            if (bilanzAktiv) LinearProgressIndicator(
                progress = { if (bilanzAlle > 0) bilanzDa.toFloat() / bilanzAlle else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 4.dp).height(2.dp),
            )
        }

        if (auswahl.isEmpty() && ergebnisse.isNotEmpty()) EmptyHint(S.afKeine)
        // Das Land nur, wenn der Markt mehrere hat
        val mitLand = remember(daten) { daten.map { it.land }.distinct().size > 1 }
        auswahl.take(zeigen).forEach { e ->
            FinderZeile(e, gewichte, offen == e.symbol, mitLand, e.symbol in gespraech, bilanzAktiv, onToggle = { offen = if (offen == e.symbol) null else e.symbol }, onOpen = onOpen)
        }
        if (auswahl.size > zeigen) ActionLineWide(S.afMehrZeigen(minOf(20, auswahl.size - zeigen))) { zeigen += 20 }
        else if (zeigen > 20) ActionLineWide(S.afWeniger) { zeigen = 20 }
        if (auswahl.isNotEmpty()) {
            ActionLineWide(S.afCsv) { csvMeldung = null; csvSpeichern(S.afCsvDatei(w.filter.vorlage?.lowercase() ?: "aktienfinder")) }
            csvMeldung?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
        }

        // Einzelabrufe fuer die sichtbaren besten Werte, die noch keine haben
        val d = details
        if (d != null) {
            LinearProgressIndicator(progress = { d }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp))
            Text(S.afDetailsLaedt, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else {
            val ohne = auswahl.take(FINDER_DETAILS).filter { !it.daten.mitDetails }.map { it.symbol }
            if (ohne.isNotEmpty()) ActionLineWide(S.afDetailsMehr(minOf(FINDER_DETAILS, auswahl.size))) { detailsLaden(ohne) }
        }

        val n = minOf(10, auswahl.size)
        if (n > 0) ActionLineWide(S.afNachbilden(n)) { nachbilden = true }
        if (nachbilden) NachbildenBlatt(
            state = state, titel = S.afTitel, mitAnzahl = false, notiz = S.afHinweis,
            gewichte = { _ -> auswahl.take(n).map { it.symbol to 1.0 / n } }, onDismiss = { nachbilden = false },
        )
        }
    }
    }
    }

    // Gewichte, aufklappbar
    Row(
        Modifier.fillMaxWidth().clickable { gewichteOffen = !gewichteOffen }.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.afGewichte, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        // Zusammengeklappt zeigt ein Balken die Verteilung der Gewichte statt unbenannter Zahlen
        val summeGewicht = Bereich.entries.sumOf { gewichte.getValue(it) }
        if (summeGewicht > 0) Row(Modifier.padding(end = 10.dp).width(72.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Bereich.entries.forEachIndexed { i, b ->
                val g = gewichte.getValue(b)
                if (g > 0) Box(Modifier.weight(g.toFloat()).fillMaxHeight().background(AnteilsFarben.folge(i)))
            }
        }
        AufklappPfeil(gewichteOffen)
    }
    if (gewichteOffen) {
        Text(S.afGewichteHinweis, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
        Bereich.entries.forEach { b ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.afBereich(b.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Fmt.num(gewichte.getValue(b), 0), style = MaterialTheme.typography.bodyMedium, color = grau)
                }
                Slider(value = (w.gewichte[b] ?: b.gewicht.toFloat()), onValueChange = { w.gewichte[b] = (it / 5f).roundToInt() * 5f }, valueRange = 0f..50f, steps = 9)
            }
        }
        if (w.gewichte.isNotEmpty()) ActionLineWide(S.afStandard) { w.gewichte.clear() }
    }

    // Methodik, fruehere Ergebnisse und langes Halten eingeklappt (0.68)
    ErklaerungAufklappen(S.afErklaerung, Modifier.padding(top = 8.dp), titel = S.afSoRechnet)
    // Rueckblick-Test: Kernzahlen aus der mitgelieferten Zusammenfassung
    // Zweites Universum (Nasdaq-100), sobald seine Zusammenfassung beiliegt
    val ndx100 = FINDER_RUECKBLICK_NDX100
    var universum by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    if (ndx100 != null) FinderUniversumWahl(universum) { universum = it }
    if (ndx100 != null && universum == UNIVERSUM_NDX100) FinderUniversumBlock(ndx100)
    else FINDER_RUECKBLICK?.let { r ->
        val h = r.horizont(12) ?: return@let
        // Gemessen mit den Gewichten der Datei; weichen sie von den heutigen ab, steht das an der Zeile
        val alt = if (r.passtZuGewichten(FinderRueckblick.aktuelleGewichte())) null else S.afRueckblickAlt
        // Abdeckung der damaligen Mitglieder: Schnitt, erstes und letztes Jahr (nur Dateien mit Mitgliederliste)
        val abdeckung = r.abdeckungSchnitt()?.let { a ->
            fun p(x: Double) = Fmt.pct(x * 100.0, 0).replace(' ', ' ')
            val jahre = r.abdeckungJeJahr().let { l -> listOfNotNull(l.firstOrNull(), l.lastOrNull()?.takeIf { l.size > 1 }) }
            S.afRueckblickAbdeckung(p(a), jahre.map { (j, x) -> j.toString() to p(x) }, Math.round(a * 100.0) >= 100)
        }
        ErklaerungAufklappen(
            S.afRueckblickText(r.von.take(4), r.bis.take(4), r.werte, Fmt.signedPct(h.top20 * 100.0, 1), Fmt.signedPct(h.msci * 100.0, 1), Fmt.signedPct(h.spx * 100.0, 1), Fmt.pct(h.treffer * 100.0, 0)) +
                "\n\n" + r.ics.filter { it.bereich == Bereich.BEWERTUNG.name }.let { bew ->
                    val ab = bew.minOfOrNull { it.ab }
                    S.afRueckblickGrenzen(ab?.take(4), ab != null && ab.take(7) <= r.von.take(7), h.ic, h.icSe, bew.firstOrNull { it.monate == 12 }?.let { it.ic to it.se }, abdeckung)
                },
            titel = S.afRueckblickTitel, zusatz = alt,
        )
        FinderZufallAbschnitt(r.zufall)
        // Kaufen und halten (5.93): erster Stichtag bis heute und die langen Horizonte, nur wenn die Datei sie hat
        if (r.halten.isNotEmpty() || r.haltenHeute != null) {
            val zeilen = listOfNotNull(
                r.haltenHeute?.let {
                    S.afHaltenSeit(it.von.take(4), Fmt.num(it.jahre, 0), Fmt.signedPct(it.top20 * 100.0, 0), Fmt.signedPct(it.msci * 100.0, 0), Fmt.signedPct(it.spx * 100.0, 0))
                },
            ) + r.halten.sortedBy { it.monate }.map {
                S.afHaltenZeile((it.monate / 12).toString(), Fmt.signedPct(it.top20 * 100.0, 0), Fmt.signedPct(it.msci * 100.0, 0), Fmt.signedPct(it.spx * 100.0, 0))
            } + listOf("", S.afHaltenHinweis(Fmt.pct(r.fehlend * 100.0, 0)))
            ErklaerungAufklappen(zeilen.joinToString("\n"), titel = S.afHaltenTitel, zusatz = alt)
        }
    }
    ToolNote(S.afHinweis)
    QuellenAnhang(
        listOf(QuelleLink("BaFin", "https://www.bafin.de"), QuelleLink("ApeWisdom", "https://apewisdom.io")) +
            // Mitglieder des S&P 500 im Rueckblick (MIT, Urheber nennen)
            listOfNotNull(
                FINDER_RUECKBLICK?.takeIf { it.abdeckung.isNotEmpty() }?.let { QuelleLink("Farrell J. Aultman, sp500 (MIT)", "https://github.com/fja05680/sp500") },
                ndx100?.let { QuelleLink("Jeff McCarrell, n100tickers (MIT)", NDX100_QUELLE) },
            ),
    )
}

// [0.77] Stand der Bilanzdaten in den Wegen Branchen und Vorschlagsdepot, solange welche fehlen
@Composable
private fun BilanzFortschritt(daten: List<Aktienfinder.Daten>, bilanzNeu: Int, bilanzAktiv: Boolean, onWeiter: () -> Unit) {
    val (da, alle) = remember(daten, bilanzNeu) { FinderBilanzSpeicher.stand(daten) }
    if (alle <= 0 || (!bilanzAktiv && da >= alle)) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.afBilanzStand(da, alle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (!bilanzAktiv && da < alle) Text(
            S.afBilanzWeiter, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(12.dp, 0.dp) { onWeiter() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
    }
    if (bilanzAktiv) LinearProgressIndicator(
        progress = { da.toFloat() / alle },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 4.dp).height(2.dp),
    )
}

// Zusammenfassung des Rueckblick-Tests (mitgeliefert, nur Kennzahlen), einmal gelesen.
private val FINDER_RUECKBLICK: de.troetenrepublik.app.engine.FinderRueckblickMessung.Datei? by lazy {
    runCatching {
        de.troetenrepublik.app.engine.FinderRueckblickMessung.lesen(de.troetenrepublik.app.plattform.Plattform.asset("indikatoren/finder_rueckblick.json").decodeToString())
    }.getOrNull()?.takeIf { it.stichtage > 0 }
}

private fun sortName(s: Aktienfinder.Sortierung): String = when (s) {
    Aktienfinder.Sortierung.Gesamt -> S.afPunktzahl
    is Aktienfinder.Sortierung.Teil -> S.afBereich(s.bereich.name)
    Aktienfinder.Sortierung.Dividende -> S.afDividende
    Aktienfinder.Sortierung.Boersenwert -> S.afBoersenwert
    is Aktienfinder.Sortierung.NachFeld -> S.afSortFeld(S.afFeld(s.feld.name), s.aufsteigend)
    is Aktienfinder.Sortierung.Rang -> S.afRangsumme
}

// Je Runde hoechstens so viele Werte mit Tageskursen (20 je Abfrage) und mit Profil (eine Abfrage je Wert).
private const val VORLAGE_KERZEN = 400
private const val VORLAGE_EINZELN = 45

// Vorlage (5.94): Grenzen als Pillen (Tipp schaltet eine aus oder wieder an), Alarm und wie viele Werte noch auf Profil
// oder Tageskurse warten; Idee, Luecken und Hinweise eingeklappt (0.68).
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun VorlagenKasten(
    v: FinderVorlagen.Vorlage, aktiv: List<FinderVorlagen.Grenze>, offen: Int, frei: Boolean,
    onGrenze: (FinderVorlagen.Grenze) -> Unit, onWeiter: () -> Unit, alarm: Boolean = false, onAlarm: () -> Unit = {},
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                v.grenzen.filter { !it.wahl }.forEach { g -> FilterPill(grenzeText(g), active = g in aktiv) { onGrenze(g) } }
            }
            // [0.78] Grenzen, von denen nur ein Teil passen muss, als eigene Gruppe mit der noetigen Zahl
            val wahl = v.grenzen.filter { it.wahl }
            if (wahl.isNotEmpty()) {
                val an = wahl.count { it in aktiv }
                Text(
                    S.afMindestens(minOf(v.mindestens.takeIf { it > 0 } ?: an, an), an), style = MaterialTheme.typography.labelSmall, color = grau,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    wahl.forEach { g -> FilterPill(grenzeText(g), active = g in aktiv) { onGrenze(g) } }
                }
            }
            Row(Modifier.padding(top = 8.dp)) { FilterPill(S.afAlarm, active = alarm) { onAlarm() } }
            if (offen > 0) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.afVorlageStand(offen), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
                if (frei) Text(
                    S.afVorlageWeiter, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link,
                    modifier = Modifier.textKlick(12.dp, 0.dp) { onWeiter() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        // Idee, Notizen und Alarm-Hinweis zusammen hinter Titel und Pfeil (0.68)
        val texte = listOfNotNull(
            S.afVorlageIdee(v.id), if (v.nurUs) S.afVorlageNurUs else null,
            // Der Hinweis auf die Herkunft und die Dollargrenzen gilt nur fuer die Vorlagen bis Nummer 23
            if (v.unvollstaendig) S.afVorlageFehlt(v.id) else null, if (v.nummer <= 23) S.afVorlageHinweis else null, S.afAlarmHinweis,
        ).filter { it.isNotEmpty() }
        ErklaerungAufklappen(texte.joinToString("\n\n"), titel = S.infoErklaerung)
    }
}

// Zahl einer Grenze in der Einheit ihres Felds, ohne ueberfluessige Nachkommastelle.
private fun grenzeZahl(f: Feld, x: Double): String {
    fun knapp(t: String) = t.replace(Regex("[.,]0(?=\\D|$)"), "")
    return when (f.einheit) {
        FinderVorlagen.Einheit.PROZENT -> Fmt.pct(x, if (x % 1.0 == 0.0) 0 else 1)
        FinderVorlagen.Einheit.EURO -> if (x >= 1e4) knapp(Fmt.compactEuro(x)) else Fmt.euro(x, if (x % 1.0 == 0.0) 0 else 2)
        FinderVorlagen.Einheit.STUECK -> knapp(Fmt.compact(x))
        else -> Fmt.num(x, if (x % 1.0 == 0.0) 0 else 1)
    }
}

internal fun grenzeText(g: FinderVorlagen.Grenze): String {
    val f = g.feld
    val min = g.min
    val max = g.max
    val linie = f == Feld.LINIE20 || f == Feld.LINIE50 || f == Feld.LINIE200 || f == Feld.SMA50_200
    return when {
        // [0.78] Die n hoechsten oder niedrigsten Werte, der guenstigste Teil des Markts, mindestens so weit unter dem Jahreshoch
        g.spitze > 0 -> S.afFeldSpitze(S.afFeld(f.name), g.spitze, g.oben)
        g.anteil != null -> S.afFeldAnteil(S.afFeld(f.name), Fmt.pct(g.anteil * 100.0, 0), g.oben)
        f == Feld.HOCH52 && max != null && min == null -> S.afUnterHoch52(Fmt.pct(-max, 0))
        f == Feld.KREUZUNG -> S.afKreuzung
        (f == Feld.HOCH20 || f == Feld.HOCH50) && min == 0.0 && max == null -> S.afNeuesHoch(if (f == Feld.HOCH20) 20 else 50)
        f == Feld.HOCH52 && min != null && max == null -> S.afNahHoch52(Fmt.pct(-min, 0))
        f == Feld.ZAHLEN_TAGE && max != null -> S.afZahlenIn(max.toInt())
        linie && min == 0.0 && max == null -> S.afUeberLinie(f.name)
        linie && max == 0.0 && min == null -> S.afUnterLinie(f.name)
        min == 0.01 && max == null -> S.afFeldPositiv(S.afFeld(f.name))
        max == -0.01 && min == null -> S.afFeldNegativ(S.afFeld(f.name))
        min != null && max != null -> S.afFeldZwischen(S.afFeld(f.name), grenzeZahl(f, min), grenzeZahl(f, max))
        min != null -> S.afFeldAb(S.afFeld(f.name), grenzeZahl(f, min))
        max != null -> S.afFeldBis(S.afFeld(f.name), grenzeZahl(f, max))
        else -> S.afFeld(f.name)
    }
}

// Pille mit Menue: Tipp oeffnet die Liste, ein Eintrag waehlt. gewaehlt bekommt den Haken.
@Composable
internal fun MenuePille(
    label: String, aktiv: Boolean, eintraege: List<String>, gewaehlt: Int, waehlen: (Int) -> Unit,
) {
    var auf by remember { mutableStateOf(false) }
    Box {
        FilterPill(label, active = aktiv, chevron = true) { auf = true }
        DropdownMenu(expanded = auf, onDismissRequest = { auf = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            eintraege.forEachIndexed { i, t ->
                DropdownMenuItem(
                    text = { Text(t) },
                    trailingIcon = if (i == gewaehlt) { { CheckMark(size = 15.dp) } } else null,
                    onClick = { auf = false; waehlen(i) },
                )
            }
        }
    }
}

// Eine Aktie: Punktzahl, Name, Einordnung und der wichtigste Grund; aufgeklappt die Bereiche, alle Gruende und der Weg
// zur Wertpapierseite.
@Composable
private fun FinderZeile(
    e: Aktienfinder.Ergebnis, gewichte: Map<Bereich, Double>, offen: Boolean, mitLand: Boolean, gespraech: Boolean, bilanzAktiv: Boolean,
    onToggle: () -> Unit, onOpen: (String) -> Unit,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val d = e.daten
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Text(Fmt.num(e.gesamt, 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleSmall)
            PunktZeile(
                listOfNotNull(S.afEinordnung(e.einordnung.name), S.industryName(d.branche), S.countryLabel(d.land).takeIf { mitLand }, S.afImGespraech.takeIf { gespraech }),
                color = grau,
            )
            if (!offen) e.gruende.firstOrNull()?.let { Text(grundText(it), style = MaterialTheme.typography.bodySmall, color = grau) }
        }
        Spacer(Modifier.width(8.dp))
        AufklappPfeil(offen)
    }
    if (!offen) return
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 6.dp)) {
        Bereich.entries.forEachIndexed { i, b ->
            // Land und Rohstoffbindung werden berechnet und gezeigt, auch wenn sie kein Gewicht haben
            if ((gewichte[b] ?: 0.0) <= 0.0 && b != Bereich.LAND && b != Bereich.WELT) return@forEachIndexed
            val p = e.bereiche[b]
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.afBereich(b.name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(104.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    if (p != null) Box(Modifier.fillMaxHeight().fillMaxWidth((p / 100.0).toFloat().coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp)).background(AnteilsFarben.folge(0)))
                }
                Text(p?.let { Fmt.num(it, 0) } ?: "–", style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.width(32.dp).padding(start = 8.dp))
            }
        }
        // [0.72] Waage der Kennzahlen und Indikatoren aus den Tageskursen, sobald die Stufen da sind
        val waage = FinderVorlagen.wert(d, FinderVorlagen.Feld.WAAGE)
        val rsi = FinderVorlagen.wert(d, FinderVorlagen.Feld.RSI)
        val kennlinie = listOfNotNull(
            waage?.let { S.afWaage(Math.round(it).toInt()) },
            rsi?.let { S.afRsi(Fmt.num(it, 0)) },
            d.werte[FinderVorlagen.Feld.LINIE50]?.let { S.afLinieKurz(50, Fmt.signedPct(it, 1)) },
            d.werte[FinderVorlagen.Feld.LINIE200]?.let { S.afLinieKurz(200, Fmt.signedPct(it, 1)) },
        )
        if (kennlinie.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PunktZeile(kennlinie, color = grau)
        }
        // Bilanz als eigene Zeile: Ampel und Satz wie auf der Wertpapierseite, darunter F-Score und Altman-Zone
        val bilanzZeile = Aktienfinder.mitBilanz(d.branche) && (d.bilanz != null || (bilanzAktiv && !FinderBilanzSpeicher.vorhanden(d.symbol)))
        if (bilanzZeile) {
            val a = d.bilanz
            Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
                AmpelPunkt(ampelZeichen(a), ampelFarbe(a), Modifier.padding(end = 8.dp, top = 1.dp))
                Column {
                    Text(
                        if (a != null) S.bkBilanzSatz(a.ordinal, d.bilanzGruende.map { S.bkGrund(it.name) }) else S.afBilanzFolgt,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val zahlen = S.afBilanzZahlen(d.fScore?.let { Fmt.num(it, 0) }, d.zone?.let { S.qsZone(it.name) })
                    if (a != null && zahlen.isNotBlank()) Text(zahlen, style = MaterialTheme.typography.labelSmall, color = grau)
                }
            }
        }
        // [0.77] Marktmacht als eigener Block mit Stufe und Gruenden
        val mm = d.marktmacht?.takeIf { it.stufe != null }
        mm?.let { MarktmachtBlock(it) }
        // Gruende ohne die Bilanz und die Marktmacht, wenn sie schon als eigene Zeile stehen
        val gruende = (if (bilanzZeile) e.gruende.filter { it.kennzahl !in Aktienfinder.BILANZTEIL && it.kennzahl != Kennzahl.BILANZ } else e.gruende)
            .filter { mm == null || it.kennzahl.bereich != Bereich.MARKTMACHT }
        if (gruende.isNotEmpty()) Spacer(Modifier.height(6.dp))
        gruende.forEach { g ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                AmpelPunkt(
                    if (g.gut) AmpelZeichen.GUT else AmpelZeichen.ACHTUNG, if (g.gut) LocalSignal.current.up else LocalSignal.current.warn,
                    Modifier.padding(end = 8.dp),
                )
                Text(grundText(g), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(S.afDatenlage(Fmt.pct(e.abdeckung * 100, 0)), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 6.dp))
        Text(
            S.afZurAktie, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(0.dp) { onOpen(d.symbol) }.padding(vertical = 10.dp),
        )
    }
}

// Satz zu einem Grund mit dem Wert in der passenden Form.
internal fun grundText(g: Aktienfinder.Grund): String {
    val k = g.kennzahl
    val wert = when (k) {
        Kennzahl.KGV, Kennzahl.KGV_ERWARTET, Kennzahl.KBV, Kennzahl.PEG, Kennzahl.KUV, Kennzahl.EV_EBITDA, Kennzahl.NOTE -> Fmt.num(g.wert, 1)
        Kennzahl.IM_KURS, Kennzahl.RENDITE, Kennzahl.MARGE, Kennzahl.LEER -> Fmt.pct(g.wert, 1)
        Kennzahl.STELLUNG -> Fmt.pct(g.wert, if (g.wert < 10.0) 1 else 0)
        Kennzahl.PREIS_BRUTTO, Kennzahl.PREIS_BETRIEB, Kennzahl.BESTAENDIG -> Fmt.pct(g.wert, 0)
        Kennzahl.WACHSTUM, Kennzahl.KURSZIEL, Kennzahl.JAHR -> Fmt.signedPct(g.wert, 0)
        Kennzahl.LINIE200 -> Fmt.pct(abs(g.wert), 0)
        Kennzahl.SCHULDEN -> Fmt.pct(g.wert * 100.0, 0)
        Kennzahl.ERWAEHNUNGEN, Kennzahl.PIOTROSKI, Kennzahl.LAND, Kennzahl.ROHSTOFFE -> Fmt.num(g.wert, 0)
        Kennzahl.BILANZ, Kennzahl.ALTMAN, Kennzahl.BEREINIGT, Kennzahl.INSIDER, Kennzahl.RSI, Kennzahl.LINIE50 -> ""
    }
    val vergleich = g.vergleich?.let { v ->
        when (k) {
            Kennzahl.IM_KURS -> Fmt.pct(v, 1)
            Kennzahl.ERWAEHNUNGEN, Kennzahl.STELLUNG, Kennzahl.BESTAENDIG -> Fmt.num(v, 0)
            Kennzahl.PREIS_BRUTTO, Kennzahl.PREIS_BETRIEB -> Fmt.pct(v, 0)
            else -> Fmt.num(v, 1)
        }
    }
    return S.afGrund(k.name, g.gut, wert, vergleich)
}
