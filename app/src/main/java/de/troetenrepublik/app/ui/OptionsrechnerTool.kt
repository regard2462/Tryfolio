package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.engine.Optionsrechner
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Leg
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import de.troetenrepublik.app.i18n.S
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.OptionsrechnerPlan
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Optionsrechner (0.78): Strategie aus der Optionskette zusammenstellen, Auszahlung bei Verfall und zu einem Datum,
// Gewinnschwelle, groesster Gewinn und Verlust, Chance auf Gewinn, Spanne von einer Standardabweichung, Griechen.
// Einstieg ueber die Erwartung (steigt, faellt, seitwaerts, starke Bewegung), darunter die passenden Strategien.
// Die Kette kommt ueber die US-Notierung, Preise stehen daher in US-Dollar.
private const val USD = "USD"

private sealed interface OrLadung {
    data object Laden : OrLadung
    data object Leer : OrLadung
    class Daten(val kurs: Double, val kontrakte: List<Kontrakt>) : OrLadung
}

// Blatt fuer die Optionenkarte, der Wert ist vorgewaehlt. onDerivat bekommt die Kennung eines Scheins, dessen Kaufseite
// geoeffnet werden soll; das Blatt wartet unter der Seite und steht nach dem Zurueck wieder da.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsrechnerBlatt(
    state: AppState, symbol: String, onDismiss: () -> Unit, onDerivat: ((String) -> Unit)? = null,
    // Vorbelegung mit einer verkauften Option aus „Optionen verkaufen“
    start: OptionsrechnerPlan.Start? = null,
) {
    WiederkehrBlatt("optionsrechner-" + symbol) { w ->
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = blattZustandHier(),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Text(S.orKarte, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
                OptionsrechnerTool(state, symbol, waehlbar = false, onDerivat = onDerivat?.let { f -> { id: String -> w.verlassen(); f(id) } }, start = start)
            }
        }
    }
}

@Composable
internal fun OptionsrechnerTool(
    state: AppState, vorgabe: String? = null, waehlbar: Boolean = true, onDerivat: ((String) -> Unit)? = null,
    start: OptionsrechnerPlan.Start? = null,
) {
    val konto by state.account.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var symbol by rememberSaveable(vorgabe) { mutableStateOf(vorgabe.orEmpty()) }
    var eingabe by rememberSaveable { mutableStateOf("") }
    val fokus = LocalFocusManager.current
    if (waehlbar) {
        val eigene = remember(konto) { (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).distinct().take(14) }
        if (eigene.isNotEmpty()) ToolChoice(null, eigene.map { it to it }, symbol) { symbol = it; eingabe = "" }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = eingabe, onValueChange = { eingabe = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' || c == '^' }.take(12) },
                singleLine = true, label = { Text(S.orSymbol) }, shape = RoundedCornerShape(Radius.field), modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(Modifier.width(8.dp))
            // Nach dem Laden gibt die Tastatur das Ergebnis frei
            Chip(S.orLaden, eingabe.isNotBlank()) { if (eingabe.isNotBlank()) { symbol = eingabe.trim().uppercase(); fokus.clearFocus() } }
        }
    }
    if (symbol.isBlank()) return
    val ladung by produceState<OrLadung>(OrLadung.Laden, symbol) {
        value = OrLadung.Laden
        value = runCatching { state.market.optionskette(symbol) }.getOrNull()?.let { OrLadung.Daten(it.first, it.second) } ?: OrLadung.Leer
    }
    // Alle gelisteten Verfallstermine; spaetere Termine werden beim Antippen nachgeladen
    val termine by produceState<List<Long>>(emptyList(), symbol) { value = runCatching { state.market.optionsTermine(symbol) }.getOrNull().orEmpty() }
    // Kurs des Basiswerts fuer den Kauf bereitstellen
    LaunchedEffect(symbol) { if (quotes[symbol] == null) runCatching { state.kursFuer(symbol) } }
    when (val l = ladung) {
        OrLadung.Laden -> ToolNote(S.toolLoading)
        OrLadung.Leer -> ToolNote(S.orKeine)
        is OrLadung.Daten -> OrRechner(state, symbol, quotes[symbol], l.kurs, l.kontrakte, termine, onDerivat, start.takeIf { symbol == vorgabe })
    }
}

private fun guvText(v: Double): String = (if (v > 0.5) "+" else "") + Fmt.moneyIn(v, USD, 0)

// Beine des Rechners als Text je Bein (Art, Menge, Strike).
private val BEINE_SAVER = androidx.compose.runtime.saveable.listSaver<List<Leg>, String>(
    save = { beine -> beine.map { de.troetenrepublik.app.engine.Blattzustand.beinText(it.art.name, it.menge, it.strike) } },
    restore = { zeilen -> zeilen.mapNotNull { z -> de.troetenrepublik.app.engine.Blattzustand.beinLesen(z)?.let { (art, menge, strike) -> runCatching { Leg(Art.valueOf(art), menge, strike) }.getOrNull() } } },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrRechner(
    state: AppState, symbol: String, quote: Quote?, kurs: Double, basis: List<Kontrakt>, gelistet: List<Long>,
    onDerivat: ((String) -> Unit)?,
    start: OptionsrechnerPlan.Start? = null,
) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    val jetzt = remember(basis) { System.currentTimeMillis() / 1000 }
    // Kontrakte spaeterer Termine, je Termin einmal nachgeladen
    var extra by remember(basis) { mutableStateOf<Map<Long, List<Kontrakt>>>(emptyMap()) }
    val geladen = remember(basis, extra) { basis + extra.values.flatten() }
    val termine = remember(geladen, gelistet) { OptionsrechnerPlan.termine(geladen, gelistet, jetzt) }
    if (termine.isEmpty()) { ToolNote(S.orKeine); return }
    // Start mit einer verkauften Option, wenn die Kette ihren Kontrakt fuehrt
    val beginn = remember(basis, start) { start?.takeIf { OptionsrechnerPlan.startPasst(it, basis) } }
    val startVerfall = remember(basis, beginn) {
        val eigene = Optionsrechner.verfaelle(basis, jetzt)
        beginn?.verfall ?: eigene.firstOrNull { Optionsrechner.tage(it, jetzt) >= 14 } ?: eigene.firstOrNull() ?: termine.first()
    }
    // Termin, Erwartung, Strategie und Beine bleiben, wenn das Blatt nach einer geoeffneten Seite wiederkommt
    var verfall by rememberSaveable(basis) { mutableStateOf(startVerfall) }
    // Ein Datum ohne Boersentermin: Kontrakte aus den Nachbarterminen
    val istFrei = verfall !in termine
    val kontrakte = remember(geladen, verfall, istFrei) { if (istFrei) geladen + OptionsrechnerPlan.frei(kurs, geladen, verfall, jetzt) else geladen }
    val startVorlage = beginn?.let { OptionsrechnerPlan.startVorlage(it) } ?: Vorlage.LONG_CALL
    var erwartung by rememberSaveable(basis) { mutableStateOf(OptionsrechnerPlan.erwartung(startVorlage) ?: OptionsrechnerPlan.Erwartung.STEIGT) }
    var vorlage by rememberSaveable(basis) { mutableStateOf(startVorlage) }
    var legs by rememberSaveable(basis, stateSaver = BEINE_SAVER) {
        mutableStateOf(beginn?.let { OptionsrechnerPlan.startBeine(it) } ?: Optionsrechner.vorlage(Vorlage.LONG_CALL, kurs, basis, startVerfall).orEmpty())
    }
    var tageWahl by remember(basis, verfall) { mutableFloatStateOf(0f) }
    var sel by remember(basis, verfall, legs) { mutableStateOf<Int?>(null) }
    var anpassenWahl by remember(basis) { mutableStateOf<Boolean?>(null) }
    var datumDialog by remember { mutableStateOf(false) }
    var kaufBlatt by remember { mutableStateOf(false) }
    var erkl by remember { mutableStateOf<String?>(null) }

    fun strikesFuer(art: Art) = Optionsrechner.strikes(kontrakte, verfall, art == Art.CALL)
    fun einrasten(l: Leg, f: Long, liste: List<Kontrakt>): Leg {
        if (l.art == Art.AKTIE) return l
        val strikes = Optionsrechner.strikes(liste, f, l.art == Art.CALL)
        return Optionsrechner.naechster(strikes, l.strike)?.let { l.copy(strike = it) } ?: l
    }
    fun eigene(neu: List<Leg>) { vorlage = Vorlage.EIGENE; legs = neu }
    // Verfall wechseln: Vorlage neu aufstellen, eigene Optionen auf die Basispreise des Termins setzen
    fun anwenden(f: Long, liste: List<Kontrakt>) {
        verfall = f
        legs = if (vorlage != Vorlage.EIGENE) Optionsrechner.vorlage(vorlage, kurs, liste, f).orEmpty() else legs.map { einrasten(it, f, liste) }
    }
    // Termin antippen: fehlen seine Kontrakte, wird einmal nachgeladen; ohne Antwort bleibt die Auswahl, wie sie ist
    suspend fun nachladen(t: Long): List<Kontrakt>? {
        if (geladen.any { it.verfall == t }) return geladen
        val k = runCatching { state.market.optionsTermin(symbol, t) }.getOrNull() ?: return null
        extra = extra + (t to k)
        return geladen + k
    }
    fun waehleTermin(t: Long) {
        scope.launch { nachladen(t)?.let { anwenden(t, it) } }
    }
    // Eigenes Datum: Basispreise und Volatilitaeten vom naechstgelegenen gelisteten Termin
    fun waehleDatum(t: Long) {
        if (t in termine) { waehleTermin(t); return }
        scope.launch {
            val nah = termine.minByOrNull { kotlin.math.abs(it - t) } ?: return@launch
            val liste = nachladen(nah) ?: return@launch
            anwenden(t, liste + OptionsrechnerPlan.frei(kurs, liste, t, jetzt))
        }
    }

    // Erwartung und Strategie
    ToolChoice(S.orErwartung, OptionsrechnerPlan.Erwartung.entries.map { S.orErw(it.name) to it }, erwartung) { e ->
        erwartung = e
        vorlage = OptionsrechnerPlan.vorlagen(e).first()
        legs = Optionsrechner.vorlage(vorlage, kurs, kontrakte, verfall).orEmpty()
        Optionsrechner.verfallFuer(vorlage, termine, verfall, jetzt).let { if (it != verfall) waehleTermin(it) }
    }
    ToolChoice(null, (OptionsrechnerPlan.vorlagen(erwartung) + Vorlage.EIGENE).map { S.orVorlage(it.name) to it }, vorlage) { v ->
        vorlage = v
        if (v != Vorlage.EIGENE) legs = Optionsrechner.vorlage(v, kurs, kontrakte, verfall).orEmpty()
        // Vorlage mit Mindestlaufzeit: auf den ersten Termin springen, der lang genug laeuft
        Optionsrechner.verfallFuer(v, termine, verfall, jetzt).let { if (it != verfall) waehleTermin(it) }
    }

    val beine = remember(legs, verfall, kontrakte) { Optionsrechner.beine(kurs, kontrakte, verfall, legs, jetzt) }
    // Zins aus den Einstellungen, wie auf der Seite eines Scheins
    val zins = state.riskFree
    val e = remember(beine, zins) { beine?.let { Optionsrechner.auswerten(it, kurs, kontrakte, verfall, jetzt, zins) } }
    val name = remember(symbol, quote?.name) {
        HeatmapBeschriftung.bezeichnung(symbol, quote?.name?.ifBlank { null } ?: Symbols.find(symbol)?.name) ?: symbol
    }
    if (beine != null && e != null) {
        klarText(vorlage, e, beine)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        Text(
            kernSatz(name, terminText(verfall), kurs, e, beine), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 4.dp),
        )
    }

    // Verfall: alle gelisteten Termine, am Ende ein eigenes Datum
    Text(S.orVerfall, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        termine.forEach { t -> Chip(terminText(t), t == verfall) { waehleTermin(t) } }
        Chip(if (istFrei) terminText(verfall) else S.orDatumWahl, istFrei) { datumDialog = true }
    }

    // Anpassen: Karten je Option und der Knopf fuer eine weitere Option
    val offen = anpassenWahl ?: (vorlage == Vorlage.EIGENE)
    KernKopf(S.orAnpassen, null, offen, gross = false) { anpassenWahl = !offen }
    if (offen) {
        legs.forEachIndexed { i, l ->
            BeinZeile(
                leg = l, bein = beine?.getOrNull(i), strikes = if (l.art == Art.AKTIE) emptyList() else strikesFuer(l.art), berechnet = istFrei,
                onChange = { neu -> eigene(legs.mapIndexed { j, x -> if (j == i) neu else x }) },
                onArt = { a ->
                    val neu = if (a == Art.AKTIE) Leg(a, l.menge, 0.0)
                    else einrasten(Leg(a, l.menge, if (l.strike > 0) l.strike else kurs), verfall, kontrakte)
                    eigene(legs.mapIndexed { j, x -> if (j == i) neu else x })
                },
                onRemove = { eigene(legs.filterIndexed { j, _ -> j != i }) },
                onInfo = { erkl = it },
            )
        }
        if (legs.size < Optionsrechner.MAX_BEINE) {
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                Chip("+ " + S.orBeinNeu, false) {
                    val s = Optionsrechner.naechster(strikesFuer(Art.CALL), kurs)
                    if (s != null) eigene(legs + Leg(Art.CALL, 1, s))
                }
            }
        }
    }
    if (beine == null || e == null) {
        if (legs.isEmpty() || vorlage != Vorlage.EIGENE) ToolNote(S.orNichtMoeglich)
        return
    }

    // Diagramm zuerst: die Auszahlung ist die Kernaussage, darunter die Zahlen
    val tg = Optionsrechner.tage(verfall, jetzt)
    val tageAuf = tageWahl.roundToInt().coerceIn(0, tg)
    val rest = (tg - tageAuf) / 365.0
    val (lo, hi) = Optionsrechner.bereich(beine, kurs, e.band)
    val xs = remember(beine, lo, hi) {
        (Optionsrechner.punkte(lo, hi, 121) + beine.filter { it.art != Art.AKTIE }.map { it.strike } + kurs).filter { it in lo..hi }.distinct().sorted()
    }
    val verfallKurve = remember(beine, xs) { xs.map { Optionsrechner.guvVerfall(beine, it) } }
    val datumKurve = if (tageAuf < tg) xs.map { Optionsrechner.guvDatum(beine, it, rest) } else null
    val tagText = if (tageAuf == 0) S.orHeute else Fmt.dayMonth(jetzt * 1000 + tageAuf * 86_400_000L)
    if (tg >= 1) {
        SliderRow(S.orWertAm, tagText, tageWahl, 0f..tg.toFloat(), 1f) { tageWahl = it }
    }
    val idx = sel ?: xs.indices.minByOrNull { abs(xs[it] - kurs) } ?: 0
    val ocker = Kategorie.ocker
    val lesen = S.orLesen(
        Fmt.price(xs[idx], USD), guvText(verfallKurve[idx]), tagText,
        datumKurve?.let { guvText(it[idx]) } ?: guvText(verfallKurve[idx]),
    )
    Text(lesen, style = MaterialTheme.typography.bodySmall, color = grau,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    GuvDiagramm(
        xs = xs, verfall = verfallKurve, datum = datumKurve, kurs = kurs, band = e.band, breakEven = e.breakEven,
        sel = idx, onSel = { sel = it }, datumText = datumKurve?.let { S.orAmDatum(tagText) }, datumFarbe = ocker,
        modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 20.dp, vertical = 10.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Fmt.price(lo, USD), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        Text(Fmt.price(hi, USD), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
    }

    Spacer(Modifier.height(12.dp))
    KeyValueRow(if (e.einsatz >= 0) S.orKosten else S.orErloes, Fmt.moneyIn(abs(e.einsatz), USD, 0))
    KeyValueRow(S.orMaxGewinn, e.maxGewinn.wert?.let { guvText(it) } ?: S.orUnbegrenzt, valueColor = signal.up)
    KeyValueRow(S.orMaxVerlust, e.maxVerlust.wert?.let { guvText(it) } ?: S.orUnbegrenzt, valueColor = signal.down)
    e.breakEven.forEach { b ->
        KeyValueRow(S.orBreakEven, Fmt.price(b, USD) + " (" + Fmt.signedPct((b / kurs - 1) * 100, 1) + ")", onClick = { erkl = "gewinnschwelle" })
    }
    e.chance?.let { KeyValueRow(S.orChance, Fmt.pct(it * 100, 0), onClick = { erkl = "chance" }) }

    // Ins Depot: gekaufte Optionen als Optionsschein, verkaufte als Stillhalterposition, dazu das Aktienbein
    val waehrungPasst = quote != null && quote.price > 0 && quote.nativeCurrency.equals(USD, ignoreCase = true) && state.market.optionsNotierung(symbol) == null
    val plan = remember(beine, verfall, waehrungPasst) { OptionsrechnerPlan.plan(beine, verfall, System.currentTimeMillis(), waehrungPasst) }
    when (plan) {
        is OptionsrechnerPlan.Plan.Kaufbar -> {
            val einzeln = plan.teile.singleOrNull()?.takeIf { it.art != Art.AKTIE && !it.verkauft }
            FloatingPill(
                label = if (plan.teile.any { it.verkauft }) S.orAusfuehren else S.buy, plus = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (einzeln != null && onDerivat != null) {
                    // Eine gekaufte Option: Kaufseite des Scheins, Stueckzahl vorbelegt
                    scope.launch {
                        val d = OptionsrechnerPlan.optionsschein(symbol, quote?.name?.ifBlank { null } ?: name, USD, einzeln, System.currentTimeMillis(), kurs)
                        state.registerDerivatives(listOf(d))
                        state.kaufMengeMerken(d.id, einzeln.stueck)
                        onDerivat(d.id)
                    }
                } else kaufBlatt = true
            }
            if (kaufBlatt) OptionsKaufBlatt(state, symbol, quote?.name?.ifBlank { null } ?: name, USD, plan.teile, kurs) { kaufBlatt = false }
        }
        OptionsrechnerPlan.Plan.Leerverkauf ->
            Text(S.error(de.troetenrepublik.app.engine.Err.LEERVERKAUF, ""), style = MaterialTheme.typography.bodySmall, color = grau,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        OptionsrechnerPlan.Plan.Keiner -> {}
    }

    // Griechen der Gesamtposition zum gewaehlten Datum, beim aktuellen Kurs
    val g = remember(beine, rest, kurs) { Optionsrechner.griechen(beine, kurs, rest) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Delta" to Fmt.num(g.delta, 1), "Gamma" to Fmt.num(g.gamma, 2), "Theta" to Fmt.num(g.theta, 2), "Vega" to Fmt.num(g.vega, 2)).forEach { (n, w) ->
            Column(Modifier.weight(1f).clickable { erkl = n.lowercase() }) {
                // Info-Symbol zeigt, dass der Name antippbar ist und eine Erklärung öffnet
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.orGriechName(n), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(11.dp))
                }
                Text(w.replace('-', '−'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }

    SectionHeader(S.orBewegungTitel, unten = 4.dp)
    BewegungTabelle(Optionsrechner.bewegungen(kurs, kontrakte, jetzt), USD)
    ToolNote(S.opStand)

    if (datumDialog) {
        val (von, bis) = OptionsrechnerPlan.datumsgrenzen(termine, jetzt)
        val st = rememberDatePickerState(
            initialSelectedDateMillis = (if (istFrei) verfall else von) * 1000,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis / 1000 in von..bis
            },
        )
        DatePickerDialog(
            onDismissRequest = { datumDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms -> if (ms / 1000 in von..bis) waehleDatum(ms / 1000) }
                    datumDialog = false
                }) { Text(S.apply) }
            },
            dismissButton = { TextButton(onClick = { datumDialog = false }) { Text(S.cancel) } },
        ) { DatePicker(state = st, showModeToggle = false) }
    }
    erkl?.let { key ->
        AlertDialog(
            onDismissRequest = { erkl = null },
            title = { Text(when (key) { "basispreis" -> S.orBasispreis; "gewinnschwelle" -> S.orBreakEven; "chance" -> S.orChance; "delta", "gamma", "theta", "vega" -> S.orGriechName(key.replaceFirstChar { it.uppercase() }); else -> S.impliedVol }) },
            text = { Text(S.orErkl(key), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { erkl = null }) { Text(S.ok) } },
        )
    }
}

// Eine Karte je Option: Kauf oder Verkauf, Art, Basispreis und Menge einstellbar, darunter Preis und Volatilitaet.
@Composable
private fun BeinZeile(
    leg: Leg, bein: Optionsrechner.Bein?, strikes: List<Double>, berechnet: Boolean,
    onChange: (Leg) -> Unit, onArt: (Art) -> Unit, onRemove: () -> Unit, onInfo: (String) -> Unit,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (leg.menge > 0) S.orKauf else S.orVerkauf, true) { onChange(leg.copy(menge = -leg.menge)) }
            Chip(S.orArt(leg.art.name), true) {
                onArt(when (leg.art) { Art.CALL -> Art.PUT; Art.PUT -> Art.AKTIE; Art.AKTIE -> Art.CALL })
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Close, S.delete, tint = grau, modifier = Modifier.textKlick(8.dp) { onRemove() }.padding(horizontal = 8.dp, vertical = 8.dp).size(20.dp))
        }
        if (leg.art != Art.AKTIE) {
            val i = strikes.indexOf(leg.strike)
            OrStepper(
                S.orBasispreis, Fmt.price(leg.strike, USD),
                onMinus = { strikes.getOrNull(i - 1)?.let { onChange(leg.copy(strike = it)) } },
                onPlus = { strikes.getOrNull(i + 1)?.let { onChange(leg.copy(strike = it)) } },
                onLabel = { onInfo("basispreis") },
            )
        }
        val n = abs(leg.menge)
        OrStepper(
            S.orMenge, n.toString(),
            onMinus = { if (n > 1) onChange(leg.copy(menge = (n - 1) * if (leg.menge < 0) -1 else 1)) },
            onPlus = { if (n < 9) onChange(leg.copy(menge = (n + 1) * if (leg.menge < 0) -1 else 1)) },
        )
        bein?.let {
            if (it.art == Art.AKTIE) {
                Text(S.orBeinAktie(n, Fmt.price(it.preis, USD)), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(
                    S.orBeinOption(Fmt.price(it.preis, USD), Fmt.pct(it.iv * 100, 0), berechnet), style = MaterialTheme.typography.bodySmall, color = grau,
                    modifier = Modifier.textKlick(0.dp) { onInfo("iv") }.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OrStepper(label: String, wert: String, onMinus: () -> Unit, onPlus: () -> Unit, onLabel: (() -> Unit)? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = grau,
            modifier = Modifier.weight(1f).then(if (onLabel != null) Modifier.clickable { onLabel() } else Modifier),
        )
        Icon(Icons.Filled.Remove, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.textKlick(14.dp) { onMinus() }.padding(horizontal = 14.dp, vertical = 6.dp).size(20.dp))
        Text(wert, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 80.dp))
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.textKlick(14.dp) { onPlus() }.padding(horizontal = 14.dp, vertical = 6.dp).size(20.dp))
    }
}

// Erwartete Bewegung je Verfall: Straddle am Geld als Betrag und Prozent, Balken relativ zum groessten, darunter die Spanne.
@Composable
internal fun BewegungTabelle(liste: List<Optionsrechner.Bewegung>, waehrung: String) {
    if (liste.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val maxProzent = liste.maxOf { it.prozent }.coerceAtLeast(0.01)
    liste.forEach { b ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayMonth(b.verfall * 1000) + " · " + S.opTage(b.tage), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                Text(
                    "±" + Fmt.price(b.betrag, waehrung) + "  ±" + Fmt.pct(b.prozent, 1),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                )
            }
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth((b.prozent / maxProzent).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().background(Kategorie.blau))
            }
            Text(
                S.szSpanne(Fmt.price(b.von, waehrung), Fmt.price(b.bis, waehrung)),
                style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// Auszahlung ueber dem Kurs: Gewinn gruen, Verlust rot, dazu die Linie zum gewaehlten Datum, das Band von einer
// Standardabweichung, der aktuelle Kurs gestrichelt mit Beschriftung oben und die Gewinnschwellen auf der Nulllinie mit
// Beschriftung unten, jeweils in der Farbe der Marke. Beschriftungen stehen in Streifen ueber und unter den Daten.
@Composable
private fun GuvDiagramm(
    xs: List<Double>, verfall: List<Double>, datum: List<Double>?, kurs: Double, band: Pair<Double, Double>?,
    breakEven: List<Double>, sel: Int?, onSel: (Int?) -> Unit, datumText: String?, datumFarbe: Color, modifier: Modifier = Modifier,
) {
    val signal = LocalSignal.current
    val linie = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val hintergrund = MaterialTheme.colorScheme.background
    val ocker = Kategorie.ocker
    val melde by rememberUpdatedState(onSel)
    val punkte by rememberUpdatedState(xs)
    val messer = rememberTextMeasurer()
    val beschriftung = MaterialTheme.typography.labelSmall
    if (xs.size < 2) { Box(modifier); return }
    val lo = xs.first(); val hi = xs.last()
    val alle = verfall + (datum ?: emptyList())
    var mn = min(alle.min(), 0.0); var mx = max(alle.max(), 0.0)
    if (mx - mn < 1e-9) { mx += 1.0; mn -= 1.0 }
    Canvas(
        modifier.pointerInput(Unit) {
            fun index(px: Float): Int {
                val x = lo + (hi - lo) * (px / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                return punkte.indices.minByOrNull { abs(punkte[it] - x) } ?: 0
            }
            detectHorizontalDragGestures(
                onDragStart = { melde(index(it.x)) },
                onDragEnd = { melde(null) },
                onDragCancel = { melde(null) },
                onHorizontalDrag = { change, _ -> melde(index(change.position.x)) },
            )
        },
    ) {
        val streifen = 18.dp.toPx()
        val padV = streifen + size.height * 0.04f
        fun px(x: Double) = (size.width * ((x - lo) / (hi - lo))).toFloat()
        fun py(v: Double) = (padV + (size.height - 2 * padV) * (1.0 - (v - mn) / (mx - mn))).toFloat()
        val y0 = py(0.0)
        band?.let { (a, b) ->
            val l = px(max(a, lo)); val r = px(min(b, hi))
            if (r > l) drawRect(grau.copy(alpha = 0.14f), Offset(l, streifen), Size(r - l, size.height - 2 * streifen))
        }
        val flaeche = Path().apply {
            moveTo(px(xs.first()), y0)
            xs.forEachIndexed { i, x -> lineTo(px(x), py(verfall[i])) }
            lineTo(px(xs.last()), y0)
            close()
        }
        clipRect(top = 0f, bottom = y0) { drawPath(flaeche, signal.up.copy(alpha = 0.22f)) }
        clipRect(top = y0, bottom = size.height) { drawPath(flaeche, signal.down.copy(alpha = 0.22f)) }
        drawLine(grau.copy(alpha = 0.5f), Offset(0f, y0), Offset(size.width, y0), strokeWidth = 1.dp.toPx())
        fun zeichne(werte: List<Double>, farbe: Color) {
            val pfad = Path()
            werte.forEachIndexed { i, v -> if (i == 0) pfad.moveTo(px(xs[i]), py(v)) else pfad.lineTo(px(xs[i]), py(v)) }
            drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        datum?.let { zeichne(it, ocker) }
        zeichne(verfall, linie)

        // Kurs heute: gestrichelte Marke, Beschriftung im Streifen oben
        var rechtsKante = size.width
        datumText?.let { t ->
            val m = messer.measure(t, beschriftung, maxLines = 1, softWrap = false)
            val x = size.width - m.size.width
            drawText(m, datumFarbe, Offset(x, 0f))
            rechtsKante = x - 8.dp.toPx()
        }
        if (kurs in lo..hi) {
            drawLine(
                grau, Offset(px(kurs), streifen), Offset(px(kurs), size.height - streifen), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val m = messer.measure(Fmt.price(kurs, USD), beschriftung, maxLines = 1, softWrap = false)
            val x = (px(kurs) - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            if (x + m.size.width <= rechtsKante) drawText(m, grau, Offset(x, 0f))
        }

        // Gewinnschwellen: Punkt auf der Nulllinie, kurzer Strich nach unten, Beschriftung im Streifen unten
        var letzteKante = -1f
        breakEven.filter { it in lo..hi }.forEach { b ->
            drawCircle(hintergrund, radius = 5.dp.toPx(), center = Offset(px(b), y0))
            drawCircle(linie, radius = 3.dp.toPx(), center = Offset(px(b), y0))
            drawLine(linie.copy(alpha = 0.35f), Offset(px(b), y0), Offset(px(b), size.height - streifen), strokeWidth = 1.dp.toPx())
            val m = messer.measure(Fmt.price(b, USD), beschriftung, maxLines = 1, softWrap = false)
            val x = (px(b) - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            if (x > letzteKante + 4.dp.toPx()) {
                drawText(m, linie, Offset(x, size.height - streifen + 2.dp.toPx()))
                letzteKante = x + m.size.width
            }
        }
        if (sel != null && sel in xs.indices) {
            val x = px(xs[sel])
            drawLine(grau.copy(alpha = 0.6f), Offset(x, streifen), Offset(x, size.height - streifen), strokeWidth = 1.dp.toPx())
            drawCircle(hintergrund, radius = 6.dp.toPx(), center = Offset(x, py(verfall[sel])))
            drawCircle(linie, radius = 4.dp.toPx(), center = Offset(x, py(verfall[sel])))
            datum?.let {
                drawCircle(hintergrund, radius = 6.dp.toPx(), center = Offset(x, py(it[sel])))
                drawCircle(ocker, radius = 4.dp.toPx(), center = Offset(x, py(it[sel])))
            }
        }
    }
}
