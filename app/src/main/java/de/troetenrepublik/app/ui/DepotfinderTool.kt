package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Depotfinder
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// Universum des Depotfinders: Kennung, Vergleichswert fuer die Schwankung und dessen Name. Indizes sind alle,
// deren Zusammensetzung der Katalog vollstaendig hat.
private class Universum(val code: String, val vergleich: String, val vergleichName: String)

private val UNIVERSEN = listOf(
    Universum("ALLE", "URTH", "MSCI World"),
    Universum("USA", "^GSPC", "S&P 500"),
    Universum("EUROPA", "^STOXX", "Stoxx Europe 600"),
    Universum("SP500", "^GSPC", "S&P 500"),
    Universum("NDX", "^NDX", "Nasdaq 100"),
    Universum("DJIA", "^DJI", "Dow Jones"),
    Universum("DAX", "^GDAXI", "DAX"),
    Universum("MDAX", "^MDAXI", "MDAX"),
    Universum("SDAX", "^SDAXI", "SDAX"),
    Universum("TECDAX", "^TECDAX", "TecDAX"),
    Universum("ESTX50", "^STOXX50E", "Euro Stoxx 50"),
    Universum("FTSE100", "^FTSE", "FTSE 100"),
    Universum("CAC40", "^FCHI", "CAC 40"),
    Universum("SMI", "^SSMI", "SMI"),
    Universum("AEX", "^AEX", "AEX"),
    Universum("IBEX35", "^IBEX", "IBEX 35"),
    Universum("FTSEMIB", "FTSEMIB.MI", "FTSE MIB"),
    Universum("ATX", "^ATX", "ATX"),
    Universum("OMXS30", "^OMX", "OMX Stockholm 30"),
    Universum("OMXC25", "^OMXC25", "OMX Copenhagen 25"),
    Universum("OMXH25", "^OMXH25", "OMX Helsinki 25"),
    Universum("OBX", "OBX.OL", "OBX"),
    Universum("NIKKEI225", "^N225", "Nikkei 225"),
)

// Laender, die zur Region Europa zaehlen.
private val EUROPA = setOf("DE", "GB", "FR", "IT", "ES", "CH", "NL", "SE", "DK", "FI", "AT", "NO", "IE", "LU", "BE", "CZ", "PT", "PL")

private fun universumName(code: String): String =
    if (code == "ALLE" || code == "USA" || code == "EUROPA") S.dfUniversumName(code) else Catalog.indexName(code)

// Depotfinder: Universum, Zahl der Aktien, Mindestscore und Gewichte der Faktoren waehlen, dann sucht die App die
// Aktien mit dem besten Score (Depotfinder). Kennzahlen und Kurse holt sie erst beim Tippen auf „Depot finden“.
@Composable
internal fun DepotfinderTool(state: AppState, onOpen: (String) -> Unit) {
    katalogBereit()
    var universum by rememberSaveable { mutableStateOf("ALLE") }
    var anzahl by rememberSaveable { mutableStateOf(10) }
    var mindest by rememberSaveable { mutableStateOf(50f) }
    val gewichte = remember {
        Depotfinder.Faktor.entries.associateWith { f ->
            mutableStateOf(if (f == Depotfinder.Faktor.WACHSTUM || f == Depotfinder.Faktor.QUALITAET) 50f else 0f)
        }
    }
    var fortschritt by remember { mutableStateOf<Float?>(null) }
    var ergebnis by remember { mutableStateOf<Depotfinder.Ergebnis?>(null) }
    var marktVola by remember { mutableStateOf<Double?>(null) }
    var nachbilden by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current

    fun suchen() {
        val katalog = Catalog.loaded() ?: return
        val aktien = when (universum) {
            "ALLE" -> katalog.aktien
            "USA" -> katalog.aktien.filter { it.c == "US" }
            "EUROPA" -> katalog.aktien.filter { it.c in EUROPA }
            else -> katalog.aktien.filter { universum in it.x }
        }.distinctBy { it.s }
        val index = (UNIVERSEN.firstOrNull { it.code == universum } ?: UNIVERSEN.first()).vergleich
        fortschritt = 0f
        ergebnis = null
        scope.launch {
            val symbole = aktien.map { it.s }
            val kennzahlen = state.kennzahlen(symbole)
            fortschritt = 0.2f
            val teile = (symbole + index).chunked(20)
            var fertig = 0
            val gleichzeitig = Semaphore(4)
            val kurse = coroutineScope {
                teile.map { t ->
                    async { gleichzeitig.withPermit { state.schlusskurse(t, "1y").also { fertig++; fortschritt = 0.2f + 0.8f * fertig / teile.size } } }
                }.awaitAll()
            }.fold(HashMap<String, List<de.troetenrepublik.app.data.Candle>>()) { m, x -> m.putAll(x); m }
            val kandidaten = aktien.map { a ->
                val k = kennzahlen[a.s].orEmpty()
                Depotfinder.Kandidat(
                    a.s, a.n, k["trailingPE"], k["priceToBook"], k["epsTrailingTwelveMonths"], k["epsForward"], k["bookValue"],
                    k["trailingAnnualDividendYield"], kurse[a.s].orEmpty().map { it.close },
                )
            }
            marktVola = Depotfinder.volatilitaet(kurse[index].orEmpty().map { it.close })
            ergebnis = Depotfinder.finden(kandidaten, gewichte.mapValues { it.value.value.toDouble() }, anzahl, mindest.toDouble())
            fortschritt = null
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(S.dfUniversum, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(Modifier.horizontalScrollMaus(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UNIVERSEN.forEach { u -> Chip(universumName(u.code), active = u.code == universum) { universum = u.code } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.dfAnzahl, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { anzahl = (anzahl - 1).coerceAtLeast(3) }.padding(horizontal = 14.dp, vertical = 4.dp))
            Text(anzahl.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { anzahl = (anzahl + 1).coerceAtMost(30) }.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Regler(S.dfMindest, mindest, 0f..90f, 8) { mindest = it }
        Text(S.dfFaktoren, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        Text(S.dfGewichtHinweis, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        Depotfinder.Faktor.entries.forEach { f ->
            val w = gewichte.getValue(f)
            Regler(S.dfFaktor(f.name), w.value, 0f..100f, 9, S.dfFaktorErklaerung(f.name)) { w.value = it }
        }
        val f = fortschritt
        if (f != null) {
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(S.dfLaedt, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 4.dp))
        } else {
            ActionLineWide(S.dfStart) { suchen() }
        }
    }

    ergebnis?.let { e ->
        if (e.treffer.isEmpty()) { ToolNote(S.dfKeine); return@let }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Kennzahl(S.dfSchnitt, Fmt.pct(e.schnittScore, 1))
            e.depotVolatilitaet?.let { Kennzahl(S.dfDepotVola, Fmt.pct(it * 100, 1)) }
            marktVola?.let { Kennzahl(S.dfMarktVola((UNIVERSEN.firstOrNull { it.code == universum } ?: UNIVERSEN.first()).vergleichName), Fmt.pct(it * 100, 1)) }
        }
        // Ohne Querscrollen: Name nimmt den Rest der Breite, Zahlenspalten fest
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Zelle(S.dfSpalteScore, 40, grau); Zelle(S.dfSpalteName, null, grau, links = true)
                Zelle(S.dfSpalteVola, 74, grau); Zelle(S.dfSpalteJahr, 70, grau)
            }
            e.treffer.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t.symbol) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Zelle(Fmt.num(t.score, 0), 40, MaterialTheme.colorScheme.onSurface)
                    Zelle("${t.name} (${t.symbol})", null, MaterialTheme.colorScheme.onSurface, links = true)
                    Zelle(t.volatilitaet?.let { Fmt.pct(it * 100, 1) } ?: "–", 74, MaterialTheme.colorScheme.onSurface)
                    Zelle(t.jahr?.let { Fmt.signedPct(it * 100, 1) } ?: "–", 70, if ((t.jahr ?: 0.0) >= 0) signal.up else signal.down)
                }
            }
        }
        ActionLineWide(S.dfNachbilden) { nachbilden = true }
        ToolNote(S.dfHinweis)
        if (nachbilden) NachbildenBlatt(
            state = state, titel = S.ptTitle("FINDER"), mitAnzahl = false, notiz = S.dfHinweis,
            gewichte = { _ -> e.treffer.map { it.symbol to 1.0 / e.treffer.size } }, onDismiss = { nachbilden = false },
        )
    }
}

@Composable
private fun Regler(
    titel: String, wert: Float, bereich: ClosedFloatingPointRange<Float>, schritte: Int, erklaerung: String? = null, setzen: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.pct(wert.toDouble(), 0), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Ein Satz, der Rest hinter Mehr
        erklaerung?.let { AufklappText(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = wert, onValueChange = setzen, valueRange = bereich, steps = schritte)
    }
}

@Composable
private fun Kennzahl(titel: String, wert: String) {
    Column {
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.titleSmall)
    }
}

// Zelle der Ergebnistabelle; breite null nimmt den Rest der Zeile.
@Composable
private fun RowScope.Zelle(text: String, breite: Int?, farbe: androidx.compose.ui.graphics.Color, links: Boolean = false) {
    VollText(
        text, style = MaterialTheme.typography.bodySmall, color = farbe, textAlign = if (links) TextAlign.Start else TextAlign.End,
        modifier = (if (breite == null) Modifier.weight(1f) else Modifier.width(breite.dp)).padding(horizontal = 3.dp),
    )
}
