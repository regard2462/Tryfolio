package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import de.troetenrepublik.app.market.Derivate
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf

// Depot einer anderen Person fuer die Werkzeuge aus Analytics: Abgeordnete, Senatoren und der Praesident.
// Die Werkzeuge lesen sonst das eigene Depot; innerhalb von LocalFremdesDepot rechnen sie mit diesem.
data class FremdesDepot(
    val name: String,
    val valuation: Valuation,
    val account: Account,
    // Wert in Optionen je Symbol (Euro), Teil der Position; der Depot-Check zaehlt ihn als Hebelprodukt.
    val optionen: Map<String, Double> = emptyMap(),
    // Heimwaehrung der Person (Abgeordnete, Senatoren, Praesident: US-Dollar).
    val heim: String = "USD",
)

val LocalFremdesDepot = compositionLocalOf<FremdesDepot?> { null }

// Bewertung fuer die Werkzeuge: das fremde Depot, sonst das eigene.
@Composable
internal fun depotBewertung(state: AppState): Valuation {
    val f = LocalFremdesDepot.current
    if (f != null) return f.valuation
    val v by state.valuation.collectAsStateWithLifecycle()
    return v
}

// Konto fuer die Werkzeuge: das fremde Depot (ohne Bargeld, Sparplaene und Buchungen), sonst das eigene.
@Composable
internal fun depotKonto(state: AppState): Account {
    val f = LocalFremdesDepot.current
    if (f != null) return f.account
    val a by state.account.collectAsStateWithLifecycle()
    return a
}

// Geschaetzter Bestand einer Person als Positionen in Euro: Stueckzahl aus dem Wert in US-Dollar und dem heutigen Kurs.
// Ohne Betraege (vorberechneter Verlauf des Praesidenten) verteilt ersatzSumme die Anteile.
@Composable
internal fun rememberFremdesDepot(state: AppState, name: String, pv: Portfolioverlauf.KongressVerlauf?, ersatzSumme: Double): FremdesDepot? {
    val werte = remember(pv) {
        pv?.werte?.takeIf { it.isNotEmpty() }
            ?: pv?.bestand?.map { (k, anteil) -> Portfolioverlauf.Wert(k, k, anteil * ersatzSumme, anteil * ersatzSumme) }.orEmpty()
    }
    val symbole = remember(werte) { werte.map { CopyRouten.symbol(it.kuerzel) }.distinct() }
    LaunchedEffect(symbole) { if (symbole.isNotEmpty()) runCatching { state.market.track(symbole) } }
    val quotes by state.quotes.collectAsStateWithLifecycle()
    if (werte.isEmpty()) return null
    val positionen = werte.mapNotNull { w ->
        val sym = CopyRouten.symbol(w.kuerzel)
        val q = quotes[sym] ?: return@mapNotNull null
        if (q.price <= 0 || q.nativePrice <= 0 || w.usd <= 0) return@mapNotNull null
        val stueck = w.usd / q.nativePrice
        val einstand = if (w.einstand > 0) w.einstand / w.usd * q.price else q.price
        ValuedPosition(
            Position(key = sym, name = CopyRouten.name(w.kuerzel, w.name), symbol = sym, qty = stueck, avgPrice = einstand, openedAt = 0L),
            price = q.price, prevPrice = q.prevClose.takeIf { it > 0 } ?: q.price,
        )
    }
    if (positionen.isEmpty()) return null
    val v = Valuation(positions = positionen, cash = 0.0, deposited = positionen.sumOf { it.invested })
    val optionen = werte.filter { it.optionUsd > 0 }.mapNotNull { w ->
        val sym = CopyRouten.symbol(w.kuerzel)
        val q = quotes[sym] ?: return@mapNotNull null
        if (q.nativePrice <= 0) null else sym to w.optionUsd / q.nativePrice * q.price
    }.toMap()
    return FremdesDepot(name, v, Account(positions = positionen.map { it.position }), optionen)
}

// Aktuelle Positionen einer Person, geschaetzt aus den gemeldeten Geschaeften. Positionen mit Optionen klappen ueber
// das V rechts auf; darunter stehen die Aktien und jede Option mit Basispreis und Verfall. Eine Option oeffnet den
// nachgebildeten Optionsschein.
@Composable
internal fun AktuellePositionen(state: AppState, pv: Portfolioverlauf.KongressVerlauf, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val werte = pv.werte.sortedByDescending { it.usd }
    val summe = werte.sumOf { it.usd }
    var alle by remember { mutableStateOf(false) }
    var offen by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()
    if (werte.isEmpty() && pv.bestand.isEmpty()) return
    SectionHeader(S.copyPositionsTitle)
    if (werte.isNotEmpty() && summe > 0) {
        val mitZweigen = werte.any { verzweigt(it) }
        (if (alle) werte else werte.take(10)).forEach { w ->
            val sym = CopyRouten.symbol(w.kuerzel)
            val name = CopyRouten.name(w.kuerzel, w.name)
            val auf = w.kuerzel in offen
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(start = 20.dp, end = if (mitZweigen) 8.dp else 20.dp).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentLogo(state, sym, name, size = 32)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    NamenText(name, zusatz = " (${w.kuerzel})", style = MaterialTheme.typography.titleSmall)
                    Text(S.copyPositionShare(Fmt.pct(w.usd / summe * 100, 1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.dollarKompakt(w.usd), style = MaterialTheme.typography.titleSmall)
                    ergebnis(w.usd, w.einstand)?.let { r ->
                        Text(Fmt.signedPct(r, 1), style = MaterialTheme.typography.bodySmall, color = if (r >= 0) signal.up else signal.down)
                    }
                }
                if (mitZweigen) AufklappKnopf(auf, verzweigt(w)) { offen = if (auf) offen - w.kuerzel else offen + w.kuerzel }
            }
            if (auf) {
                w.teile.forEachIndexed { i, t ->
                    Zweig(state, sym, name, t, w.usd, letzter = i == w.teile.lastIndex, rechts = if (mitZweigen) ZWEIG_KNOPF + 8.dp else 20.dp) {
                        val d = t.derivat
                        if (d == null) onOpen(sym) else scope.launch {
                            val id = state.derivatFuerOption(sym, d.art == Derivate.Art.CALL, d.strike!!, d.verfall!!)
                            onOpen(if (id != null) DERIVAT_ROUTE + id else sym)
                        }
                    }
                }
            }
        }
        if (werte.size > 10) ActionLineWide(if (alle) S.copyPositionsFewer else S.copyPositionsAll(werte.size)) { alle = !alle }
    } else {
        pv.bestand.take(if (alle) pv.bestand.size else 10).forEach { (k, anteil) ->
            val sym = CopyRouten.symbol(k)
            Row(Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(state, sym, CopyRouten.name(k, k), size = 32)
                Spacer(Modifier.width(12.dp))
                NamenText(CopyRouten.name(k, k), zusatz = " ($k)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Fmt.pct(anteil * 100, 1), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (pv.bestand.size > 10) ActionLineWide(if (alle) S.copyPositionsFewer else S.copyPositionsAll(pv.bestand.size)) { alle = !alle }
    }
    Text(
        S.copyPositionsNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp),
    )
}

// Breite des V zum Aufklappen rechts in der Zeile.
internal val ZWEIG_KNOPF = 36.dp

// Eine Position klappt auf, sobald sie eine Option mit Basispreis und Verfall enthaelt.
private fun verzweigt(w: Portfolioverlauf.Wert): Boolean = w.teile.any { it.derivat != null }

private fun ergebnis(usd: Double, einstand: Double): Double? = if (einstand > 0) (usd / einstand - 1) * 100 else null

// Unterzweig einer geschaetzten Position: Aktien oder eine Option mit Basispreis und Verfall.
@Composable
private fun Zweig(
    state: AppState, sym: String, name: String, t: Portfolioverlauf.Teil, gesamt: Double, letzter: Boolean, rechts: Dp, onClick: () -> Unit,
) {
    val signal = LocalSignal.current
    val d = t.derivat
    val titel = if (d == null) S.copyPartShares else S.copyPartOption(
        d.art == Derivate.Art.CALL, Fmt.num(d.strike!!, if (d.strike % 1.0 == 0.0) 0 else 2), Fmt.date(d.verfall!!),
        d.verfall < System.currentTimeMillis(),
    )
    val r = ergebnis(t.usd, t.einstand)
    ZweigZeile(
        logo = { InstrumentLogo(state, sym, name, size = 22) }, titel = titel, untertitel = S.copyPartShare(Fmt.pct(t.usd / gesamt * 100, 1)),
        wert = Fmt.dollarKompakt(t.usd), zusatz = r?.let { Fmt.signedPct(it, 1) }, zusatzFarbe = if ((r ?: 0.0) >= 0) signal.up else signal.down,
        letzter = letzter, rechts = rechts, onClick = onClick,
    )
}

// Unterzweig einer Position (Aktuelle Positionen, Bestaende der Investoren): Linie vom Logo der Position (32 bis 36 dp),
// kleineres Logo, Titel, Untertitel, rechts der Wert und darunter optional ein Zusatz. rechts: Abstand rechts, damit
// die Werte unter denen der Position stehen.
@Composable
internal fun ZweigZeile(
    logo: @Composable () -> Unit,
    titel: String,
    untertitel: String,
    wert: String,
    letzter: Boolean,
    rechts: Dp,
    untertitelFarbe: Color? = null,
    zusatz: String? = null,
    zusatzFarbe: Color? = null,
    onClick: () -> Unit,
) {
    val linie = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(start = 20.dp, end = rechts),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Linie von der Mitte des Logos der Position nach unten und zum Logo des Zweigs
        Canvas(Modifier.width(32.dp).fillMaxHeight()) {
            val x = 16.dp.toPx()
            val mitte = size.height / 2f
            val st = 1.dp.toPx()
            drawLine(linie, Offset(x, 0f), Offset(x, if (letzter) mitte else size.height), st)
            drawLine(linie, Offset(x, mitte), Offset(size.width, mitte), st)
        }
        Spacer(Modifier.width(4.dp))
        logo()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            VollText(titel, style = MaterialTheme.typography.bodyMedium)
            VollText(untertitel, style = MaterialTheme.typography.labelSmall, color = untertitelFarbe ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(wert, style = MaterialTheme.typography.bodyMedium)
            zusatz?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = zusatzFarbe ?: MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// Das breite V rechts in einer Zeile, das ihre Unterzweige auf- und zuklappt; ohne Zweige bleibt der Platz leer.
@Composable
internal fun AufklappKnopf(auf: Boolean, aktiv: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(ZWEIG_KNOPF).height(40.dp).clip(RoundedCornerShape(Radius.chip))
            .then(if (aktiv) Modifier.clickable(onClick = onClick).semantics { contentDescription = S.copyPartsToggle(auf) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (aktiv) ChevronDown(width = 16.dp, modifier = Modifier.rotate(if (auf) 180f else 0f))
    }
}

// Werkzeuge aus Analytics, die mit dem Bestand einer Person rechnen; der Vergleich mit Indizes steht im Portfolioverlauf.
internal val PERSONEN_WERKZEUGE = listOf(PortfolioTool.CHECK, PortfolioTool.DIVIDENDS, PortfolioTool.WEALTH, PortfolioTool.WITHDRAWAL)

// Blatt mit den Werkzeugen fuer eine Person; ein Tipp oeffnet das Werkzeug mit ihrem geschaetzten Depot.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonWerkzeuge(
    state: AppState,
    depot: FremdesDepot,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    // Offenes Werkzeug; der Aufrufer merkt es sich, damit es nach einem geoeffneten Wert wiederkommt (Wiederkehr).
    werkzeug: PortfolioTool? = null,
    onWerkzeug: (PortfolioTool?) -> Unit = {},
    sofort: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.copyToolsTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(S.copyToolsFor(depot.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            PERSONEN_WERKZEUGE.forEach { t -> NavRow(S.ptTitle(t.name), S.ptSubtitle(t.name)) { onWerkzeug(t) } }
        }
    }
    werkzeug?.let { t ->
        CompositionLocalProvider(LocalFremdesDepot provides depot) {
            PortfolioToolSheet(state, t, onDismiss = { onWerkzeug(null) }, onOpen = onOpen, sofort = sofort)
        }
    }
}

// Bild der Werkzeuge einer Person (oder des offenen Werkzeugs) unter einem geoeffneten Wert.
@Composable
internal fun PersonWerkzeugeBild(state: AppState, depot: FremdesDepot, werkzeug: PortfolioTool?) {
    CompositionLocalProvider(LocalFremdesDepot provides depot) {
        if (werkzeug != null) PortfolioToolInhalt(state, werkzeug, onOpen = {})
        else Column(Modifier.fillMaxSize().padding(bottom = 32.dp)) {
            Text(S.copyToolsTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(S.copyToolsFor(depot.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            PERSONEN_WERKZEUGE.forEach { t -> NavRow(S.ptTitle(t.name), S.ptSubtitle(t.name)) {} }
        }
    }
}
