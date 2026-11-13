package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KongressAuswertung
import de.troetenrepublik.app.engine.WirktDas
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

// Lernseiten „Wirkt das?“ [0.76]: Ergebnis zuerst, dann die Zeilen dahinter. Insider-Cluster-Kaeufe (gebuendelte
// Rueckschau), Stillhalter-Puts (Protokoll auf dem Geraet), Kongress kopieren (Zahl der Strategie) und IPO-Statistik.
@Composable
fun WirktDasScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    katalogBereit()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val cluster by produceState<WirktDas.Cluster?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { WirktDas.clusterLesen(Daten.text("copytrading/cluster.json")) }.getOrNull() }
    }
    val ipo by produceState<WirktDas.Ipo?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { WirktDas.ipoLesen(Plattform.asset("lernen/ipo.json").decodeToString()) }.getOrNull() }
    }
    var puts by remember { mutableStateOf<List<WirktDas.Put>>(emptyList()) }
    var putsAktiv by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        PutProtokoll.pflegen(state) { puts = it }
        putsAktiv = false
    }
    val scope = rememberCoroutineScope()
    var kongress by remember { mutableStateOf<KongressAuswertung.Strategie?>(null) }
    var kongressLaedt by remember { mutableStateOf<Float?>(null) }
    var kongressLeer by remember { mutableStateOf(false) }
    // Liegen die Kongressdaten schon im Speicher, wird gleich gerechnet; sonst erst auf Tipp (laedt Kurse von ueber tausend Aktien)
    fun kongressRechnen(daten: AppState.KongressDaten) {
        scope.launch {
            val e = withContext(Dispatchers.Default) { KongressAuswertung.strategie(daten.handel, daten.kurse, daten.index, System.currentTimeMillis()) }
            kongress = e
            kongressLeer = e == null
        }
    }
    // Liegen die Daten im Speicher, wird sofort gerechnet, sonst mit Ladebalken geholt und dann gerechnet
    LaunchedEffect(Unit) {
        val fertig = state.kongressFertig()
        if (fertig != null) kongressRechnen(fertig)
        else {
            kongressLaedt = 0f
            val d = withContext(Dispatchers.Default) { runCatching { state.kongressDaten { kongressLaedt = it } }.getOrNull() }
            kongressLaedt = null
            if (d == null) kongressLeer = true else kongressRechnen(d)
        }
    }
    var alleJahre by rememberSaveable { mutableStateOf(false) }
    var allePuts by rememberSaveable { mutableStateOf(false) }
    val sim = rememberInsiderSimulator(state)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.wdTitel, null, onBack) }

        // ---------------------------------------------------------------- Insider-Cluster
        item { WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.insiderSimulator(), Modifier.fillMaxWidth()) { MarktInfoKopf(S.wdClusterTitel, S.wdClusterMedianInfo) } }
        val c = cluster
        if (c == null) item { EmptyHint(S.wdKeineDaten) }
        else {
            // Standardregel: mitgelieferte Rueckschau; andere Regeln rechnet der Simulator im Hintergrund
            val z = if (sim.regeln.standard) c else sim.ergebnis
            item { Zeilenetikett(S.wdClusterRegel(sim.regeln.mindest, sim.regeln.fenster, sim.regeln.halten, if (sim.regeln.standard) c.ab else sim.beginn.ifBlank { c.ab })) }
            item { SimulatorZeile(sim) }
            if (z == null) {
                item {
                    val f = sim.laedt
                    if (f == null && !sim.regeln.standard) EmptyHint(S.wsKeine)
                    else Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        LinearProgressIndicator(progress = { f ?: 0f }, modifier = Modifier.fillMaxWidth())
                        Text(S.kaLaedt, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                item {
                    Kennzahlen(
                        listOf(
                            S.wdClusterRendite(z.halten) to Fmt.signedPct(z.rendite * 100, 1),
                            S.wdClusterIndex to Fmt.signedPct(z.index * 100, 1),
                            S.wdClusterTreffer to Fmt.pct(z.treffer * 100, 0),
                            S.wdClusterMedian to Fmt.signedPct(z.median * 100, 1),
                            S.wdClusterSchlechtester to Fmt.signedPct(z.schlechtester.r * 100, 0) + " " + z.schlechtester.k,
                            S.wdClusterZahl to Fmt.num(z.ausgewertet.toDouble(), 0),
                        ),
                    )
                }
                if (!sim.regeln.standard) item {
                    Zeilenetikett(S.wsHinweis(Fmt.monthYear(java.time.YearMonth.parse(sim.beginn.take(7))), sim.regeln.kosten > 0))
                }
                item {
                    LinienDiagramm(
                        a = z.kurve.map { it[0] to it[1] * 100 }, b = z.kurve.map { it[0] to it[2] * 100 },
                        nameA = S.wdClusterName, nameB = S.wdClusterIndexName, achse = S.wdClusterAchse(z.halten),
                    )
                }
                item {
                    Tabelle(
                        listOf(S.wdKopfJahr, S.wdKopfCluster, S.wdKopfClusterRendite, S.wdKopfIndexRendite, S.wdKopfBesser),
                        z.jahre.sortedByDescending { it.j }.map { j ->
                            listOf(
                                TabZelle(j.j.toString()), TabZelle(Fmt.num(j.n.toDouble(), 0)),
                                TabZelle(Fmt.signedPct(j.r * 100, 1), vorzeichen(j.r)), TabZelle(Fmt.signedPct(j.i * 100, 1), vorzeichen(j.i)),
                                TabZelle(Fmt.pct(j.t * 100, 0)),
                            )
                        },
                    )
                }
                if (z.ohneKurse > 0 || z.offen > 0) item { Zeilenetikett(S.wdClusterAusgenommen(z.ohneKurse, z.offen)) }
                item { HinweisInfo(hinweisText("hist")) }
            }
        }

        // ---------------------------------------------------------------- Stillhalter-Puts
        item { MarktInfoKopf(S.wdPutTitel, S.wdPutErklaerung) }
        val b = WirktDas.bilanz(puts)
        if (puts.isEmpty()) item { EmptyHint(if (putsAktiv) S.wdPutLaedt else S.wdPutLeer) }
        else {
            item {
                Kennzahlen(
                    listOf(
                        S.wdPutProtokoll to Fmt.num(b.gesamt.toDouble(), 0),
                        S.wdPutAbgeschlossen to Fmt.num(b.fertig.toDouble(), 0),
                        S.wdPutNominal to (b.nominalJahr?.let { Fmt.signedPct(it * 100, 0) } ?: "–"),
                        S.wdPutRealisiert to (b.realisiertJahr?.let { Fmt.signedPct(it * 100, 0) } ?: "–"),
                        S.wdPutVerlust to (b.verlustAnteil?.let { Fmt.pct(it * 100, 0) } ?: "–"),
                        S.wdPutSchlechtester to (b.schlechtester?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                    ),
                )
            }
            if (b.nominalJahr != null && b.realisiertJahr != null) item {
                val s = WirktDas.spanne(listOf(b.nominalJahr, b.realisiertJahr))
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    BalkenZeile(S.wdPutNominalKurz, b.nominalJahr, s, Fmt.signedPct(b.nominalJahr * 100, 0), if (b.nominalJahr >= 0) signal.up else signal.down)
                    Spacer(Modifier.height(8.dp))
                    BalkenZeile(S.wdPutRealisiertKurz, b.realisiertJahr, s, Fmt.signedPct(b.realisiertJahr * 100, 0), if (b.realisiertJahr >= 0) signal.up else signal.down)
                }
            }
            item { Zeilenetikett(S.wdPutSeit(Fmt.date(puts.minOf { it.am }), b.offen)) }
            item {
                Tabelle(
                    listOf(S.wdKopfAktie, S.wdKopfVerfall, S.wdKopfStrike, S.wdKopfNominal, S.wdKopfRealisiert),
                    puts.sortedByDescending { it.am }.let { if (allePuts) it else it.take(8) }.map { p ->
                        val r = p.realisiert
                        listOf(
                            TabZelle(p.s), TabZelle(Fmt.dayMonth(p.verfall * 1000)), TabZelle(Fmt.num(p.strike, if (p.strike % 1.0 == 0.0) 0 else 2)),
                            TabZelle(Fmt.pct(p.nominal * 100, 1)),
                            if (r == null) TabZelle(S.wdPutOffen) else TabZelle(Fmt.signedPct(r * 100, 1), vorzeichen(r)),
                        )
                    },
                )
            }
            if (puts.size > 8) item(key = "puts-mehr") {
                ListenPfeil(allePuts, label = if (allePuts) null else S.bbAlle(puts.size)) { allePuts = !allePuts }
            }
        }

        // ---------------------------------------------------------------- Kongress kopieren
        item { SectionHeader(S.wdKongressTitel) }
        val k = kongress
        when {
            k != null -> {
                val abstand = WirktDas.kongressAbstand(k)
                item {
                    Kennzahlen(
                        listOf(
                            S.ptPerYear to (k.renditePa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.kaVergleich to (k.vergleichPa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.copyMaxDrawdown to Fmt.signedPct(k.maxVerlust * 100, 1),
                            S.wdKongressAbstand to (abstand?.let { Fmt.signedPct(it, 1) } ?: "–"),
                        ),
                    )
                }
                // Kleine Kurve: Wertentwicklung in Prozent seit Beginn, Kongress gegen den Vergleichsindex
                val v0 = k.verlauf.firstOrNull()
                val i0 = k.vergleich.firstOrNull()
                if (v0 != null && i0 != null && v0.second > 0 && i0.second > 0 && k.verlauf.size > 2 && k.vergleich.size > 2) item {
                    val t0 = v0.first
                    val tag = 86_400_000.0
                    LinienDiagramm(
                        a = k.verlauf.map { (it.first - t0) / tag to (it.second / v0.second - 1) * 100 },
                        b = k.vergleich.map { (it.first - t0) / tag to (it.second / i0.second - 1) * 100 },
                        nameA = S.kaTitel, nameB = S.wdClusterIndexName, achse = S.copyRange(Fmt.date(v0.first), Fmt.date(k.verlauf.last().first)),
                    )
                }
                item { HinweisInfo(hinweisText("hist")) }
            }
            kongressLeer -> item { EmptyHint(S.copyNoHistory) }
            kongressLaedt != null -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    LinearProgressIndicator(progress = { kongressLaedt ?: 0f }, modifier = Modifier.fillMaxWidth())
                    Text(S.kaLaedt, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 6.dp))
                }
            }
            else -> Unit
        }
        item { NavRow(S.kaTitel, "") { oeffnen(CopyRouten.KONGRESS_KAUFT) } }

        // ---------------------------------------------------------------- IPO-Statistik
        item { SectionHeader(S.wdIpoTitel) }
        val i = ipo
        if (i == null) item { EmptyHint(S.wdKeineDaten) }
        else {
            val g = WirktDas.ipoGesamt(i)
            item {
                Kennzahlen(
                    listOf(
                        S.wdIpoTag1 to Fmt.signedPct(g.t1, 1),
                        S.wdIpoMinus to Fmt.pct(g.neg, 1),
                        S.wdIpoDrei to (g.m3?.let { Fmt.signedPct(it, 0) } ?: "–"),
                        S.wdIpoUnterMarkt to (g.m3Anteil?.let { Fmt.pct(it * 100, 0) } ?: "–"),
                    ),
                )
            }
            item { Zeilenetikett(S.wdIpoRegel(g.n, g.von, g.bis)) }
            val jahre = i.jahre.sortedByDescending { it.j }
            val sicht = if (alleJahre) jahre else jahre.take(12)
            item {
                Tabelle(
                    listOf(S.wdKopfJahr, S.wdKopfIpos, S.wdKopfTag1, S.wdKopfMinus, S.wdKopfDrei),
                    sicht.map { j ->
                        listOf(
                            TabZelle(j.j.toString()), TabZelle(Fmt.num(j.n.toDouble(), 0)), TabZelle(Fmt.signedPct(j.t1, 1), vorzeichen(j.t1)),
                            TabZelle(Fmt.pct(j.neg, 0)),
                            j.m3?.let { TabZelle(Fmt.signedPct(it, 0), vorzeichen(it)) } ?: TabZelle("–"),
                        )
                    },
                )
            }
            item { ActionLineWide(if (alleJahre) S.wdWenigerJahre else S.wdAlleJahre) { alleJahre = !alleJahre } }
            item { QuellenAnhang(listOf(QuelleLink("Jay R. Ritter, University of Florida", "https://site.warrington.ufl.edu/ritter/ipo-data/"))) }
        }
        item { RhythmusAbschnitt(state) } // [0.78] 16-Wochen-Rhythmus
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ------------------------------------------------------------------ Bausteine

private class TabZelle(val text: String, val farbe: Color = Color.Unspecified)

@Composable
private fun vorzeichen(v: Double): Color = if (v > 0) LocalSignal.current.up else if (v < 0) LocalSignal.current.down else Color.Unspecified

@Composable
private fun Zeilenetikett(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

// Tabelle mit festen Spalten: die erste links, die uebrigen rechtsbuendig; nichts bricht um. Die Kopfzeile darf zwischen
// Woertern auf zwei Zeilen stehen.
@Composable
private fun Tabelle(kopf: List<String>, zeilen: List<List<TabZelle>>) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            kopf.forEachIndexed { n, t ->
                Text(
                    t, style = MaterialTheme.typography.labelSmall, color = grau, textAlign = if (n == 0) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.weight(if (n == 0) 1.1f else 1f),
                )
            }
        }
        zeilen.forEach { z ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                z.forEachIndexed { n, c ->
                    Text(
                        c.text, style = MaterialTheme.typography.bodySmall, color = c.farbe, maxLines = 1, softWrap = false,
                        textAlign = if (n == 0) TextAlign.Start else TextAlign.End,
                        modifier = Modifier.weight(if (n == 0) 1.1f else 1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BalkenZeile(label: String, wert: Double, spanne: Double, text: String, farbe: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            val anteil = (kotlin.math.abs(wert) / spanne).toFloat().coerceIn(0.02f, 1f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(anteil).clip(RoundedCornerShape(4.dp)).background(farbe))
        }
    }
}

// Zwei Linien ueber dieselbe Achse (Tage nach dem Kauf, Rendite in Prozent), mit Nulllinie. Name und Endwert stehen am
// Ende jeder Linie, rechts neben dem Diagramm.
@Composable
private fun LinienDiagramm(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>, nameA: String, nameB: String, achse: String, hoehe: Dp = 150.dp) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linieA = LocalSignal.current.link
    val linieB = grau
    val raster = MaterialTheme.colorScheme.outlineVariant
    val alle = a + b
    val lo = minOf(0.0, alle.minOf { it.second })
    val hi = maxOf(0.0, alle.maxOf { it.second })
    val x0 = alle.minOf { it.first }
    val x1 = alle.maxOf { it.first }
    val messer = androidx.compose.ui.text.rememberTextMeasurer()
    val namenStil = MaterialTheme.typography.labelSmall
    val wertStil = MaterialTheme.typography.titleSmall
    val rand = 92.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(hoehe)) {
            val breite = size.width - rand.toPx()
            val spanne = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
            fun punkt(x: Double, y: Double) = Offset(((x - x0) / (x1 - x0).coerceAtLeast(1e-9)).toFloat() * breite, (1 - (y - lo) / spanne).toFloat() * size.height)
            val null0 = punkt(x0, 0.0).y
            drawLine(raster, Offset(0f, null0), Offset(breite, null0), strokeWidth = 1.dp.toPx())
            fun linie(l: List<Pair<Double, Double>>, f: Color) {
                val p = Path()
                l.forEachIndexed { n, (x, y) -> val o = punkt(x, y); if (n == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                drawPath(p, f, style = Stroke(width = 2.dp.toPx()))
            }
            linie(b, linieB)
            linie(a, linieA)
            // Beschriftung am Linienende: Name klein, darunter der Endwert; liegen die Enden nah, rueckt die Beschriftung auseinander
            val breitePx = (rand.toPx() - 8.dp.toPx()).toInt().coerceAtLeast(1)
            fun beschriftung(name: String, ende: Double, f: Color) = messer.measure(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(namenStil.toSpanStyle().copy(color = grau)) { append(name) }
                    append("\n")
                    withStyle(wertStil.toSpanStyle().copy(color = f)) { append(Fmt.signedPct(ende, 1)) }
                },
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = breitePx), maxLines = 2, softWrap = false,
            )
            val tA = beschriftung(nameA, a.last().second, linieA)
            val tB = beschriftung(nameB, b.last().second, linieB)
            val hText = tA.size.height.toFloat()
            var yA = punkt(a.last().first, a.last().second).y - hText / 2
            var yB = punkt(b.last().first, b.last().second).y - hText / 2
            if (kotlin.math.abs(yA - yB) < hText) {
                val mitte = (yA + yB) / 2
                if (yA <= yB) { yA = mitte - hText / 2; yB = mitte + hText / 2 } else { yA = mitte + hText / 2; yB = mitte - hText / 2 }
            }
            val x = breite + 8.dp.toPx()
            drawText(tA, topLeft = Offset(x, yA.coerceIn(0f, (size.height - hText).coerceAtLeast(0f))))
            drawText(tB, topLeft = Offset(x, yB.coerceIn(0f, (size.height - hText).coerceAtLeast(0f))))
        }
        Text(achse, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 4.dp))
    }
}

// ------------------------------------------------------------------ Protokoll der Stillhalter-Puts

// Liest das Protokoll auf dem Geraet, traegt Schlusskurse faelliger Puts nach und nimmt hoechstens einmal je 20 Stunden
// neue Puts auf: je Aktie des Universums der Put mit der hoechsten Rendite aufs Jahr, davon die fuenf besten.
internal object PutProtokoll {
    private val sperre = Mutex()
    private const val TAG = KongressAuswertung.TAG

    suspend fun pflegen(state: AppState, aktualisiert: (List<WirktDas.Put>) -> Unit) = withContext(Dispatchers.Default) {
        sperre.withLock {
            val datei = SichereDatei(File(state.store.ordner, "stillhalter-protokoll.json"))
            var liste = runCatching { WirktDas.protokollLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull().orEmpty()
            aktualisiert(liste)
            val jetzt = System.currentTimeMillis()
            var geaendert = false

            // Schlusskurse am Verfall nachtragen
            val faellig = liste.filter { WirktDas.faellig(it, jetzt) }
            if (faellig.isNotEmpty()) {
                val bedarf = faellig.groupBy { it.s }.mapValues { (_, l) -> l.minOf { it.verfall } * 1000 - 7 * TAG }
                val kurse = runCatching { CopyKurse.laden(state, bedarf).kurse }.getOrDefault(emptyMap())
                liste = liste.map { p ->
                    if (p !in faellig) return@map p
                    val k = kurse[p.s]
                    val tag = Math.floorDiv(p.verfall * 1000, TAG).toInt()
                    val s = if (k != null && !k.leer && k.tagBei(k.anzahl - 1) >= tag) k.am(p.verfall * 1000) else null
                    if (s != null && s > 0) { geaendert = true; p.copy(schluss = s) } else p
                }
            }

            // Neue Puts aufnehmen
            if (WirktDas.neuAufnehmen(liste, jetzt)) {
                val kandidaten = coroutineScope {
                    val gleichzeitig = Semaphore(3)
                    WirktDas.UNIVERSUM.map { s ->
                        async {
                            gleichzeitig.withPermit {
                                val kette = runCatching { state.market.optionskette(s) }.getOrNull() ?: return@withPermit null
                                WirktDas.besterPut(s, kette.first, kette.second, jetzt)
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                val neu = WirktDas.auswaehlen(kandidaten, liste)
                if (neu.isNotEmpty()) { liste = liste + neu; geaendert = true }
            }
            if (geaendert) {
                runCatching { datei.schreiben(WirktDas.protokollSchreiben(liste).toByteArray(Charsets.UTF_8)) }
                aktualisiert(liste)
            }
        }
    }
}
