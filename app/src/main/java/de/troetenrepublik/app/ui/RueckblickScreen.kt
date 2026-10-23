package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Tages- und Wochenrueckblicke nach Wochen, neueste zuerst. Je Woche oben der Ueberblick, darunter jeder Tag mit
// einem kurzen Fazit; aufgeklappt (der neueste von selbst) folgen Maerkte, Branchen, Depot, Watchlist und Nachrichten.
@Composable
fun RueckblickScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val archiv by state.rueckblicke.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.rueckblickeLaden() }
    val wochen = remember(archiv) { archiv?.let { Tagesrueckblick.wochen(it) }.orEmpty() }
    val neuester = archiv?.tage?.firstOrNull()?.tag
    var offen by remember(neuester) { mutableStateOf(setOfNotNull(neuester)) }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.rbTitel, null, onBack) }
        item {
            ToggleRow(S.rbSchalter, settings.tagesrueckblick, S.rbSchalterHinweis) { v -> scope.launch { state.settings.update { it.copy(tagesrueckblick = v) } } }
            if (settings.tagesrueckblick && !settings.notifications && de.troetenrepublik.app.plattform.Plattform.mitteilungen) Hinweiszeile(S.copyBellNotificationsOff)
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
                if (w.tage.size > 1) WochenKarte(w, onOpen)
            }
            items(w.tage, key = { "t-${it.tag}" }) { r ->
                TagKarte(r, r.tag in offen, onOpen) { offen = if (r.tag in offen) offen - r.tag else offen + r.tag }
            }
        }
        item { Hinweiszeile(S.rbHinweis) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun WochenKarte(w: Woche, onOpen: (String) -> Unit) {
    Karte {
        Text(S.rbWocheUeberblick, style = MaterialTheme.typography.titleSmall)
        Fliesstext(wochenFazit(w))
        Abschnitt(S.rbMarkt)
        MarktReihe(w.markt)
        if (w.branchen.size >= 2) {
            Abschnitt(S.rbBranchen)
            BranchenZeilen(w.branchen)
        }
        if (w.tage.any { it.depotWert > 0 }) {
            Abschnitt(S.rbDepot)
            Wertzeile(Fmt.signedEuro(w.depotAenderung) + (w.depotProzent?.let { " (" + Fmt.signedPct(it, 1) + ")" } ?: ""), w.depotAenderung)
        }
        if (w.bewegungen.isNotEmpty()) {
            Abschnitt(S.rbBewegungen)
            w.bewegungen.forEach { BewegungZeile(it, onOpen) }
        }
        if (w.nachrichten.isNotEmpty()) {
            Abschnitt(S.rbNachrichten)
            w.nachrichten.forEach { NachrichtZeile(it) }
        }
    }
}

@Composable
private fun TagKarte(r: Rueckblick, offen: Boolean, onOpen: (String) -> Unit, umschalten: () -> Unit) {
    val datum = remember(r.tag) { LocalDate.ofEpochDay(r.tag).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", I18n.locale)) }
    val fazit = remember(r, I18n.lang) { rueckblickFazit(r) }
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
        if (fazit.isNotBlank()) Text(fazit, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp).clickable(onClick = umschalten))
        if (!offen) return@Karte

        Abschnitt(S.rbMarkt)
        MarktReihe(r.markt)
        if (r.branchen.size >= 2) {
            Abschnitt(S.rbBranchen)
            BranchenZeilen(r.branchen)
        }
        if (r.gewinner.isNotEmpty()) {
            Abschnitt(S.rbGewinner)
            r.gewinner.forEach { SpitzeZeile(it, onOpen) }
        }
        if (r.verlierer.isNotEmpty()) {
            Abschnitt(S.rbVerlierer)
            r.verlierer.forEach { SpitzeZeile(it, onOpen) }
        }
        Abschnitt(S.rbDepot)
        if (r.depotWert <= 0) Fliesstext(S.rbKeinDepot)
        else Wertzeile(S.rbDepotZeile(Fmt.signedEuro(r.depotAenderung), r.depotProzent?.let { Fmt.signedPct(it, 1) }, Fmt.euro(r.depotWert)), r.depotAenderung)
        if (r.depotWert > 0) {
            Abschnitt(S.rbBewegungen)
            if (r.bewegungen.isEmpty()) Fliesstext(S.rbKeineBewegung) else r.bewegungen.forEach { BewegungZeile(it, onOpen) }
        }
        if (r.merkliste.isNotEmpty()) {
            Abschnitt(S.rbMerkliste)
            r.merkliste.forEach { SpitzeZeile(it, onOpen) }
        }
        Abschnitt(S.rbNachrichten)
        if (r.nachrichten.isEmpty()) Fliesstext(S.rbKeineNachrichten) else r.nachrichten.forEach { NachrichtZeile(it) }
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

private fun fazit(markt: List<Markt>, depotWert: Double, prozent: Double?, betrag: Double, beweger: Bewegung?, branchen: List<Markt>, woche: Boolean): String {
    val leit = Tagesrueckblick.leitmarkt(markt)
    val saetze = mutableListOf<String>()
    if (depotWert > 0 && prozent != null) {
        val grenze = if (woche) 2.0 else 1.0
        val stufe = when { prozent >= grenze -> 1; prozent <= -grenze -> -1; else -> 0 }
        val depot = S.rbFazitDepot(S.rbFazitTon(stufe, woche), prozent >= 0, Fmt.pct(abs(prozent), 1), Fmt.signedEuro(betrag))
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

// Maerkte nebeneinander, jeder mit seiner farbigen Aenderung; bricht um, wenn der Platz nicht reicht.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarktReihe(liste: List<Markt>) {
    val signal = LocalSignal.current
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        liste.forEach { m ->
            Row {
                Text(Tagesrueckblick.marktName(m.schluessel) + " ", style = MaterialTheme.typography.bodyMedium)
                Text(Fmt.signedPct(m.aenderung, 1), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(m.aenderung))
            }
        }
    }
}

// Die drei staerksten und die drei schwaechsten Branchen.
@Composable
private fun BranchenZeilen(liste: List<Markt>) {
    val n = minOf(3, liste.size / 2)
    BranchenZeile(S.rbStaerkste, liste.take(n))
    BranchenZeile(S.rbSchwaechste, liste.takeLast(n).reversed())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchenZeile(titel: String, liste: List<Markt>) {
    val signal = LocalSignal.current
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        liste.forEach { m ->
            Row {
                Text(branchenName(m.schluessel) + " ", style = MaterialTheme.typography.bodyMedium)
                Text(Fmt.signedPct(m.aenderung, 1), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(m.aenderung))
            }
        }
    }
}

// Eine Bewegung: Wert, Prozent und Euro, darunter der wahrscheinliche Grund oder der Markt.
@Composable
private fun BewegungZeile(b: Bewegung, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(b.symbol) }, verticalAlignment = Alignment.CenterVertically) {
            NamenText(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(Fmt.signedPct(b.prozent, 1) + " · " + Fmt.signedEuro(b.betrag), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(b.betrag), maxLines = 1)
        }
        val g = b.grund
        when {
            g != null -> GrundZeile(g)
            b.markt != null && b.marktAenderung != null ->
                Unterzeile(S.rbMitMarkt(Tagesrueckblick.marktName(b.markt), Fmt.signedPct(b.marktAenderung, 1)))
            else -> Unterzeile(S.rbOhneGrund)
        }
    }
}

// Ein Wert am Markt oder auf der Watchlist: Name und Prozent, darunter der Grund, falls einer gefunden wurde.
@Composable
private fun SpitzeZeile(sp: Spitze, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(sp.symbol) }, verticalAlignment = Alignment.CenterVertically) {
            NamenText(sp.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(Fmt.signedPct(sp.prozent, 1), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(sp.prozent), maxLines = 1)
        }
        sp.grund?.let { GrundZeile(it) }
    }
}

@Composable
private fun GrundZeile(g: Nachricht) {
    val uri = LocalUriHandler.current
    Text(
        S.rbWahrscheinlich(g.titel, g.quelle), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
        modifier = if (g.link.startsWith("http")) Modifier.clickable { runCatching { uri.openUri(g.link) } } else Modifier,
    )
}

@Composable
private fun Unterzeile(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NachrichtZeile(n: Nachricht) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .then(if (n.link.startsWith("http")) Modifier.clickable { runCatching { uri.openUri(n.link) } } else Modifier),
    ) {
        Text(n.titel, style = MaterialTheme.typography.bodyMedium)
        Text(listOf(n.quelle, Fmt.time(n.zeit)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
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
    Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Fliesstext(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Wertzeile(text: String, wert: Double) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.forChange(wert), modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hinweiszeile(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}
