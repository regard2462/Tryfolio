package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Depotzerlegung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf.Waehrung
import de.troetenrepublik.app.market.Reihe
import kotlin.math.abs

// So viele der letzten Geschaefte zeigen Depotverlauf und Orders; alle stehen in der Aktivitaet.
internal const val GESCHAEFTE_GEZEIGT = 5

// Buchungen, die im Depotverlauf als Kauf oder Verkauf zaehlen.
private val HANDEL = setOf(TxType.BUY, TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY)

// Kauf- und Verkaufsmarken des eigenen Depots: je Tag und Richtung eine Marke mit den Werten und Betraegen.
fun eigeneMarken(transaktionen: List<Transaction>): List<Marke> =
    transaktionen.filter { it.type in HANDEL && it.qty > 0 }
        .groupBy { Depotzerlegung.tagBeginn(it.ts) to (it.bestandsDelta > 0) }
        .map { (k, l) ->
            val (tag, kauf) = k
            val werte = l.groupBy { it.title }.map { (n, h) -> n to h.sumOf { it.qty * it.price } }.sortedByDescending { it.second }
                .joinToString(", ") { (n, b) -> "$n ${Fmt.money(b)}" }
            val datum = Fmt.date(tag + 12 * 3_600_000L)
            Marke(l.minOf { it.ts }, if (kauf) S.pvMarkBuys(datum, werte) else S.pvMarkSells(datum, werte), kauf)
        }.sortedBy { it.t }

// Depotverlauf: Kurve des eigenen Depots mit Kauf- und Verkaufsmarken, dazu die groessten Anstiege und
// Rueckgaenge mit den tragenden Positionen, das Ergebnis je Wert und die eigenen Geschaefte im gewaehlten Zeitraum.
// imBlatt: im Blatt ueber Analytics, mit Titel statt Zurueck-Pfeil; das Wegwischen uebernimmt das Blatt.
@Composable
fun DepotVerlaufScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, imBlatt: Boolean = false, startAbschnitt: String? = null) {
    val account by state.account.collectAsStateWithLifecycle()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val signal = LocalSignal.current

    var reihe by remember { mutableStateOf<Reihe?>(null) }
    var zerlegung by remember { mutableStateOf<AppState.Zerlegung?>(null) }
    var laedt by remember { mutableStateOf(true) }
    LaunchedEffect(account.transactions.size, account.positions.size) {
        laedt = true
        reihe = runCatching { VerlaufLader.reihe(state, VerlaufLader.DEPOT)?.first }.getOrNull()
        zerlegung = runCatching { state.depotZerlegung() }.getOrNull()
        laedt = false
    }
    var von by remember { mutableLongStateOf(0L) }
    var bis by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var raster by rememberSaveable { mutableStateOf(Depotzerlegung.Raster.TAG) }
    var offen by rememberSaveable { mutableStateOf("") }
    var alleWerte by rememberSaveable { mutableStateOf(false) }
    // Sprung zu einem Abschnitt (Schluessel des Listeneintrags), sobald die Liste steht
    var startErledigt by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(zerlegung != null, startAbschnitt) {
        if (!startErledigt && startAbschnitt != null && zerlegung != null) { startErledigt = true; liste.zuSchluessel(startAbschnitt) }
    }
    val marken = remember(account.transactions) { eigeneMarken(account.transactions) }

    val z = zerlegung
    // Beginnt der Zeitraum mit dem Depot (Max), zaehlt alles; die Kurve setzt erst am Tag nach dem ersten Kauf ein
    val ab = remember(z, von) {
        val erster = z?.tage?.firstOrNull()?.beginn ?: 0L
        if (von <= erster + Depotzerlegung.TAG) 0L else Depotzerlegung.tagBeginn(von)
    }
    val tage = remember(z, ab, bis) { z?.tage?.filter { it.beginn >= ab && it.beginn <= bis }.orEmpty() }
    // Je Raster die drei groessten Anstiege und Rueckgaenge
    val bewegungen = remember(tage) {
        Depotzerlegung.Raster.entries.map { r ->
            val abschnitte = Depotzerlegung.abschnitte(tage, r)
            Depotzerlegung.groesste(abschnitte, 3, anstieg = true) to Depotzerlegung.groesste(abschnitte, 3, anstieg = false)
        }
    }
    val summen = remember(tage) { Depotzerlegung.summe(tage) }
    val geschaefte = remember(account.transactions, ab, bis) {
        account.transactions.filter { it.type in HANDEL && it.qty > 0 && it.ts >= ab && it.ts <= bis }.sortedByDescending { it.ts }
    }
    val name = { k: String -> z?.namen?.get(k) ?: k }
    // Beim Ziehen durch den Graphen: nur der Tag unter dem Finger, mit den Werten, die ihn bewegt haben
    val jeTag = remember(z) { z?.tage?.associateBy { it.beginn }.orEmpty() }
    val handelJeTag = remember(account.transactions) {
        account.transactions.filter { it.type in HANDEL && it.qty > 0 }.groupBy { Depotzerlegung.tagBeginn(it.ts) }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .then(if (imBlatt) Modifier else Modifier.sheetDismiss(dismiss) { !liste.canScrollBackward }),
        state = liste,
    ) {
        if (imBlatt) item {
            Text(S.pvTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
        } else item { ScreenHeader(S.pvTitle, null, onBack) }
        item {
            PortfolioKarte(
                state, titel = S.ptYourDepot, haupt = reihe?.let { it to Waehrung.EUR }, laedt = laedt && reihe == null,
                hinweis = S.ptTimeWeightedHint, eigener = VerlaufLader.DEPOT, marken = marken,
                standardZeitraum = VerlaufZeitraum.MAX, leerText = S.dvNone, onZeitraum = { von = it },
                eigenerZeitraum = true, onEnde = { bis = it },
                onJahr = { state.springeZu(de.troetenrepublik.app.data.WidgetZiel.PERFORMANCE) },
                erklaerung = { t ->
                    val tag = Depotzerlegung.tagBeginn(t)
                    TagErklaerung(jeTag[tag], handelJeTag[tag].orEmpty(), name)
                },
            )
        }
        if (z == null) {
            item {
                Text(if (laedt) S.pvLoading else S.dvNone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            }
            return@LazyColumn
        }

        // Groesste Anstiege und Rueckgaenge mit ihren Ursachen
        item { SectionHeader(S.dvMoves) }
        item {
            // Tag, Woche, Monat in der Zeitleiste wie beim Chart, ohne Pillen und ohne Wischen
            ZeitLeiste(
                Depotzerlegung.Raster.entries.map { S.dvRaster(it.name) }, selected = raster.ordinal,
                onSelect = { raster = Depotzerlegung.Raster.entries[it] }, modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        listOf(true, false).forEach { hoch ->
            item(key = "kopf-$hoch") {
                Text(if (hoch) S.dvRises else S.dvDrops, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 2.dp))
            }
            val je = bewegungen.map { if (hoch) it.first else it.second }
            val r = raster
            val zeilen = je[r.ordinal]
            if (zeilen.isEmpty()) item(key = "leer-$hoch") {
                Text(S.dvNoMoves, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            items(zeilen.size, key = { i -> "b-$hoch-${r.name}-$i" }) { i ->
                val a = zeilen[i]
                val id = "$hoch-${r.name}-${a.beginn}"
                BewegungZeile(a, r, name, offen == id) { offen = if (offen == id) "" else id }
            }
        }

        // Ergebnis je Wert im Zeitraum
        if (summen.isNotEmpty()) {
            item { SectionHeader(S.dvContrib) }
            val groesst = summen.maxOf { abs(it.second) }
            items(if (alleWerte) summen else summen.take(8), key = { "w-" + it.first }) { (k, betrag) ->
                val zusatz = when {
                    k in z.geschaetzt -> S.dvEstimated
                    k in z.imDepot -> S.dvHeld
                    else -> S.copySold
                }
                Column(
                    Modifier.fillMaxWidth().clickable { z.symbole[k]?.let(onOpen) }.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InstrumentLogo(state, z.symbole[k] ?: k, name(k), size = 28)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            NamenText(name(k), style = MaterialTheme.typography.bodyMedium)
                            Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ChangeValue(betrag, Fmt.signedMoney(betrag), style = MaterialTheme.typography.bodyMedium)
                    }
                    Balken(abs(betrag) / groesst, if (betrag >= 0) signal.up else signal.down)
                }
            }
            if (summen.size > 8) item { ActionLine(if (alleWerte) S.dvFewer else S.dvAll(summen.size)) { alleWerte = !alleWerte } }
        }

        // Abgeschlossene Verkaeufe und dasselbe Geld im Index
        item(key = "markt") { Column(Modifier.fillMaxWidth()) { MarktVergleichTeile(state, onOpen) } }

        // Eigene Kaeufe und Verkaeufe im Zeitraum
        if (geschaefte.isNotEmpty()) {
            item { SectionHeader(S.dvTrades) }
            items(geschaefte.take(GESCHAEFTE_GEZEIGT), key = { "g-" + it.id }) { t ->
                // Stueck ins Depot: Kauf, Rueckkauf und Ausgleich einer verkauften Option
                val kauf = t.bestandsDelta > 0
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(t.symbol.ifBlank { t.posKey }) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(28.dp)) { DirectionTriangle(if (kauf) 1.0 else -1.0, color = if (kauf) signal.up else signal.down) }
                    Column(Modifier.weight(1f)) {
                        NamenText(t.title.ifBlank { t.symbol }, style = MaterialTheme.typography.bodyMedium)
                        PunktText(
                            handelsArt(t) + " · " + Fmt.date(t.ts) + " · " + S.dvQtyAt(Fmt.qty(t.qty), Fmt.priceEuro(t.price)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Fmt.money(t.qty * t.price), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Alle Geschaefte stehen in der Aktivitaet unter Handel
            item { NavRow(S.dp("dv.alle")) { onOpen(SEITEN_PRAEFIX + AKTIVITAET_HANDEL) } }
        }

        item {
            HinweisInfo(S.dvNote)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// Erklaerung unter dem Graphen beim Ziehen: Veraenderung des Tages, die hoechstens zwei Werte, die sie
// getragen haben, und die Kaeufe und Verkaeufe dieses Tages nur mit Namen.
@Composable
private fun TagErklaerung(tag: Depotzerlegung.Tag?, handel: List<Transaction>, name: (String) -> String) {
    val signal = LocalSignal.current
    val leise = MaterialTheme.colorScheme.onSurfaceVariant
    val a = tag?.let { Depotzerlegung.Abschnitt(it.beginn, it.beginn + Depotzerlegung.TAG - 1, it.je, it.wertVorher) }
    if (a != null && abs(a.summe) >= 0.01) {
        val hoch = a.summe >= 0
        val farbe = if (hoch) signal.up else signal.down
        Text(
            Fmt.signedMoney(a.summe) + (a.prozent?.let { "  " + Fmt.signedPct(it, 2) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = farbe,
        )
        val haupt = Depotzerlegung.ursachen(a).filter { if (hoch) it.second > 0 else it.second < 0 }.take(2)
        if (haupt.isNotEmpty()) Text(
            S.dvBy(haupt.joinToString(", ") { (k, v) -> name(k) + " " + Fmt.signedMoney(v) }),
            style = MaterialTheme.typography.bodySmall, color = leise,
        )
    }
    fun kurz(namen: List<String>) =
        namen.take(2).joinToString(", ") + if (namen.size > 2) " " + S.dvUndWeitere(namen.size - 2) else ""
    val kaeufe = handel.filter { it.bestandsDelta > 0 }.map { it.title.ifBlank { it.symbol } }.distinct()
    val verkaeufe = handel.filter { it.bestandsDelta <= 0 }.map { it.title.ifBlank { it.symbol } }.distinct()
    if (kaeufe.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
        DirectionTriangle(1.0, color = signal.up)
        Spacer(Modifier.width(6.dp))
        Text(S.dvTagKauf(kurz(kaeufe)), style = MaterialTheme.typography.bodySmall, color = signal.up)
    }
    if (verkaeufe.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
        DirectionTriangle(-1.0, color = signal.down)
        Spacer(Modifier.width(6.dp))
        Text(S.dvTagVerkauf(kurz(verkaeufe)), style = MaterialTheme.typography.bodySmall, color = signal.down)
    }
}

// Ein Anstieg oder Rueckgang: Zeitraum, Ergebnis und die Werte, die ihn getragen haben; aufgeklappt alle Werte.
@Composable
private fun BewegungZeile(
    a: Depotzerlegung.Abschnitt,
    raster: Depotzerlegung.Raster,
    name: (String) -> String,
    offen: Boolean,
    onClick: () -> Unit,
) {
    val signal = LocalSignal.current
    val hoch = a.summe >= 0
    val farbe = if (hoch) signal.up else signal.down
    val mitte = a.beginn + 12 * 3_600_000L
    val wann = when (raster) {
        Depotzerlegung.Raster.TAG -> Fmt.date(mitte)
        Depotzerlegung.Raster.WOCHE -> S.dvWeek(Fmt.date(mitte))
        Depotzerlegung.Raster.MONAT -> java.time.Instant.ofEpochMilli(mitte).atZone(java.time.ZoneOffset.UTC)
            .let { Fmt.monthYear(java.time.YearMonth.of(it.year, it.monthValue)) }
        Depotzerlegung.Raster.QUARTAL -> java.time.Instant.ofEpochMilli(mitte).atZone(java.time.ZoneOffset.UTC)
            .let { "Q" + ((it.monthValue - 1) / 3 + 1) + " " + it.year }
        Depotzerlegung.Raster.JAHR -> java.time.Instant.ofEpochMilli(mitte).atZone(java.time.ZoneOffset.UTC).year.toString()
    }
    val ursachen = Depotzerlegung.ursachen(a)
    val haupt = ursachen.filter { if (hoch) it.second > 0 else it.second < 0 }.take(3)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp)) { DirectionTriangle(if (hoch) 1.0 else -1.0, color = farbe) }
            Text(wann, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                // Betraege und Prozent bleiben in einer Zeile
                Text(Fmt.signedMoney(a.summe), style = MaterialTheme.typography.bodyMedium, color = farbe, maxLines = 1, softWrap = false)
                a.prozent?.let { Text(Fmt.signedPct(it, 2), style = MaterialTheme.typography.bodySmall, color = farbe, maxLines = 1, softWrap = false) }
            }
        }
        if (haupt.isNotEmpty()) Text(
            S.dvBy(haupt.joinToString(", ") { (k, v) -> name(k) + " " + Fmt.signedMoney(v) }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp),
        )
        if (offen) {
            val groesst = ursachen.maxOfOrNull { abs(it.second) } ?: 1.0
            Column(Modifier.padding(start = 28.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ursachen.forEach { (k, v) ->
                    Column {
                        Row {
                            NamenText(name(k), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(Fmt.signedMoney(v), style = MaterialTheme.typography.bodySmall, color = if (v >= 0) signal.up else signal.down)
                        }
                        Balken(abs(v) / groesst, if (v >= 0) signal.up else signal.down)
                    }
                }
            }
        }
    }
}

// Waagerechter Anteilsbalken (0 bis 1) in farbe auf grauem Grund.
@Composable
private fun Balken(anteil: Double, farbe: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth(anteil.coerceIn(0.01, 1.0).toFloat()).height(4.dp).background(farbe))
    }
}
