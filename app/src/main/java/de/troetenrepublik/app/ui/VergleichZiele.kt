package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Vergleichsrechnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Symbols
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Vergleichsziele fuer „Was waere, wenn“ und „Depots vergleichen“ (5.96): Schluessel und Namen der Ziele,
// Laden der Wertreihen und die Listen zur Auswahl.
object VergleichZiele {

    // Namen aus der Suche (Schluessel „a:SYMBOL“), damit Chips und Zeilen den Namen zeigen, auch wenn der Wert nicht im Katalog ist.
    val namen = ConcurrentHashMap<String, String>()

    // Zuletzt gewaehlte Ziele in „Was waere, wenn“, fuer die naechste Wertpapierseite.
    var zuletzt: List<String> = listOf("m:msci")

    // Vorauswahl fuer symbol: zuletzt ohne den Wert selbst („a:symbol“ und Massstaebe mit demselben Symbol).
    fun start(symbol: String): List<String> = zuletzt.filter { k ->
        k != "a:$symbol" && Portfolioverlauf.MASSSTAEBE.none { "m:" + it.id == k && it.symbol == symbol }
    }

    fun name(state: AppState, schluessel: String): String {
        if (schluessel.startsWith("l:")) Vergleichsrechnung.laborRegel(schluessel.substringAfter(':'))?.let { return regelName(it) }
        return VerlaufLader.name(state, schluessel)
    }

    // Wertreihe eines Ziels; geschaefte nur bei Politikern (Zahl der eingerechneten Geschaefte).
    data class Daten(val reihe: List<Pair<Long, Double>>?, val geschaefte: Int? = null)

    // Laden fuer „Was waere, wenn“, gerechnet wie die Aktie selbst: Aktien, ETFs und Massstaebe mit
    // state.dailyCloses (zehn Jahre, Euro zum heutigen Kurs); Aktien und ETFs mit wieder angelegten Dividenden
    // (Analysis.thesaurierend mit state.market.dividendHistory, ein Abruffehler gilt als keine Dividenden);
    // Massstaebe ohne (thesaurierende ETFs, Performanceindex). „l:“ laeuft auf den Gesamtrendite-Kerzen der
    // Aktie. Alle anderen ueber VerlaufLader.reihe, die Waehrung bleibt, wie die Aktie in ihrer Handelswaehrung
    // rechnet; bei Politikern („k:“, „n:“, „r:“) zaehlen die eingerechneten Geschaefte.
    suspend fun wasWaere(state: AppState, schluessel: String, basis: List<Candle>?): Daten {
        val praefix = schluessel.substringBefore(':')
        val id = schluessel.substringAfter(':')
        return when (praefix) {
            "a" -> {
                val kurse = state.dailyCloses(id)
                if (kurse.size < 2) return Daten(null)
                val divs = runCatching { state.market.dividendHistory(id) }.getOrDefault(emptyList())
                Daten(Analysis.thesaurierend(kurse, divs).map { it.t to it.close })
            }
            "m" -> {
                val m = Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == id } ?: return Daten(null)
                Daten(state.dailyCloses(m.symbol).map { it.t to it.close }.takeIf { it.size >= 2 })
            }
            "l" -> {
                val regel = Vergleichsrechnung.laborRegel(id) ?: return Daten(null)
                val kerzen = basis ?: return Daten(null)
                Daten(Vergleichsrechnung.laborReihe(kerzen, regel, StrategieLabor.Kosten(state.settingsFlow.value.spreadStockBps.toDouble())))
            }
            else -> {
                val reihe = VerlaufLader.reihe(state, schluessel)?.first
                val geschaefte = if (praefix == "k" || praefix == "n" || praefix == "r") VerlaufLader.geschaefte(schluessel) else null
                Daten(reihe, geschaefte)
            }
        }
    }

    // Politiker zum Auswaehlen: Regierung, Senat, Repraesentantenhaus, Gefolgte immer zuerst. Mit alle=true alle; sonst
    // die Gefolgten und dazu bis zu 2 aus der Regierung, die 5 aktivsten aus dem Haus und die 3 aktivsten aus dem
    // Senat, ohne Doppelte.
    fun politiker(state: AppState, gefolgt: Set<String>, alle: Boolean, jetzt: Long = System.currentTimeMillis()): List<VerlaufLader.Wahl> {
        val regierung = state.regierung?.personen.orEmpty().filter { it.bestand == null }
            .map { VerlaufLader.Wahl("r:" + it.id, it.name, S.vgRegierung) }
        val senat = state.senat?.let { Politik.personen(it, jetzt) }.orEmpty()
            .map { VerlaufLader.Wahl("n:" + it.schluessel, it.name, S.vgSenat + " · " + S.copyFilings(it.meldungen.size)) }
        val haus = Kongress.personen(state.copy?.stand?.value?.meldungen.orEmpty())
            .map { VerlaufLader.Wahl("k:" + it.schluessel, it.name, S.vgHaus + " · " + S.copyFilings(it.meldungen.size)) }
        val personen = (regierung + senat + haus).distinctBy { it.schluessel }
        val vorn = personen.filter { it.schluessel in gefolgt }
        val rest = personen.filter { it.schluessel !in gefolgt }
        if (alle) return vorn + rest
        return vorn +
            rest.filter { it.schluessel.startsWith("r:") }.take(2) +
            rest.filter { it.schluessel.startsWith("k:") }.take(5) +
            rest.filter { it.schluessel.startsWith("n:") }.take(3)
    }

    // Gruppen ohne Suche: die Kandidaten des Verlaufs ohne die Gruppe der gefolgten Mitglieder (die stehen bei
    // den Politikern vorne), nach den Strategien die Regeln des Strategie-Labors auf die gezeigte Aktie, am
    // Ende die Politiker.
    fun gruppen(state: AppState, gefolgt: Set<String>, laborSymbol: String?): List<Pair<String, List<VerlaufLader.Wahl>>> {
        val gruppen = VerlaufLader.kandidaten(state, gefolgt).filter { it.first != S.pvGroupMembers }.toMutableList()
        if (laborSymbol != null) {
            val labor = S.vgLabor to StrategieLabor.REGELN.filter { it.art != StrategieLabor.Art.HALTEN }.map {
                VerlaufLader.Wahl("l:" + Vergleichsrechnung.laborSchluessel(it), regelName(it), S.vgLaborUnter(Symbols.name(laborSymbol)))
            }
            val bei = gruppen.indexOfFirst { it.first == S.copyStrategies }
            if (bei >= 0) gruppen.add(bei + 1, labor) else gruppen.add(labor)
        }
        gruppen.add(S.vgPolitiker to politiker(state, gefolgt, alle = false))
        return gruppen
    }
}

// Laedt die Daten der Ziele fuer „Was waere, wenn“: fehlt ein Schluessel in der Karte, laeuft er noch; eine
// Laborregel wartet auf die Kerzen der Aktie. Bis zu drei Versuche, falls ein Kursabruf ausfaellt. Das
// Ergebnis bleibt je Schluessel gemerkt, solange die Seite offen ist.
@Composable
fun rememberVergleichsDaten(state: AppState, ziele: List<String>, basis: List<Candle>?): Map<String, VergleichZiele.Daten> {
    val daten = remember { mutableStateMapOf<String, VergleichZiele.Daten>() }
    val inArbeit = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    // basis nur als Schalter, damit nicht jede Neuzeichnung die Kerzenlisten vergleicht
    LaunchedEffect(ziele, basis != null) {
        // fehlgeschlagene Ziele, die abgewaehlt sind, beim naechsten Waehlen neu laden
        daten.keys.filter { it !in ziele && daten[it]?.reihe == null }.forEach { daten.remove(it) }
        ziele.filter { it !in daten && inArbeit[it] != true }.forEach { k ->
            if (k.startsWith("l:") && basis == null) return@forEach
            inArbeit[k] = true
            scope.launch {
                // bis zu drei Versuche, falls ein Kursabruf ausfaellt (etwa bei vielen gleichzeitigen Abrufen)
                var r: VergleichZiele.Daten? = null
                for (versuch in 0..2) {
                    r = runCatching { VergleichZiele.wasWaere(state, k, basis) }.getOrNull()
                    if (r?.reihe != null) break
                    delay(2_000L + versuch * 4_000L)
                }
                daten[k] = r ?: VergleichZiele.Daten(null)
                inArbeit[k] = false
            }
        }
    }
    return daten
}

// Auswahlblatt fuer beide Werkzeuge: ohne Suche die Gruppen mit den Politikern am Ende, mit Suche Aktien und
// ETFs aus dem Katalog und von Yahoo zuerst, danach alle Gruppen durchsucht.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VergleichZielBlatt(state: AppState, gewaehlt: List<String>, ausgeschlossen: Set<String>, max: Int, laborSymbol: String?, onDismiss: () -> Unit, onToggle: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    // Stand des Repraesentantenhauses lesen, damit die Liste der Personen nachkommt
    val copy = state.copy
    val stand = if (copy != null) copy.stand.collectAsStateWithLifecycle().value else null
    LaunchedEffect(Unit) {
        val repo = state.copy ?: return@LaunchedEffect
        if (repo.stand.value.meldungen.isEmpty()) runCatching { repo.verzeichnis() }
    }
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        // Treffer der vorigen Eingabe nicht stehen lassen
        treffer = emptyList()
        if (q.length < 2) return@LaunchedEffect
        delay(300)
        treffer = runCatching { state.market.yahooSuche(q) }.getOrElse { e ->
            if (e is CancellationException) throw e
            emptyList()
        }
    }
    val senat by state.senatFluss.collectAsStateWithLifecycle()
    val gruppen = remember(settings.copyGefolgt, laborSymbol, stand, senat) { VergleichZiele.gruppen(state, state.gefolgt(settings), laborSymbol) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Text(S.pvCompareTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                Text(S.vgMax(max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                OutlinedTextField(
                    value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, placeholder = { Text(S.vgSuche) },
                    shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            val q = suche.trim()
            if (q.length < 2) {
                gruppen.forEach { (titel, wahl) ->
                    item(key = "g-$titel") { SectionHeader(titel) }
                    items(wahl.filter { it.schluessel !in ausgeschlossen }, key = { "w-" + it.schluessel }) { w ->
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            OptionRow(w.name, w.unter, selected = w.schluessel in gewaehlt) { onToggle(w.schluessel) }
                        }
                    }
                }
                item {
                    Text(S.vgPolitikerMehr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            } else {
                // lokale Treffer sofort, die von Yahoo nach kurzer Wartezeit dazu
                val aktien = (Symbols.search(q).take(8).map { VerlaufLader.Wahl("a:" + it.symbol, it.name.ifBlank { it.symbol }, it.symbol) } +
                    treffer.map { h ->
                        VerlaufLader.Wahl("a:" + h.symbol, h.name.ifBlank { h.symbol },
                            listOf(h.symbol, h.exchange).filter { it.isNotBlank() }.joinToString(" · "))
                    }).distinctBy { it.schluessel }.filter { it.schluessel !in ausgeschlossen }
                val weiter = gruppen.dropLast(1) + (S.vgPolitiker to VergleichZiele.politiker(state, state.gefolgt(settings), alle = true))
                val sichtbar = (listOf(S.vgAktienEtfs to aktien) + weiter.map { (titel, wahl) ->
                    titel to wahl.filter {
                        it.schluessel !in ausgeschlossen && (it.name.contains(q, ignoreCase = true) || it.unter.contains(q, ignoreCase = true))
                    }.take(20)
                }).filter { it.second.isNotEmpty() }
                if (sichtbar.isEmpty()) {
                    item {
                        Text(S.vgKeineTreffer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                } else {
                    sichtbar.forEach { (titel, wahl) ->
                        item(key = "g-$titel") { SectionHeader(titel) }
                        items(wahl, key = { "w-" + it.schluessel }) { w ->
                            Box(Modifier.padding(horizontal = 8.dp)) {
                                OptionRow(w.name, w.unter, selected = w.schluessel in gewaehlt) {
                                    if (w.schluessel.startsWith("a:")) VergleichZiele.namen[w.schluessel] = w.name
                                    onToggle(w.schluessel)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

// Chip eines gewaehlten Ziels: Punkt in der Linienfarbe, Name und Zusatz, ein Antippen entfernt das Ziel.
@Composable
fun ZielChip(farbe: Color, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
        Spacer(Modifier.width(6.dp))
        Text(text + "  ×", style = MaterialTheme.typography.labelMedium)
    }
}

// Ergebniszeile eines Ziels: farbiger Punkt, Name, Wert. Wie KeyValueRow: passen beide nebeneinander, stehen
// sie in einer Zeile, sonst rueckt der Wert rechtsbuendig unter den Namen und bricht nur um, wenn er allein
// breiter als die Zeile ist.
@Composable
fun ZielZeile(farbe: Color, name: String, wert: String, wertFarbe: Color? = null) {
    val stil = MaterialTheme.typography.bodyMedium
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val abstand = with(dichte) { 12.dp.roundToPx() }
    // Punkt samt Abstand daneben
    val punkt = with(dichte) { 14.dp.roundToPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        val breite = constraints.maxWidth
        val (nebeneinander, wertPasst) = remember(name, wert, breite, stil) {
            fun w(t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
            val breit = w(wert)
            (w(name) + punkt + abstand + breit <= breite) to (breit <= breite)
        }
        val nameText = @Composable { m: Modifier ->
            Row(m, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
                Spacer(Modifier.width(6.dp))
                Text(name, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val wertText = @Composable { m: Modifier ->
            Text(
                wert,
                style = stil,
                color = wertFarbe ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                softWrap = !wertPasst,
                maxLines = if (wertPasst) 1 else Int.MAX_VALUE,
                modifier = m,
            )
        }
        if (nebeneinander) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                nameText(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                wertText(Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                nameText(Modifier)
                wertText(Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
    }
}
