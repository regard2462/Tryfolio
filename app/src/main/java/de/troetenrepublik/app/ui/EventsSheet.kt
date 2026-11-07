package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Anstehende Termine (Geschaeftszahlen mit erwartetem Gewinn je Aktie und Umsatz, Ex-Dividende, Zahltag) und vergangene
// (gezahlte Dividenden, Quartalszahlen mit Ist und Erwartung, Ampel und Abweichung). Mit seite in der Waehrung, in der
// die Wertpapierseite gerade Kurse zeigt (5.92).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsSheet(state: AppState, symbol: String, name: String, profile: MarketApi.Profile?, seite: SeitenWaehrung? = null, onDismiss: () -> Unit) {
    var dividends by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>>(emptyList()) }
    LaunchedEffect(symbol) { dividends = state.dividendsFor(symbol) }
    val now = System.currentTimeMillis()
    val fw = profile?.financialCurrency.orEmpty()
    val umr = seite ?: SeitenWaehrung(fw, emptyMap())

    data class Ev(val title: String, val ts: Long, val subtitle: String?, val ueberraschung: Bewertungsblick.Ueberraschung? = null)
    val upcoming = buildList {
        profile?.earningsDate?.takeIf { it > 0 }?.let { add(Ev(S.eventEarnings, it, erwartungText(profile, umr))) }
        profile?.exDividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventExDividend, it, null)) }
        profile?.dividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventDividendPayment, it, null)) }
    }.sortedBy { it.ts }
    val past = buildList {
        dividends.filter { it.exDate <= now }.forEach { add(Ev(S.eventExDividend, it.exDate, Fmt.price(it.amount) + " · " + S.dividendPerShare)) }
        profile?.epsHistory.orEmpty().forEach { e ->
            val ts = quartalTag(e.label)
            if (ts != null) add(Ev(S.eventEarnings, ts, S.epsActualVsEstimate(umr.preis(e.actual, fw), umr.preis(e.estimate, fw)), Bewertungsblick.ueberraschung(e.actual, e.estimate)))
        }
    }.sortedByDescending { it.ts }.take(16)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(S.events, style = MaterialTheme.typography.headlineLarge)
                    NamenText(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(S.upcomingEvents) }
            if (upcoming.isEmpty()) item { EmptyHint(S.noEvents) }
            items(upcoming) { e -> EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.untilDay(e.ts), e.subtitle).joinToString(" · ")) }
            if (past.isNotEmpty()) {
                item { SectionHeader(S.pastEvents) }
                items(past) { e ->
                    EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.date(e.ts), e.subtitle).joinToString(" · "), ueberraschung = e.ueberraschung)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// Quartalsende („2025-09-30“) als Zeitpunkt; null bei anderem Format.
private fun quartalTag(label: String): Long? =
    runCatching { java.time.LocalDate.parse(label).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

// Was zu den naechsten Geschaeftszahlen erwartet wird: Gewinn je Aktie und Umsatz; null ohne Erwartung.
private fun erwartungText(p: MarketApi.Profile, umr: SeitenWaehrung): String? {
    val fw = p.financialCurrency
    val eps = p.earningsEstimate.takeIf { it != 0.0 }?.let { umr.preis(it, fw) }
    val umsatz = p.mehr.umsatzErwartet.takeIf { it > 0 }?.let { umr.kompakt(it, fw) }
    if (eps == null && umsatz == null) return null
    return S.evErwartet(eps, umsatz)
}

// Ereignisse auf der Wertpapierseite (5.92): die naechsten zwei Termine, bei Geschaeftszahlen mit der Erwartung, und
// die letzten Zahlen gegen die Erwartung. Pfeile nur an den Zeilen, ein Tipp oeffnet alle Termine.
@Composable
internal fun EreignisseAbschnitt(profile: MarketApi.Profile, seite: SeitenWaehrung, onOpen: () -> Unit) {
    val now = System.currentTimeMillis()
    val upcoming = listOfNotNull(
        profile.earningsDate.takeIf { it > now - 86_400_000L }?.let { Triple(S.eventEarnings, it, erwartungText(profile, seite)) },
        profile.exDividendDate.takeIf { it > now - 86_400_000L }?.let { Triple(S.eventExDividend, it, null) },
        profile.dividendDate.takeIf { it > now - 86_400_000L }?.let { Triple(S.eventDividendPayment, it, null) },
    ).sortedBy { it.second }
    // Letzte veroeffentlichte Zahlen; Yahoo nennt das Quartalsende, veroeffentlicht wird einige Wochen spaeter
    val letzte = profile.epsHistory.mapNotNull { e -> quartalTag(e.label)?.let { it to e } }.filter { it.first <= now }
        .maxByOrNull { it.first }?.takeIf { now - it.first < 200 * 86_400_000L }
    if (upcoming.isEmpty() && letzte == null) return
    SectionHeader(S.events, onClick = onOpen)
    upcoming.take(2).forEach { (titel, ts, erwartung) ->
        EventRow(titel, ts, subtitle = listOfNotNull(Fmt.untilDay(ts), erwartung).joinToString(" · "), onClick = onOpen)
    }
    letzte?.let { (ts, e) ->
        val fw = profile.financialCurrency
        EventRow(
            S.evLetzteZahlen, ts, subtitle = S.evQuartal(Fmt.date(ts)) + " · " + S.epsActualVsEstimate(seite.preis(e.actual, fw), seite.preis(e.estimate, fw)),
            onClick = onOpen, ueberraschung = Bewertungsblick.ueberraschung(e.actual, e.estimate),
        )
    }
}
