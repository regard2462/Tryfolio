package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.engine.Entnahmeplan
import de.troetenrepublik.app.engine.HistorieRechner
import de.troetenrepublik.app.engine.PortfolioTools
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.engine.Steuerwerte
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.EntnahmeTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

// Werkzeug „Entnahmeplan“ (0.78): Ansparen bis zum Zielvermoegen und Entnahme ueber die echten Renditefolgen der Geschichte.
// Zwei Teile, gemeinsame Annahmen (Land der Renditen, Aktienanteil, Kosten, deutsche Steuer). Rechnung in engine/Entnahmeplan.kt.

internal fun entnahmeText(key: String, vararg w: String): String = EntnahmeTexte.text(I18n.lang == Lang.DE, key, w.toList())

private fun t(key: String, vararg w: String) = entnahmeText(key, *w)

// Dauer der Entnahme zur Wahl, in Jahren
private val DAUERN = listOf(15, 20, 25, 30, 40, 50, 60)

// Grenzen der schwankenden Regeln als Anteil des Bedarfs; 0 = keine
private val UNTERGRENZEN = listOf(0f, 0.5f, 0.75f, 0.9f)
private val OBERGRENZEN = listOf(0f, 1.25f, 1.5f)

// Ergebnis einer Rechnung; fertig auch dann, wenn nichts herauskam
private class Fertig<T>(val wert: T?)

private class EntnahmeStand(
    val ergebnis: Entnahmeplan.Ergebnis, val ziehung: Entnahmeplan.Ziehung?, val alle: Double?, val teil: Double?,
    val vor: DoubleArray, val rueck: DoubleArray,
)

private fun rund(v: Double, schritt: Double): Float = ((v / schritt).roundToInt() * schritt).toFloat()

@Composable
internal fun EntnahmeplanTool(state: AppState) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val signal = LocalSignal.current
    val deutsch = I18n.lang == Lang.DE
    var stand by remember { mutableStateOf(HistorieLader.gespeichert()) }
    LaunchedEffect(Unit) { if (stand == null) stand = HistorieLader.laden() }
    val geladen = stand ?: run { ToolNote(t("laden")); return }
    val d = geladen.jst
    val kennungen = remember(d) { d?.let { Entnahmeplan.kennungen(it) }.orEmpty() }
    if (d == null || kennungen.isEmpty()) { ToolNote(t("ohneDaten")); return }

    var teil by rememberSaveable { mutableIntStateOf(0) }
    var land by rememberSaveable { mutableStateOf(kennungen.first()) }
    val reihe = remember(d, land) { Entnahmeplan.reihe(d, land) } ?: return
    // Ohne eigene Wahl (-1) folgen Aktienanteil und Kursgewinn dem Depot
    var aktienWahl by rememberSaveable { mutableFloatStateOf(-1f) }
    val aktien = if (aktienWahl >= 0f) aktienWahl else if (valuation.total > 0.0) rund(valuation.securities / valuation.total * 100.0, 5.0).coerceIn(0f, 100f) else 60f
    var gewinnWahl by rememberSaveable { mutableFloatStateOf(-1f) }
    val gewinn = if (gewinnWahl >= 0f) gewinnWahl
    else if (valuation.securities > 0.0) rund((1.0 - valuation.invested / valuation.securities) * 100.0, 5.0).coerceIn(0f, 100f) else 0f
    var kosten by rememberSaveable { mutableFloatStateOf(0.2f) }
    var steuerAn by rememberSaveable { mutableStateOf(deutsch) }
    var jahre by rememberSaveable { mutableIntStateOf(30) }
    var bedarf by rememberSaveable { mutableFloatStateOf(24_000f) }
    var annahmenOffen by rememberSaveable { mutableStateOf(false) }

    val werte = remember { Steuerwerte.jetzt() }
    val teilfrei = remember { Trading.teilfreistellung("", "", AssetType.ETF) }
    val steuer = if (steuerAn) Entnahmeplan.Steuer(werte.satz, teilfrei, werte.pauschbetrag, gewinn / 100.0) else null
    val zins = remember(reihe, aktien, kosten) { Entnahmeplan.mittlereRendite(reihe, aktien / 100.0, kosten / 100.0) }
    val a2 = remember { HistorieRechner.A3.entries.associate { it.value to it.key } }
    fun landName(k: String): String =
        if (k == Entnahmeplan.WELT) S.hrWeltName else a2[k]?.let { c -> Regionen.LAENDER.firstOrNull { it.code == c }?.name(deutsch) } ?: k
    val anteil95 = Fmt.pct(Entnahmeplan.ZIEL_ANTEIL * 100.0, 0)

    // Teil 1: Startwert und Sparrate
    val plaene = remember(account.plans) {
        account.plans.filter { !it.paused }.sumOf { p ->
            p.amount * when (p.interval) {
                PlanInterval.WEEKLY -> 52.0 / 12.0; PlanInterval.BIWEEKLY -> 2.0; PlanInterval.MONTHLY -> 1.0; PlanInterval.QUARTERLY -> 1.0 / 3.0
            }
        }
    }
    var startWahl by rememberSaveable { mutableIntStateOf(-1) }
    val startMode = PortfolioTools.entnahmeStart(startWahl, valuation.securities)
    var eigenerStart by rememberSaveable { mutableFloatStateOf(rund(valuation.securities, 1_000.0).coerceIn(0f, 2_000_000f)) }
    val start = when (startMode) { 0 -> valuation.securities; 1 -> valuation.total; else -> eigenerStart.toDouble() }
    var sparrate by rememberSaveable { mutableFloatStateOf(if (plaene > 0) rund(plaene, 25.0).coerceIn(0f, 5_000f) else 500f) }

    val zielPlan = Entnahmeplan.Plan(0.0, bedarf.toDouble(), jahre, aktien / 100.0, kosten / 100.0, steuer = steuer)
    val startRund = rund(start, 100.0).toDouble()
    var freiheit by remember { mutableStateOf<Fertig<Entnahmeplan.Freiheit>?>(null) }
    LaunchedEffect(reihe, zielPlan, startRund, sparrate) {
        freiheit = Fertig(withContext(Dispatchers.Default) {
            Entnahmeplan.freiheit(reihe, startRund, startRund * (1.0 - (steuer?.gewinnanteil ?: 0.0)), sparrate.toDouble(), zielPlan) { ensureActive() }
        })
    }
    val ziel = freiheit?.wert?.ziel

    // Teil 2: Vermoegen, Regel und Grenzen
    var vermWahl by rememberSaveable { mutableIntStateOf(-1) }
    val vermMode = if (vermWahl == 2 && ziel == null) -1 else vermWahl
    val vermEff = PortfolioTools.entnahmeVermoegen(vermMode, valuation.securities, ziel)
    var eigenesVermoegen by rememberSaveable { mutableFloatStateOf(rund(valuation.securities, 5_000.0).coerceIn(0f, 5_000_000f)) }
    val vermoegen = when (vermEff) { 0 -> valuation.securities; 1 -> valuation.total; 2 -> ziel ?: 0.0; else -> eigenesVermoegen.toDouble() }
    var regelName by rememberSaveable { mutableStateOf(Entnahmeplan.Regel.FEST.name) }
    val regel = Entnahmeplan.Regel.valueOf(regelName)
    var untergrenze by rememberSaveable { mutableFloatStateOf(0f) }
    var obergrenze by rememberSaveable { mutableFloatStateOf(0f) }
    val schwankend = regel == Entnahmeplan.Regel.PROZENT || regel == Entnahmeplan.Regel.RESTLAUFZEIT

    ToolChoice(null, listOf(t("teil.ansparen") to 0, t("teil.entnahme") to 1), teil) { teil = it }

    val annahmen: @Composable () -> Unit = {
        ToolZeit(t("dauer"), DAUERN.map { S.toolYearsKurz(it) to it }, jahre) { jahre = it }
        AufklappZeile(
            t("annahmen"),
            listOfNotNull(
                landName(land), t("annahmen.aktien", Fmt.pct(aktien.toDouble(), 0)), t("annahmen.kosten", Fmt.pct(kosten.toDouble(), 2)),
                t("annahmen.steuer").takeIf { steuerAn },
            ).joinToString(" · "),
            annahmenOffen,
        ) { annahmenOffen = !annahmenOffen }
        if (annahmenOffen) {
            ToolChoice(t("land"), kennungen.map { landName(it) to it }, land) { land = it }
            if (land == "USA") {
                val vergleich = remember(d) {
                    val usa = Entnahmeplan.reihe(d, "USA")
                    val welt = Entnahmeplan.reihe(d, Entnahmeplan.WELT)
                    if (usa == null || welt == null) null
                    else Triple(Entnahmeplan.mittlereRendite(usa, 1.0, 0.0) * 100.0, welt.laender, Entnahmeplan.mittlereRendite(welt, 1.0, 0.0) * 100.0)
                }
                if (vergleich != null && vergleich.first > vergleich.third) {
                    Text(
                        t("land.usa", Fmt.pct(vergleich.first, 1), vergleich.second.toString(), Fmt.pct(vergleich.third, 1)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    )
                }
            }
            SliderRow(t("aktien"), Fmt.pct(aktien.toDouble(), 0), aktien, 0f..100f, 5f) { aktienWahl = it }
            SliderRow(t("kosten"), Fmt.pct(kosten.toDouble(), 2), kosten, 0f..2f, 0.05f) { kosten = it }
            ToggleRow(
                t("steuer"), steuerAn,
                hint = t("steuer.hint", Fmt.pct(werte.satz * 100.0, 3), Fmt.pct(teilfrei * 100.0, 0), Fmt.euro(werte.pauschbetrag, 0)),
            ) { steuerAn = it }
            if (steuerAn) SliderRow(t("gewinn"), Fmt.pct(gewinn.toDouble(), 0), gewinn, 0f..100f, 5f) { gewinnWahl = it }
        }
    }

    val zuKurz = remember(reihe, jahre) { Entnahmeplan.starts(reihe, jahre).size < HistorieRechner.MIN_FAELLE }
    if (teil == 0) {
        val eingaben: @Composable () -> Unit = {
            EpWahl(S.ptStartValue, listOf(
                Triple(t("start.wertpapiere"), Fmt.compactEuro(valuation.securities), 0),
                Triple(t("start.gesamt"), Fmt.compactEuro(valuation.total), 1),
                Triple(t("start.eigen"), null, 3),
            ), startMode) { startWahl = it }
            if (startMode == 3) SliderRow(S.ptStartValue, Fmt.euro(eigenerStart.toDouble(), 0), eigenerStart, 0f..2_000_000f, 1_000f) { eigenerStart = it }
            SliderRow(t("sparrate"), Fmt.euro(sparrate.toDouble(), 0), sparrate, 0f..5_000f, 25f) { sparrate = it }
            SliderRow(t("bedarf"), Fmt.euro(bedarf.toDouble(), 0), bedarf, 0f..120_000f, 1_000f) { bedarf = it }
            annahmen()
        }
        val fertig = freiheit ?: run { eingaben(); ToolNote(t("laden")); return }
        val f = fertig.wert ?: run {
            eingaben()
            ToolNote(if (zuKurz) t("zuKurz", S.toolYears(jahre), landName(land)) else t("ziel.unerreichbar", anteil95))
            return
        }
        val a = f.ansparen
        val minJ = a.min?.let { Entnahmeplan.volleJahre(it) }
        val maxJ = a.max?.let { Entnahmeplan.volleJahre(it) }
        val kern = when {
            minJ == null || maxJ == null -> t("ziel.nie", a.horizont.toString())
            maxJ == 0 -> t("ziel.heute")
            minJ == maxJ -> S.toolYears(minJ)
            else -> t("ziel.spanne", minJ.toString(), maxJ.toString())
        }
        val mitte = when {
            maxJ == 0 -> null
            a.median != null -> t("ziel.mitte", S.toolYears(Entnahmeplan.volleJahre(a.median)))
            else -> t("ziel.mitteNie", a.horizont.toString())
        }
        val offen = if (a.erreicht in 1 until a.faelle) t("ziel.offen", (a.faelle - a.erreicht).toString(), a.faelle.toString(), a.horizont.toString()) else null
        EpKopf(
            t("ziel.kopf"), kern, listOfNotNull(mitte, offen).joinToString(" · ").ifEmpty { null },
            when { minJ == null -> signal.down; maxJ == 0 -> signal.up; else -> null },
        )
        KeyValueRow(
            t("ziel.betrag"), Fmt.euro(Entnahmeplan.zielAnzeige(f.ziel), 0),
            hint = if (f.ziel > 0.0) t("ziel.betragHint", Fmt.pct(bedarf / f.ziel * 100.0, 1), S.toolYears(jahre), anteil95) else null,
        )
        eingaben()
        if (maxJ != 0) {
            val ende = if (a.erreicht == a.faelle && maxJ != null) min(a.horizont, maxJ + 1).coerceAtLeast(2) else a.horizont
            val linie = remember(a, ende) { DoubleArray(ende + 1) { f.ziel } }
            val farbe = MaterialTheme.colorScheme.onSurface
            FanChart(
                median = remember(a, ende) { a.p50.copyOf(ende + 1) },
                outer = remember(a, ende) { a.p10.copyOf(ende + 1) to a.p90.copyOf(ende + 1) }, inner = null, reference = linie, color = farbe,
                startLabel = S.today, endLabel = S.ptInYears(ende),
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp, vertical = 10.dp),
            )
            ChartLegend(listOf(S.ptMedian to farbe, S.ptBandLabel to farbe.copy(alpha = 0.35f), t("ziel.linie") to MaterialTheme.colorScheme.onSurfaceVariant))
            if (a.schnellStart != null && minJ != null) KeyValueRow(t("ziel.schnell", a.schnellStart.toString()), S.toolYears(minJ), signal.up)
            if (a.median != null) KeyValueRow(t("ziel.mittel"), S.toolYears(Entnahmeplan.volleJahre(a.median)))
            if (a.langsamStart != null && maxJ != null) KeyValueRow(t("ziel.langsam", a.langsamStart.toString()), S.toolYears(maxJ), signal.down)
            if (steuerAn && a.gewinnanteil != null) KeyValueRow(t("ziel.gewinn"), Fmt.pct(a.gewinnanteil * 100.0, 0), hint = t("ziel.gewinnHint"))
        }
        KeyValueRow(t("startjahre"), Fmt.num(a.faelle.toDouble(), 0), hint = S.histZeitraum(a.ersterStart, a.letzterStart))
    } else {
        val eingaben: @Composable () -> Unit = {
            EpWahl(t("vermoegen"), listOfNotNull(
                Triple(t("start.wertpapiere"), Fmt.compactEuro(valuation.securities), 0),
                Triple(t("start.gesamt"), Fmt.compactEuro(valuation.total), 1),
                ziel?.takeIf { it > 0.0 }?.let { Triple(t("start.ziel"), Fmt.euro(Entnahmeplan.zielAnzeige(it), 0), 2) },
                Triple(t("start.eigen"), null, 3),
            ), vermEff) { vermWahl = it }
            if (vermEff == 3) SliderRow(t("vermoegen"), Fmt.euro(eigenesVermoegen.toDouble(), 0), eigenesVermoegen, 0f..5_000_000f, 5_000f) { eigenesVermoegen = it }
            SliderRow(t("bedarf"), Fmt.euro(bedarf.toDouble(), 0), bedarf, 0f..120_000f, 1_000f) { bedarf = it }
            Entnahmeplan.Regel.entries.let { regeln ->
                ToolFelder(t("regel"), regeln.map { t("regel." + it.name) to it }, regel, if (regeln.size == 3) 3 else 2) { regelName = it.name }
            }
            if (schwankend) {
                ToolChoice(t("untergrenze"), UNTERGRENZEN.map { (if (it == 0f) t("grenze.keine") else Fmt.pct(it * 100.0, 0)) to it }, untergrenze) { untergrenze = it }
                ToolChoice(t("obergrenze"), OBERGRENZEN.map { (if (it == 0f) t("grenze.keine") else Fmt.pct(it * 100.0, 0)) to it }, obergrenze) { obergrenze = it }
            }
            annahmen()
        }
        if (vermoegen < 1.0) { eingaben(); ToolNote(S.ptNoStart); return }
        val plan = Entnahmeplan.Plan(
            rund(vermoegen, 100.0).toDouble(), bedarf.toDouble(), jahre, aktien / 100.0, kosten / 100.0, regel,
            if (schwankend) untergrenze.toDouble() else 0.0,
            if (schwankend && obergrenze > 0f) obergrenze.toDouble() else Double.POSITIVE_INFINITY, steuer, zins,
        )
        var rechnung by remember { mutableStateOf<Fertig<EntnahmeStand>?>(null) }
        LaunchedEffect(reihe, plan) {
            rechnung = Fertig(withContext(Dispatchers.Default) {
                val e = Entnahmeplan.historisch(reihe, plan) { ensureActive() } ?: return@withContext null
                val fest = plan.regel == Entnahmeplan.Regel.FEST || plan.regel == Entnahmeplan.Regel.LEITPLANKEN
                val folge = Entnahmeplan.folge(reihe, e.schlechtPlatz, plan.jahre)
                EntnahmeStand(
                    e, Entnahmeplan.ziehung(reihe, plan) { ensureActive() },
                    if (fest) Entnahmeplan.hoechsteEntnahme(reihe, plan, 1.0) { ensureActive() } else null,
                    if (fest) Entnahmeplan.hoechsteEntnahme(reihe, plan, Entnahmeplan.ZIEL_ANTEIL) { ensureActive() } else null,
                    Entnahmeplan.lauf(plan, folge).vermoegen, Entnahmeplan.lauf(plan, folge.umgekehrt()).vermoegen,
                )
            })
        }
        val fertig = rechnung ?: run { eingaben(); ToolNote(t("laden")); return }
        val r = fertig.wert ?: run { eingaben(); ToolNote(t("zuKurz", S.toolYears(jahre), landName(land))); return }
        val e = r.ergebnis
        EpKopf(
            t("reicht.kopf", S.toolYears(e.jahre)), t("reicht.wert", Fmt.pct(e.anteil * 100.0, 0)),
            t("reicht.sub", Fmt.num(e.reicht.toDouble(), 0), Fmt.num(e.faelle.toDouble(), 0), S.histZeitraum(e.ersterStart, e.letzterStart)),
            when { e.anteil >= Entnahmeplan.ZIEL_ANTEIL -> signal.up; e.anteil < 0.8 -> signal.down; else -> null },
        )
        eingaben()
        val farbe = MaterialTheme.colorScheme.onSurface
        FanChart(
            median = e.p50, outer = e.p10 to e.p90, inner = e.p25 to e.p75, reference = null, color = farbe,
            startLabel = S.today, endLabel = S.ptInYears(e.jahre),
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp, vertical = 10.dp),
            yMax = PortfolioTools.entnahmeAchsenMax(e.p50, e.p90),
        )
        ChartLegend(listOf(S.ptMedian to farbe, S.ptBandLabel to farbe.copy(alpha = 0.35f)))
        KeyValueRow(
            S.hrSchlechtStart(e.schlechtStart),
            e.schlechtLeerIm?.let { S.hrLeerNach(it.coerceAtLeast(1)) } ?: t("schlecht.rest", Fmt.euro(e.schlechtEnde, 0)),
            valueColor = signal.down.takeIf { e.schlechtLeerIm != null },
        )
        r.ziehung?.let { z ->
            KeyValueRow(t("ziehung"), Fmt.pct(z.anteil * 100.0, 0), hint = t("ziehung.hint", Fmt.num(z.verlaeufe.toDouble(), 0), z.block.toString()))
        }
        r.alle?.let { KeyValueRow(t("hoechste.alle"), Fmt.euro(it, 0), hint = t("hoechste.hint", Fmt.pct(it / plan.vermoegen * 100.0, 1))) }
        r.teil?.let { KeyValueRow(t("hoechste.teil", anteil95), Fmt.euro(it, 0), hint = t("hoechste.hint", Fmt.pct(it / plan.vermoegen * 100.0, 1))) }
        KeyValueRow(t("rest"), Fmt.euro(e.p50[e.jahre], 0))
        if (steuerAn) KeyValueRow(t("steuer.summe"), Fmt.euro(e.steuerMitte, 0))

        if (regel != Entnahmeplan.Regel.FEST) {
            SectionHeader(t("ent.titel"))
            val blass = farbe.copy(alpha = 0.4f)
            EpLinien(listOf(e.entTief to blass, e.entMitte to farbe), bezug = plan.entnahme)
            ChartLegend(listOf(t("ent.linieMitte") to farbe, t("ent.linieTief") to blass, t("ent.linieBedarf") to MaterialTheme.colorScheme.onSurfaceVariant))
            if (regel == Entnahmeplan.Regel.RESTLAUFZEIT) KeyValueRow(t("ent.erste"), Fmt.euro(e.entMitte.first(), 0))
            KeyValueRow(t("ent.mitte"), Fmt.euro(e.entMedian, 0))
            KeyValueRow(
                t("ent.tief"), Fmt.euro(e.entMin, 0), signal.down.takeIf { e.entMin < plan.entnahme },
                hint = t("ent.tiefHint", e.entMinStart.toString(), e.entMinJahr.toString()),
            )
        } else if (plan.entnahme > 0.0) {
            // Renditereihenfolge: der schlechteste Start und dieselben Jahre rueckwaerts
            SectionHeader(t("folge.titel"))
            Text(
                t("folge.satz"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            EpLinien(listOf(r.rueck to Kategorie.blau, r.vor to signal.down), bezug = null)
            KeyValueRow(t("folge.vor", e.schlechtStart.toString()), Fmt.euro(r.vor.last(), 0), signal.down)
            KeyValueRow(t("folge.rueck"), Fmt.euro(r.rueck.last(), 0), Kategorie.blau)
        }
    }

    val hinweis = remember(reihe, steuerAn, zins, I18n.lang) {
        buildString {
            append(t("hinweis", reihe.erstes.toString(), reihe.letztes.toString(), anteil95))
            if (reihe.kennung == Entnahmeplan.WELT) append("\n" + t("hinweis.welt", reihe.laender.toString()))
            append("\n\n" + t(
                "hinweis.regeln", Fmt.pct(Entnahmeplan.SCHRITT * 100.0, 0), Fmt.pct(Entnahmeplan.BAND * 100.0, 0),
                Entnahmeplan.OHNE_KUERZUNG.toString(), Fmt.pct(zins * 100.0, 1),
            ))
            if (steuerAn) append("\n\n" + t("hinweis.steuer"))
            append("\n\n" + quellenText(d))
        }
    }
    HinweisInfo(hinweis)
    QuellenAnhang(listOf(histQuelle()) + Entnahmeplan.QUELLEN.map { QuelleLink(it.name, it.url) })
}

// Kernaussage: Bezeichnung, grosser Wert in einer Zeile, darunter die Einordnung
@Composable
private fun EpKopf(bezeichnung: String, wert: String, unter: String?, farbe: Color? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text(bezeichnung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.headlineMedium, color = farbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        if (unter != null) Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Auswahl in gleich breiten Feldern, zwei je Zeile: Bezeichnung oben (bricht um), Betrag darunter in einer Zeile.
@Composable
private fun <T> EpWahl(label: String, optionen: List<Triple<String, String?, T>>, gewaehlt: T, onSelect: (T) -> Unit) {
    Text(
        label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        optionen.chunked(2).forEach { zeile ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                zeile.forEach { (name, betrag, wert) ->
                    val aktiv = wert == gewaehlt
                    Box(
                        Modifier.weight(1f).fillMaxHeight().auswahlChip(aktiv, horizontal = 14.dp) { onSelect(wert) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column {
                            Text(name, style = MaterialTheme.typography.bodySmall, color = auswahlChipSchrift(aktiv))
                            if (betrag != null) Text(betrag, style = MaterialTheme.typography.titleSmall, color = auswahlChipSchrift(aktiv), maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (zeile.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// Linien ueber die Jahre auf einer Skala ab null, je mit Endpunkt; bezug als gestrichelte Waagerechte.
@Composable
private fun EpLinien(linien: List<Pair<DoubleArray, Color>>, bezug: Double?) {
    val n = linien.minOfOrNull { it.first.size } ?: return
    if (n < 2) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp, vertical = 8.dp)) {
        val rand = 4.dp.toPx()
        val max = maxOf(linien.maxOf { it.first.max() }, bezug ?: 0.0).takeIf { it > 0 } ?: return@Canvas
        val breite = size.width - 2 * rand
        val hoehe = size.height - 2 * rand
        fun punkt(i: Int, v: Double) = Offset(rand + breite * i / (n - 1), rand + hoehe * (1f - (v / max).toFloat()))
        drawLine(grau.copy(alpha = 0.6f), Offset(rand, size.height - rand), Offset(size.width - rand, size.height - rand), strokeWidth = 1.dp.toPx())
        if (bezug != null && bezug > 0.0) {
            val y = punkt(0, bezug).y
            drawLine(
                grau, Offset(rand, y), Offset(size.width - rand, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        val strich = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        linien.forEach { (werte, farbe) ->
            val pfad = Path().apply { for (i in 0 until n) punkt(i, werte[i]).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            drawPath(pfad, farbe, style = strich)
            drawCircle(farbe, 3.dp.toPx(), punkt(n - 1, werte[n - 1]))
        }
    }
}
