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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.TradeCalendar
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Handelstage
import de.troetenrepublik.app.market.Kongress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Kalender der gemeldeten Geschaefte einer Person: gruen bei mehr Kaeufen, rot bei mehr Verkaeufen, kraeftiger
// bei groesseren Betraegen, darin die Zahl der Geschaefte. Ein Tipp auf einen Tag zeigt jedes Geschaeft.
@Composable
fun GeschaeftsKalender(state: AppState, geschaefte: List<Kongress.Geschaeft>, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val tage = remember(geschaefte) { Handelstage.tage(geschaefte) }
    val heute = remember { LocalDate.now(ZoneId.systemDefault()) }
    val spanne = remember(tage) { Handelstage.spanne(tage, YearMonth.from(heute)) }
    // Beginn im letzten Monat mit Geschaeften; dort steht das Neueste
    var monatText by rememberSaveable(spanne.endInclusive.toString()) { mutableStateOf(spanne.endInclusive.toString()) }
    val monat = runCatching { YearMonth.parse(monatText) }.getOrDefault(spanne.endInclusive).let { minOf(maxOf(it, spanne.start), spanne.endInclusive) }
    var offen by remember { mutableStateOf<LocalDate?>(null) }
    val summe = remember(tage, monat) { Handelstage.monat(tage, monat) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monatUndJahr(monat), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowLeft, S.calPrevMonth, enabled = monat > spanne.start) { monatText = monat.minusMonths(1).toString() }
            MonatsKnopf(Icons.AutoMirrored.Filled.KeyboardArrowRight, S.calNextMonth, enabled = monat < spanne.endInclusive) { monatText = monat.plusMonths(1).toString() }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            S.calWeekdays.forEachIndexed { i, w ->
                Text(
                    w, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
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
            label = "geschaeftsmonat",
        ) { ym ->
            GeschaeftsRaster(ym, tage, heute, signal) { offen = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Summe(
                S.ctcBuys, Fmt.num(summe.kaeufe.toDouble(), 0),
                if (summe.kaeufe > 0) signal.up else MaterialTheme.colorScheme.onSurface,
                if (summe.kaeufe > 0) S.ctcApprox(Fmt.dollarKompakt(summe.kaufSumme)) else null, Modifier.weight(1f),
            )
            Summe(
                S.ctcSells, Fmt.num(summe.verkaeufe.toDouble(), 0),
                if (summe.verkaeufe > 0) signal.down else MaterialTheme.colorScheme.onSurface,
                if (summe.verkaeufe > 0) S.ctcApprox(Fmt.dollarKompakt(summe.verkaufSumme)) else null, Modifier.weight(1f),
            )
            Summe(S.ctcDays, Fmt.num(summe.tage.toDouble(), 0), MaterialTheme.colorScheme.onSurface, S.copyTradesCount(summe.geschaefte), Modifier.weight(1f))
        }
        AufklappText(if (summe.geschaefte == 0) S.ctcEmpty + " " + S.ctcHint else S.ctcHint, Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp))
    }

    // Nach der Rueckkehr von einem geoeffneten Wert ist der Tag wieder offen
    val rueck = rememberWiederkehr()
    offen?.let { d ->
        tage[d]?.let { tag ->
            if (!rueck.wartet) TagesGeschaefte(state, tag, onDismiss = { offen = null; rueck.vergessen() }, onOpen = { rueck.verlassen(); onOpen(it) }, sofort = rueck.sofort)
            BlattBild(rueck, "geschaeftstag") { TagesGeschaefteInhalt(state, tag, onOpen = {}) }
        }
    }
}

@Composable
private fun GeschaeftsRaster(ym: YearMonth, tage: Map<LocalDate, Handelstage.Tag>, heute: LocalDate, signal: Signal, onTag: (LocalDate) -> Unit) {
    val erster = ym.atDay(1)
    val start = erster.minusDays((erster.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val wochen = ((erster.dayOfWeek.value - 1) + ym.lengthOfMonth() + 6) / 7
    // Faerbung relativ zum groessten Tagesbetrag des Monats
    val groesster = tage.values.filter { YearMonth.from(it.datum) == ym }.maxOfOrNull { it.volumen } ?: 0.0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(wochen) { w ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { i ->
                    val d = start.plusDays((w * 7 + i).toLong())
                    val imMonat = YearMonth.from(d) == ym
                    val tag = if (imMonat) tage[d] else null
                    val form = RoundedCornerShape(12.dp)
                    val farbe = tag?.let {
                        when {
                            it.netto > 0.5 -> signal.up
                            it.netto < -0.5 -> signal.down
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                    val staerke = if (tag != null) TradeCalendar.staerke(tag.volumen, groesster) else 0f
                    val istHeute = d == heute
                    val wochenende = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                    Box(
                        Modifier
                            .weight(1f)
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
                            .clickable(enabled = tag != null) { onTag(d) }
                            .alpha(if (!imMonat) 0.28f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (tag != null || istHeute) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    farbe != null -> farbe
                                    d.isAfter(heute) || wochenende -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (d.isAfter(heute)) 0.6f else 0.85f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (tag != null) Text(
                                Fmt.num(tag.geschaefte.size.toDouble(), 0), color = farbe ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Alle Geschaefte eines Tags: Zahl, Kaeufe und Verkaeufe mit geschaetzten Betraegen, darunter jedes einzelne.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagesGeschaefte(state: AppState, tag: Handelstage.Tag, onDismiss: () -> Unit, onOpen: (String) -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        TagesGeschaefteInhalt(state, tag, onOpen)
    }
}

@Composable
private fun TagesGeschaefteInhalt(state: AppState, tag: Handelstage.Tag, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    run {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Text(tagTitel(tag.datum), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 6.dp),
                ) {
                    KeyValueRow(S.copyTrades, Fmt.num(tag.geschaefte.size.toDouble(), 0))
                    if (tag.kaeufe > 0) KeyValueRow(S.ctcBuys, S.ctcCountApprox(Fmt.num(tag.kaeufe.toDouble(), 0), Fmt.dollarKompakt(tag.kaufSumme)), valueColor = signal.up)
                    if (tag.verkaeufe > 0) KeyValueRow(S.ctcSells, S.ctcCountApprox(Fmt.num(tag.verkaeufe.toDouble(), 0), Fmt.dollarKompakt(tag.verkaufSumme)), valueColor = signal.down)
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(tag.geschaefte, key = { i, _ -> "tg-$i" }) { _, g ->
                GeschaeftZeile(state, g, untertitel = S.ctcReported(Fmt.date(g.gemeldet))) { g.kuerzel?.let { onOpen(CopyRouten.symbol(it)) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
