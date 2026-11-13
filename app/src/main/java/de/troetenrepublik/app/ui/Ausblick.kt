package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.gdpNow
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Ausblickdeutung
import de.troetenrepublik.app.engine.Ausblickgesamt
import de.troetenrepublik.app.engine.AusblickStaerke
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.Zinserwartung
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Markterwartung
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn

// Reiter „Ausblick“ der Seite Maerkte (seit 5.92): was der Markt erwartet (US-Leitzins aus den Fed-Funds-Futures,
// Wahrscheinlichkeiten von Polymarket zu Zinsen, Rezession, Inflation, Oel, Nahost und Wahlen), Wirtschaftstermine der
// Nasdaq (5.95), Lager und Wirtschaftsdaten aus oeffentlichen Quellen und der Blasen-Detektor. Zeigt und erklaert,
// ohne zu empfehlen.
@Composable
// onEngpass: ein Tipp auf einen Engpass der Uebersicht (Kennung des Engpasses); der Aufrufer oeffnet das Engpass-Blatt.
internal fun AusblickReiter(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit, onEngpass: (String) -> Unit = {}, startAbschnitt: String? = null) {
    // null: wird noch geladen
    var zins by remember { mutableStateOf<Zinserwartung.Erwartung?>(null) }
    var zinsGeladen by remember { mutableStateOf(false) }
    var erwartet by remember { mutableStateOf<List<AppState.Erwartet>?>(null) }
    var blasen by remember { mutableStateOf<AppState.BlasenLage?>(null) }
    // [Ausblick 0.70] Daten der weiteren Abschnitte fuer die Gesamtaussage (die Abrufe sind zwischengespeichert)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var fruehe by remember { mutableStateOf<Map<String, Fruehindikatoren.Signal>?>(null) }
    var ersatz by remember { mutableStateOf<Map<String, Ersatzquellen.Signal>?>(null) }
    var weitere by remember { mutableStateOf<AppState.WeitereSignale?>(null) }
    var weitereGeladen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.keyAgsi) { fruehe = runCatching { state.fruehSignale(Fruehindikatoren.AUSBLICK) }.getOrNull().orEmpty() }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { ersatz = runCatching { state.ersatzSignale() }.getOrNull().orEmpty() }
            launch { weitere = runCatching { state.weitereSignale() }.getOrNull(); weitereGeladen = true }
        }
    }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { zins = runCatching { state.zinsErwartung() }.getOrNull(); zinsGeladen = true }
            launch { erwartet = runCatching { state.marktErwartungen() }.getOrNull().orEmpty() }
            launch { blasen = runCatching { state.blasen() }.getOrNull() ?: AppState.BlasenLage(emptyList(), emptyList(), null, 0L) }
        }
    }
    // Marktzeichen (0.78): Vierer-Indikator und Bodenzeichen, ui/Marktzeichen.kt; ohne Daten fehlen sie
    var vierer by remember { mutableStateOf<de.troetenrepublik.app.engine.Vierer.Ergebnis?>(null) }
    var boden by remember { mutableStateOf<List<de.troetenrepublik.app.engine.Boden.Zeichen>>(emptyList()) }
    LaunchedEffect(Unit) { vierer = runCatching { state.vierer() }.getOrNull() }
    // GDPNow (ui/GdpNowZeile.kt): die Quelle steht im Anhang, sobald die Zeile Daten zeigt
    var gdpNowStand by remember { mutableStateOf<de.troetenrepublik.app.engine.GdpNow.Stand?>(null) }
    LaunchedEffect(Unit) { gdpNowStand = runCatching { state.gdpNow() }.getOrNull() }
    // Rueckblick fuer Blasen-Detektor und Bodenzeichen, ui/ZeichenRueckblickTeil.kt
    val rueckblick = rememberZeichenRueckblick()
    LaunchedEffect(blasen) { blasen?.takeIf { it.indikatoren.isNotEmpty() }?.let { b -> boden = runCatching { state.bodenzeichen(b) }.getOrDefault(emptyList()) } }
    val uri = appLinks()
    val beitraege = remember(zins, erwartet, fruehe, ersatz, weitere) {
        ausblickBeitraege(zins, erwartet, fruehe, ersatz, weitere, java.time.LocalDate.now().toEpochDay())
    }
    val alleGeladen = zinsGeladen && erwartet != null && blasen != null && fruehe != null && ersatz != null && weitereGeladen
    // Die Karte steht, sobald fuer Aktien (Wochen oder Monate) genug Signale da sind; sonst nach dem letzten Abruf
    val genug = remember(beitraege) {
        val einheiten = Ausblickgesamt.einheiten(beitraege)
        Ausblickgesamt.Horizont.entries.any { Ausblickgesamt.auswerten(einheiten, Ausblickgesamt.Markt.AKTIEN, it).urteil != Ausblickgesamt.Urteil.ZU_WENIG }
    }
    // Position der Abschnitte in der Liste, damit ein Tipp auf einen Treiber dorthin springt
    val positionen = remember { HashMap<String, Int>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val springen: (String) -> Unit = { k -> positionen[k]?.let { i -> scope.launch { liste.animateScrollToItem(i) } } }
    // Ziel eines Widgets: gleich zu den Blasen-Anzeichen, sobald die Liste steht
    var startErledigt by remember { mutableStateOf(startAbschnitt == null) }
    LaunchedEffect(blasen != null) {
        if (!startErledigt && startAbschnitt == "blasen" && blasen != null) { startErledigt = true; springen("blasen-kopf") }
    }
    // Ziel der Lage: gleich zur Zinserwartung
    LaunchedEffect(zinsGeladen) {
        if (!startErledigt && startAbschnitt == "zins" && zinsGeladen) { startErledigt = true; liste.zuSchluessel("zins") }
    }
    // Ziele der Verweise aus Globus (Rezession in zwoelf Monaten, erste Zeile der Fruehsignale) und Bewegungen
    // (Wirtschaftstermine): Abschnitte, die von Anfang an in der Liste stehen
    LaunchedEffect(Unit) {
        val ziel = when (startAbschnitt) { "rezession" -> "fruehsignale"; "wirtschaft" -> "wirtschaft"; else -> null }
        if (!startErledigt && ziel != null) {
            startErledigt = true
            androidx.compose.runtime.snapshotFlow { liste.layoutInfo.totalItemsCount }.first { it > 0 }
            liste.zuSchluessel(ziel)
        }
    }
    // Tipp auf die Saisonzeile des Phasenzaehlers: Saison des S&P 500 in den Mustern aufklappen und dorthin springen
    var saisonSprung by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        positionen.clear()
        var pos = 0
        fun marke(k: String, n: Int = 1) { positionen[k] = pos; pos += n }
        // [Ausblick 0.70] Gesamtaussage und Waage
        marke("gesamt-kopf")
        item(key = "gesamt-kopf") { MarktInfoKopf(S.agTitel, remember { ausblickInfoText() }) }
        marke("gesamt")
        if (alleGeladen || genug) {
            item(key = "gesamt") { GesamtKarte(beitraege, blasen) { springen("blasen-kopf") } }
            marke("waage")
            item(key = "waage") { WaageKarte(beitraege, springen) }
        } else item(key = "gesamt") { EmptyHint(S.trLaedt) }
        marke("phasen"); item(key = "phasen") { PhasenzaehlerKarte(state) { saisonSprung++; springen("muster") } } // [0.78] Phasenzaehler
        // Vierer-Indikator direkt unter dem Phasenzaehler: beide Zaehlregeln stehen beieinander
        vierer?.let { v -> marke("vierer"); item(key = "vierer") { ViererZeile(v) } }
        marke("einzel-kopf")
        item(key = "einzel-kopf") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 4.dp)) {
                Text(S.agEinzelTitel, style = MaterialTheme.typography.titleLarge)
            }
        }
        // Kurzaussage aus Zinserwartung und Rezessionswahrscheinlichkeit von Polymarket
        val rezChance = erwartet?.firstOrNull { it.thema.schluessel == "rezession" }
            ?.let { Markterwartung.ausgaenge(it.ereignis).singleOrNull()?.second }
        val kern = if (zinsGeladen && erwartet != null)
            S.meKern(Ausblickdeutung.zinsRichtung(zins?.schritte), Ausblickdeutung.rezessionStufe(rezChance)) else ""
        if (kern.isNotEmpty()) { marke("kern"); item(key = "kern") { KernKarte(kern) } }
        // Was die Zahlen bedeuten (Wirkungskette), ui/WirkungsKarten.kt
        marke("wirkung")
        item(key = "wirkung") { WirkungAbschnitt(state) }
        marke("wirkung-netz")
        item(key = "wirkung-netz") { NavRow(S.wnTitel, S.abhWirkungUnter) { oeffnen("wirkung") } }
        // Verflechtungen: Lieferketten, Beteiligungen und Kreislaeufe, Einstieg mit den KI-Kreislaeufen
        marke("netz-einstieg")
        item(key = "netz-einstieg") { NavRow(de.troetenrepublik.app.i18n.NetzTexte.t("titel"), de.troetenrepublik.app.i18n.NetzTexte.t("einstieg.unter")) { oeffnen("netz") } }
        marke("erwartet-kopf")
        item(key = "erwartet-kopf") { MarktInfoKopf(S.meTitel, S.meInfo) }
        marke("zins")
        item(key = "zins") {
            when {
                !zinsGeladen -> EmptyHint(S.trLaedt)
                zins != null -> ZinsKarte(zins!!)
            }
        }
        // Projektionen der Fed-Mitglieder (Punktdiagramm) neben der Erwartung des Markts
        marke("fed-punkte")
        item(key = "fed-punkte") { FedPunkteKarte(state, zins) }
        if (zins != null) { marke("zins-netz"); item(key = "zins-netz") { NavRow(S.wnTitel, S.abhWirkungUnter) { oeffnen("wirkung/zins") } } }
        val e = erwartet
        when {
            e == null -> item(key = "erwartet-laedt") { EmptyHint(S.trLaedt) }
            e.isEmpty() -> item(key = "erwartet-leer") { EmptyHint(S.heatmapLeer) }
            else -> {
                e.forEachIndexed { i, x -> positionen["e-" + x.thema.schluessel] = pos + i }
                pos += e.size
                items(e, key = { "e-" + it.thema.schluessel }) { x ->
                    ErwartungZeile(x, zins?.schritte) { oeffnen(PmRouten.ereignis(x.ereignis.quelle, x.ereignis.id)) }
                }
            }
        }

        // Wirtschaftstermine des naechsten Handelstags aus dem Kalender der Nasdaq (5.95), ui/NasdaqSeiten.kt
        marke("wirtschaft")
        item(key = "wirtschaft") { WirtschaftAbschnitt(state, oeffnen) }
        // Fruehe Signale aus oeffentlichen Daten (Lager, Erstantraege, Fluggaeste, Duerre), ui/FruehSignale.kt
        marke("fruehsignale")
        item(key = "fruehsignale") { FruehSignaleAbschnitt(state, oeffnen) }
        // Fracht, Lieferketten und Halbleiter aus offenen Ersatzquellen, ui/ErsatzSignale.kt
        marke("ersatzsignale")
        item(key = "ersatzsignale") { ErsatzSignaleAbschnitt(state, oeffnen, onEngpass) }
        // Muster der Vergangenheit (Praesidentschaftszyklus, Saison, Baerenmaerkte), ui/MusterVergangenheit.kt
        marke("muster")
        item(key = "muster") { MusterAbschnitt(state, oeffnen, saisonSprung) }

        marke("blasen-kopf")
        item(key = "blasen-kopf") { MarktInfoKopf(state, "blasen", S.blTitel, S.blInfo + (if (rueckblick.hochs.isEmpty()) "" else "\n\n" + S.mz("zr.h.info"))) }
        val b = blasen
        when {
            b == null -> item(key = "blasen-laedt") { EmptyHint(S.trLaedt) }
            b.indikatoren.isEmpty() -> item(key = "blasen-leer") { EmptyHint(S.heatmapLeer) }
            else -> {
                item(key = "blasen") { BlasenInhalt(state, b, oeffnen) }
                marke("blasen-rueck", blasenRueckblick(rueckblick))
            }
        }
        // [Lange Lage] Risikopraemie der Aktien (Damodaran), nur mit Daten
        item(key = "praemie") { RisikopraemieAbschnitt() }
        if (boden.size >= de.troetenrepublik.app.engine.Boden.MINDESTENS) {
            marke("boden-kopf")
            item(key = "boden-kopf") { MarktInfoKopf(S.mz("b.titel"), S.mz("b.info") + (if (rueckblick.boeden.isEmpty()) "" else "\n\n" + S.mz("zr.b.info"))) }
            marke("boden")
            item(key = "boden") { BodenInhalt(boden) }
            marke("boden-rueck", bodenRueckblick(rueckblick))
        }
        // Quellen gesammelt am Seitenende
        item(key = "quellen") { QuellenAnhang(ausblickQuellen(fruehe.orEmpty(), ersatz.orEmpty(), weitere, blasen, vierer != null, gdpNowStand)) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Quellen des Reiters, soweit ihre Bedingungen eine Nennung verlangen oder unklar sind; nur wenn ihre Daten gezeigt werden.
private fun ausblickQuellen(
    fruehe: Map<String, Fruehindikatoren.Signal>, ersatz: Map<String, Ersatzquellen.Signal>, weitere: AppState.WeitereSignale?, blasen: AppState.BlasenLage?, vierer: Boolean = false, gdpNow: de.troetenrepublik.app.engine.GdpNow.Stand? = null,
): List<QuelleLink> {
    val bl = blasen?.indikatoren?.map { it.schluessel }.orEmpty()
    val fredIds = listOfNotNull("ICSA".takeIf { Fruehindikatoren.ERSTANTRAEGE in fruehe }) +
        Ersatzquellen.FRED_SIGNALE.filterKeys { it in ersatz }.values +
        listOfNotNull(Ersatzquellen.MARGIN_REIHE.takeIf { Blasen.MARGIN in bl })
    return listOfNotNull(
        QuelleLink("GIE AGSI+", "https://agsi.gie.eu").takeIf { Fruehindikatoren.GAS_EU in fruehe },
        QuelleLink("U.S. Drought Monitor", "https://droughtmonitor.unl.edu").takeIf { Fruehindikatoren.DUERRE in fruehe },
        ersatz[Ersatzquellen.LIEFERKETTEN]?.let { QuelleLink("© " + java.time.LocalDate.ofEpochDay(it.stand).year + " Federal Reserve Bank of New York", Ersatzquellen.GSCPI_SEITE) },
        // Rezessionswahrscheinlichkeit aus der Zinskurve (Fruehsignale), ui/RezessionsSignal.kt
        nyFedQuelle().takeIf { de.troetenrepublik.app.nyFedRezessionBekannt() != null },
        gdpNow?.let(::gdpNowQuelle),
        QuelleLink("Drewry", "https://www.drewry.co.uk").takeIf { weitere?.wci != null },
        QuelleLink("Association of American Railroads", de.troetenrepublik.app.engine.WeitereQuellen.AAR_SEITE).takeIf { weitere?.bahn != null },
        QuelleLink("WSTS", de.troetenrepublik.app.engine.WeitereQuellen.WSTS_SEITE).takeIf { weitere?.halbleiter != null },
        QuelleLink("Robert Shiller", Ersatzquellen.SHILLER_SEITE).takeIf { Blasen.CAPE in bl || (bl.isNotEmpty() && de.troetenrepublik.app.engine.ZeichenRueckblick.datei != null) },
        QuelleLink(S.quelleWeltbank, "https://data.worldbank.org").takeIf { Blasen.BUFFETT in bl && blasen?.bipJahr != null },
        QuelleLink(S.ecbAbbrev, de.troetenrepublik.app.engine.Vierer.EZB_SEITE).takeIf { vierer },
    ) + fredQuellen(fredIds)
}

// Kurzaussage ganz oben im Ausblick: ein Satz, was Zinserwartung und Rezessionswahrscheinlichkeit zusammen bedeuten.
@Composable
private fun KernKarte(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.meKernTitel, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

// Ueberschrift eines Abschnitts mit Info-Symbol, wenn es eine Erklaerung gibt; ein Tipp darauf zeigt die Erklaerung in einem Blatt.
@Composable
internal fun MarktInfoKopf(state: AppState, abschnitt: String, titel: String, info: String?) {
    // Langes Druecken auf die Ueberschrift legt das Widget zum Abschnitt an (nur Android)
    WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.abschnitt(abschnitt), Modifier.fillMaxWidth()) { MarktInfoKopf(titel, info) }
}

@Composable
internal fun MarktInfoKopf(titel: String, info: String?) {
    var offen by remember { mutableStateOf(false) }
    Row(
        // Mindesthoehe wie mit Info-Symbol, sonst springt die Ueberschrift
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 20.dp, bottom = 4.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (info != null) IconButton(onClick = { offen = true }) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (offen && info != null) MarktInfoBlatt(titel, info) { offen = false }
}

// Erklaerung als Blatt: Absaetze und Stichpunkte wie im Text.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarktInfoBlatt(titel: String, text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(titel, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            StichpunktText(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// US-Leitzins: heute und erwartet in drei, sechs und zwoelf Monaten, ein Satz und der Verlauf als Stufen.
@Composable
private fun ZinsKarte(z: Zinserwartung.Erwartung) {
    val signal = LocalSignal.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.zeTitel, style = MaterialTheme.typography.titleSmall)
        Text(S.zeSatz(z.schritte), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ZinsSpalte(S.zeHeute, z.heute.zins, null)
            z.in3?.let { ZinsSpalte(S.zeIn(3), it.zins, it.zins - z.heute.zins) }
            z.in6?.let { ZinsSpalte(S.zeIn(6), it.zins, it.zins - z.heute.zins) }
            z.in12?.let { ZinsSpalte(S.zeIn(12), it.zins, it.zins - z.heute.zins) }
        }
        Spacer(Modifier.height(8.dp))
        ZinsStufen(z.pfad, signal.up, signal.down)
        TendenzZeile(Tendenz.ausZins(Ausblickdeutung.zinsRichtung(z.schritte)), Modifier.padding(top = 10.dp), ausblickStaerke("zins"))
    }
}

@Composable
private fun ZinsSpalte(titel: String, zins: Double, aenderung: Double?) {
    Column {
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.num(zins, 2) + " %", style = MaterialTheme.typography.titleSmall)
        aenderung?.let {
            Text(
                (if (it >= 0.005) "+" else if (it <= -0.005) "−" else "±") + Fmt.num(kotlin.math.abs(it), 2),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Erwarteter Satz je Monat als Balken, beschriftet mit dem Monat; erste Spalte heute.
@Composable
private fun ZinsStufen(pfad: List<Zinserwartung.Monat>, hoch: androidx.compose.ui.graphics.Color, runter: androidx.compose.ui.graphics.Color) {
    if (pfad.size < 2) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = androidx.compose.ui.text.rememberTextMeasurer()
    val klein = MaterialTheme.typography.labelSmall
    val lo = pfad.minOf { it.zins } - 0.25
    val hi = pfad.maxOf { it.zins } + 0.05
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val unten = 14.dp.toPx()
        val flaeche = size.height - unten
        val platz = size.width / pfad.size
        val breite = (platz * 0.6f).coerceAtMost(14.dp.toPx())
        pfad.forEachIndexed { i, m ->
            val h = (flaeche * ((m.zins - lo) / (hi - lo))).toFloat().coerceAtLeast(2.dp.toPx())
            val farbe = when {
                i == 0 -> grau
                m.zins > pfad[0].zins + 0.05 -> hoch
                m.zins < pfad[0].zins - 0.05 -> runter
                else -> grau
            }
            val mitte = platz * i + platz / 2
            drawRoundRect(farbe, Offset(mitte - breite / 2, flaeche - h), Size(breite, h), CornerRadius(2.dp.toPx()))
            // Jeden dritten Monat beschriften
            if (i % 3 == 0) {
                val t = java.time.YearMonth.of(m.jahr, m.monat).format(java.time.format.DateTimeFormatter.ofPattern("MMM", I18n.locale)).removeSuffix(".")
                val g = messer.measure(t, klein.copy(color = schrift))
                drawText(g, topLeft = Offset((mitte - g.size.width / 2).coerceIn(0f, size.width - g.size.width), flaeche + 1.dp.toPx()))
            }
        }
    }
}

// Ein Thema als abgegrenzte Karte: Ueberschrift, Frage bei Polymarket, bis zu drei Ausgaenge mit Balken und eine
// Zeile, was ein Eintreten fuer die Maerkte hiesse. Ein Tipp oeffnet das Ereignis.
@Composable
private fun ErwartungZeile(x: AppState.Erwartet, zinsSchritte: Double?, onClick: () -> Unit) {
    val ausgaenge = remember(x.ereignis) { Markterwartung.ausgaenge(x.ereignis) }
    val tendenz = remember(x.ereignis, zinsSchritte) { Tendenz.erwartung(x.thema.schluessel, Markterwartung.ausgaenge(x.ereignis, 50), zinsSchritte) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(16.dp)) {
        // Was ein Eintreten fuer die Maerkte hiesse, steht hinter dem Info-Symbol
        val falls = S.meFalls(x.thema.schluessel)
        var fallsOffen by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.meThema(x.thema.schluessel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (falls.isNotEmpty()) Icon(
                Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable { fallsOffen = true },
            )
            Spacer(Modifier.width(12.dp))
            ChevronRight()
        }
        if (fallsOffen) MarktInfoBlatt(S.meThema(x.thema.schluessel), falls) { fallsOffen = false }
        Text(x.ereignis.titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((name, p) in ausgaenge) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                MarktAnteilBalken(p, Modifier.width(72.dp).height(6.dp))
                Spacer(Modifier.width(8.dp))
                Text(Fmt.num(p * 100, 0) + " %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        // Schwellen, die schon eingetreten sind, stehen nicht mehr unter den offenen Ausgaengen
        if (x.ereignis.erreicht.isNotEmpty()) Text(
            S.meErreicht(x.ereignis.erreicht.map { it.titel }.sorted().joinToString(", ")), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
        TendenzZeile(tendenz, Modifier.padding(top = 10.dp), ausblickStaerke(x.thema.schluessel))
    }
}

// Tendenz eines Eintrags (5.93): „Bullish fuer Aktien“ in Gruen, „Bearish …“ in Rot, „Neutral …“ in Grau.
@Composable
internal fun TendenzZeile(t: Tendenz.Lage, modifier: Modifier = Modifier, staerke: AusblickStaerke.Staerke? = null) {
    val signal = LocalSignal.current
    val farbe = when (t.richtung) {
        Tendenz.Richtung.BULLISH -> signal.up
        Tendenz.Richtung.BEARISH -> signal.down
        Tendenz.Richtung.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (staerke == null) Text(S.tdText(t.richtung.ordinal, t.ziel), style = MaterialTheme.typography.labelMedium, color = farbe, modifier = modifier)
    else Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(S.tdText(t.richtung.ordinal, t.ziel), style = MaterialTheme.typography.labelMedium, color = farbe, softWrap = false)
        Spacer(Modifier.width(8.dp))
        StaerkeBalken(staerke)
    }
}

// Duenner Balken von 0 bis 1.
@Composable
internal fun MarktAnteilBalken(anteil: Double, modifier: Modifier, farbe: androidx.compose.ui.graphics.Color? = null) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val voll = farbe ?: AnteilsFarben.folge(0)
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(bahn, cornerRadius = r)
        val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
        if (w > 0) drawRoundRect(voll, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

// Blasen-Detektor: Gesamtbild, dann was dafuer und was dagegen spricht, dann die heissesten Branchen. Ein Tipp auf eine
// Messgroesse oeffnet ihre Karte (ui/BlasenKarte.kt).
@Composable
private fun BlasenInhalt(state: AppState, b: AppState.BlasenLage, oeffnen: (String) -> Unit) {
    val stufe = remember(b) { Blasen.gesamt(b.indikatoren) }
    val deutsch = I18n.lang == Lang.DE
    val heiss = b.sektoren.firstOrNull { it.ampel != Fruehwarnung.Ampel.GRUEN }
        ?.let { s -> Branchentrends.BRANCHEN.firstOrNull { it.symbol == s.symbol }?.let { if (deutsch) it.de else it.en } }
    val z = remember(b) { Blasen.zaehlung(b.indikatoren) }
    var kopfOffen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (stufe != null) {
            val ampel = when (stufe) { Blasen.Stufe.GERING -> Fruehwarnung.Ampel.GRUEN; Blasen.Stufe.ERHOEHT -> Fruehwarnung.Ampel.GELB; Blasen.Stufe.HOCH -> Fruehwarnung.Ampel.ROT }
            BlasenKopf(
                stufe, ampel, S.blSatz(z.rot, z.gesamt, heiss), z, Blasen.uebersicht(b.indikatoren), kopfOffen,
            ) { kopfOffen = !kopfOffen }
        }
        val dafuer = Blasen.DAFUER.mapNotNull { k -> b.indikatoren.firstOrNull { it.schluessel == k } }
        val dagegen = Blasen.DAGEGEN.mapNotNull { k -> b.indikatoren.firstOrNull { it.schluessel == k } }
        var gewaehlt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
        if (dafuer.isNotEmpty()) Zwischenkopf(S.blGruppe(S.blDafuer, z.dafuerRot, z.dafuerGesamt))
        for (i in dafuer) BlasenZeile(i) { gewaehlt = i.schluessel }
        if (dagegen.isNotEmpty()) Zwischenkopf(S.blGruppe(S.blDagegen, z.dagegenRot, z.dagegenGesamt))
        for (i in dagegen) BlasenZeile(i) { gewaehlt = i.schluessel }
        gewaehlt?.let { k ->
            b.indikatoren.firstOrNull { it.schluessel == k }?.let { i -> WiederkehrBlatt("blase-$k") { w -> BlasenKarteBlatt(state, i, b, { r -> w.verlassen(); oeffnen(r) }, zumDetektor = null) { gewaehlt = null } } }
        }
        if (b.sektoren.isNotEmpty()) {
            Zwischenkopf(S.blBranchen)
            var alleSektoren by remember { mutableStateOf(false) }
            for (s in if (alleSektoren) b.sektoren else b.sektoren.take(5)) {
                val name = Branchentrends.BRANCHEN.firstOrNull { it.symbol == s.symbol }?.let { if (deutsch) it.de else it.en } ?: s.symbol
                // Ein Tipp fuehrt zu Branchen und Themen im Reiter Trends
                Row(
                    Modifier.fillMaxWidth().clickable { oeffnen("$MAERKTE_ROUTE/trends") }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmpelPunkt(fwAmpelZeichen(s.ampel), fwAmpelFarbe(s.ampel))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(S.blSektor(Fmt.signedPct(s.jahr, 0), Fmt.signedPct(s.zu200, 0)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    ChevronRight()
                }
            }
            if (b.sektoren.size > 5) ListenPfeil(alleSektoren, label = if (alleSektoren) S.less else S.bbAlle(b.sektoren.size)) { alleSektoren = !alleSektoren }
        }
    }
}

// Kopf des Blasen-Detektors: Stufe und Satz, darunter ein Punkt je gezaehlter Messgroesse (Rot zuerst) mit Zaehlung nach
// Farben. Ein Tipp klappt die Namen aller gezaehlten Messgroessen auf und nennt, was nicht mitzaehlt.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BlasenKopf(
    stufe: Blasen.Stufe, ampel: Fruehwarnung.Ampel, satz: String, z: Blasen.Zaehlung, punkte: List<Blasen.Indikator>,
    offen: Boolean, onClick: () -> Unit,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.blStufe(stufe.ordinal), style = MaterialTheme.typography.titleMedium)
                Text(satz, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            AufklappPfeil(offen)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in punkte) AmpelPunkt(fwAmpelZeichen(i.ampel), fwAmpelFarbe(i.ampel))
        }
        Text(S.blFarben(z.rot, z.gelb, z.gruen), style = MaterialTheme.typography.labelMedium, color = grau, modifier = Modifier.padding(top = 4.dp))
        if (offen) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in punkte) {
                    val tint = if (i.ampel == Fruehwarnung.Ampel.ROT) fwAmpelFarbe(i.ampel).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
                    Row(Modifier.clip(RoundedCornerShape(50)).background(tint).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        AmpelPunkt(fwAmpelZeichen(i.ampel), fwAmpelFarbe(i.ampel))
                        Spacer(Modifier.width(6.dp))
                        Text(blNameAlle(i.schluessel), style = MaterialTheme.typography.labelMedium, softWrap = false)
                    }
                }
            }
            Text(S.blGezaehlt(z.gesamt), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// Eine Messgroesse: Ampel, Name, Einordnung in einem Satz, Wert; ein Tipp oeffnet ihre Karte.
@Composable
private fun BlasenZeile(i: Blasen.Indikator, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmpelPunkt(fwAmpelZeichen(i.ampel), fwAmpelFarbe(i.ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(blNameAlle(i.schluessel), style = MaterialTheme.typography.titleSmall)
                Text(blLageAlle(i.schluessel, i.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Zeichen einer Blase sprechen gegen Aktien
                TendenzZeile(Tendenz.blase(i.ampel), staerke = blasenStaerke(i.schluessel))
            }
            Spacer(Modifier.width(10.dp))
            Text(blWert(i), style = MaterialTheme.typography.titleSmall, softWrap = false)
            Spacer(Modifier.width(8.dp))
            ChevronRight()
        }
        // Rueckblick ueber die volle Breite unter Titel und Wert, eingerueckt auf den Text
        when (i.schluessel) {
            Blasen.CAPE, Blasen.KGV, Blasen.PRAEMIE, Blasen.MARGIN, Blasen.BUFFETT -> WarnKurzzeile(i.schluessel, modifier = Modifier.padding(start = 30.dp))
            Blasen.TREND -> WarnKurzzeile(de.troetenrepublik.app.engine.WarnRueckblick.HITZE, modifier = Modifier.padding(start = 30.dp))
            else -> {}
        }
    }
}

// Wert in der passenden Einheit.
internal fun blWert(i: Blasen.Indikator): String = when (i.schluessel) {
    Blasen.KGV, Blasen.CAPE -> Fmt.num(i.wert, 1)
    Blasen.STIMMUNG, Blasen.ERWAEHNUNG -> Fmt.num(i.wert, 0)
    Blasen.BUFFETT, Blasen.KONZENTRATION -> Fmt.pct(i.wert, 0)
    Blasen.PRAEMIE, Blasen.BREITE -> (if (i.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(i.wert), 1) + " " + S.fwPunkte
    else -> Fmt.signedPct(i.wert, 0)
}
