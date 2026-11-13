package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.Kapitalkennzahlen
import de.troetenrepublik.app.engine.Kennzahlvergleich
import de.troetenrepublik.app.engine.Kennzahlvergleich.Zeile
import de.troetenrepublik.app.engine.Paare
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.Risikokennzahlen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Hoechstens so viele Aktien nebeneinander.
private const val KV_MAX = 5

// Kennzahlen einer Aktie aus Profil und Kurs; Prozentwerte von Yahoo stehen schon in Prozent (MarketApi).
private fun kvWerte(p: MarketApi.Profile, q: Quote?, k: Kapitalkennzahlen.Ergebnis?, r: Risikokennzahlen.Ergebnis?): Kennzahlvergleich.Werte {
    val o = Kennzahlvergleich::oderNull
    return Kennzahlvergleich.Werte(
        kgv = o(p.trailingPe) ?: q?.peRatio?.let(o),
        kgvErwartet = o(p.forwardPe),
        kuv = o(p.priceToSales),
        kbv = o(p.priceToBook),
        evEbitda = o(p.evToEbitda),
        bruttomarge = o(p.grossMargin),
        operativeMarge = o(p.operatingMargin),
        umsatzwachstum = o(p.revenueGrowth),
        verschuldung = o(p.debtToEquity),
        // Keine Dividende ist eine echte Angabe
        dividende = p.dividendYield,
        kursziel = Kennzahlvergleich.abstandKursziel(p.currentPrice.takeIf { it > 0 } ?: q?.nativePrice ?: 0.0, p.targetPrice),
        kursCashflow = k?.kursCashflow,
        roic = k?.roic,
        ownerEarnings = k?.ownerRendite,
        zinsdeckung = k?.zinsdeckung,
        beta = o(p.beta),
        volatilitaet = r?.volatilitaet,
        maxRueckgang = r?.maxRueckgang,
    )
}

private fun kvText(z: Zeile, v: Double): String = when (z) {
    Zeile.KGV, Zeile.KGV_ERWARTET, Zeile.KUV, Zeile.KBV, Zeile.EV_EBITDA, Zeile.KURS_CASHFLOW, Zeile.ZINSDECKUNG -> Fmt.num(v, 1) + "×"
    Zeile.VERSCHULDUNG -> Fmt.num(v, 2)
    Zeile.BETA -> Fmt.num(v, 2)
    Zeile.KURSZIEL, Zeile.UMSATZWACHSTUM, Zeile.OWNER_EARNINGS, Zeile.MAX_RUECKGANG -> Fmt.signedPct(v, 1)
    else -> Fmt.pct(v, 1)
}

// Bezeichnung einer Zeile: die vorhandenen Namen fuer KGV, erwartetes KGV und Dividendenrendite, damit deren
// Erklaerung (S.kennzahlErklaerung) dieselbe ist wie bei den Kennzahlen der Wertpapierseite.
// Hoehe einer Tabellenzeile, auch fuer zweizeilige Begriffe.
private val KV_ZEILE = 36.dp

// Zeilen aus den Jahresabschluessen; sie haben erst einen Wert, sobald die Abschluesse geladen sind
private val KV_BILANZ = setOf(Zeile.KURS_CASHFLOW, Zeile.ROIC, Zeile.OWNER_EARNINGS, Zeile.ZINSDECKUNG)

// Zeilen aus den Tageskursen des letzten Jahres
private val KV_KURSE = setOf(Zeile.VOLATILITAET, Zeile.MAX_RUECKGANG)

private fun kvLabel(z: Zeile): String = when (z) {
    Zeile.KGV_ERWARTET -> S.forwardPe
    Zeile.DIVIDENDE -> S.dividendYield
    else -> S.kvZeile(z.name)
}

// Name, unter dem S.kennzahlErklaerung die Zeile erklaert; dieselbe Quelle wie auf der Wertpapierseite.
private fun kvErklaerLabel(z: Zeile): String = when (z) {
    Zeile.KGV -> S.peRatio
    Zeile.KUV -> S.bkKuv
    Zeile.KBV -> S.priceToBook
    Zeile.EV_EBITDA -> S.bkEvEbitda
    Zeile.BRUTTOMARGE -> S.grossMargin
    Zeile.OPERATIVE_MARGE -> S.operatingMargin
    Zeile.UMSATZWACHSTUM -> S.revenueGrowth
    Zeile.VERSCHULDUNG -> S.bkVerschuldung
    Zeile.KURS_CASHFLOW -> S.bkKursCashflow
    Zeile.ROIC -> S.bkRoic
    Zeile.OWNER_EARNINGS -> S.bkOwnerEarnings
    Zeile.ZINSDECKUNG -> S.bkZinsdeckung
    else -> kvLabel(z)
}

// Fundamentalvergleich von 2 bis 5 Aktien als Tabelle: Zeilen sind die Kennzahlen, Spalten die Aktien. Der beste Wert
// jeder Zeile ist hervorgehoben, ein Tipp auf eine Kennzahl erklaert sie. Vorschlaege aus Yahoo (aehnliche Werte) und
// aus derselben Branche des Katalogs, dazu freie Auswahl ueber die Suche.
@Composable
fun KennzahlVergleichScreen(state: AppState, symbol: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var gewaehltText by rememberSaveable(symbol) { mutableStateOf(symbol) }
    val gewaehlt = gewaehltText.split(',').filter { it.isNotBlank() }
    fun umschalten(s: String) {
        gewaehltText = (if (s in gewaehlt) gewaehlt - s else if (gewaehlt.size >= KV_MAX) gewaehlt else gewaehlt + s).joinToString(",")
    }
    fun name(s: String) = Symbols.find(s)?.name ?: quotes[s]?.name?.ifBlank { null } ?: Catalog.get().aktien.firstOrNull { it.s == s }?.n ?: s

    // Profile je Aktie (zwischengespeichert in AppState), Kurse fuer KGV und Kursziel; Jahresabschluesse je Symbol fuer
    // die Kennzahlen aus der Bilanz (nach Symbol gespeichert, deshalb nebenlaufig sicher)
    val profile = remember { mutableStateMapOf<String, MarketApi.Profile?>() }
    val abschluss = remember { mutableStateMapOf<String, List<Qualitaet.Jahr>>() }
    val risiko = remember { mutableStateMapOf<String, Risikokennzahlen.Ergebnis?>() }
    val risikoFertig = remember { mutableStateMapOf<String, Boolean>() }
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(gewaehltText) {
        state.market.track(gewaehlt)
        gewaehlt.filter { it !in profile }.forEach { s -> scope.launch { profile[s] = state.profileFor(s) } }
        gewaehlt.filter { it !in risikoFertig }.forEach { s ->
            scope.launch {
                risiko[s] = try { Risikokennzahlen.berechne(state.dailyCloses(s).map { it.close }) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                risikoFertig[s] = true
            }
        }
        // Ein Fehler beim Abruf zaehlt als „keine Daten“, ein Abbruch bleibt Abbruch
        gewaehlt.filter { it !in abschluss }.forEach { s ->
            scope.launch { abschluss[s] = try { state.jahresabschluesse(s) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
        }
    }

    // Vorschlaege: aehnlich laut Yahoo, dazu die groessten Werte derselben Branche im Katalog
    var aehnlich by remember(symbol) { mutableStateOf<List<String>>(emptyList()) }
    var branche by remember(symbol) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(symbol) {
        aehnlich = state.aehnliche(symbol).filter { it != symbol && Symbols.typeOf(it) == AssetType.STOCK }.take(6)
        val katalog = Catalog.get()
        val ind = katalog.aktien.firstOrNull { it.s == symbol }?.i?.takeIf { it.isNotBlank() }
        if (ind != null) {
            val kandidaten = katalog.aktien.filter { it.i == ind && it.s != symbol }.map { it.s }.take(80)
            val kurse = runCatching { state.market.snapshot(kandidaten) }.getOrDefault(emptyMap())
            branche = kandidaten.sortedByDescending { kurse[it]?.marketCap ?: 0.0 }.filter { it !in aehnlich }.take(6)
        }
        // Beim ersten Oeffnen zwei Vorschlaege vorbelegen, die aus derselben Branche zuerst
        if (gewaehltText == symbol) {
            val vorschlag = (branche + aehnlich).distinct().take(2)
            if (vorschlag.isNotEmpty()) gewaehltText = (listOf(symbol) + vorschlag).joinToString(",")
        }
    }

    // Suche nach weiteren Aktien
    var suche by rememberSaveable { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        val lokal = if (q.length >= 2) Symbols.search(q).filter { it.type == AssetType.STOCK }.map { it.symbol to it.name } else emptyList()
        treffer = lokal.take(8)
        if (q.length < 2) return@LaunchedEffect
        try {
            delay(350)
            val bekannt = lokal.map { it.first }.toSet()
            val fern = state.market.search(q).filter { it.type == "EQUITY" && it.symbol !in bekannt }.map { it.symbol to it.name }
            treffer = (lokal + fern).distinctBy { it.first }.take(10)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    val werte = gewaehlt.map { s -> profile[s]?.let { p -> kvWerte(p, quotes[s], abschluss[s]?.let { kapitalAus(it, p, quotes[s], fx) }, risiko[s]) } }
    var erklaert by remember { mutableStateOf<Zeile?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().sheetDismiss(dismiss) { !liste.canScrollBackward },
        state = liste,
    ) {
        item { ScreenHeader(S.kvTitel, name(symbol), onBack) }

        // Gewaehlte Aktien zum Entfernen
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gewaehlt.forEach { s -> Chip(name(s) + if (s != symbol) "  ×" else "", active = true) { if (s != symbol) umschalten(s) } }
            }
        }

        // Tabelle
        if (gewaehlt.size >= 2) item {
            val spalte = 100.dp
            // Namensspalte so breit wie das laengste einzelne Wort der Zeilennamen: keines bricht mitten im Wort um,
            // auch nicht bei grosser Systemschrift oder Schriftskalierung; darunter nie schmaler als 140 dp (5.93)
            val begriffStil = MaterialTheme.typography.bodySmall
            val messer = rememberTextMeasurer()
            val dichte = LocalDensity.current
            val namenBreite = remember(begriffStil, dichte) {
                val wort = Zeile.entries.maxOf { z -> kvLabel(z).split(' ').maxOf { w -> messer.measure(w, begriffStil).size.width } }
                (with(dichte) { wort.toDp() } + 6.dp).coerceAtLeast(140.dp)
            }
            // Kopfhoehe: mindestens 44 dp, waechst mit dem laengsten Namen (Namen brechen um, nie abgeschnitten)
            var kopfHoehe by remember(gewaehlt.size) { mutableStateOf(44.dp) }
            // Der Wertbereich scrollt bis zum Bildschirmrand; die Namensspalte bleibt stehen
            Row(Modifier.fillMaxWidth().padding(start = 20.dp).padding(top = 12.dp)) {
                // Begriffe brechen nur an Leerzeichen um; jede Zeile ist links und rechts gleich hoch (5.92)
                Column(Modifier.width(namenBreite)) {
                    Spacer(Modifier.height(kopfHoehe))
                    Zeile.entries.forEach { z ->
                        Text(
                            kvLabel(z), style = MaterialTheme.typography.bodySmall, maxLines = 2, lineHeight = 15.sp,
                            color = if (erklaert == z) MaterialTheme.colorScheme.onSurface else grau,
                            modifier = Modifier.height(KV_ZEILE).textKlick(0.dp, 6.dp) { erklaert = if (erklaert == z) null else z }
                                .wrapContentHeight(Alignment.CenterVertically).padding(end = 6.dp),
                        )
                    }
                }
                Row(Modifier.weight(1f).horizontalScrollMaus(rememberScrollState())) {
                    Row(Modifier.padding(end = 20.dp)) {
                        gewaehlt.forEachIndexed { i, s ->
                            Column(Modifier.width(spalte), horizontalAlignment = Alignment.End) {
                                Column(
                                    Modifier.heightIn(min = kopfHoehe).onSizeChanged { h -> val d = with(dichte) { h.height.toDp() }; if (d > kopfHoehe) kopfHoehe = d }
                                        .textKlick(6.dp, 0.dp) { onOpen(s) }.padding(start = 6.dp, top = 2.dp),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    // Bei einem Zahlenkuerzel steht nur der Name darunter, nie die Zahl
                                    if (!HeatmapBeschriftung.nurZiffern(s)) Text(s.substringBefore('.'), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                                        NamenText(name(s), modifier = Modifier.wrapContentWidth(), style = MaterialTheme.typography.labelSmall, color = grau)
                                    }
                                }
                                Zeile.entries.forEach { z ->
                                    val v = werte[i]?.wert(z)
                                    val best = z.hervorheben && Kennzahlvergleich.bester(werte.map { it?.wert(z) }, z.richtung) == i
                                    Text(
                                        when {
                                            s !in profile || (z in KV_BILANZ && s !in abschluss) || (z in KV_KURSE && risikoFertig[s] != true) -> "…"
                                            v == null -> "–"
                                            else -> kvText(z, v)
                                        },
                                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                                        fontWeight = if (best) FontWeight.SemiBold else null,
                                        color = if (best) signal.up else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.height(KV_ZEILE).wrapContentHeight(Alignment.CenterVertically),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                erklaert?.let { z ->
                    val label = kvLabel(z)
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    StichpunktText(S.kennzahlErklaerung(kvErklaerLabel(z)).orEmpty(), style = MaterialTheme.typography.bodySmall, color = grau)
                }
                if (gewaehlt.any { it !in profile }) Text(S.kvLaden, style = MaterialTheme.typography.labelSmall, color = grau)
            }
        }

        // Paare der groessten Werte derselben Branche: ein Tipp ersetzt die Auswahl durch genau diese beiden
        val paare = Paare.bilden(branche + aehnlich.filter { it !in branche })
        if (paare.isNotEmpty()) item(key = "paare") {
            SectionHeader(S.vbPaare)
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                paare.forEach { (a, b) ->
                    Chip(HeatmapBeschriftung.bezeichnung(a, name(a)).orEmpty() + " / " + HeatmapBeschriftung.bezeichnung(b, name(b)).orEmpty(), active = gewaehlt.size == 2 && a in gewaehlt && b in gewaehlt) { gewaehltText = "$a,$b" }
                }
            }
        }

        // Vorschlaege
        listOf(S.kvAehnlich to aehnlich, S.kvBranche to branche).filter { it.second.isNotEmpty() }.forEach { (titel, l) ->
            item(key = "v-$titel") {
                SectionHeader(titel)
                Row(
                    Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    l.forEach { s -> Chip(name(s), active = s in gewaehlt) { umschalten(s) } }
                }
            }
        }

        // Suche
        item {
            SectionHeader(S.kvSuche)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(Radius.field))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (suche.isEmpty()) Text(S.kvSucheHinweis, style = MaterialTheme.typography.bodyLarge, color = grau)
                    BasicTextField(
                        value = suche, onValueChange = { suche = it }, singleLine = true,
                        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        items(treffer, key = { "t-" + it.first }) { (s, n) ->
            Row(
                Modifier.fillMaxWidth().clickable { umschalten(s) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentLogo(state, s, n, size = 28)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    NamenText(n, style = MaterialTheme.typography.bodyMedium)
                    Text(s, style = MaterialTheme.typography.bodySmall, color = grau)
                }
                if (s in gewaehlt) CheckMark(size = 14.dp)
            }
        }

        item { AufklappText(S.kvHinweis, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
