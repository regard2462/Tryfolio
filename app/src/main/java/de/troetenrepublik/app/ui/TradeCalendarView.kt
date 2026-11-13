package de.troetenrepublik.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.PortfolioTools
import de.troetenrepublik.app.engine.TradeCalendar
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.i18n.KalenderTexte
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs


// Tage und blaetterbare Monate des Handelskalenders; erwartete Dividenden laden im Hintergrund nach.
// rueckblicke: Tage mit gespeichertem Tagesrueckblick; erster: Tag der ersten Buchung (Rueckblicke fuer die Vergangenheit
// lassen sich ab da erstellen).
class KalenderDaten(
    val tage: Map<LocalDate, TradeCalendar.Tag>,
    val spanne: ClosedRange<YearMonth>,
    val rueckblicke: Set<LocalDate> = emptySet(),
    val erster: LocalDate? = null,
    // Boersenlaender der gehaltenen Werte (Alarmarten.land), fuer die Boersenfeiertage
    val feiertagLaender: Set<String> = emptySet(),
)

// Daten des Handelskalenders: geschlossene Positionen und erhaltene Dividenden aus den Buchungen, dazu die
// erwarteten Dividenden der naechsten zwoelf Monate wie im Dividendenkalender von Analytics (Ausschuettungen
// der letzten zwoelf Monate fortgeschrieben, am Zahltag und nach Steuern, siehe Dividendenerwartung).
@Composable
fun rememberKalenderDaten(state: AppState, account: Account): KalenderDaten {
    val zone = remember { ZoneId.systemDefault() }
    val holdings = account.positions.filter {
        it.derivative == null && !it.isKnockedOut && Symbols.typeOf(it.symbol).let { t -> t == AssetType.STOCK || t == AssetType.ETF }
    }
    val bestand = holdings.joinToString("|") { "${it.key}:${it.qty}" }
    var erwartet by remember { mutableStateOf<List<TradeCalendar.Dividende>>(emptyList()) }
    var exQuelle by remember { mutableStateOf<ExQuelle?>(null) }
    LaunchedEffect(bestand, account.tax) {
        val now = System.currentTimeMillis()
        val quellen = coroutineScope {
            holdings.map { p -> async { p to dividendenQuelle(state, p.key, p.name, p.symbol, p.qty) } }.awaitAll()
        }
        val symbolVon = quellen.associate { (p, q) -> q.key to p.symbol }
        val liste = PortfolioTools.expectedDividends(quellen.map { it.second }, now)
        val zahlungen = de.troetenrepublik.app.engine.Dividendenerwartung.fuerKonto(liste, account.tax, state.settingsFlow.value.allowance, now)
        exQuelle = ExQuelle(
            quellen.associate { (_, q) -> q.key to q.events.map { it.first } },
            zahlungen.map { e -> Triple(symbolVon[e.key].orEmpty().ifBlank { e.key }, e.name, e.exTag) },
        )
        erwartet = zahlungen.map { e ->
            TradeCalendar.Dividende(
                key = e.key, name = e.name, symbol = symbolVon[e.key].orEmpty(), ts = e.zahltag,
                brutto = e.brutto, steuer = e.brutto - e.netto, stueck = holdings.firstOrNull { it.key == e.key }?.qty ?: 0.0,
                jeStueck = 0.0, erwartet = true,
            )
        }
    }
    // Insider-Geschaefte der gehaltenen Aktien aus den gespeicherten Staenden, im Hintergrund; der Kalender steht sofort
    val aktien = holdings.filter { Symbols.typeOf(it.symbol) == AssetType.STOCK }
    val aktienSchluessel = aktien.joinToString("|") { it.symbol }
    var insider by remember { mutableStateOf<List<TradeCalendar.Insider>>(emptyList()) }
    LaunchedEffect(aktienSchluessel) { insider = runCatching { insiderLaden(state, aktien) }.getOrDefault(emptyList()) }
    val zusatz = rememberKalenderZusatz(state, account, aktien, exQuelle)
    val feiertage = remember(bestand) { feiertagLaender(holdings.map { it.symbol }) }
    val buchungen = account.transactions
    LaunchedEffect(Unit) { runCatching { state.rueckblickeLaden() } }
    val archiv by state.rueckblicke.collectAsStateWithLifecycle()
    return remember(buchungen, erwartet, archiv, insider, zusatz, feiertage) {
        val relevant = buchungen.filter { TradeCalendar.schliesst(it) || it.type == TxType.DIVIDEND }
        KalenderDaten(
            TradeCalendar.tage(relevant, erwartet, zone, insider, zusatz),
            TradeCalendar.spanne(buchungen, erwartet, YearMonth.now(zone), zone),
            archiv?.tage.orEmpty().mapTo(HashSet()) { LocalDate.ofEpochDay(it.tag) },
            TradeCalendar.ersterTag(buchungen, zone),
            feiertage,
        )
    }
}

// Gespeicherte Staende (SEC, BaFin, Register weiterer Laender) ohne Netzabruf, zugeordnet zu den gehaltenen Aktien.
private suspend fun insiderLaden(state: AppState, aktien: List<Position>): List<TradeCalendar.Insider> = withContext(Dispatchers.Default) {
    if (aktien.isEmpty()) return@withContext emptyList()
    val kurse = state.quotes.value
    val gehalten = aktien.map { p ->
        TradeCalendar.Aktie(
            p.symbol, p.name, state.isinFor(p.symbol), istUsAktie(p.symbol, kurse[p.symbol]?.nativeCurrency),
            listOfNotNull(Symbols.find(p.symbol)?.name, p.name, kurse[p.symbol]?.name?.takeIf { it.isNotBlank() }).distinct(),
        )
    }
    val bafin = runCatching { state.bafinLaden(netz = false) }.getOrNull()
    val sec = if (gehalten.any { it.us }) runCatching { state.secLaden(netz = false) }.getOrNull() else null
    val laender = LinkedHashSet<String>(EuInsider.LAENDER)
    gehalten.forEach { a ->
        InsiderWelt.ort(a.symbol, a.isin)?.first?.let { laender += it }
        if (a.isin.length == 12 && !a.isin.startsWith("US") && !a.isin.startsWith("DE")) laender += a.isin.take(2).uppercase()
    }
    val eu = laender.mapNotNull { l -> runCatching { state.euLaden(l, netz = false) }.getOrNull()?.let { l to it.geschaefte } }.toMap()
    TradeCalendar.insiderFuerDepot(gehalten, sec?.geschaefte.orEmpty(), eu, bafin?.eigengeschaefte.orEmpty())
}

// Kalendersymbol rechts neben „Max“: wechselt vom Chart in die Monatsansicht.
@Composable
fun CalendarToggle(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = S.calToggle },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(22.dp),
            tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun monatsName(ym: YearMonth): String = ym.format(DateTimeFormatter.ofPattern("MMMM", I18n.locale))

internal fun monatUndJahr(ym: YearMonth): String = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale))

internal fun tagTitel(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern(if (I18n.lang == Lang.DE) "EEEE, d. MMMM yyyy" else "EEEE, d MMMM yyyy", I18n.locale))

// Prozent fuer ein Kalenderfeld: kurz, eine Nachkommastelle, ab 100 ohne.
private fun feldProzent(v: Double): String = Fmt.signedPct(v, if (abs(v) >= 100) 0 else 1)

// Monatsansicht des Handelskalenders: Tage mit geschlossenen Positionen gruen oder rot (kraeftiger bei
// grossen Ergebnissen) mit dem Tagesergebnis in Prozent, Dividenden als goldener Punkt (gefuellt erhalten,
// hohl erwartet), heute umrandet. Darunter die Summen des Monats. Ein Tipp auf einen Tag zeigt die Einzelheiten.
@Composable
fun TradeCalendarView(
    daten: KalenderDaten,
    monat: YearMonth,
    onMonat: (YearMonth) -> Unit,
    hideBalances: Boolean,
    state: AppState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val signal = LocalSignal.current
    val heute = remember { LocalDate.now(ZoneId.systemDefault()) }
    var offen by remember { mutableStateOf<LocalDate?>(null) }
    // Ausgeblendete Markierungen (gemerkt) und Makrotermine des angezeigten Monats, die erst beim Blaettern ankommen
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val aus = remember(einstellungen.kalenderAus) { TradeCalendar.Art.lesen(einstellungen.kalenderAus) }
    var makro by remember { mutableStateOf<Map<YearMonth, List<TradeCalendar.Makro>>>(emptyMap()) }
    val makroAn = TradeCalendar.Art.MAKRO !in aus
    LaunchedEffect(monat, makroAn) {
        if (!makroAn || monat in makro || state.nasdaqPausiert()) return@LaunchedEffect
        val tage = (1..monat.lengthOfMonth()).map { monat.atDay(it) }.filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        val antwort = ohneAbbruch { state.nasdaqWirtschaft(tage) }.getOrNull() ?: return@LaunchedEffect
        val termine = antwort.values.filterNotNull().flatten()
        if (antwort.values.any { it != null }) makro = makro + (monat to TradeCalendar.makro(termine))
    }
    val gesamt = remember(daten, makro) { KalenderDaten(TradeCalendar.mitMakro(daten.tage, makro.values.flatten()), daten.spanne, daten.rueckblicke, daten.erster, daten.feiertagLaender) }
    val summe = remember(gesamt, monat) { TradeCalendar.monat(gesamt.tage, monat) }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monatUndJahr(monat), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowLeft, S.calPrevMonth, enabled = monat > gesamt.spanne.start) { onMonat(monat.minusMonths(1)) }
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowRight, S.calNextMonth, enabled = monat < gesamt.spanne.endInclusive) { onMonat(monat.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            S.calWeekdays.forEachIndexed { i, w ->
                Text(
                    w, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (i >= 5) 0.6f else 1f),
                )
            }
        }
        AnimatedContent(
            targetState = monat,
            transitionSpec = {
                val vor = targetState > initialState
                (slideInHorizontally(tween(260)) { w -> if (vor) w / 3 else -w / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(220)) { w -> if (vor) -w / 3 else w / 3 } + fadeOut(tween(180)))
            },
            label = "monat",
        ) { ym ->
            MonatsRaster(ym, gesamt, heute, signal, aus) { offen = it }
        }
        // Summen des Monats
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Summe(
                S.calRealized,
                if (hideBalances) "•••• " + Fmt.symbol else Fmt.signedMoney(summe.realisiert),
                if (summe.trades > 0 && !hideBalances) signal.forChange(summe.realisiert) else MaterialTheme.colorScheme.onSurface,
                null, Modifier.weight(1f),
            )
            Summe(
                S.calTrades, summe.trades.toString(), MaterialTheme.colorScheme.onSurface,
                null, Modifier.weight(1f),
                unten = if (summe.trades > 0) ({ PlusMinus(summe.gewinne, summe.verluste, signal) }) else null,
            )
            // Erhaltene Dividenden in Gold; in Monaten ohne Zahlung, aber mit erwarteter, die erwartete in Grau
            val nurErwartet = summe.dividendenErhalten <= 0 && summe.dividendenErwartet > 0
            Summe(
                S.widgetDividenden,
                when {
                    hideBalances -> "•••• " + Fmt.symbol
                    nurErwartet -> Fmt.money(summe.dividendenErwartet)
                    else -> Fmt.money(summe.dividendenErhalten)
                },
                when {
                    nurErwartet -> MaterialTheme.colorScheme.onSurfaceVariant
                    summe.dividendenErhalten > 0 -> LocalSignal.current.warn
                    else -> MaterialTheme.colorScheme.onSurface
                },
                when {
                    nurErwartet -> S.calDivExpected
                    summe.dividendenErwartet > 0 && !hideBalances -> S.calExpectedAmount(Fmt.money(summe.dividendenErwartet))
                    else -> null
                },
                Modifier.weight(1f),
            )
        }
        KalenderLegende(aus) { art, an -> state.kalenderArtSchalten(art, an) }
    }

    // Nach dem geoeffneten Wert wieder der Tag
    val rueck = rememberWiederkehr()
    offen?.let { d ->
        val tag = gesamt.tage[d] ?: TradeCalendar.Tag(d, emptyList(), emptyList())
        val rueckblick = d in gesamt.rueckblicke || TradeCalendar.rueckblickMoeglich(d, gesamt.erster, heute)
        WiederkehrBlatt("kalendertag", rueck) { TagesBlatt(tag, hideBalances, state, rueckblick, onDismiss = { offen = null; rueck.vergessen() }, onOpen = { rueck.verlassen(); onOpen(it) }, sofort = rueck.sofort) }
        BlattBild(rueck, "kalendertag") { TagesInhalt(tag, hideBalances, state, rueckblick, onOpen = {}) }
    }
}

@Composable
internal fun MonatsKnopf(icon: androidx.compose.ui.graphics.vector.ImageVector, beschreibung: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f))
    }
}

@Composable
internal fun Summe(titel: String, wert: String, farbe: Color, zusatz: String?, modifier: Modifier, unten: (@Composable () -> Unit)? = null) {
    Column(modifier) {
        Text(titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.titleSmall, color = farbe, modifier = Modifier.padding(top = 2.dp))
        if (zusatz != null) {
            // Zwei Zeilen, damit „erwartet, vor Steuern“ in der schmalen dritten Spalte nicht abgeschnitten wird
            Beschriftung(zusatz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        unten?.invoke()
    }
}

// Gewinner und Verlierer des Monats in ihren Farben; wird es eng, rutscht der zweite Eintrag als Ganzes in die naechste Zeile
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlusMinus(plus: Int, minus: Int, signal: Signal) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(S.calWins(plus), style = MaterialTheme.typography.labelSmall, color = if (plus > 0) signal.up else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text(S.calLosses(minus), style = MaterialTheme.typography.labelSmall, color = if (minus > 0) signal.down else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

// Raster eines Monats: Wochen von Montag bis Sonntag, Tage des Vor- und Folgemonats blass.
@Composable
private fun MonatsRaster(ym: YearMonth, gesamt: KalenderDaten, heute: LocalDate, signal: Signal, aus: Set<TradeCalendar.Art>, onTag: (LocalDate) -> Unit) {
    val erster = ym.atDay(1)
    val start = erster.minusDays((erster.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val wochen = ((erster.dayOfWeek.value - 1) + ym.lengthOfMonth() + 6) / 7
    // Faerbung relativ zum groessten Tagesergebnis des Monats
    val groesster = gesamt.tage.values.filter { YearMonth.from(it.datum) == ym && it.hatTrades }.maxOfOrNull { abs(it.realisiert) } ?: 0.0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(wochen) { w ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { i ->
                    val d = start.plusDays((w * 7 + i).toLong())
                    TagesFeld(
                        datum = d,
                        imMonat = YearMonth.from(d) == ym,
                        tag = gesamt.tage[d],
                        arten = TradeCalendar.marken(gesamt.tage[d], d in gesamt.rueckblicke, aus),
                        feiertag = TradeCalendar.Art.FEIERTAG !in aus && TradeCalendar.feiertag(d, gesamt.feiertagLaender),
                        antippbar = d in gesamt.rueckblicke || TradeCalendar.rueckblickMoeglich(d, gesamt.erster, heute),
                        heute = heute,
                        groesster = groesster,
                        signal = signal,
                        modifier = Modifier.weight(1f),
                        onClick = onTag,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagesFeld(
    datum: LocalDate,
    imMonat: Boolean,
    tag: TradeCalendar.Tag?,
    arten: List<TradeCalendar.Art>,
    feiertag: Boolean,
    antippbar: Boolean,
    heute: LocalDate,
    groesster: Double,
    signal: Signal,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit,
) {
    val form = RoundedCornerShape(12.dp)
    val handel = imMonat && tag?.hatTrades == true
    val farbe = if (handel) signal.forChange(tag!!.realisiert).takeIf { abs(tag.realisiert) >= 0.005 } ?: MaterialTheme.colorScheme.onSurfaceVariant else null
    val staerke = if (handel) TradeCalendar.staerke(tag!!.realisiert, groesster) else 0f
    val istHeute = datum == heute
    val wochenende = datum.dayOfWeek == DayOfWeek.SATURDAY || datum.dayOfWeek == DayOfWeek.SUNDAY
    val zukunft = datum.isAfter(heute)
    val klickbar = imMonat && (tag != null || antippbar)
    Box(
        modifier
            .height(50.dp)
            .clip(form)
            .then(
                when {
                    farbe != null -> Modifier
                        .background(farbe.copy(alpha = 0.08f + 0.22f * staerke))
                        .border(BorderStroke(1.dp, farbe.copy(alpha = 0.3f + 0.45f * staerke)), form)
                    istHeute && imMonat -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, form)
                    else -> Modifier
                },
            )
            .clickable(enabled = klickbar) { onClick(datum) }
            .alpha(if (!imMonat) 0.28f else if (feiertag) 0.45f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                datum.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (istHeute || handel) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    farbe != null -> farbe
                    zukunft || wochenende -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (handel) {
                Text(
                    feldProzent(tag!!.prozent), color = farbe ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                )
            }
        }
        if (imMonat) MarkenZeile(arten, tag, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
    }
}

// Einzelheiten eines Tags: Zahl der geschlossenen Trades, realisiertes Ergebnis in Euro und Prozent, Gebuehren,
// Steuern und was danach bleibt; darunter die einzelnen Positionen und die Dividenden des Tags.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagesBlatt(tag: TradeCalendar.Tag, hideBalances: Boolean, state: AppState, rueckblick: Boolean, onDismiss: () -> Unit, onOpen: (String) -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        TagesInhalt(tag, hideBalances, state, rueckblick, onOpen)
    }
}

@Composable
private fun TagesInhalt(tag: TradeCalendar.Tag, hideBalances: Boolean, state: AppState, rueckblick: Boolean, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    fun geld(v: Double, mitVorzeichen: Boolean = false) = if (hideBalances) "•••• " + Fmt.symbol else if (mitVorzeichen) Fmt.signedMoney(v) else Fmt.money(v)
    run {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(tagTitel(tag.datum), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            if (tag.hatTrades) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 6.dp),
                ) {
                    KeyValueRow(S.calClosedTrades, tag.trades.size.toString())
                    KeyValueRow(S.calRealized, geld(tag.realisiert, true), valueColor = if (hideBalances) null else signal.forChange(tag.realisiert))
                    KeyValueRow(S.calDayResult, Fmt.signedPct(tag.prozent), valueColor = signal.forChange(tag.prozent))
                    if (tag.gebuehren > 0) KeyValueRow(S.calFees, geld(tag.gebuehren))
                    if (tag.steuern > 0.005) KeyValueRow(S.taxes, geld(tag.steuern))
                    KeyValueRow(S.calAfterCosts, geld(tag.realisiert - tag.gebuehren - tag.steuern, true))
                }
                SectionHeader(S.calPositions)
                tag.trades.forEach { t ->
                    val tx = t.tx
                    val art = when (tx.type) {
                        TxType.KNOCKOUT -> S.knockOut
                        TxType.EXPIRY -> S.calTradeExpiry
                        TxType.BUY -> S.shGeschaeft(false) + " · " + S.dvQtyAt(Fmt.qty(tx.qty), Fmt.price(tx.price))
                        else -> S.calTradeSell(Fmt.qty(tx.qty), Fmt.price(tx.price))
                    }
                    TradeZeile(state, tx.symbol, tx.title, art, geld(t.realisiert, true), Fmt.signedPct(t.prozent), signal.forChange(t.realisiert)) { if (!tx.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) onOpen(tx.symbol) }
                }
            }
            // Abschnitte in der Reihenfolge der Markierungen
            if (rueckblick) KalenderRueckblick(tag.datum, state, onOpen)
            ZahlenAbschnitt(tag, state, onOpen)
            if (tag.insider.isNotEmpty()) InsiderAbschnitt(tag, state, onOpen)
            KongressAbschnitt(tag, state, onOpen)
            VerfallAbschnitt(tag, state, onOpen)
            ExDivAbschnitt(tag, state, onOpen)
            if (tag.dividenden.isNotEmpty()) {
                SectionHeader(S.widgetDividenden)
                tag.dividenden.forEach { d ->
                    val zeile = if (d.erwartet) S.calDivExpected else S.calDivReceived(Fmt.qty(d.stueck), Fmt.price(d.jeStueck))
                    TradeZeile(
                        state, d.symbol, d.name, zeile, geld(d.netto, !d.erwartet),
                        null, if (d.erwartet) MaterialTheme.colorScheme.onSurfaceVariant else LocalSignal.current.warn,
                    ) { if (d.symbol.isNotBlank() && !d.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) onOpen(d.symbol) }
                }
            }
            StufungAbschnitt(tag, state, onOpen)
            KaufAbschnitt(tag, state, hideBalances, onOpen)
            MakroAbschnitt(tag, onOpen)
        }
    }
}

// Insider-Geschaefte des Tags bei gehaltenen Aktien, eine Zeile je Geschaeft; ab sechs eingeklappt mit „Alle n“.
@Composable
private fun InsiderAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    val geld = rememberInsiderGeld(state)
    var alle by remember(tag.datum) { mutableStateOf(false) }
    val liste = remember(tag) { tag.insider.sortedWith(compareByDescending<TradeCalendar.Insider> { it.wert }.thenBy { it.symbol }) }
    SectionHeader(KalenderTexte.t("insider"))
    (if (alle || liste.size <= 5) liste else liste.take(5)).forEach { g ->
        val (betrag, orig) = if (g.wert > 0) geld.text(g.wert, g.waehrung) else "–" to null
        val rolle = g.rolle.ifBlank {
            when (g.quelle) { "US" -> S.usRolle(g.rolleCode); "EU" -> S.euRolle(g.rolleCode); else -> "" }
        }
        InsiderZeile(
            name = kurzName(g.symbol, g.name),
            angaben = listOf(g.person, listOf(KalenderTexte.t(if (g.kauf) "insider.kauf" else "insider.verkauf"), rolle).filter { it.isNotBlank() }.joinToString(" · ")),
            kauf = g.kauf, verkauf = !g.kauf, betrag = betrag, datum = "", zusatz = orig,
            onClick = { if (!g.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) onOpen(g.symbol) },
        )
    }
    if (liste.size > 5) {
        Text(
            if (alle) S.less else S.bbAlle(liste.size), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(20.dp) { alle = !alle }.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun TradeZeile(state: AppState, symbol: String, name: String, unter: String, betrag: String, prozent: String?, farbe: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, symbol, name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, style = MaterialTheme.typography.titleSmall)
            PunktText(unter)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (betrag.isNotEmpty()) Text(betrag, style = MaterialTheme.typography.titleSmall, color = farbe, maxLines = 1)
            if (prozent != null) Text(prozent, style = MaterialTheme.typography.bodySmall, color = farbe, maxLines = 1)
        }
    }
}

// Tagesrueckblick eines Tags im Tagesblatt: der gespeicherte, bei einem vergangenen Handelstag sonst nachtraeglich erstellt.
@Composable
private fun KalenderRueckblick(datum: LocalDate, state: AppState, onOpen: (String) -> Unit) {
    val tag = datum.toEpochDay()
    var fertig by remember(tag) { mutableStateOf(false) }
    var rueckblick by remember(tag) { mutableStateOf<de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick?>(null) }
    // Gemerkt, damit der Artikel nach einer aus ihm geoeffneten Seite mit dem Tagesblatt wiederkommt
    var nachricht by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<de.troetenrepublik.app.engine.Tagesrueckblick.Nachricht?, String>(
        save = { de.troetenrepublik.app.engine.Blattzustand.nachrichtFelder(it) }, restore = { de.troetenrepublik.app.engine.Blattzustand.nachrichtLesen(it) },
    )) { mutableStateOf<de.troetenrepublik.app.engine.Tagesrueckblick.Nachricht?>(null) }
    LaunchedEffect(tag) {
        rueckblick = runCatching { state.rueckblickFuerTag(tag) }.getOrNull()
        fertig = true
    }
    SectionHeader(S.rbTitel)
    val r = rueckblick
    when {
        r != null -> RueckblickTagKarte(r, onOpen) { nachricht = it }
        !fertig -> Text(S.calReviewLaedt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        else -> Text(S.calReviewKeineKurse, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    }
    nachricht?.let { n -> NachrichtBlatt(state, rueckblickEintrag(state, n), onOpen = onOpen, onDismiss = { nachricht = null }) }
}
