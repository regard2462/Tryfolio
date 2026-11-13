package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.engine.Wertglocke
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols

// Glocke eines Werts (0.77): alle Mitteilungsarten dieses Werts als Schalter an einem Ort, in wenigen Gruppen. Moeglich
// sind nur die Arten, die es fuer den Wert gibt (Wertglocke.moeglich). Kursmarken werden wie bisher ueber das
// Ziffernfeld angelegt; ob eine Marke ueber oder unter gilt, ergibt sich aus der Marke im Vergleich zum aktuellen Kurs.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val quote = quotes[symbol]
    val price = quote?.price ?: 0.0
    val mine = account.alerts.filter { it.symbol == symbol }
    val vorhanden = remember(mine) { mine.map { Wertglocke.artVon(it) }.toSet() }
    var anlegen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val target = parseAmount(input)
    val profil by produceState<MarketApi.Profile?>(null, symbol) { value = runCatching { state.profileFor(symbol) }.getOrNull() }
    val typ = remember(symbol) { Symbols.typeOf(symbol) }
    val us = istUsAktie(symbol, quote?.nativeCurrency)
    val dividende = profil?.let { it.dividendRate > 0 || it.exDividendDate > 0 }
    val moeglich = remember(typ, us, dividende) { Wertglocke.moeglich(typ, us, dividende) }
    val gruppen = remember(moeglich, vorhanden) { Wertglocke.sichtbar(moeglich, vorhanden) }
    val alleArten = remember(gruppen) { Wertglocke.fuerAlle(gruppen) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(state, symbol, name, size = 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.notifications, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(name, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Text(Fmt.priceEuro(price), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(12.dp))

            if (!anlegen) {
                SchalterZeile(S.glockeAlle, Wertglocke.alleAn(gruppen, vorhanden)) { an -> state.wertArtenSetzen(symbol, name, alleArten, an) }
                gruppen.forEach { g ->
                    Zwischenkopf(S.glockeGruppe(g.gruppe.name))
                    g.arten.forEach { art ->
                        when (art) {
                            Wertglocke.MARKE -> {
                                Text(S.glockePreismarken, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                                mine.filter { Wertglocke.artVon(it) == Wertglocke.MARKE }.forEach { a ->
                                    Box(Modifier.padding(horizontal = 20.dp)) { MarkenZeile(a, onDelete = { state.removeAlert(a.id) }) }
                                }
                                Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                    FloatingPill(label = S.glockeNeueMarke, plus = true, filled = false, modifier = Modifier.fillMaxWidth()) { anlegen = true }
                                }
                            }
                            Wertglocke.BEWEGUNG -> {
                                val aktuell = mine.firstOrNull { Wertglocke.artVon(it) == Wertglocke.BEWEGUNG }
                                SchalterZeile(S.glockeBewegung(aktuell?.let { Fmt.pct(it.tagesProzent, 1) }), aktuell != null) { an ->
                                    state.wertArtenSetzen(symbol, name, listOf(Wertglocke.BEWEGUNG), an)
                                }
                                if (aktuell != null) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Wertglocke.BEWEGUNG_STUFEN.forEach { pct ->
                                            val gewaehlt = kotlin.math.abs(aktuell.tagesProzent - pct) < 1e-9
                                            Box(
                                                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip))
                                                    .background(if (gewaehlt) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .clickable { state.bewegungSetzen(symbol, name, pct) }.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    Fmt.pct(pct, 0), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                                                    color = if (gewaehlt) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> SchalterZeile(S.glockeArt(art), art in vorhanden) { an -> state.wertArtenSetzen(symbol, name, listOf(art), an) }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    BetragText("${displayAmount(input)}${160.toChar()}€", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth())
                    Text(
                        when {
                            target <= 0 -> S.enterTargetPrice
                            target > price -> S.alertRises(Fmt.priceEuro(target), Fmt.pct((target / price - 1) * 100))
                            target < price -> S.alertFalls(Fmt.priceEuro(target), Fmt.pct((1 - target / price) * 100))
                            else -> S.targetEqualsPrice
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-10, -5, 5, 10).forEach { pct ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { input = toInput(price * (1 + pct / 100.0)) }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text((if (pct > 0) "+" else "−") + "${kotlin.math.abs(pct)} %", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it, maxDecimals = 4) }, onBackspace = { input = input.dropLast(1) })
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { anlegen = false; input = "" }
                        FloatingPill(label = S.setAlert, enabled = target > 0 && target != price && price > 0, modifier = Modifier.weight(1f)) {
                            if (state.addAlert(symbol, name, target)) { input = ""; anlegen = false }
                        }
                    }
                }
            }
        }
    }
}

// Eine gesetzte Kursmarke mit Loeschen.
@Composable
private fun MarkenZeile(a: PriceAlert, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.priceEuro(a.target), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            PunktText((if (a.above) S.whenRises else S.whenFalls) + (if (a.referencePrice > 0) " · " + S.setAt(Fmt.priceEuro(a.referencePrice)) else ""))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onDelete).padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(S.delete, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
    }
}
