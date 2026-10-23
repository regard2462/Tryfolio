package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KongressAuswertung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Vermoegen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Laedt Geschaefte und Kurse aller Abgeordneten und Senatoren einmal (siehe AppState.kongressDaten) und zeigt
// solange einen Fortschrittsbalken.
@Composable
private fun mitKongressDaten(state: AppState): Pair<AppState.KongressDaten?, Float?> {
    var daten by remember { mutableStateOf(state.kongressFertig()) }
    var fortschritt by remember { mutableStateOf<Float?>(if (daten == null) 0f else null) }
    LaunchedEffect(Unit) {
        if (daten != null) return@LaunchedEffect
        daten = withContext(Dispatchers.Default) { runCatching { state.kongressDaten { fortschritt = it } }.getOrNull() }
        fortschritt = null
    }
    return daten to fortschritt
}

@Composable
private fun Laden(f: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
        Text(S.kaLaedt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// Strategie „Kongress kauft“: Verlauf seit 2024 gegen den S&P 500, heutige Zusammensetzung, Nachbilden.
@Composable
fun KongressKauftScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val (daten, fortschritt) = mitKongressDaten(state)
    var ergebnis by remember { mutableStateOf<KongressAuswertung.Strategie?>(null) }
    var gerechnet by remember { mutableStateOf(false) }
    var nachbilden by remember { mutableStateOf(false) }
    // Aus den Kaeufen welches Zeitraums das Depot besteht
    var wahl by rememberSaveable { mutableStateOf(0) }
    var eigeneMonate by rememberSaveable { mutableStateOf(24f) }
    val fenster = KongressAuswertung.FENSTER.getOrNull(wahl) ?: (eigeneMonate.toInt() * 30)
    LaunchedEffect(daten, fenster) {
        val d = daten ?: return@LaunchedEffect
        ergebnis = withContext(Dispatchers.Default) { KongressAuswertung.strategie(d.handel, d.kurse, d.index, System.currentTimeMillis(), fenster) }
        gerechnet = true
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.kaTitel, null, onBack) }
        item { CopyHinweis(S.kaText) }
        item(key = "fenster") {
            Text(S.kaFenster, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
            ZeitLeiste(KongressAuswertung.FENSTER.map { S.kaFensterKurz(it) } + S.kaFensterEigen, selected = wahl,
                onSelect = { wahl = it }, modifier = Modifier.padding(horizontal = 12.dp))
            // Eigener Zeitraum in Monaten
            if (wahl == KongressAuswertung.FENSTER.size) {
                SliderRow(S.kaFensterMonate, S.kaMonate(eigeneMonate.toInt()), eigeneMonate, 1f..36f, 1f) { eigeneMonate = it }
            }
        }
        val e = ergebnis
        when {
            fortschritt != null -> item { Laden(fortschritt) }
            e == null && gerechnet -> item { EmptyHint(S.copyNoHistory) }
            e == null -> item { CopyLade() }
            else -> {
                item {
                    Kennzahlen(
                        listOf(
                            S.copyPeriod to S.copyRange(Fmt.date(e.verlauf.first().first), Fmt.date(e.verlauf.last().first)),
                            S.copyReturnPa to (e.renditePa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.kaVergleich to (e.vergleichPa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.copyMaxDrawdown to Fmt.signedPct(e.maxVerlust * 100, 1),
                        ),
                    )
                }
                item {
                    PortfolioKarte(
                        state, titel = S.kaTitel,
                        haupt = e.verlauf to Portfolioverlauf.Waehrung.USD,
                        laedt = false, hinweis = S.kaHinweisVerlauf, eigener = "kongress-kauft",
                        vorauswahl = listOf("m:sp500"),
                    )
                }
                item { SectionHeader(S.kaJetzt) }
                itemsIndexed(e.gewichte.take(30), key = { _, (s, _) -> "g-$s" }) { _, (sym, w) ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NamenText(CopyRouten.katalogName(sym) ?: sym, style = MaterialTheme.typography.titleSmall)
                            Text(sym, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Fmt.pct(w * 100, 1), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (e.gewichte.isNotEmpty()) item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
            }
        }
        item { Quelle(S.kaQuelle) }
    }
    val e = ergebnis
    if (nachbilden && e != null) {
        NachbildenBlatt(
            state, titel = S.kaTitel, mitAnzahl = true, notiz = S.copyNote(S.kaTitel), onDismiss = { nachbilden = false },
            gewichte = { n ->
                val teil = e.gewichte.take(n)
                val s = teil.sumOf { it.second }
                if (s <= 0) emptyList() else teil.map { it.first to it.second / s }
            },
        )
    }
}

private enum class RangOrdnung { VORSPRUNG, QUOTE, KAEUFE, VERMOEGEN }

// Rangliste: Abgeordnete und Senatoren nach dem Vorsprung ihrer Kaeufe vor dem S&P 500.
@Composable
fun RanglisteScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val (daten, fortschritt) = mitKongressDaten(state)
    var raenge by remember { mutableStateOf<List<KongressAuswertung.Rang>?>(null) }
    var ordnung by rememberSaveable { mutableStateOf(RangOrdnung.VORSPRUNG.name) }
    val signal = LocalSignal.current
    LaunchedEffect(daten) {
        val d = daten ?: return@LaunchedEffect
        val i = d.index ?: run { raenge = emptyList(); return@LaunchedEffect }
        raenge = withContext(Dispatchers.Default) { KongressAuswertung.rangliste(d.handel, d.kurse, i, System.currentTimeMillis()) }
    }
    val vermoegen = state.vermoegen
    fun vm(r: KongressAuswertung.Rang): Vermoegen.E? = if (r.person.startsWith("k:")) vermoegen[r.person.removePrefix("k:")] else null
    val gezeigt = remember(raenge, ordnung) {
        val l = raenge.orEmpty()
        when (RangOrdnung.valueOf(ordnung)) {
            RangOrdnung.VORSPRUNG -> l.sortedByDescending { it.vorsprung }
            RangOrdnung.QUOTE -> l.sortedWith(compareByDescending<KongressAuswertung.Rang> { it.quote }.thenByDescending { it.kaeufe })
            RangOrdnung.KAEUFE -> l.sortedByDescending { it.kaeufe }
            RangOrdnung.VERMOEGEN -> l.filter { vm(it) != null }.sortedByDescending { vm(it)!!.nettoBis }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.rgTitel, null, onBack) }
        item { CopyHinweis(S.rgText) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RangOrdnung.entries.forEach { o -> Chip(S.rgOrdnung(o.name), active = ordnung == o.name) { ordnung = o.name } }
            }
        }
        when {
            fortschritt != null -> item { Laden(fortschritt) }
            raenge == null -> item { CopyLade() }
            gezeigt.isEmpty() -> item { EmptyHint(S.copyNoHistory) }
            else -> itemsIndexed(gezeigt, key = { _, r -> r.person }) { i, r ->
                val v = vm(r)
                val unter = listOfNotNull(
                    S.rgZeile((if (r.vorsprung >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(r.vorsprung) * 100, 1), Fmt.pct(r.quote * 100, 0), r.kaeufe),
                    v?.let { S.rgVermoegen(vermoegenText(it)) },
                ).joinToString(" · ")
                val haus = r.person.startsWith("k:")
                val schluessel = r.person.substringAfter(':')
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (i + 1).toString(), style = MaterialTheme.typography.titleSmall,
                        color = if (r.vorsprung >= 0) signal.up else signal.down,
                        modifier = Modifier.padding(start = 20.dp).padding(end = 0.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        PersonZeile(
                            name = r.name + if (haus) "" else " · " + S.copySenate,
                            unter = unter,
                            gefolgt = r.person in state.gefolgt(),
                            foto = Fotos.von(state, r.person),
                        ) { oeffnen(if (haus) CopyRouten.person(schluessel) else CopyRouten.senator(schluessel)) }
                    }
                }
            }
        }
        item { Quelle(S.kaQuelle) }
    }
}

// Netto-Spanne laut Jahresmeldung, in der Anzeigewaehrung; negativ, wenn die Verbindlichkeiten ueberwiegen koennen.
internal fun vermoegenText(v: Vermoegen.E): String {
    val (a, z) = Fmt.ausDollar(v.nettoVon.toDouble())
    val b = Fmt.ausDollar(v.nettoBis.toDouble()).first
    fun k(x: Double): String {
        val m = kotlin.math.abs(x)
        val t = if (m >= 1_000_000) Fmt.num(m / 1_000_000.0, 1) + S.copyMillion else Fmt.num(zweiStellen(m), 0)
        return if (x < 0) "−$t" else t
    }
    return if (v.ao) S.copyOver(Fmt.mitSymbol(k(a), z)) else Fmt.mitSymbol(S.vmSpanne(k(a), k(b)), z)
}

// Zeile auf der Seite eines Abgeordneten: Vermoegen laut neuester Jahresmeldung, aufklappbar mit den Teilen.
@Composable
internal fun VermoegenZeile(state: AppState, person: String) {
    val v = state.vermoegen[person] ?: return
    var offen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.vmTitel(v.j), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(vermoegenText(v), style = MaterialTheme.typography.titleSmall)
        }
        if (offen) {
            Text(
                S.vmTeile(spanne(v.av, if (v.ao) null else v.ab), spanne(v.sv, if (v.so) null else v.sb), v.z),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
            Text(S.vmHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
