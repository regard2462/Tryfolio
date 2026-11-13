package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.Kaufkraft
import de.troetenrepublik.app.engine.PortfolioBacktest
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Testdepot
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.engine.Vierer
import de.troetenrepublik.app.i18n.BacktestTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Werkzeug „Portfolio-Backtest“ (0.78): bis zu drei Mischungen aus Wertpapieren mit Gewichten, gerechnet auf
// Tagesschlusskursen in Euro mit Umschichtregel, Startbetrag, Sparrate und Kosten (engine/PortfolioBacktest.kt), gegen
// einen Vergleichsindex. Oben das Ergebnis, darunter Mischungen und Regeln. Kursreihen wie im Strategietest: die ganze
// Kursgeschichte in Euro zum heutigen Kurs, sonst die letzten zehn Jahre, Dividenden wieder angelegt.

internal fun btText(key: String, vararg w: String): String = BacktestTexte.text(I18n.lang == Lang.DE, key, w.toList())

internal data class BtPosten(val symbol: String, val name: String, val prozent: Double)

// vorlage: Kennung der Vorlage (Depot oder Musterdepot), solange die Mischung ihr entspricht; sonst leer.
internal data class BtMischung(val vorlage: String, val posten: List<BtPosten>)

internal class BtReihe(val kurse: List<Pair<Long, Double>>, val mitDividenden: Boolean)

// Eingaben und geladene Kursreihen bleiben bis zum Programmende, auch wenn das Blatt geschlossen wird.
internal object BacktestZustand {
    const val MAX_MISCHUNGEN = 3

    // Aus dem Depot kommen hoechstens so viele Werte, die groessten zuerst (jeder Wert braucht einen Kursabruf).
    const val MAX_DEPOTWERTE = 20
    const val DEPOT = "depot"
    val BAENDER = listOf(2, 5, 10, 15)

    val mischungen = mutableStateListOf<BtMischung>()
    var vorbelegt = false
    var gewaehlt by mutableIntStateOf(0)
    var takt by mutableStateOf(PortfolioBacktest.Takt.JAEHRLICH)
    var bandAn by mutableStateOf(false)
    var band by mutableIntStateOf(5)
    var start by mutableStateOf("10000")
    var sparrate by mutableStateOf("0")
    var real by mutableStateOf(false)
    var massstab by mutableStateOf(Portfolioverlauf.MASSSTAEBE.first().id)
    // 0 = ganzer gemeinsamer Zeitraum
    var jahre by mutableIntStateOf(0)
    val offen = mutableStateMapOf<String, Boolean>()
    val reihen = HashMap<String, BtReihe>()
}

// Mischung aus dem Depot: Wert je Basiswert als Gewicht in Prozent, ohne Derivate.
internal fun btDepotMischung(positionen: List<ValuedPosition>): BtMischung? {
    val werte = Testdepot.ausDepot(positionen).take(BacktestZustand.MAX_DEPOTWERTE)
    val summe = werte.sumOf { it.betrag }
    if (werte.isEmpty() || summe <= 0) return null
    return BtMischung(BacktestZustand.DEPOT, werte.map { BtPosten(it.symbol, it.name, (it.betrag / summe * 1000).roundToInt() / 10.0) })
}

internal fun btMusterMischung(s: Strategien.Strategie): BtMischung =
    BtMischung(s.id, s.anteile.map { (sym, w) -> BtPosten(sym, CopyRouten.katalogName(sym) ?: Symbols.name(sym), (w * 1000).roundToInt() / 10.0) })

private fun btName(m: BtMischung, stelle: Int): String = when {
    m.vorlage == BacktestZustand.DEPOT -> S.ptYourDepot
    m.vorlage.isNotEmpty() -> Strategien.finde(m.vorlage)?.let { if (I18n.lang == Lang.DE) it.nameDe else it.nameEn } ?: btText("mischung", (stelle + 1).toString())
    else -> btText("mischung", (stelle + 1).toString())
}

private fun btProzentText(p: Double): String = if (p % 1.0 == 0.0) p.toInt().toString() else Fmt.num(p, 1)

// Kursreihe eines Werts wie im Strategietest: bevorzugt die ganze Kursgeschichte in Euro, sonst zehn Jahre.
private suspend fun btLaden(state: AppState, symbol: String): BtReihe {
    val lang = runCatching { state.market.gesamtHistorieEuro(symbol) }.getOrDefault(emptyList())
    if (lang.size >= 300) {
        val divs = runCatching { state.market.gesamtDividenden(symbol) }.getOrDefault(emptyList())
        return BtReihe(StrategieLabor.ertragsreihe(lang, divs).map { it.t to it.close }, divs.isNotEmpty())
    }
    val kurz = state.dailyCloses(symbol)
    val divs = if (kurz.isEmpty()) emptyList() else runCatching { state.market.dividendHistory(symbol) }.getOrDefault(emptyList())
    return BtReihe(StrategieLabor.ertragsreihe(kurz, divs).map { it.t to it.close }, divs.isNotEmpty())
}

// Rechnung

internal data class BtEingabe(
    val mischungen: List<BtMischung>,
    val regel: PortfolioBacktest.Regel,
    val band: Double,
    val start: Double,
    val sparrate: Double,
    val real: Boolean,
    val massstab: String,
    val jahre: Int,
    val kosten: PortfolioBacktest.Kosten,
    val zins: Double,
    val gewaehlt: Int,
)

internal class BtLauf(val ergebnis: PortfolioBacktest.Ergebnis?, val kennzahlen: PortfolioBacktest.Kennzahlen?, val fehler: String?)

internal class BtRegelZeile(val regel: PortfolioBacktest.Regel, val kennzahlen: PortfolioBacktest.Kennzahlen, val umschichtungen: Int, val kosten: Double)

internal class BtErgebnis(
    val von: Long,
    val bis: Long,
    // Laenge des gemeinsamen Zeitraums vor der Wahl in der Zeitleiste.
    val jahreDa: Double,
    val laeufe: List<BtLauf>,
    val index: BtLauf?,
    val halte: List<PortfolioBacktest.Haltedauer>,
    val regeln: List<BtRegelZeile>,
    val begrenzer: String?,
    val alleMitDividenden: Boolean,
)

// Alle Mischungen und der Index ueber denselben Zeitraum. preise und preisAb (Beginn des Preisindex in ms) nur fuer
// die reale Rechnung. Mischungen ohne Werte oder mit fehlenden Kursen bekommen einen Fehlertext und rechnen nicht mit.
internal fun btRechnen(e: BtEingabe, reihen: Map<String, BtReihe>, massstabSymbol: String, preise: Kaufkraft.Index?, preisAb: Long): BtErgebnis {
    val posten = e.mischungen.map { m -> m.posten.filter { it.prozent > 0 } }
    val fehler = posten.map { p ->
        when {
            p.isEmpty() -> btText("leer")
            p.any { reihen[it.symbol] == null } -> btText("fehlt", p.filter { reihen[it.symbol] == null }.joinToString(", ") { it.name })
            else -> null
        }
    }
    val nutzbar = posten.indices.filter { fehler[it] == null }
    val symbole = (nutzbar.flatMap { i -> posten[i].map { it.symbol } } + listOfNotNull(massstabSymbol.takeIf { it in reihen })).distinct()
    val spanne = if (nutzbar.isEmpty()) null else PortfolioBacktest.zeitraum(symbole.map { reihen.getValue(it).kurse })
    val real = e.real && preise != null
    val bis = spanne?.second ?: 0L
    var von = spanne?.first ?: 0L
    if (real) von = maxOf(von, preisAb)
    if (spanne == null || bis - von < 30 * PortfolioBacktest.TAG) {
        return BtErgebnis(0, 0, 0.0, fehler.map { BtLauf(null, null, it ?: btText("kurz")) }, null, emptyList(), emptyList(), null, false)
    }
    val jahrMs = 365.25 * PortfolioBacktest.TAG
    val jahreDa = (bis - von) / jahrMs
    // Der Wert mit dem spaetesten Beginn begrenzt den Zeitraum
    val begrenzer = symbole.takeIf { it.size > 1 }?.maxByOrNull { reihen.getValue(it).kurse.first().first }
        ?.takeIf { reihen.getValue(it).kurse.first().first >= von - 3 * PortfolioBacktest.TAG }
    if (e.jahre > 0 && e.jahre <= jahreDa + 0.05) von = maxOf(von, bis - (e.jahre * jahrMs).toLong())
    val zahlungen = PortfolioBacktest.sparrate(von, bis, e.sparrate)

    fun lauf(kurse: List<List<Pair<Long, Double>>>, gewichte: List<Double>, regel: PortfolioBacktest.Regel): Pair<PortfolioBacktest.Ergebnis, PortfolioBacktest.Kennzahlen>? {
        val nominal = PortfolioBacktest.rechnen(PortfolioBacktest.Eingabe(kurse, gewichte, regel, e.start, zahlungen, e.kosten, von, bis)) ?: return null
        val kn = PortfolioBacktest.kennzahlen(nominal, e.zins)
        if (!real) return nominal to kn
        val r = PortfolioBacktest.real(nominal) { preise!!.wert(it) } ?: return null
        // Sharpe und Sortino bleiben nominal: der Zins auf Bargeld ist ein nominaler Zins
        return r to PortfolioBacktest.kennzahlen(r, 0.0).copy(sharpe = kn.sharpe, sortino = kn.sortino)
    }

    val laeufe = posten.indices.map { i ->
        if (fehler[i] != null) return@map BtLauf(null, null, fehler[i])
        val l = lauf(posten[i].map { reihen.getValue(it.symbol).kurse }, posten[i].map { it.prozent }, e.regel)
        if (l == null) BtLauf(null, null, btText("kurz")) else BtLauf(l.first, l.second, null)
    }
    val index = reihen[massstabSymbol]?.let { lauf(listOf(it.kurse), listOf(1.0), PortfolioBacktest.Regel(PortfolioBacktest.Takt.NIE)) }
        ?.let { BtLauf(it.first, it.second, null) }
    val g = e.gewaehlt.coerceIn(0, (posten.size - 1).coerceAtLeast(0))
    val gewaehlt = laeufe.getOrNull(g)?.ergebnis
    val halte = gewaehlt?.let { PortfolioBacktest.haltedauern(it.index) }.orEmpty()
    val regeln = if (gewaehlt == null || posten[g].size < 2) emptyList() else {
        val kurse = posten[g].map { reihen.getValue(it.symbol).kurse }
        val gewichte = posten[g].map { it.prozent }
        listOf(
            PortfolioBacktest.Regel(PortfolioBacktest.Takt.NIE), PortfolioBacktest.Regel(PortfolioBacktest.Takt.JAEHRLICH),
            PortfolioBacktest.Regel(PortfolioBacktest.Takt.QUARTAL), PortfolioBacktest.Regel(PortfolioBacktest.Takt.JAEHRLICH, e.band),
        ).mapNotNull { r -> lauf(kurse, gewichte, r)?.let { BtRegelZeile(r, it.second, it.first.umschichtungen, it.first.kosten) } }
    }
    return BtErgebnis(
        von, bis, jahreDa, laeufe, index, halte, regeln, begrenzer?.let { s -> posten.flatten().firstOrNull { it.symbol == s }?.name ?: Symbols.name(s) },
        symbole.all { reihen.getValue(it).mitDividenden },
    )
}

// Werte einer Reihe an anzahl gleichmaessig verteilten Zeitpunkten zwischen von und bis (jeweils der letzte Punkt davor).
internal fun btRaster(reihe: List<Pair<Long, Double>>, von: Long, bis: Long, anzahl: Int): List<Double> {
    if (reihe.isEmpty() || anzahl < 2) return emptyList()
    var j = 0
    return (0 until anzahl).map { i ->
        val t = von + (bis - von) * i / (anzahl - 1)
        while (j + 1 < reihe.size && reihe[j + 1].first <= t) j++
        reihe[j].second
    }
}

private fun regelName(r: PortfolioBacktest.Regel): String =
    r.band?.let { btText("band.name", (it * 100).roundToInt().toString()) } ?: btText("takt." + r.takt.name)

private const val RASTER = 240

@Composable
internal fun BacktestTool(state: AppState, onOpen: (String) -> Unit) {
    katalogBereit()
    val z = BacktestZustand
    val valuation = depotBewertung(state)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val de = I18n.lang == Lang.DE

    // Vorbelegung: das eigene Depot, ohne Depot das klassische Musterdepot
    if (!z.vorbelegt) {
        z.vorbelegt = true
        if (z.mischungen.isEmpty()) z.mischungen += btDepotMischung(valuation.positions) ?: btMusterMischung(Strategien.finde("klassisch") ?: Strategien.alle.first())
    }
    val mischungen = z.mischungen.toList()
    val massstab = Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == z.massstab } ?: Portfolioverlauf.MASSSTAEBE.first()
    val symbole = remember(mischungen, massstab) {
        (mischungen.flatMap { m -> m.posten.filter { it.prozent > 0 }.map { it.symbol } } + massstab.symbol).distinct()
    }

    // Kursreihen je Wert einmal laden, in kleinen Gruppen; ohne Antwort bleibt der Wert ohne Kurse
    var stand by remember { mutableIntStateOf(0) }
    var laedt by remember { mutableStateOf(false) }
    val versucht = remember { HashSet<String>() }
    LaunchedEffect(symbole) {
        val fehlend = symbole.filter { it !in z.reihen && it !in versucht }
        if (fehlend.isEmpty()) return@LaunchedEffect
        laedt = true
        try {
            for (gruppe in fehlend.chunked(6)) {
                val geladen = coroutineScope { gruppe.map { s -> async { s to btLaden(state, s) } }.awaitAll() }
                geladen.forEach { (s, r) -> if (r.kurse.size >= 2) z.reihen[s] = r else versucht += s }
                stand++
            }
        } finally { laedt = false }
    }

    // Verbraucherpreise fuer die reale Rechnung; ohne sie gibt es nur nominal
    val preise by produceState<Pair<Kaufkraft.Index, Long>?>(null) {
        val monate = try { state.verbraucherpreiseEuro() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        value = monate.takeIf { it.size >= 36 }?.let { Kaufkraft.Index(it) to Portfolioverlauf.iso(it.first().m + "-01") }
    }

    val kosten = PortfolioBacktest.Kosten(settings.spreadStockBps.toDouble(), Trading.FEE)
    val eingabe = BtEingabe(
        mischungen = mischungen,
        regel = PortfolioBacktest.Regel(z.takt, if (z.bandAn) z.band / 100.0 else null),
        band = z.band / 100.0,
        start = betragLesen(z.start)?.takeIf { it > 0 } ?: 10_000.0,
        sparrate = betragLesen(z.sparrate)?.takeIf { it > 0 } ?: 0.0,
        real = z.real && preise != null,
        massstab = massstab.symbol,
        jahre = z.jahre,
        kosten = kosten,
        zins = settings.interestPct / 100.0,
        gewaehlt = z.gewaehlt,
    )
    val ergebnis by produceState<BtErgebnis?>(null, eingabe, stand, preise, de) {
        val reihen = HashMap(z.reihen)
        val p = preise
        value = withContext(Dispatchers.Default) { btRechnen(eingabe, reihen, massstab.symbol, p?.first, p?.second ?: 0L) }
    }

    val farben = Kategorie.farben
    val indexFarbe = grau
    val e = ergebnis
    val sparen = eingabe.sparrate > 0
    val g = z.gewaehlt.coerceIn(0, (mischungen.size - 1).coerceAtLeast(0))

    // Ergebnis: je Mischung Endwert und Rendite pro Jahr, darunter der Index
    Spacer(Modifier.height(6.dp))
    mischungen.forEachIndexed { i, m ->
        val l = e?.laeufe?.getOrNull(i)
        BtKernzeile(farben[i % farben.size], btName(m, i), l?.kennzahlen, l?.fehler ?: if (l == null && !laedt && e != null) btText("kurz") else null, laedt || e == null)
    }
    e?.index?.kennzahlen?.let { BtKernzeile(indexFarbe, S.ptBenchName(massstab.schluessel), it, null, false) }

    if (e != null && e.bis > e.von) {
        val zeiten = remember(e.jahreDa) { listOf(1, 3, 5, 10, 15, 20).filter { it <= e.jahreDa - 0.2 }.map { S.toolYearsKurz(it) to it } + (S.toolMax to 0) }
        ToolZeit(S.widgetZeitraum, zeiten, if (zeiten.any { it.second == z.jahre }) z.jahre else 0) { z.jahre = it }
        val linien = remember(e) {
            val zeiten = (0 until RASTER).map { e.von + (e.bis - e.von) * it / (RASTER - 1) }
            e.laeufe.mapIndexedNotNull { i, l ->
                l.ergebnis?.let { Linie(btName(mischungen[i], i), zeiten.zip(btRaster(it.verlauf, e.von, e.bis, RASTER)), farben[i % farben.size]) }
            } + listOfNotNull(
                e.index?.ergebnis?.let { Linie(S.ptBenchName(massstab.schluessel), zeiten.zip(btRaster(it.verlauf, e.von, e.bis, RASTER)), indexFarbe) },
            )
        }
        // Datum und Werte an den Achsen; die Namen mit Endwert stehen schon in den Zeilen darueber
        VergleichsChart(
            linien, format = { Fmt.euro(it, 0) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), hoehe = 210.dp,
            datumKopf = false, legende = false,
        )
        // Die Spanne steht auf der Zeitachse; hier nur, wenn ein Wert den Beginn begrenzt
        if (e.begrenzer != null && z.jahre == 0) Text(
            btText("begrenzer", S.copyRange(Fmt.date(e.von), Fmt.date(e.bis)), e.begrenzer),
            style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
        )

        // Kennzahlen je Mischung und Index nebeneinander
        val spalten = e.laeufe.mapIndexedNotNull { i, l -> l.kennzahlen?.let { Triple(farben[i % farben.size], it, l.ergebnis!!) } } +
            listOfNotNull(e.index?.let { ix -> ix.kennzahlen?.let { Triple(indexFarbe, it, ix.ergebnis!!) } })
        if (spalten.isNotEmpty()) {
            fun zeile(name: String, wert: (PortfolioBacktest.Kennzahlen, PortfolioBacktest.Ergebnis) -> String) = name to spalten.map { wert(it.second, it.third) }
            fun anteil(v: Double?, vorzeichen: Boolean = true) = v?.let { if (vorzeichen) Fmt.signedPct(it * 100, 1) else Fmt.pct(it * 100, 1) } ?: "–"
            val zeilen = listOfNotNull(
                zeile(btText("endwert")) { k, _ -> Fmt.euro(k.endwert, 0) },
                if (sparen) zeile(btText("eingezahlt")) { k, _ -> Fmt.euro(k.eingezahlt, 0) } else null,
                if (spalten.any { it.second.zeitgewichtet != null }) zeile(btText(if (sparen) "rendite.zeit" else "rendite")) { k, _ -> anteil(k.zeitgewichtet) }
                else zeile(btText("rendite.gesamt")) { k, _ -> anteil(k.gesamt) },
                if (sparen) zeile(btText("rendite.geld")) { k, _ -> anteil(k.geldgewichtet) } else null,
                zeile(S.toolVolatility) { k, _ -> anteil(k.schwankung, false) },
                zeile(S.copyMaxDrawdown) { k, _ -> anteil(k.rueckgang?.tiefe) },
                zeile(btText("dauer")) { k, _ ->
                    k.rueckgang?.takeIf { it.tiefe < 0 }?.let { btText(if (it.bis == null) "dauer.laufend" else "dauer.tage", Fmt.num(it.tage.toDouble(), 0)) } ?: "–"
                },
                zeile(btText("sharpe")) { k, _ -> k.sharpe?.let { Fmt.num(it, 2) } ?: "–" },
                zeile(btText("sortino")) { k, _ -> k.sortino?.let { Fmt.num(it, 2) } ?: "–" },
                zeile(btText("umschichtungen")) { _, r -> Fmt.num(r.umschichtungen.toDouble(), 0) },
                zeile(btText("kosten")) { _, r -> Fmt.euro(r.kosten, 0) },
            )
            BtTabelle(spalten.map<_, @Composable () -> Unit> { s -> { BtPunkt(s.first) } }, zeilen, spalte = 112.dp)
        }

        // Rendite je Haltedauer der gewaehlten Mischung
        val farbe = farben[g % farben.size]
        if (e.laeufe.getOrNull(g)?.ergebnis != null) {
            SectionHeader(btText("halte.titel"))
            Text(
                btText("halte.unter", btName(mischungen[g], g)), style = MaterialTheme.typography.bodySmall, color = grau,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (e.halte.isEmpty()) ToolNote(btText("halte.kurz")) else {
                BtHaltedauern(e.halte, farbe)
                HinweisInfo(btText("halte.info", PortfolioBacktest.MIN_FENSTER.toString()))
            }
        }

        // Rueckgangsverlauf, eingeklappt
        val tiefster = spalten.mapNotNull { it.second.rueckgang?.tiefe }.minOrNull()
        if (tiefster != null && tiefster < 0) {
            Trennlinie()
            val auf = z.offen["rueckgang"] == true
            AufklappZeile(btText("rueckgang.titel"), btText("rueckgang.unter", Fmt.pct(-tiefster * 100, 1)), auf) { z.offen["rueckgang"] = !auf }
            if (auf) {
                val rueck = remember(e) {
                    e.laeufe.mapIndexedNotNull { i, l ->
                        l.ergebnis?.let { btRaster(PortfolioBacktest.rueckgangReihe(it.index), e.von, e.bis, RASTER).map { v -> v * 100 } to farben[i % farben.size] }
                    } + listOfNotNull(e.index?.ergebnis?.let { btRaster(PortfolioBacktest.rueckgangReihe(it.index), e.von, e.bis, RASTER).map { v -> v * 100 } to indexFarbe })
                }
                LinesChart(rueck, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(130.dp), baseline = 0.0, scrubben = false)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                    Text(Fmt.date(e.von), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
                    Spacer(Modifier.weight(1f))
                    Text(Fmt.signedPct(tiefster * 100, 1), style = MaterialTheme.typography.labelSmall, color = signal.down, maxLines = 1, softWrap = false)
                }
            }
        }

        // Umschichtregeln nebeneinander fuer die gewaehlte Mischung, eingeklappt
        if (e.regeln.size >= 2) {
            Trennlinie()
            val auf = z.offen["regeln"] == true
            val beste = e.regeln.maxByOrNull { it.kennzahlen.zeitgewichtet ?: it.kennzahlen.gesamt }!!
            AufklappZeile(btText("regeln.titel"), btText("regeln.unter", btName(mischungen[g], g), regelName(beste.regel)), auf) { z.offen["regeln"] = !auf }
            if (auf) {
                val kopf = listOf(btText("rendite"), S.copyMaxDrawdown, btText("umschichtungen"), btText("kosten"))
                BtTabelle(
                    kopf.map<_, @Composable () -> Unit> { k -> { Text(k, style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End) } },
                    e.regeln.map { r ->
                        regelName(r.regel) to listOf(
                            Fmt.signedPct((r.kennzahlen.zeitgewichtet ?: r.kennzahlen.gesamt) * 100, 1), r.kennzahlen.rueckgang?.let { Fmt.signedPct(it.tiefe * 100, 1) } ?: "–",
                            Fmt.num(r.umschichtungen.toDouble(), 0), Fmt.euro(r.kosten, 0),
                        )
                    },
                    spalte = 104.dp,
                )
            }
        }
        Trennlinie()
    }

    // Mischungen
    SectionHeader(btText("mischungen"))
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        mischungen.forEachIndexed { i, m -> Chip(btName(m, i), active = i == g, accent = farben[i % farben.size]) { z.gewaehlt = i } }
        if (mischungen.size < BacktestZustand.MAX_MISCHUNGEN) {
            Chip(btText("hinzu"), active = false) {
                // Neue Mischung beginnt mit dem naechsten Musterdepot, das noch nicht gewaehlt ist
                val frei = Strategien.alle.firstOrNull { s -> mischungen.none { it.vorlage == s.id } } ?: Strategien.alle.first()
                z.mischungen += btMusterMischung(frei)
                z.gewaehlt = z.mischungen.lastIndex
            }
        }
    }
    mischungen.getOrNull(g)?.let { m ->
        BtMischungEditor(state, m, g, valuation.positions) { neu -> if (g < z.mischungen.size) z.mischungen[g] = neu }
        if (mischungen.size > 1) ActionLineWide(btText("entfernen"), danger = true) {
            if (g < z.mischungen.size) z.mischungen.removeAt(g)
            z.gewaehlt = 0
        }
    }

    // Regeln
    SectionHeader(btText("regeln"))
    val taktWahl = PortfolioBacktest.Takt.entries.map { btText("takt." + it.name) to it.name } + (btText("takt.band") to "BAND")
    ToolChoice(btText("umschichtung"), taktWahl, if (z.bandAn) "BAND" else z.takt.name) { wahl ->
        if (wahl == "BAND") z.bandAn = true else { z.bandAn = false; z.takt = PortfolioBacktest.Takt.valueOf(wahl) }
    }
    if (z.bandAn) ToolChoice(btText("band.wahl"), BacktestZustand.BAENDER.map { btText("band.punkte", it.toString()) to it }, z.band) { z.band = it }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BtBetragFeld(btText("start"), z.start, Modifier.weight(1f)) { z.start = it }
        BtBetragFeld(btText("sparrate"), z.sparrate, Modifier.weight(1f)) { z.sparrate = it }
    }
    ToolChoice(btText("vergleich"), Portfolioverlauf.MASSSTAEBE.map { S.ptBenchName(it.schluessel) to it.id }, massstab.id) { z.massstab = it }
    if (preise != null) ToolChoice(btText("kaufkraft"), listOf(btText("nominal") to false, btText("real") to true), z.real) { z.real = it }

    HinweisInfo(
        listOfNotNull(
            btText("hinweis", Fmt.pct(kosten.spreadBps / 200.0, 2), Fmt.euro(kosten.gebuehr, 0)),
            e?.takeIf { it.bis > it.von }?.let { btText(if (it.alleMitDividenden) "dividenden.ja" else "dividenden.teils") },
            if (z.bandAn) btText("band.hinweis") else null,
            if (eingabe.real) btText("real.hinweis") else null,
        ).joinToString(" "),
        kurz = hinweisText("hist"),
    )
    if (eingabe.real) QuellenAnhang(listOf(QuelleLink(S.ecbAbbrev, Vierer.EZB_SEITE)))
}

@Composable
internal fun BtPunkt(farbe: Color, groesse: Dp = 10.dp) {
    Box(Modifier.size(groesse).clip(CircleShape).background(farbe))
}

// Kernzeile einer Mischung: Farbpunkt und Name links (bricht um), rechts Endwert und darunter die Rendite pro Jahr.
@Composable
private fun BtKernzeile(farbe: Color, name: String, k: PortfolioBacktest.Kennzahlen?, fehler: String?, laedt: Boolean) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BtPunkt(farbe)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            if (k == null) Text(fehler ?: if (laedt) S.toolLoading else "", style = MaterialTheme.typography.bodySmall, color = grau)
        }
        if (k != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.euro(k.endwert, 0), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                val rendite = k.zeitgewichtet
                Text(
                    if (rendite != null) btText("pa", Fmt.signedPct(rendite * 100, 1)) else btText("gesamt.kurz", Fmt.signedPct(k.gesamt * 100, 1)),
                    style = MaterialTheme.typography.bodySmall, color = signal.forChange(rendite ?: k.gesamt), maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

// Tabelle mit Bezeichnung links und festen Wertspalten rechts; passt sie nicht in die Breite, laesst sie sich
// waagerecht wischen. Werte bleiben einzeilig, Bezeichnungen brechen um.
@Composable
internal fun BtTabelle(kopf: List<@Composable () -> Unit>, zeilen: List<Pair<String, List<String>>>, spalte: Dp) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frei = maxWidth - 40.dp - spalte * kopf.size
        val links = if (frei > 132.dp) frei else 132.dp
        Column(Modifier.horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(links))
                kopf.forEach { k -> Box(Modifier.width(spalte).padding(start = 8.dp), contentAlignment = Alignment.CenterEnd) { k() } }
            }
            zeilen.forEach { (name, werte) ->
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.width(links))
                    werte.forEach { w ->
                        // Zahlen mit Vorzeichen: Plus gruen, Minus rot
                        val farbe = if (w.startsWith("+")) signal.up else if (w.startsWith("−") || w.startsWith("-")) signal.down else MaterialTheme.colorScheme.onSurface
                        Text(w, style = MaterialTheme.typography.bodyMedium, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(spalte))
                    }
                }
            }
        }
    }
}

// Boxplot je Haltedauer auf gemeinsamer Skala: Linie vom schlechtesten zum besten Ergebnis, Kasten vom unteren zum
// oberen Quartil, Strich am Median, senkrechte Linie bei null. Rechts der Median, darunter Spanne und Verlustanteil.
@Composable
private fun BtHaltedauern(halte: List<PortfolioBacktest.Haltedauer>, farbe: Color) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stark = MaterialTheme.colorScheme.onSurface
    val lo = minOf(0.0, halte.minOf { it.min })
    val hi = maxOf(0.0, halte.maxOf { it.max }).let { if (it - lo < 1e-9) lo + 0.01 else it }
    halte.forEach { h ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.toolYearsKurz(h.jahre), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, modifier = Modifier.width(40.dp))
                Canvas(Modifier.weight(1f).height(26.dp)) {
                    fun x(v: Double) = (size.width * ((v - lo) / (hi - lo))).toFloat()
                    val mitte = size.height / 2
                    val null0 = x(0.0)
                    drawLine(grau.copy(alpha = 0.5f), Offset(null0, 0f), Offset(null0, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(grau, Offset(x(h.min), mitte), Offset(x(h.max), mitte), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    val kasten = size.height * 0.7f
                    drawRoundRect(
                        farbe, Offset(x(h.q1), mitte - kasten / 2), Size((x(h.q3) - x(h.q1)).coerceAtLeast(2.dp.toPx()), kasten),
                        CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                    drawLine(stark, Offset(x(h.median), mitte - kasten / 2), Offset(x(h.median), mitte + kasten / 2), strokeWidth = 2.dp.toPx())
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    btText("halte.median", Fmt.signedPct(h.median * 100, 1)), style = MaterialTheme.typography.bodyMedium,
                    color = signal.forChange(h.median), maxLines = 1, softWrap = false,
                )
            }
            Text(
                btText(
                    "halte.zeile", Fmt.signedPct(h.min * 100, 1), Fmt.signedPct(h.max * 100, 1), Fmt.pct(h.verlust * 100, 0),
                    Fmt.num(h.fenster.toDouble(), 0),
                ),
                style = MaterialTheme.typography.bodySmall, color = if (h.verlust > 0) signal.down else grau, modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

@Composable
private fun BtBetragFeld(label: String, text: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text, onValueChange = { t -> onChange(t.filter { it.isDigit() }.take(9)) },
        label = { Text(label) }, singleLine = true, suffix = { Text("€") },
        visualTransformation = remember { TausenderTransformation(tausenderTrenner()) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(Radius.field), modifier = modifier,
    )
}

// Eine Mischung bearbeiten: Vorlagen (Depot, Musterdepots), Werte mit Gewicht in Prozent, Suche zum Hinzufuegen.
@Composable
private fun BtMischungEditor(state: AppState, m: BtMischung, stelle: Int, eigenes: List<ValuedPosition>, onChange: (BtMischung) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val de = I18n.lang == Lang.DE
    val depot = remember(eigenes) { btDepotMischung(eigenes) }
    val vorlagen = listOfNotNull(depot?.let { S.ptYourDepot to BacktestZustand.DEPOT }) + Strategien.alle.map { (if (de) it.nameDe else it.nameEn) to it.id }
    // Zaehlt mit jeder gewaehlten Vorlage hoch, damit die Gewichtsfelder deren Werte zeigen
    var fassung by remember(stelle) { mutableIntStateOf(0) }
    ToolChoice(btText("vorlage"), vorlagen, m.vorlage) { id ->
        fassung++
        onChange(if (id == BacktestZustand.DEPOT) depot ?: m else Strategien.finde(id)?.let(::btMusterMischung) ?: m)
    }
    Spacer(Modifier.height(6.dp))
    m.posten.forEach { p ->
        // Das Feld behaelt den getippten Text; eine neue Vorlage setzt es neu
        var text by remember(stelle, fassung, p.symbol) { mutableStateOf(btProzentText(p.prozent)) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(p.name, style = MaterialTheme.typography.bodyMedium)
                Text(p.symbol, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            val beschreibung = btText("gewicht")
            OutlinedTextField(
                value = text,
                onValueChange = { neu ->
                    text = neu.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                    val wert = if (text.isBlank()) 0.0 else betragLesen(text)?.takeIf { it in 0.0..100.0 }
                    // Eine geaenderte Vorlage ist eine eigene Mischung
                    if (wert != null) onChange(BtMischung("", m.posten.map { if (it.symbol == p.symbol) it.copy(prozent = wert) else it }))
                },
                singleLine = true, suffix = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.field), modifier = Modifier.width(116.dp).semantics { contentDescription = beschreibung },
            )
            val entfernen = btText("posten.entfernen")
            Text(
                "×", style = MaterialTheme.typography.titleLarge, color = grau,
                modifier = Modifier.textKlick(14.dp) { onChange(BtMischung("", m.posten.filter { it.symbol != p.symbol })) }
                    .padding(horizontal = 14.dp, vertical = 8.dp).semantics { contentDescription = entfernen },
            )
        }
    }
    val summe = m.posten.sumOf { it.prozent }
    if (m.posten.isNotEmpty() && kotlin.math.abs(summe - 100.0) > 0.05) KeyValueRow(btText("summe"), Fmt.pct(summe, 1), hint = btText("summe.hinweis"))

    // Suche: Treffer aus dem Katalog sofort, weitere nach kurzer Wartezeit
    var suche by remember(stelle) { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        val lokal = if (q.length >= 2) Symbols.search(q).map { it.symbol to it.name.ifBlank { it.symbol } } else emptyList()
        treffer = lokal.take(8)
        if (q.length < 2) return@LaunchedEffect
        try {
            delay(350)
            val bekannt = lokal.map { it.first }.toSet()
            val fern = state.market.search(q).filter { (it.type == "EQUITY" || it.type == "ETF" || it.type == "CRYPTOCURRENCY") && it.symbol !in bekannt }
                .map { it.symbol to it.name.ifBlank { it.symbol } }
            treffer = (lokal + fern).distinctBy { it.first }.take(10)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    OutlinedTextField(
        value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, placeholder = { Text(btText("suche")) },
        shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (suche.trim().length >= 2) {
        treffer.filter { (s, _) -> m.posten.none { it.symbol == s } }.forEach { (symbol, name) ->
            Column(
                Modifier.fillMaxWidth().clickable {
                    // Der neue Wert bekommt den Rest bis 100 %, mindestens 10 %
                    val rest = (100.0 - m.posten.sumOf { it.prozent }).takeIf { it >= 1.0 } ?: 10.0
                    onChange(BtMischung("", m.posten + BtPosten(symbol, name, rest)))
                    suche = ""
                }.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(symbol, style = MaterialTheme.typography.bodySmall, color = grau)
            }
        }
    }
}
