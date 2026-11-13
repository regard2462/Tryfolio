package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Konvergenz
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Konvergenz (0.77): Aktien, bei denen mindestens zwei von drei Gruppen kaufen (Insider, Kongress, Fonds). Die Rechnung
// steht in engine/Konvergenz.kt; hier die Daten der App zusammentragen und anzeigen.
internal object KonvergenzDaten {

    class Ergebnis(val zeilen: List<Konvergenz.Zeile>, val fenster: Konvergenz.Fenster, val fonds: Gesamt13f.Datei?)

    // Insider-Daten wie auf den Insider-Seiten (mit Abruf, wenn eine Kontaktadresse da ist), Kongress-Geschaefte ohne Kurse
    // (sechs Stunden im Speicher), 13F-Gesamtbild aus dem Bündel.
    suspend fun laden(state: AppState): Ergebnis? = withContext(Dispatchers.Default) {
        val sec = state.secLaden()
        if (sec.geschaefte.isEmpty()) return@withContext null
        val f = fensterFuer(sec)
        val handel = runCatching { state.kongressHandel() }.getOrDefault(emptyList())
        val g13 = Gesamt13f.aktuell
        val zeilen = Konvergenz.liste(
            Konvergenz.insiderKaeufer(sec.geschaefte, f.ab, f.bis), Konvergenz.kongressKaeufer(handel, f.abMs, f.bisMs), Konvergenz.fondsKaeufer(g13),
        )
        Ergebnis(zeilen, f, g13)
    }

    fun fensterFuer(sec: SecInsider.Datei): Konvergenz.Fenster = Konvergenz.fenster(java.time.LocalDate.parse(standEnde(sec)))

    // Zahl der Abgeordneten mit Kauf im Fenster fuer ein Kuerzel
    suspend fun kongressFuer(state: AppState, symbol: String, f: Konvergenz.Fenster): Int = withContext(Dispatchers.Default) {
        val k = Konvergenz.kuerzel(symbol)
        Konvergenz.kongressKaeufer(runCatching { state.kongressHandel() }.getOrDefault(emptyList()).filter { Konvergenz.kuerzel(it.symbol) == k }, f.abMs, f.bisMs)[k] ?: 0
    }
}

@Composable
fun KonvergenzScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    katalogBereit()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    var fertig by remember { mutableStateOf(false) }
    val daten by produceState<KonvergenzDaten.Ergebnis?>(null) {
        value = runCatching { KonvergenzDaten.laden(state) }.getOrNull()
        fertig = true
    }
    val drei = remember(daten) { daten?.zeilen?.filter { it.gruppen == 3 }.orEmpty() }
    val zwei = remember(daten) { daten?.zeilen?.filter { it.gruppen == 2 }.orEmpty() }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item(key = "kopf") { WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.konvergenz(), Modifier.fillMaxWidth()) { ScreenHeader(S.konTitel, null, onBack) } }
        val d = daten
        if (d == null) {
            item(key = "laedt") { if (!fertig) CopyLade() else EmptyHint(S.kvLeer) }
        } else {
            item(key = "erkl", contentType = "text") {
                Text(
                    S.kvErklaerung(Fmt.date(Politik.tag(d.fenster.bis)), d.fonds?.let { quartalKurz(it.stichtag) } ?: "–"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (d.zeilen.isEmpty()) item(key = "leer") { EmptyHint(S.kvLeer) }
            if (drei.isNotEmpty()) {
                item(key = "drei-kopf", contentType = "kopf") { SectionHeader(S.kvDrei) }
                items(drei.size, key = { "d-" + drei[it].symbol }, contentType = { "kv-zeile" }) { i -> KonvergenzZeile(state, drei[i], oeffnen) }
            }
            if (zwei.isNotEmpty()) {
                item(key = "zwei-kopf", contentType = "kopf") { SectionHeader(S.kvZwei) }
                items(zwei.size, key = { "z-" + zwei[it].symbol }, contentType = { "kv-zeile" }) { i -> KonvergenzZeile(state, zwei[i], oeffnen) }
            }
        }
        item(key = "fuss") { Spacer(Modifier.height(32.dp)) }
    }
}

// Name mit Kuerzel, darunter die drei Marken (Punkt gefuellt: Gruppe kauft, mit der Zahl der Kaeufer).
@Composable
private fun KonvergenzZeile(state: AppState, z: Konvergenz.Zeile, oeffnen: (String) -> Unit) {
    val sym = CopyRouten.symbol(z.symbol)
    val name = CopyRouten.name(z.symbol, z.name)
    Row(
        Modifier.fillMaxWidth().clickable { oeffnen("instrument/$sym") }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, sym, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${z.symbol})", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Marke(S.kvInsider, z.insider)
                Marke(S.kvKongress, z.kongress)
                Marke(S.kvFonds, z.fonds)
            }
        }
    }
}

// Kleine Marke: gefuellter Punkt, Name und Zahl der Kaeufer in der Kauffarbe, wenn die Gruppe kauft; sonst Ring und Name
// gedaempft, ohne Zahl.
@Composable
private fun Marke(gruppe: String, zahl: Int) {
    val erfuellt = zahl > 0
    val name = if (erfuellt) gruppe + "\u00A0" + Fmt.num(zahl.toDouble(), 0) else gruppe
    val grau = MaterialTheme.colorScheme.outline
    // Kaufende Gruppen in der Kauffarbe, nicht kaufende als Ring
    val kauf = LocalSignal.current.up
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier.size(8.dp).clip(CircleShape).then(if (erfuellt) Modifier.background(kauf) else Modifier.border(1.dp, grau, CircleShape)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            name, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
            // gedaempft gegen den Namen darueber
            color = if (erfuellt) kauf else grau,
        )
    }
}

// Kernaussage auf der Wertpapierseite (unter „Signale“): steht der Wert auf der Konvergenz-Liste, eine Zeile mit den
// kaufenden Gruppen und der Zahl ihrer Kaeufer, darunter Zaehlweise und Fenster (die Zeilen Insider und Kongress
// darunter zaehlen Netto-Kaeufer und Geschaefte); ein Tipp oeffnet die Liste. Der Kongress wird nur gelesen, wenn
// Insider oder Fonds kaufen.
@Composable
internal fun KonvergenzKern(state: AppState, symbol: String, oeffnen: () -> Unit) {
    val sec by state.sec.collectAsStateWithLifecycle()
    val bis = remember(sec) { sec?.let { KonvergenzDaten.fensterFuer(it).bis } }
    val zeile by produceState<Konvergenz.Zeile?>(null, symbol, sec) {
        val d = sec ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val f = KonvergenzDaten.fensterFuer(d)
                val k = Konvergenz.kuerzel(symbol)
                val fonds = Gesamt13f.fuer(Gesamt13f.aktuell, symbol)
                val i = Konvergenz.insiderKaeufer(d.geschaefte.filter { Konvergenz.kuerzel(it.k) == k }, f.ab, f.bis)[k]?.first ?: 0
                if (i == 0 && Konvergenz.fonds(fonds) == 0) return@runCatching null
                Konvergenz.fuerWert(symbol, d.geschaefte, KonvergenzDaten.kongressFuer(state, symbol, f), fonds, f)
            }.getOrNull()
        }
    }
    val z = zeile ?: return
    fun marke(gruppe: String, n: Int) = if (n > 0) gruppe + "\u00A0" + Fmt.num(n.toDouble(), 0) else null
    Column(Modifier.fillMaxWidth().clickable(onClick = oeffnen).padding(horizontal = 20.dp, vertical = 6.dp)) {
        PunktText(
            listOfNotNull(S.konTitel, marke(S.kvInsider, z.insider), marke(S.kvKongress, z.kongress), marke(S.kvFonds, z.fonds)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        bis?.let {
            Text(S.kvFensterBis(Konvergenz.TAGE, Fmt.date(Politik.tag(it))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
