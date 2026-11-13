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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.Zahlentermine
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.ohneAbbruch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.r2
import de.troetenrepublik.app.market.MarketApi

// Anstehende Termine (Geschaeftszahlen mit erwartetem Gewinn je Aktie und Umsatz, Ex-Dividende, Zahltag) und vergangene
// (gezahlte Dividenden, Quartalszahlen mit Ist und Erwartung, Ampel und Abweichung). Mit seite in der Waehrung, in der
// die Wertpapierseite gerade Kurse zeigt (5.92).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsSheet(state: AppState, symbol: String, name: String, profile: MarketApi.Profile?, seite: SeitenWaehrung? = null, onGlocke: (() -> Unit)? = null, onDismiss: () -> Unit) {
    var dividends by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>>(emptyList()) }
    LaunchedEffect(symbol) { dividends = state.dividendsFor(symbol) }
    // Bekannte Tage der Veroeffentlichung (Tage seit 1970), fuer vergangene Geschaeftszahlen
    var meldetage by remember(symbol) { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(symbol) { meldetage = ohneAbbruch { state.nasdaqUeberraschung(symbol) }.getOrNull().orEmpty().map { it.tag } }
    val now = System.currentTimeMillis()
    val glocken = terminGlocken(state, symbol)
    val fw = profile?.financialCurrency.orEmpty()
    val umr = seite ?: SeitenWaehrung(fw, emptyMap())

    data class Ev(val title: String, val ts: Long, val subtitle: String?, val ueberraschung: Bewertungsblick.Ueberraschung? = null)
    val upcoming = buildList {
        profile?.earningsDate?.takeIf { Zahlentermine.anstehend(it, now) }?.let {
            add(Ev(S.eventEarnings, it, listOfNotNull(geschaetztText(profile), erwartungText(profile, umr)).joinToString(" · ").ifEmpty { null }))
        }
        profile?.exDividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventExDividend, it, null)) }
        profile?.dividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventDividendPayment, it, null)) }
    }.sortedBy { it.ts }
    val past = buildList {
        dividends.filter { it.exDate <= now }.forEach { add(Ev(S.eventExDividend, it.exDate, Fmt.price(it.amount))) }
        val termine = bekannteTermine(profile, meldetage, now)
        profile?.epsHistory.orEmpty().forEach { e ->
            val ts = meldeTag(e.label, termine) ?: quartalTag(e.label)
            if (ts != null) add(Ev(S.eventEarnings, ts, S.epsStatt(umr.preis(e.actual, fw), umr.preis(e.estimate, fw)), Bewertungsblick.ueberraschung(e.actual, e.estimate)))
        }
    }.sortedByDescending { it.ts }

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
            items(upcoming) { e -> EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.untilDay(e.ts), e.subtitle).joinToString(" · "), onGlocke = onGlocke.takeIf { terminGlocke(e.title) != null }, glockeAn = terminGlocke(e.title) in glocken) }
            if (past.isNotEmpty()) {
                item { SectionHeader(if (dividends.any { it.exDate <= now }) r2("past.mitDividende") else S.pastEvents) }
                items(past) { e ->
                    EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.date(e.ts), e.subtitle).joinToString(" · "), ueberraschung = e.ueberraschung)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// Art der Glocke zu einer Terminzeile: Geschaeftszahlen und Ex-Tag haben eine, andere Termine nicht.
private fun terminGlocke(titel: String): String? = when (titel) {
    S.eventEarnings -> de.troetenrepublik.app.engine.Alarmarten.QUARTAL
    S.eventExDividend -> de.troetenrepublik.app.engine.Alarmarten.EXDIV
    else -> null
}

// Eingeschaltete Glocken-Arten des Werts.
@Composable
private fun terminGlocken(state: AppState, symbol: String): Set<String> {
    val konto by state.account.collectAsStateWithLifecycle()
    return remember(konto.alerts, symbol) { konto.alerts.filter { it.symbol == symbol }.mapTo(HashSet()) { it.art } }
}

// Quartalsende („2025-09-30“) als Zeitpunkt; null bei anderem Format.
private fun quartalTag(label: String): Long? =
    runCatching { java.time.LocalDate.parse(label).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

// Tage (seit 1970), an denen Geschaeftszahlen veroeffentlicht wurden: die gemeldeten Termine und der bestaetigte
// Termin des Profils, sobald er vorbei ist.
private fun bekannteTermine(p: MarketApi.Profile?, meldetage: List<Long>, jetzt: Long): List<Long> =
    meldetage + listOfNotNull(p?.earningsDate?.takeIf { it > 0 && it <= jetzt && p.quartal?.geschaetzt != true }?.let { nyTagVon(it) })

// Tag der Veroeffentlichung zu einem Quartalsende als Zeitpunkt; null, wenn keiner bekannt ist.
private fun meldeTag(label: String, termine: List<Long>): Long? {
    val ende = runCatching { java.time.LocalDate.parse(label).toEpochDay() }.getOrNull() ?: return null
    val tag = Zahlentermine.veroeffentlicht(ende, termine) ?: return null
    return java.time.LocalDate.ofEpochDay(tag).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

// Kennzeichen eines nur geschaetzten Termins der naechsten Zahlen; null bei bestaetigtem Termin.
private fun geschaetztText(p: MarketApi.Profile): String? = if (p.quartal?.geschaetzt == true) S.qzGeschaetzt else null

// Eintrag des Ergebniskalenders zu einem Tag (Tage seit 1970) fuer diese Aktie oder eine ihrer Notierungen; null ohne
// Eintrag oder ohne Antwort.
private suspend fun kalenderTermin(state: AppState, symbol: String, tag: Long): Quartalszahlen.Termin? {
    val familie = Gleichwerte.familien.value[symbol].orEmpty() + symbol
    val d = java.time.LocalDate.ofEpochDay(tag)
    return withTimeoutOrNull(4_000L) {
        ohneAbbruch { state.quartalszahlen(listOf(d)) }.getOrNull()?.get(d)?.firstOrNull { it.symbol in familie }
    }
}

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
internal fun EreignisseAbschnitt(
    state: AppState, symbol: String, profile: MarketApi.Profile, seite: SeitenWaehrung, merker: SeitenMerker? = null,
    onGlocke: (() -> Unit)? = null, onOpenSymbol: (String) -> Unit = {}, onOpen: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val glocken = terminGlocken(state, symbol)
    // Bekannte Tage der Veroeffentlichung; die Karte darunter holt dieselbe Liste, ein zweiter Abruf entsteht nicht
    var meldetage by gemerkt<List<Long>?>(merker, "evt/$symbol") { null }
    LaunchedEffect(symbol) {
        if (meldetage == null) meldetage = ohneAbbruch { state.nasdaqUeberraschung(symbol) }.getOrNull().orEmpty().map { it.tag }
    }
    // Ein Tipp auf Geschaeftszahlen oeffnet den Quartalsueberblick des Kalenders; ohne Kalendereintrag alle Termine
    val scope = rememberCoroutineScope()
    var ueberblick by remember(symbol) { mutableStateOf<Quartalszahlen.Termin?>(null) }
    val konto by state.account.collectAsStateWithLifecycle()
    fun zahlenOeffnen(tag: Long?) {
        if (tag == null) return onOpen()
        scope.launch { kalenderTermin(state, symbol, tag)?.let { ueberblick = it } ?: onOpen() }
    }
    val upcoming = listOfNotNull(
        profile.earningsDate.takeIf { Zahlentermine.anstehend(it, now) }?.let {
            Triple(S.eventEarnings, it, listOfNotNull(geschaetztText(profile), erwartungText(profile, seite)).joinToString(" · ").ifEmpty { null })
        },
        profile.exDividendDate.takeIf { it > now - 86_400_000L }?.let { Triple(S.eventExDividend, it, null) },
        profile.dividendDate.takeIf { it > now - 86_400_000L }?.let { Triple(S.eventDividendPayment, it, null) },
    ).sortedBy { it.second }
    // Letzte veroeffentlichte Zahlen; Yahoo nennt das Quartalsende, veroeffentlicht wird einige Wochen spaeter
    val letzte = profile.epsHistory.mapNotNull { e -> quartalTag(e.label)?.let { it to e } }.filter { it.first <= now }
        .maxByOrNull { it.first }?.takeIf { now - it.first < 200 * 86_400_000L }
    if (upcoming.isEmpty() && letzte == null) return
    SectionHeader(S.events, onClick = onOpen)
    upcoming.take(2).forEach { (titel, ts, erwartung) ->
        EventRow(
            titel, ts, subtitle = listOfNotNull(Fmt.untilDay(ts), erwartung).joinToString(" · "),
            onClick = if (titel == S.eventEarnings) ({ zahlenOeffnen(nyTagVon(ts)) }) else onOpen,
            onGlocke = onGlocke.takeIf { terminGlocke(titel) != null }, glockeAn = terminGlocke(titel) in glocken,
        )
    }
    // Die weiteren Termine stehen im Blatt dahinter
    if (upcoming.size > 2) ActionLineWide(S.bbAlle(upcoming.size)) { onOpen() }
    letzte?.let { (ts, e) ->
        val fw = profile.financialCurrency
        // Die Plakette zeigt den Tag der Veroeffentlichung, soweit er bekannt ist, sonst das Quartalsende
        val meldung = meldeTag(e.label, bekannteTermine(profile, meldetage.orEmpty(), now))
        EventRow(
            S.evLetzteZahlen, meldung ?: ts, subtitle = S.evQuartal(Fmt.date(ts)) + "\n" + S.epsStatt(seite.preis(e.actual, fw), seite.preis(e.estimate, fw)),
            onClick = { zahlenOeffnen(meldung?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() }) },
            ueberraschung = Bewertungsblick.ueberraschung(e.actual, e.estimate),
        )
    }
    ueberblick?.let { t ->
        val eigene = remember(konto) { (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet() }
        QuartalsUeberblickBlatt(state, t, eigene, onDismiss = { ueberblick = null }, onOpen = onOpenSymbol, hier = symbol)
    }
    // Eingepreist gegen tatsaechlich (0.76): bei kommenden Zahlen die erwartete Bewegung, sonst die des letzten Termins
    if (profile.quartal != null || letzte != null) {
        // Liegt der naechste Termin weit weg, gibt es noch keine Erwartung; dann zaehlt der letzte Termin
        val bevor = profile.earningsDate.takeIf { it > now - 86_400_000L && (letzte == null || it - now < 45 * 86_400_000L) }
        ZahlenReaktionKarte(
            state, symbol, bevor?.let { nyTagVon(it) }, bevor != null && nyStundeVon(bevor) >= 16, vergangen = bevor == null,
            verlauf = profile.quartal?.verlauf.orEmpty(),
            epsAbweichung = if (bevor == null) profile.quartal?.verlauf?.lastOrNull()?.abweichung else null,
            epsText = { seite.preis(it, profile.financialCurrency) },
            merker = merker,
        )
    }
}
