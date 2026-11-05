package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Tagesrueckblick
import de.troetenrepublik.app.engine.Tagesrueckblick.Bewegung
import de.troetenrepublik.app.engine.Tagesrueckblick.Markt
import de.troetenrepublik.app.engine.Tagesrueckblick.Nachricht
import de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick
import de.troetenrepublik.app.engine.Tagesrueckblick.Spitze
import de.troetenrepublik.app.engine.Tagesrueckblick.Woche
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Rss
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Feste Abstaende der Karten: zwischen Zeilen, vor Abschnitts- und vor Gruppenueberschriften.
private val ZEILE = 6.dp
private val ABSCHNITT = 16.dp
private val GRUPPE = 10.dp

// Tages- und Wochenrueckblicke nach Wochen, neueste zuerst. Je Woche oben der Ueberblick, darunter jeder Tag mit
// einer kurzen Einleitung; aufgeklappt (der neueste von selbst) folgen Maerkte, Branchen, Depot, Watchlist und Nachrichten.
@Composable
fun RueckblickScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val archiv by state.rueckblicke.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf(false) }
    var nachricht by remember { mutableStateOf<Nachricht?>(null) }
    val onNachricht: (Nachricht) -> Unit = { nachricht = it }
    LaunchedEffect(Unit) { state.rueckblickeLaden() }
    val wochen = remember(archiv) { archiv?.let { Tagesrueckblick.wochen(it) }.orEmpty() }
    val neuester = archiv?.tage?.firstOrNull()?.tag
    var offen by remember(neuester) { mutableStateOf(setOfNotNull(neuester)) }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.rbTitel, null, onBack) }
        item {
            ToggleRow(S.rbSchalter, settings.tagesrueckblick) { v -> scope.launch { state.settings.update { it.copy(tagesrueckblick = v) } } }
            Hinweiszeile(S.rbSchalterHinweis)
            if (settings.tagesrueckblick) RueckblickUhrzeit(state)
            if (settings.tagesrueckblick && de.troetenrepublik.app.plattform.Plattform.mitteilungen) {
                ToggleRow(S.rbMitteilung, settings.rueckblickMitteilung) { v -> scope.launch { state.settings.update { it.copy(rueckblickMitteilung = v) } } }
                Hinweiszeile(S.rbMitteilungHinweis)
            }
            if (settings.tagesrueckblick && settings.rueckblickMitteilung && !settings.notifications && de.troetenrepublik.app.plattform.Plattform.mitteilungen) Hinweiszeile(S.copyBellNotificationsOff)
            if (laeuft) Hinweiszeile(S.rbJetztLaeuft)
            else ActionLineWide(S.rbJetzt) {
                laeuft = true
                fehler = false
                scope.launch {
                    fehler = !runCatching { state.rueckblickJetzt() }.getOrDefault(false)
                    laeuft = false
                }
            }
            if (fehler) Hinweiszeile(S.rbJetztFehler)
        }
        if (archiv != null && wochen.isEmpty()) item { EmptyHint(S.rbLeer) }
        wochen.forEach { w ->
            item(key = "w-${w.montag}") {
                SectionHeader(S.rbWoche(Fmt.dayMonth(Tagesrueckblick.beginn(w.montag)), Fmt.dayMonth(Tagesrueckblick.beginn(w.freitag))))
                if (w.tage.size > 1) WochenKarte(w, onOpen, onNachricht)
            }
            items(w.tage, key = { "t-${it.tag}" }) { r ->
                TagKarte(r, r.tag in offen, onOpen, onNachricht) { offen = if (r.tag in offen) offen - r.tag else offen + r.tag }
            }
        }
        item { Hinweiszeile(S.rbHinweis) }
        item { Spacer(Modifier.height(40.dp)) }
    }

    nachricht?.let { n -> NachrichtBlatt(state, rueckblickEintrag(state, n), onOpen = onOpen, onDismiss = { nachricht = null }) }
}

// Schlagzeile zu einer Nachricht des Rueckblicks: der Feed-Eintrag mit gleichem Link, sonst ein Eintrag aus den Rueckblick-Daten.
private fun rueckblickEintrag(state: AppState, n: Nachricht): Rss.Eintrag =
    state.news.stand.value.eintraege.firstOrNull { it.link == n.link && n.link.isNotBlank() }
        ?: Rss.Eintrag(n.titel, n.link, n.zeit, herausgeber = n.quelle, quelle = n.quelle)

// Uhrzeit des Rueckblicks: Zeile mit der gewaehlten Zeit, aufgeklappt die Auswahl und der Hinweis.
@Composable
internal fun RueckblickUhrzeit(state: AppState) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var offen by rememberSaveable { mutableStateOf(false) }
    Column {
        KeyValueRow(S.rbUhrzeit, Tagesrueckblick.uhrzeit(settings.rueckblickMinute), onClick = { offen = !offen })
        if (offen) {
            BpsRow(Tagesrueckblick.UHRZEITEN, settings.rueckblickMinute, labels = Tagesrueckblick.UHRZEITEN.map { Tagesrueckblick.uhrzeit(it) }) { m ->
                scope.launch { state.settings.update { it.copy(rueckblickMinute = m) } }
            }
            AufklappText(S.rbUhrzeitHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun WochenKarte(w: Woche, onOpen: (String) -> Unit, onNachricht: (Nachricht) -> Unit) {
    Karte {
        Text(S.rbWocheUeberblick, style = MaterialTheme.typography.titleSmall)
        Fliesstext(wochenEinleitung(w))
        Abschnitt(S.rbMarkt)
        MarktListe(w.markt)
        if (w.branchen.size >= 2) {
            Abschnitt(S.etfBranchen)
            BranchenListe(w.branchen)
        }
        if (w.tage.any { it.depotWert > 0 }) {
            Abschnitt(S.ptYourDepot)
            DepotZeilen(w.depotProzent, w.depotAenderung, null)
        }
        if (w.bewegungen.isNotEmpty()) {
            Abschnitt(S.rbBewegungen)
            w.bewegungen.forEach { BewegungZeile(it, onOpen, onNachricht) }
        }
        if (w.nachrichten.isNotEmpty()) {
            Abschnitt(S.rbNachrichten)
            w.nachrichten.forEach { NachrichtZeile(it, onNachricht) }
        }
    }
}

@Composable
private fun TagKarte(r: Rueckblick, offen: Boolean, onOpen: (String) -> Unit, onNachricht: (Nachricht) -> Unit, umschalten: () -> Unit) {
    val datum = remember(r.tag) { LocalDate.ofEpochDay(r.tag).format(DateTimeFormatter.ofPattern(if (I18n.lang == Lang.DE) "EEEE, d. MMMM" else "EEEE, d MMMM", I18n.locale)) }
    val einleitung = remember(r, I18n.lang) { rueckblickEinleitung(r) }
    Karte {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = umschalten)
                .semantics { stateDescription = if (offen) S.rbAufgeklappt else S.rbZugeklappt },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(datum, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (r.depotWert > 0) r.depotProzent?.let { MetricValue(it, Fmt.pct(abs(it), 1)) }
            Spacer(Modifier.width(10.dp))
            ChevronDown(Modifier.graphicsLayer { rotationZ = if (offen) 180f else 0f })
        }
        if (r.vorlaeufig) Fliesstext(S.rbVorlaeufig)
        if (einleitung.isNotBlank()) Text(einleitung, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp).clickable(onClick = umschalten))
        if (!offen) return@Karte

        Abschnitt(S.rbMarkt)
        MarktListe(r.markt)
        if (r.branchen.size >= 2) {
            Abschnitt(S.etfBranchen)
            BranchenListe(r.branchen)
        }
        if (r.gewinner.isNotEmpty()) {
            Abschnitt(S.rbGewinner)
            r.gewinner.forEach { SpitzeZeile(it, onOpen, onNachricht) }
        }
        if (r.verlierer.isNotEmpty()) {
            Abschnitt(S.rbVerlierer)
            r.verlierer.forEach { SpitzeZeile(it, onOpen, onNachricht) }
        }
        Abschnitt(S.ptYourDepot)
        if (r.depotWert <= 0) Fliesstext(S.rbKeinDepot)
        else DepotZeilen(r.depotProzent, r.depotAenderung, r.depotWert)
        if (r.depotWert > 0) {
            Abschnitt(S.rbBewegungen)
            if (r.bewegungen.isEmpty()) Fliesstext(S.rbKeineBewegung) else r.bewegungen.forEach { BewegungZeile(it, onOpen, onNachricht) }
        }
        if (r.merkliste.isNotEmpty()) {
            Abschnitt(S.rbMerkliste)
            r.merkliste.forEach { SpitzeZeile(it, onOpen, onNachricht) }
        }
        Abschnitt(S.rbNachrichten)
        if (r.nachrichten.isEmpty()) Fliesstext(S.rbKeineNachrichten) else r.nachrichten.forEach { NachrichtZeile(it, onNachricht) }
    }
}

// Fazit eines Tags: wie das Depot lief, verglichen mit dem MSCI World, und welcher Wert es am staerksten
// bewegt hat; ohne Depot, wie der Markt schloss. branchen: dazu die staerkste und die schwaechste Branche.
internal fun rueckblickFazit(r: Rueckblick, branchen: Boolean = true): String =
    fazit(r.markt, r.depotWert, r.depotProzent, r.depotAenderung, r.bewegungen.firstOrNull(), if (branchen) r.branchen else emptyList(), woche = false)

// Fazit einer Woche, wie rueckblickFazit.
internal fun wochenFazit(w: Woche, branchen: Boolean = true): String =
    fazit(w.markt, if (w.tage.any { it.depotWert > 0 }) 1.0 else 0.0, w.depotProzent, w.depotAenderung, w.bewegungen.firstOrNull(),
        if (branchen) w.branchen else emptyList(), woche = true)

// Kurze Einleitung eines Tags: Depot gegen Leitmarkt, ohne Depot der Markt. Details stehen in den Listen darunter.
internal fun rueckblickEinleitung(r: Rueckblick): String = einleitung(r.markt, r.depotWert, r.depotProzent, woche = false)

// Kurze Einleitung einer Woche, wie rueckblickEinleitung.
internal fun wochenEinleitung(w: Woche): String =
    einleitung(w.markt, if (w.tage.any { it.depotWert > 0 }) 1.0 else 0.0, w.depotProzent, woche = true)

// Stufe der Entwicklung: 1 deutlich im Plus, -1 deutlich im Minus, sonst 0 (Woche 2 Prozent, Tag 1 Prozent).
private fun stufe(prozent: Double, woche: Boolean): Int {
    val grenze = if (woche) 2.0 else 1.0
    return when { prozent >= grenze -> 1; prozent <= -grenze -> -1; else -> 0 }
}

private fun einleitung(markt: List<Markt>, depotWert: Double, prozent: Double?, woche: Boolean): String {
    val leit = Tagesrueckblick.leitmarkt(markt)
    if (depotWert > 0 && prozent != null) {
        val depot = S.rbEinleitungDepot(S.rbFazitTon(stufe(prozent, woche), woche), prozent >= 0, Fmt.pct(abs(prozent), 1))
        if (leit == null) return "$depot."
        val d = prozent - leit.aenderung
        return depot + S.rbFazitVergleich(Tagesrueckblick.marktName(leit.schluessel), Fmt.num(abs(d), 1), lage(d))
    }
    return leit?.let { S.rbFazitMarkt(Tagesrueckblick.marktName(it.schluessel), Fmt.pct(abs(it.aenderung), 1), lage(it.aenderung), woche) }.orEmpty()
}

private fun fazit(markt: List<Markt>, depotWert: Double, prozent: Double?, betrag: Double, beweger: Bewegung?, branchen: List<Markt>, woche: Boolean): String {
    val leit = Tagesrueckblick.leitmarkt(markt)
    val saetze = mutableListOf<String>()
    if (depotWert > 0 && prozent != null) {
        val depot = S.rbFazitDepot(S.rbFazitTon(stufe(prozent, woche), woche), prozent >= 0, Fmt.pct(abs(prozent), 1), Fmt.signedEuro(betrag))
        saetze += if (leit == null) "$depot." else {
            val d = prozent - leit.aenderung
            depot + S.rbFazitVergleich(Tagesrueckblick.marktName(leit.schluessel), Fmt.num(abs(d), 1), lage(d))
        }
        beweger?.let { saetze += S.rbFazitBeweger(it.name, Fmt.signedPct(it.prozent, 1)) }
    } else if (leit != null) {
        saetze += S.rbFazitMarkt(Tagesrueckblick.marktName(leit.schluessel), Fmt.pct(abs(leit.aenderung), 1), lage(leit.aenderung), woche)
    }
    if (branchen.size >= 2) {
        val (b, s) = branchen.first() to branchen.last()
        saetze += S.rbFazitBranchen(branchenName(b.schluessel), Fmt.signedPct(b.aenderung, 1), branchenName(s.schluessel), Fmt.signedPct(s.aenderung, 1))
    }
    return saetze.joinToString(" ")
}

// Vorn, gleichauf oder hinten: unter 0,2 Prozentpunkten zaehlt es als gleich.
private fun lage(d: Double): Int = when { d >= 0.2 -> 1; d <= -0.2 -> -1; else -> 0 }

private fun branchenName(symbol: String): String =
    Branchentrends.BRANCHEN.firstOrNull { it.symbol == symbol }?.let { if (I18n.lang == Lang.DE) it.de else it.en } ?: symbol

// Eine Zeile im Raster: Name links (darf umbrechen), Wert rechts (bleibt in einer Zeile).
@Composable
private fun RasterZeile(name: String, wert: String, farbe: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(top = ZEILE),
        verticalAlignment = Alignment.Top,
    ) {
        NamenText(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, color = farbe, maxLines = 1, softWrap = false)
    }
}

// Kleine Ueberschrift ueber einer Gruppe von Zeilen.
@Composable
private fun Gruppe(titel: String) {
    Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = GRUPPE))
}

// Maerkte als Zeilen mit der farbigen Aenderung rechts.
@Composable
private fun MarktListe(liste: List<Markt>) {
    val signal = LocalSignal.current
    liste.forEach { m -> RasterZeile(Tagesrueckblick.marktName(m.schluessel), Fmt.signedPct(m.aenderung, 1), signal.forChange(m.aenderung)) }
}

// Die drei staerksten und die drei schwaechsten Branchen.
@Composable
private fun BranchenListe(liste: List<Markt>) {
    val signal = LocalSignal.current
    val n = minOf(3, liste.size / 2)
    Gruppe(S.rbStaerkste)
    liste.take(n).forEach { m -> RasterZeile(branchenName(m.schluessel), Fmt.signedPct(m.aenderung, 1), signal.forChange(m.aenderung)) }
    Gruppe(S.rbSchwaechste)
    liste.takeLast(n).reversed().forEach { m -> RasterZeile(branchenName(m.schluessel), Fmt.signedPct(m.aenderung, 1), signal.forChange(m.aenderung)) }
}

// Depot im Raster: Veraenderung in Prozent und Euro, bei einem Tag dazu der Wert.
@Composable
private fun DepotZeilen(prozent: Double?, aenderung: Double, wert: Double?) {
    val signal = LocalSignal.current
    RasterZeile(S.rbVeraenderung, (prozent?.let { Fmt.signedPct(it, 1) + " · " } ?: "") + Fmt.signedEuro(aenderung), signal.forChange(aenderung))
    if (wert != null) RasterZeile(S.rbWert, Fmt.euro(wert), MaterialTheme.colorScheme.onSurface)
}

// Eine Bewegung: Wert, Prozent und Euro, darunter der wahrscheinliche Grund oder der Markt.
@Composable
private fun BewegungZeile(b: Bewegung, onOpen: (String) -> Unit, onNachricht: (Nachricht) -> Unit) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        RasterZeile(b.name, Fmt.signedPct(b.prozent, 1) + " · " + Fmt.signedEuro(b.betrag), signal.forChange(b.betrag)) { onOpen(b.symbol) }
        val g = b.grund
        when {
            g != null -> GrundZeile(g, onNachricht)
            b.markt != null && b.marktAenderung != null ->
                Unterzeile(S.rbMitMarkt(Tagesrueckblick.marktName(b.markt), Fmt.signedPct(b.marktAenderung, 1)))
            else -> Unterzeile(S.rbOhneGrund)
        }
    }
}

// Ein Wert am Markt oder auf der Watchlist: Name und Prozent, darunter der Grund, falls einer gefunden wurde.
@Composable
private fun SpitzeZeile(sp: Spitze, onOpen: (String) -> Unit, onNachricht: (Nachricht) -> Unit) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        RasterZeile(sp.name, Fmt.signedPct(sp.prozent, 1), signal.forChange(sp.prozent)) { onOpen(sp.symbol) }
        sp.grund?.let { GrundZeile(it, onNachricht) }
    }
}

@Composable
private fun GrundZeile(g: Nachricht, onNachricht: (Nachricht) -> Unit) {
    val klickbar = g.link.startsWith("http")
    Text(
        S.rbWahrscheinlich(g.titel, g.quelle), style = MaterialTheme.typography.bodySmall,
        color = if (klickbar) LocalSignal.current.link else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp).then(if (klickbar) Modifier.clickable { onNachricht(g) } else Modifier),
    )
}

@Composable
private fun Unterzeile(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun NachrichtZeile(n: Nachricht, onNachricht: (Nachricht) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = ZEILE)
            .then(if (n.link.startsWith("http")) Modifier.clickable { onNachricht(n) } else Modifier),
    ) {
        Text(n.titel, style = MaterialTheme.typography.bodyMedium)
        PunktText(listOf(n.quelle, Fmt.time(n.zeit)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Karte(inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) { inhalt() }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = ABSCHNITT))
}

@Composable
private fun Fliesstext(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hinweiszeile(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}
