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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Farbe der Dividenden im Kalender (Punkt und Betraege), getrennt von Gruen und Rot.
private val DIVIDENDE = Color(0xFFF5C33B)

// Tage und blaetterbare Monate des Handelskalenders; erwartete Dividenden laden im Hintergrund nach.
class KalenderDaten(val tage: Map<LocalDate, TradeCalendar.Tag>, val spanne: ClosedRange<YearMonth>)

// Daten des Handelskalenders: geschlossene Positionen und erhaltene Dividenden aus den Buchungen, dazu die
// erwarteten Dividenden der naechsten zwoelf Monate wie im Dividendenkalender von Analytics (Ausschuettungen
// der letzten zwoelf Monate fortgeschrieben, am erwarteten Zahltag drei Tage nach dem Ex-Tag).
@Composable
fun rememberKalenderDaten(state: AppState, account: Account): KalenderDaten {
    val zone = remember { ZoneId.systemDefault() }
    val holdings = account.positions.filter {
        it.derivative == null && !it.isKnockedOut && Symbols.typeOf(it.symbol).let { t -> t == AssetType.STOCK || t == AssetType.ETF }
    }
    val bestand = holdings.joinToString("|") { "${it.key}:${it.qty}" }
    var erwartet by remember { mutableStateOf<List<TradeCalendar.Dividende>>(emptyList()) }
    LaunchedEffect(bestand) {
        val now = System.currentTimeMillis()
        val quellen = coroutineScope {
            holdings.map { p ->
                async {
                    val events = runCatching { state.dividendsFor(p.symbol) }.getOrDefault(emptyList())
                    p to PortfolioTools.DividendSource(p.key, p.name, p.qty, events.map { it.exDate to it.amount })
                }
            }.awaitAll()
        }
        val symbolVon = quellen.associate { (p, q) -> q.key to p.symbol }
        erwartet = PortfolioTools.expectedDividends(quellen.map { it.second }, now).map { e ->
            TradeCalendar.Dividende(
                key = e.key, name = e.name, symbol = symbolVon[e.key].orEmpty(), ts = e.date + ZAHLTAG_MS,
                brutto = e.amount, steuer = 0.0, stueck = holdings.firstOrNull { it.key == e.key }?.qty ?: 0.0,
                jeStueck = 0.0, erwartet = true,
            )
        }
    }
    val buchungen = account.transactions
    return remember(buchungen, erwartet) {
        val relevant = buchungen.filter { TradeCalendar.schliesst(it) || it.type == TxType.DIVIDEND }
        KalenderDaten(
            TradeCalendar.tage(relevant, erwartet, zone),
            TradeCalendar.spanne(buchungen, erwartet, YearMonth.now(zone), zone),
        )
    }
}

// Abstand zwischen Ex-Tag und Gutschrift, wie bei der Buchung erhaltener Dividenden.
private const val ZAHLTAG_MS = 3L * 86_400_000L

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
    val summe = remember(daten, monat) { TradeCalendar.monat(daten.tage, monat) }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monatUndJahr(monat), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowLeft, S.calPrevMonth, enabled = monat > daten.spanne.start) { onMonat(monat.minusMonths(1)) }
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowRight, S.calNextMonth, enabled = monat < daten.spanne.endInclusive) { onMonat(monat.plusMonths(1)) }
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
            MonatsRaster(ym, daten, heute, signal) { offen = it }
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
                if (summe.trades > 0) S.calWinsLosses(summe.gewinne, summe.verluste) else null, Modifier.weight(1f),
            )
            // Erhaltene Dividenden in Gold; in Monaten ohne Zahlung, aber mit erwarteter, die erwartete in Grau
            val nurErwartet = summe.dividendenErhalten <= 0 && summe.dividendenErwartet > 0
            Summe(
                S.calDividends,
                when {
                    hideBalances -> "•••• " + Fmt.symbol
                    nurErwartet -> Fmt.money(summe.dividendenErwartet)
                    else -> Fmt.money(summe.dividendenErhalten)
                },
                when {
                    nurErwartet -> MaterialTheme.colorScheme.onSurfaceVariant
                    summe.dividendenErhalten > 0 -> DIVIDENDE
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
        Row(Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DividendenPunkt(erwartet = false)
            Text(S.calLegendReceived, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, end = 16.dp))
            DividendenPunkt(erwartet = true)
            Text(S.calLegendExpected, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }

    // Nach dem geoeffneten Wert wieder der Tag
    val rueck = rememberWiederkehr()
    offen?.let { d ->
        daten.tage[d]?.let { tag ->
            if (!rueck.wartet) TagesBlatt(tag, hideBalances, state, onDismiss = { offen = null; rueck.vergessen() }, onOpen = { rueck.verlassen(); onOpen(it) }, sofort = rueck.sofort)
            BlattBild(rueck, "kalendertag") { TagesInhalt(tag, hideBalances, state, onOpen = {}) }
        }
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
private fun DividendenPunkt(erwartet: Boolean, groesse: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(
        Modifier
            .size(groesse)
            .clip(CircleShape)
            .then(if (erwartet) Modifier.border(1.5.dp, DIVIDENDE, CircleShape) else Modifier.background(DIVIDENDE)),
    )
}

@Composable
internal fun Summe(titel: String, wert: String, farbe: Color, zusatz: String?, modifier: Modifier) {
    Column(modifier) {
        Text(titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(wert, style = MaterialTheme.typography.titleSmall, color = farbe, modifier = Modifier.padding(top = 2.dp))
        if (zusatz != null) {
            // Zwei Zeilen, damit „erwartet, vor Steuern“ in der schmalen dritten Spalte nicht abgeschnitten wird
            Text(zusatz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Raster eines Monats: Wochen von Montag bis Sonntag, Tage des Vor- und Folgemonats blass.
@Composable
private fun MonatsRaster(ym: YearMonth, daten: KalenderDaten, heute: LocalDate, signal: Signal, onTag: (LocalDate) -> Unit) {
    val erster = ym.atDay(1)
    val start = erster.minusDays((erster.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val wochen = ((erster.dayOfWeek.value - 1) + ym.lengthOfMonth() + 6) / 7
    // Faerbung relativ zum groessten Tagesergebnis des Monats
    val groesster = daten.tage.values.filter { YearMonth.from(it.datum) == ym && it.hatTrades }.maxOfOrNull { abs(it.realisiert) } ?: 0.0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(wochen) { w ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { i ->
                    val d = start.plusDays((w * 7 + i).toLong())
                    TagesFeld(
                        datum = d,
                        imMonat = YearMonth.from(d) == ym,
                        tag = daten.tage[d],
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
    val klickbar = imMonat && tag != null
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
            .alpha(if (!imMonat) 0.28f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                datum.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (istHeute || handel) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    farbe != null -> farbe
                    zukunft || wochenende -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (zukunft) 0.6f else 0.85f)
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
        if (imMonat && tag != null && tag.dividenden.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp)) {
                DividendenPunkt(erwartet = tag.dividenden.all { it.erwartet }, groesse = 6.dp)
            }
        }
    }
}

// Einzelheiten eines Tags: Zahl der geschlossenen Trades, realisiertes Ergebnis in Euro und Prozent, Gebuehren,
// Steuern und was danach bleibt; darunter die einzelnen Positionen und die Dividenden des Tags.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagesBlatt(tag: TradeCalendar.Tag, hideBalances: Boolean, state: AppState, onDismiss: () -> Unit, onOpen: (String) -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        TagesInhalt(tag, hideBalances, state, onOpen)
    }
}

@Composable
private fun TagesInhalt(tag: TradeCalendar.Tag, hideBalances: Boolean, state: AppState, onOpen: (String) -> Unit) {
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
                    if (tag.steuern > 0.005) KeyValueRow(S.calTaxes, geld(tag.steuern))
                    KeyValueRow(S.calAfterCosts, geld(tag.realisiert - tag.gebuehren - tag.steuern, true))
                }
                SectionHeader(S.calPositions)
                tag.trades.forEach { t ->
                    val tx = t.tx
                    val art = when (tx.type) {
                        TxType.KNOCKOUT -> S.calTradeKnockout
                        TxType.EXPIRY -> S.calTradeExpiry
                        else -> S.calTradeSell(Fmt.qty(tx.qty), Fmt.price(tx.price))
                    }
                    TradeZeile(state, tx.symbol, tx.title, art, geld(t.realisiert, true), Fmt.signedPct(t.prozent), signal.forChange(t.realisiert)) { if (!tx.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) onOpen(tx.symbol) }
                }
            }
            if (tag.dividenden.isNotEmpty()) {
                SectionHeader(S.calDividends)
                tag.dividenden.forEach { d ->
                    val zeile = if (d.erwartet) S.calDivExpected else S.calDivReceived(Fmt.qty(d.stueck), Fmt.price(d.jeStueck))
                    TradeZeile(
                        state, d.symbol, d.name, zeile, geld(if (d.erwartet) d.brutto else d.netto, !d.erwartet),
                        null, if (d.erwartet) MaterialTheme.colorScheme.onSurfaceVariant else DIVIDENDE,
                    ) { if (d.symbol.isNotBlank() && !d.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) onOpen(d.symbol) }
                }
            }
        }
    }
}

@Composable
private fun TradeZeile(state: AppState, symbol: String, name: String, unter: String, betrag: String, prozent: String?, farbe: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, symbol, name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, style = MaterialTheme.typography.titleSmall)
            Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(betrag, style = MaterialTheme.typography.titleSmall, color = farbe, maxLines = 1)
            if (prozent != null) Text(prozent, style = MaterialTheme.typography.bodySmall, color = farbe, maxLines = 1)
        }
    }
}
