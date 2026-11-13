package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Anlegerprofil
import de.troetenrepublik.app.engine.DepotCheck
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Symbols


// Depot-Check: die Regeln aus DepotCheck mit Ampel, Kennzahl und Begruendung. Gezaehlt wird das Depot
// ohne Wetten; Gebuehren und Ordervolumen aus den Kaeufen und Verkaeufen der letzten zwoelf Monate.
// onSeite wechselt auf eine andere Seite des Depot-Checks, onOpen oeffnet eine Seite der App.
@Composable
internal fun DepotCheckTool(state: AppState, onOpen: (String) -> Unit = {}, onSeite: (PortfolioTool) -> Unit = {}) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val fremd = LocalFremdesDepot.current
    // Laufende Kosten der ETFs (5.94) aus der Durchschau, je Fonds einmal am Tag abgefragt
    val etfs = valuation.positions.filter { !it.position.isDerivative && it.value > 0 && Symbols.typeOf(it.position.symbol) == AssetType.ETF }
        .map { it.position.symbol }.distinct()
    val ter by produceState(emptyMap<String, Double>(), etfs) { value = etfKosten(state, etfs) }
    // Laender der Fonds fuer die Fremdwaehrung, wie die Waehrungen auf der Seite Streuung
    val etfLaender by produceState<Map<String, Map<String, Double>>>(emptyMap(), etfs) {
        value = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(valuation.positions) }.getOrNull()?.first ?: emptyMap()
    }
    // Verkaufte Optionen zaehlen mit dem Betrag ihrer Verbindlichkeit zu den Hebelprodukten
    val werte = valuation.positions.filter { it.value > 0 || it.position.verkauft }.flatMap { vp ->
        val s = vp.position.symbol
        val typ = Symbols.typeOf(s)
        val w = DepotCheck.Wert(
            name = positionsName(vp.position),
            wert = kotlin.math.abs(vp.value),
            art = when {
                vp.position.isDerivative -> DepotCheck.Art.DERIVAT
                typ == AssetType.ETF -> DepotCheck.Art.ETF
                typ == AssetType.CRYPTO -> DepotCheck.Art.KRYPTO
                typ == AssetType.STOCK -> DepotCheck.Art.AKTIE
                else -> DepotCheck.Art.SONST
            },
            waehrung = quotes[s]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(s)?.currency ?: "EUR",
            branche = Catalog.industryOf(s)?.let { S.industryName(it) } ?: Symbols.find(s)?.sector?.takeIf { it.isNotBlank() }?.let { S.sectorName(it) },
            ergebnisProzent = vp.profitPct,
            ter = if (vp.position.isDerivative) null else ter[s],
            terVorher = if (vp.position.isDerivative || ter[s] == null) null else state.terVorher(s),
            symbol = if (vp.position.isDerivative) "" else s,
        )
        // Bei Personen: Optionen auf einen Wert als eigenes Hebelprodukt
        val opt = fremd?.optionen?.get(vp.position.symbol) ?: 0.0
        if (opt <= 0 || opt >= w.wert) listOf(w)
        else listOf(w.copy(wert = w.wert - opt), w.copy(wert = opt, art = DepotCheck.Art.DERIVAT))
    }
    // Eigene Regeln und Auswertung der Thesen (seit 5.91) auch bei leerem Depot
    if (werte.isEmpty()) {
        ToolNote(S.ptNoPositions)
        if (fremd == null) { EigeneRegeln(state); AnlegerprofilTeil(account.transactions, onOpen); Selbstcheck() }
        return
    }
    val seit = System.currentTimeMillis() - 365L * 86_400_000L
    // Uebernommene Positionen sind keine Orders der App und kosten nichts
    val orders = account.transactions.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.ts >= seit && !it.via.startsWith("IMPORT") }
    val befunde = DepotCheck.pruefen(
        // Reserve ist nur das Guthaben, das verkaufte Optionen nicht binden
        werte, account.cash - account.gebunden, orders.sumOf { it.fee }, orders.sumOf { it.qty * it.price }, orders.size, account.plans.size,
        heim = fremd?.heim ?: "EUR", mitKonto = fremd == null, etf = { s -> etfLaender[s] },
    )
    val gut = befunde.count { it.stufe == DepotCheck.Stufe.GUT }
    val bewertet = befunde.count { it.stufe != DepotCheck.Stufe.HINWEIS }
    Text(
        S.dcSummary(gut, bewertet), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
    )
    // Verteilung der Ampeln als ein Balken
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
        listOf(
            DepotCheck.Stufe.GUT to signal.up, DepotCheck.Stufe.ACHTUNG to signal.warn,
            DepotCheck.Stufe.KRITISCH to signal.down, DepotCheck.Stufe.HINWEIS to MaterialTheme.colorScheme.surfaceContainerHighest,
        ).forEach { (stufe, f) ->
            val n = befunde.count { it.stufe == stufe }
            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(f))
        }
    }
    befunde.forEach { b ->
        val farbe = ampelFarbe(b.stufe, signal, MaterialTheme.colorScheme.onSurfaceVariant)
        // Die groesste Einzelposition steht mit allen Anteilen auf der Seite Streuung
        val zurStreuung = b.regel == DepotCheck.Regel.GROESSTE
        val prozent = Fmt.pct(b.anteil * 100, if (b.regel == DepotCheck.Regel.KOSTEN || b.regel == DepotCheck.Regel.FONDSKOSTEN || b.regel == DepotCheck.Regel.TER_GESTIEGEN) 2 else 0)
        // Position, Branche: Kurzname und Zahl rechts in der Kopfzeile, wie bei den anderen Kennzahlzeilen
        val imKopf = b.regel == DepotCheck.Regel.GROESSTE || b.regel == DepotCheck.Regel.BRANCHE
        CheckZeile(
            b.stufe, farbe, S.dcTitle(b.regel.name), S.dcExplain(b.regel.name),
            kopfNamen = if (imKopf) namenKandidaten(b.name) else emptyList(), kopfWert = if (imKopf) prozent else null,
            weiter = if (zurStreuung) ({ onSeite(PortfolioTool.DIVERSIFICATION) }) else null,
        ) {
            if (!imKopf) Text(S.dcValue(b.regel.name, prozent, b.name, b.anzahl), style = MaterialTheme.typography.bodyMedium, color = farbe)
            if (b.regel == DepotCheck.Regel.GROESSTE && b.anzahl > 1) Text(S.dcEinzelwerte(b.anzahl), style = MaterialTheme.typography.bodySmall, color = farbe)
            if (b.regel == DepotCheck.Regel.FONDSKOSTEN && b.name.isNotBlank()) NamenZusatz(S.dcTeuersten, namenKandidaten(b.name))
            // Mehrere betroffene Positionen: Namen in einem Satz, bricht an den Wortgrenzen um
            if (b.namenListe.isNotEmpty()) Text(b.namenListe.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ToolNote(S.dcNote)
    if (fremd == null) { EigeneRegeln(state); AnlegerprofilTeil(account.transactions, onOpen); Selbstcheck() }
}

private fun ampelFarbe(s: DepotCheck.Stufe, signal: Signal, grau: Color): Color = when (s) {
    DepotCheck.Stufe.GUT -> signal.up
    DepotCheck.Stufe.ACHTUNG -> signal.warn
    DepotCheck.Stufe.KRITISCH -> signal.down
    DepotCheck.Stufe.HINWEIS -> grau
}

// Fassungen eines Anzeigenamens, laengste zuerst, ohne Fonds- und Rechtsformzusaetze
private fun namenKandidaten(name: String): List<String> = namensKuerzungen(name).sortedByDescending { it.length }

private fun ampelZeichen(s: DepotCheck.Stufe): String = when (s) {
    DepotCheck.Stufe.GUT -> AmpelZeichen.GUT
    DepotCheck.Stufe.ACHTUNG -> AmpelZeichen.ACHTUNG
    DepotCheck.Stufe.KRITISCH -> AmpelZeichen.KRITISCH
    DepotCheck.Stufe.HINWEIS -> AmpelZeichen.HINWEIS
}

// Zeile des Checks: Ampel, Titel (mit Kurzname und Zahl rechts, wenn kopfWert gesetzt ist), darunter der Befund. Die
// Erklaerung steht hinter dem Info-Symbol und oeffnet auch per Tipp auf die Zeile; weiter fuehrt zu einer Seite der App.
@Composable
private fun CheckZeile(
    stufe: DepotCheck.Stufe, farbe: Color, titel: String, erklaerung: String,
    kopfNamen: List<String>, kopfWert: String?, weiter: (() -> Unit)?,
    inhalt: @Composable () -> Unit,
) {
    var offen by remember { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable { offen = true }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AmpelPunkt(ampelZeichen(stufe), farbe, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (kopfWert != null) KopfNamenWert(titel, kopfNamen, kopfWert, farbe)
            else Text(titel, style = MaterialTheme.typography.titleSmall)
            inhalt()
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        if (weiter != null) ChevronRight(Modifier.padding(start = 8.dp, top = 4.dp).clickable(onClick = weiter))
    }
    if (offen) MarktInfoBlatt(titel, erklaerung) { offen = false }
}

// Titel links (darf umbrechen), rechts in einer Zeile der laengste Name, der neben den Titel passt, und die Zahl
@Composable
private fun KopfNamenWert(titel: String, namen: List<String>, wert: String, farbe: Color) {
    val titelStil = MaterialTheme.typography.titleSmall
    val stil = MaterialTheme.typography.bodyMedium
    val messer = rememberTextMeasurer()
    val abstand = with(LocalDensity.current) { 12.dp.roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breite = constraints.maxWidth
        val rechts = remember(titel, namen, wert, breite, stil) {
            val frei = breite - abstand - minOf(messer.measure(titel, titelStil, maxLines = 1, softWrap = false).size.width, (breite * 0.4f).toInt())
            val name = namen.firstOrNull { messer.measure("$it  $wert", stil, maxLines = 1, softWrap = false).size.width <= frei } ?: namen.lastOrNull().orEmpty()
            if (name.isEmpty()) wert else "$name  $wert"
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(titel, style = titelStil, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            val text = buildAnnotatedString {
                append(rechts.substring(0, rechts.length - wert.length))
                withStyle(SpanStyle(color = farbe)) { append(wert) }
            }
            Text(text, style = stil, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
        }
    }
}

// Zeile unter dem Titel: Bezeichnung links, rechts der Kurzname in einer Zeile
@Composable
private fun NamenZusatz(label: String, namen: List<String>) {
    val stil = MaterialTheme.typography.bodySmall
    val messer = rememberTextMeasurer()
    val abstand = with(LocalDensity.current) { 12.dp.roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breite = constraints.maxWidth
        val name = remember(label, namen, breite, stil) {
            val frei = breite - abstand - messer.measure(label, stil, maxLines = 1, softWrap = false).size.width
            namen.firstOrNull { messer.measure(it, stil, maxLines = 1, softWrap = false).size.width <= frei } ?: namen.lastOrNull().orEmpty()
        }
        Row {
            Text(label, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(name, style = stil, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
        }
    }
}

// Anlegerprofil aus den eigenen Trades (5.94), mit Ampel wie die Regeln oben.
@Composable
private fun AnlegerprofilTeil(txs: List<de.troetenrepublik.app.data.Transaction>, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val befunde = androidx.compose.runtime.remember(txs) { Anlegerprofil.auswerten(txs) }
    SectionHeader(S.apUeberschrift)
    if (befunde.none { it.merkmal == Anlegerprofil.Merkmal.TREFFER }) ToolNote(S.apZuWenig)
    befunde.forEach { b ->
        val farbe = ampelFarbe(b.stufe, signal, MaterialTheme.colorScheme.onSurfaceVariant)
        val (a, z) = when (b.merkmal) {
            Anlegerprofil.Merkmal.HALTEDAUER -> Fmt.num(b.wert, 0) to ""
            Anlegerprofil.Merkmal.TREFFER, Anlegerprofil.Merkmal.HEBEL -> Fmt.pct(b.wert * 100, 0) to ""
            Anlegerprofil.Merkmal.CHANCE_RISIKO -> Fmt.num(b.wert, 1) to Fmt.signedPct(b.wert2, 1)
            Anlegerprofil.Merkmal.DISPOSITION -> Fmt.num(b.wert, 0) to Fmt.num(b.wert2, 0)
            Anlegerprofil.Merkmal.HAEUFIGKEIT -> Fmt.num(b.wert, 1) to ""
            Anlegerprofil.Merkmal.NACHKAUF -> "" to ""
        }
        CheckZeile(b.stufe, farbe, S.apTitel(b.merkmal.name), S.apErklaerung(b.merkmal.name), emptyList(), null, null) {
            Text(S.apWert(b.merkmal.name, a, z, b.zahl), style = MaterialTheme.typography.bodyMedium, color = farbe)
        }
        // Gewinnfaktor und Schnitt derselben Verkaeufe stehen im Depotverlauf
        if (b.merkmal == Anlegerprofil.Merkmal.TREFFER) NavRow(S.ptClosedSales, S.dp("ap.zuVerkaeufenUnter")) { onOpen(SEITEN_PRAEFIX + DEPOTVERLAUF_VERKAEUFE) }
    }
    HinweisInfo(S.apHinweis)
}

// Selbstcheck (5.94): Aussagen zum Abhaken, die keine Zahl der App pruefen kann. Nur fuer diese Seite gemerkt.
@Composable
private fun Selbstcheck() {
    val fragen = S.scFragen
    val an = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })) {
        mutableStateListOf<Int>()
    }
    SectionHeader(S.scTitel)
    Text(S.scStand(an.size, fragen.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
    fragen.forEachIndexed { i, f ->
        val gesetzt = i in an
        Row(
            Modifier.fillMaxWidth().clickable { if (gesetzt) an.remove(i) else an.add(i) }.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = gesetzt, onCheckedChange = { if (gesetzt) an.remove(i) else an.add(i) })
            Text(f, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
