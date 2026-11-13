package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Stimmungsbarometer
import de.troetenrepublik.app.engine.Suchgruppen
import de.troetenrepublik.app.engine.Suchgruppen.KryptoListe
import de.troetenrepublik.app.engine.Suchgruppen.KryptoZeile
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlin.math.abs

const val KRYPTO_ROUTE = "discover/krypto"

// Kryptowerte der Suche: die des Katalogs, danach die gepflegte Zusatzliste (Kuerzel, Name).
fun kryptoWerte(d: Catalog.Data?): List<Pair<String, String>> =
    (d?.basiswerte.orEmpty().filter { it.k == "KRYPTO" }.map { it.s to it.nameDe } + Suchgruppen.KRYPTO.map { it.symbol to it.de }).distinctBy { it.first }

// Zeile fuer die Listen aus dem Kurs; bei Krypto meldet die Kursquelle den Umsatz der letzten 24 Stunden schon in der
// Handelswaehrung, hier Euro.
internal fun kryptoZeile(symbol: String, name: String, q: Quote?, woche: Double?): KryptoZeile =
    if (q == null || q.price <= 0) KryptoZeile(symbol, name, woche = woche)
    else KryptoZeile(
        symbol, name, kurs = q.price, tag = q.changePct.takeIf { q.prevClose > 0 }, woche = woche,
        marktwert = q.marketCap.takeIf { it.isFinite() } ?: 0.0, umsatz = q.volume.takeIf { it.isFinite() } ?: 0.0, hoch52 = q.weekHigh52,
    )

// Krypto entdecken: Stimmung und Bitcoin-Anteil als Kopf, darunter eine Liste nach Wahl (Marktwert, Gewinner, Verlierer,
// Umsatz, Abstand zum 52-Wochen-Hoch), eingrenzbar auf eine Kategorie. Alle Zahlen stammen aus den Kursen der Werte
// und dem Stimmungsbarometer, die die App ohnehin laedt.
@Composable
fun KryptoEntdeckenPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, oeffnen: (String) -> Unit = {}) {
    val catalog = rememberCatalog()
    val werte = remember(catalog) { kryptoWerte(catalog) }
    val symbole = remember(werte) { werte.map { it.first } }
    val quotes = rememberSnapshot(state, symbole)
    var liste by rememberSaveable { mutableStateOf(KryptoListe.MARKTWERT.name) }
    var wochenweise by rememberSaveable { mutableStateOf(false) }
    var kategorie by rememberSaveable { mutableStateOf("") }
    val art = remember(liste) { runCatching { KryptoListe.valueOf(liste) }.getOrDefault(KryptoListe.MARKTWERT) }
    val mitZeitraum = art == KryptoListe.GEWINNER || art == KryptoListe.VERLIERER

    // Wochenwerte erst, wenn die Woche gewaehlt ist
    var woche by remember { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(wochenweise, symbole) {
        if (wochenweise && woche == null && symbole.isNotEmpty()) woche = runCatching { ZeitraumKurse.aenderungen(state, symbole, 7) }.getOrDefault(emptyMap())
    }
    var stimmung by remember { mutableStateOf<Stimmungsbarometer.Barometer?>(null) }
    LaunchedEffect(Unit) { stimmung = runCatching { state.stimmungen().krypto }.getOrNull() }
    // Umlauf der Stablecoins und in DeFi gebundenes Kapital (0.78)
    var markt by remember { mutableStateOf<KryptoMarkt?>(null) }
    LaunchedEffect(Unit) { markt = kryptoMarktLaden(state) }

    val zeilen = remember(werte, quotes, woche) { werte.map { (s, n) -> kryptoZeile(s, n, quotes[s], woche?.get(s)) } }
    val kategorien = remember(zeilen) { Suchgruppen.KRYPTO_KATEGORIEN.filter { k -> zeilen.any { it.kategorie == k } } }
    val gezeigt = remember(zeilen, art, wochenweise, kategorie) { Suchgruppen.kryptoListe(zeilen, art, wochenweise && mitZeitraum, kategorie) }
    val anteil = remember(zeilen) { Suchgruppen.bitcoinAnteil(zeilen) }
    val laedt = quotes.isEmpty() || (mitZeitraum && wochenweise && woche == null)

    val dismiss = rememberSwipeDownDismiss(onBack)
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().sheetDismiss(dismiss) { !listState.canScrollBackward }, state = listState) {
            item(key = "kopf") { DiscoverTitle(S.crypto, null, onBack) }
            stimmung?.let { b ->
                // Ein Tipp fuehrt zum Barometer mit Verlauf im Reiter Stimmung
                item(key = "stimmung") {
                    val sig = LocalSignal.current
                    val farbe = when (b.stufe) {
                        Stimmungsbarometer.Stufe.EXTREME_ANGST, Stimmungsbarometer.Stufe.ANGST -> sig.down
                        Stimmungsbarometer.Stufe.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                        Stimmungsbarometer.Stufe.GIER, Stimmungsbarometer.Stufe.EXTREME_GIER -> sig.up
                    }
                    Column {
                        KeyValueRow(S.sbKrypto, S.sbStufe(b.stufe.ordinal) + "\u00A0" + Fmt.num(b.wert, 0), valueColor = farbe, onClick = { oeffnen("$MAERKTE_ROUTE/stimmung") })
                        StimmungsLeiste(b.wert)
                    }
                }
            }
            anteil?.let { (prozent, anzahl) ->
                item(key = "anteil") { KeyValueRow(S.kyBitcoinAnteil, Fmt.pct(prozent, 1), hint = S.kyBitcoinAnteilHinweis(anzahl)) }
            }
            markt?.takeIf { it.vorhanden }?.let { m -> item(key = "markt") { KryptoMarktKarten(m) } }
            item(key = "listen") {
                PillenZeile {
                    KryptoListe.entries.forEach { l -> FilterChipPill(S.kyListe(l.name), selected = l == art, dropdown = false) { liste = l.name } }
                }
            }
            if (mitZeitraum) item(key = "zeitraum") {
                PillenZeile {
                    FilterChipPill(S.kyTag, selected = !wochenweise, dropdown = false) { wochenweise = false }
                    FilterChipPill(S.kyWoche, selected = wochenweise, dropdown = false) { wochenweise = true }
                }
            }
            if (kategorien.size > 1) item(key = "kategorien") {
                PillenZeile {
                    kategorien.forEach { k ->
                        FilterChipPill(S.suGruppe(k), selected = k == kategorie, dropdown = false) { kategorie = if (k == kategorie) "" else k }
                    }
                }
            }
            item(key = "titel") {
                Text(
                    S.kyListeTitel(art.name, wochenweise && mitZeitraum), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
                )
            }
            if (laedt) item(key = "laedt") { EmptyHint(S.loading) }
            else if (gezeigt.isEmpty()) item(key = "leer") { EmptyHint(S.noFilterResults) }
            else items(gezeigt, key = { it.symbol }, contentType = { "wert" }) { z ->
                val aenderung = if (wochenweise && mitZeitraum) z.woche else z.tag
                val unterzeile = when (art) {
                    KryptoListe.MARKTWERT -> Fmt.compactMoney(z.marktwert)
                    KryptoListe.UMSATZ -> Fmt.compactMoney(z.umsatz)
                    else -> z.kategorie.takeIf { it.isNotEmpty() }?.let { S.suGruppe(it) }
                }
                val hoch = z.unterHoch
                if (art == KryptoListe.HOCH && hoch != null) AssetRow(
                    state = state, symbol = z.symbol, name = z.name, subtitle = unterzeile,
                    rightPlain = Fmt.pct(hoch, 1), rightCaption = S.kyUnterHoch, onClick = { onOpen(z.symbol) },
                ) else AssetRow(
                    state = state, symbol = z.symbol, name = z.name, subtitle = unterzeile,
                    change = aenderung, changeText = aenderung?.let { Fmt.pct(abs(it)) }, onClick = { onOpen(z.symbol) },
                )
            }
            item(key = "quellen") {
                QuellenAnhang(listOfNotNull(
                    stimmung?.let { QuelleLink("alternative.me", Stimmungsbarometer.KRYPTO_SEITE) },
                    LLAMA_QUELLE.takeIf { markt?.vorhanden == true },
                ))
            }
            item(key = "ende") { Spacer(Modifier.height(48.dp)) }
        }
    }
}

// Waagrecht rollende Zeile mit Auswahlpillen, wie auf der Filterseite.
@Composable
private fun PillenZeile(inhalt: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { inhalt() }
}

// Skala von 0 (Angst) bis 100 (Gier) in fuenf Stufen wie beim Stimmungsbarometer der Maerkte, der Punkt zeigt den Wert.
@Composable
private fun StimmungsLeiste(wert: Double) {
    val sig = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val punkt = MaterialTheme.colorScheme.onSurface
    val farben = listOf(sig.down, sig.down.copy(alpha = 0.55f), grau.copy(alpha = 0.6f), sig.up.copy(alpha = 0.55f), sig.up)
    val grenzen = listOf(0.0, 25.0, 45.0, 55.0, 75.0, 100.0)
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 20.dp)) {
        val h = 6.dp.toPx()
        val oben = (size.height - h) / 2f
        val luecke = 2.dp.toPx()
        for (i in 0 until 5) {
            val x0 = size.width * (grenzen[i] / 100.0).toFloat()
            val x1 = size.width * (grenzen[i + 1] / 100.0).toFloat()
            drawRoundRect(farben[i], Offset(x0 + if (i == 0) 0f else luecke / 2, oben), Size(x1 - x0 - (if (i == 0 || i == 4) luecke / 2 else luecke), h), CornerRadius(h / 2))
        }
        drawCircle(punkt, 5.dp.toPx(), Offset(size.width * (wert.coerceIn(0.0, 100.0) / 100.0).toFloat(), size.height / 2f))
    }
}
