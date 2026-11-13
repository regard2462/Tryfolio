package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.SparplanRegel
import de.troetenrepublik.app.engine.Startfenster
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Taktik
import de.troetenrepublik.app.engine.Zufallspfade
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Strategietest 0.78: Zufallspfade, viele Startzeitpunkte und die Sparplan-Variante einer Regel. Alle Rechnungen
// laufen abseits der Oberflaeche und erst, wenn ihr Abschnitt offen ist.

private fun jahr(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"

private fun rueck(v: Double): String = "−" + Fmt.pct(v * 100, 1)

private val HALTEN = StrategieLabor.Regel(StrategieLabor.Art.HALTEN)

// Erklaerung des Abschnitts "Gegen den Zufall": Zufallspfade, darunter die verschobenen Signale.
internal fun zufallInfo(): String = S.st("zp.text") + "\n\n" + S.st("zp.verschoben") + "\n" + S.slZufallText

// Satz zu einer Groesse; p ist "r" (Rendite) oder "d" (Rueckgang).
internal fun zufallKernsatz(k: Zufallspfade.Kern, p: String): String = when {
    k.echtVorn && k.faellt.isEmpty() -> S.st("zp.$p.alle")
    k.echtVorn && k.haelt.isEmpty() -> S.st("zp.$p.keines")
    k.echtVorn -> S.st("zp.$p.teils", listOf(k.faellt.joinToString(S.st("zp.und")) { S.st("zp.ohne." + it.name) }))
    k.haelt.isEmpty() -> S.st("zp.$p.ohne")
    else -> S.st("zp.$p.nurPfade", listOf(k.haelt.size.toString()))
}

// Zufallspfade der gewaehlten Regel: je Groesse ein Satz, darunter je Verfahren ein Streifen mit der Lage der echten
// Geschichte, die Anteile eingeklappt.
@Composable
internal fun ZufallspfadeBlock(
    k: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel, ausstieg: StrategieLabor.Ausstieg,
    offen: SnapshotStateMap<String, Boolean>, taktik: Taktik.Reihen? = null,
) {
    val signal = LocalSignal.current
    val ergebnis by produceState<List<Zufallspfade.Ergebnis>?>(null, k, ab, kosten, regel, ausstieg, taktik) {
        value = null
        // Kurz warten: schnelles Umschalten zwischen Regeln startet keine Berechnung
        delay(300)
        value = withContext(Dispatchers.Default) { listOfNotNull(Zufallspfade.rechnen(k, ab, kosten, regel, ausstieg, abbruch = { ensureActive() }, taktik = taktik)) }
    }
    val liste = ergebnis ?: run { ToolNote(S.slLotterieLaeuft); return }
    if (liste.isEmpty()) { ToolNote(S.slZuWenig); return }
    val e = liste.first()
    val farbe = regelFarbe(regel)
    val kernR = remember(e) { Zufallspfade.kernRendite(e) }
    val kernD = remember(e) { Zufallspfade.kernRueckgang(e) }
    @Composable fun satz(kern: Zufallspfade.Kern, p: String) = Text(
        zufallKernsatz(kern, p), style = MaterialTheme.typography.titleMedium,
        color = when {
            kern.echtVorn && kern.faellt.isEmpty() -> signal.up
            kern.echtVorn && kern.haelt.isEmpty() -> signal.down
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
    )
    Text(S.st("zp.titel"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
    satz(kernR, "r")
    satz(kernD, "d")

    @Composable fun gruppe(titel: String, echt: Double, werte: (Zufallspfade.Auswertung) -> DoubleArray, lage: (Zufallspfade.Auswertung) -> Double, format: (Double) -> String) {
        val lo = minOf(echt, e.verfahren.minOf { werte(it).first() })
        val hi = maxOf(echt, e.verfahren.maxOf { werte(it).last() })
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
        e.verfahren.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.st("zp." + v.verfahren.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(Fmt.pct(lage(v) * 100, 0), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            }
            Streifen(werte(v), echt, lo, hi, farbe, S.st("zp." + v.verfahren.name) + ", " + titel)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(lo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Text(format(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
    gruppe(S.st("zp.achse.r"), e.regelRendite, { it.rendite }, { it.lageRendite }) { Fmt.signedPct(it * 100, 0) }
    gruppe(S.st("zp.achse.d"), e.regelRueckgang, { it.rueckgang }, { it.lageRueckgang }) { "−" + Fmt.pct(it * 100, 0) }

    val auf = offen["zufallspfadeAnteile"] == true
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (auf) S.less else S.more) { offen["zufallspfadeAnteile"] = !auf }
            .padding(horizontal = 20.dp).padding(top = 8.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.st("zp.anteile"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        AufklappPfeil(auf)
    }
    if (auf) e.verfahren.forEach { v ->
        Text(S.st("zp." + v.verfahren.name), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp))
        KeyValueRow(S.st("zp.pfade"), Fmt.num(v.pfade.toDouble(), 0))
        KeyValueRow(S.st("zp.spanne.r"), Fmt.signedPct(Zufallspfade.quantil(v.rendite, 0.05) * 100, 1) + S.st("zp.bis") + Fmt.signedPct(Zufallspfade.quantil(v.rendite, 0.95) * 100, 1))
        KeyValueRow(S.st("zp.spanne.d"), rueck(Zufallspfade.quantil(v.rueckgang, 0.05)) + S.st("zp.bis") + rueck(Zufallspfade.quantil(v.rueckgang, 0.95)))
        KeyValueRow(S.st("zp.mind"), Fmt.pct(v.mindestens * 100, 0))
        KeyValueRow(S.st("zp.hoechst"), Fmt.pct(v.hoechstens * 100, 0))
        KeyValueRow(S.st("zp.beides"), Fmt.pct(v.beides * 100, 0))
        KeyValueRow(S.st("zp.vorn.r"), Fmt.pct(v.schlaegtRendite * 100, 0), valueColor = if (v.schlaegtRendite >= Zufallspfade.HAELT_AB) signal.up else signal.down)
        KeyValueRow(S.st("zp.vorn.d"), Fmt.pct(v.schlaegtRueckgang * 100, 0), valueColor = if (v.schlaegtRueckgang >= Zufallspfade.HAELT_AB) signal.up else signal.down)
    }
    HinweisInfo(S.st("zp.text"), kurz = hinweisText("zufall"))
    Text(S.st("zp.verschoben"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
}

// Verteilung der Pfade auf einer Achse von lo bis hi: duenne Linie vom kleinsten zum groessten Wert, Band von 5 bis
// 95 %, kraeftiger die mittlere Haelfte, Strich am Median; der Punkt ist die echte Geschichte.
@Composable
private fun Streifen(sortiert: DoubleArray, echt: Double, lo: Double, hi: Double, farbe: Color, beschreibung: String) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val grund = flaeche()
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 2.dp).height(18.dp).semantics { contentDescription = beschreibung }) {
        val r = 5.dp.toPx()
        val breite = size.width - 2 * r
        val spanne = if (hi - lo < 1e-9) 1.0 else hi - lo
        fun x(v: Double) = r + (((v - lo) / spanne) * breite).toFloat()
        val mitte = size.height / 2
        drawLine(grau.copy(alpha = 0.5f), Offset(x(sortiert.first()), mitte), Offset(x(sortiert.last()), mitte), strokeWidth = 1.dp.toPx())
        fun band(von: Double, bis: Double, hoehe: Float, alpha: Float) {
            val a = x(Zufallspfade.quantil(sortiert, von))
            val b = x(Zufallspfade.quantil(sortiert, bis))
            drawRoundRect(grau.copy(alpha = alpha), topLeft = Offset(a, mitte - hoehe / 2), size = Size((b - a).coerceAtLeast(1f), hoehe), cornerRadius = CornerRadius(2.dp.toPx()))
        }
        band(0.05, 0.95, 6.dp.toPx(), 0.3f)
        band(0.25, 0.75, 12.dp.toPx(), 0.45f)
        val med = x(Zufallspfade.quantil(sortiert, 0.5))
        drawLine(grau, Offset(med, mitte - 6.dp.toPx()), Offset(med, mitte + 6.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        drawCircle(grund, r + 1.5.dp.toPx(), Offset(x(echt), mitte))
        drawCircle(farbe, r, Offset(x(echt), mitte))
    }
}

// Viele Startzeitpunkte: Haltedauer zur Wahl, wie oft die Regel vorn lag, darunter Rendite pro Jahr und groesster
// Rueckgang als Kastengrafik fuer Regel und Kaufen und Halten.
@Composable
internal fun StartfensterBlock(k: List<Candle>, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel, ausstieg: StrategieLabor.Ausstieg, sparplan: Boolean, taktik: Taktik.Reihen? = null) {
    val istHalten = regel.art == StrategieLabor.Art.HALTEN
    val ergebnis by produceState<List<Startfenster.Dauer>?>(null, k, kosten, regel, ausstieg, sparplan, taktik) {
        value = null
        delay(300)
        value = withContext(Dispatchers.Default) { Startfenster.rechnen(k, kosten, regel, ausstieg, sparplan, abbruch = { ensureActive() }, taktik = taktik) }
    }
    val liste = ergebnis ?: run { ToolNote(S.slLotterieLaeuft); return }
    if (liste.isEmpty()) { ToolNote(S.slZuWenig); return }
    var wahl by remember { mutableStateOf(5) }
    val d = liste.firstOrNull { it.jahre == wahl } ?: liste.first()
    ToolChoice(null, liste.map { S.toolYearsKurz(it.jahre) to it.jahre }, d.jahre) { wahl = it }
    Text(
        if (istHalten) S.st("sf.kern.halten", listOf(Fmt.num(d.fenster.toDouble(), 0)))
        else S.st("sf.kern", listOf(Fmt.num(d.vorn.toDouble(), 0), Fmt.num(d.fenster.toDouble(), 0))),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
    )
    val farbe = regelFarbe(regel)
    val farbeHalten = regelFarbe(HALTEN)
    val nameHalten = if (sparplan) S.st("sp.sparen") else S.slHalten
    @Composable fun kaesten(titel: String, r: Startfenster.Spanne, h: Startfenster.Spanne, format: (Double) -> String) {
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp))
        Kastengrafik(
            listOfNotNull(if (istHalten) null else Kasten(S.slDieRegel, r, farbe), Kasten(nameHalten, h, farbeHalten)),
            format, titel,
        )
    }
    kaesten(if (sparplan) S.ptPerYear + ", " + S.st("sp.geld") else S.ptPerYear, d.regelRendite, d.haltenRendite) { Fmt.signedPct(it * 100, 0) }
    kaesten(S.copyMaxDrawdown, d.regelRueckgang, d.haltenRueckgang) { "−" + Fmt.pct(it * 100, 0) }
    @Composable
    fun zeile(label: String, r: String, h: String, vz: Pair<Double?, Double?>? = null): VergleichZeile {
        val f = vz?.let { renditeFarben(it.first, it.second) } ?: emptyList()
        return VergleichZeile(label, if (istHalten) listOf(h) else listOf(r, h), if (istHalten) f.drop(1) else f)
    }
    VergleichTabelle(
        listOfNotNull(if (istHalten) null else VergleichSpalte(S.slDieRegel, farbe), VergleichSpalte(nameHalten, farbeHalten)),
        listOf(
            zeile(S.st("sf.median.r"), jahr(d.regelRendite.median), jahr(d.haltenRendite.median), d.regelRendite.median to d.haltenRendite.median),
            zeile(S.st("sf.min.r"), jahr(d.regelRendite.min), jahr(d.haltenRendite.min), d.regelRendite.min to d.haltenRendite.min),
            zeile(S.st("sf.median.d"), rueck(d.regelRueckgang.median), rueck(d.haltenRueckgang.median)),
            zeile(S.st("sf.max.d"), rueck(d.regelRueckgang.max), rueck(d.haltenRueckgang.max)),
        ),
    )
}

private class Kasten(val name: String, val spanne: Startfenster.Spanne, val farbe: Color)

// Liegende Kastengrafik auf gemeinsamer Achse: je Zeile der Name ueber dem Kasten, rechts daneben der Median; unter
// der Achse kleinster und groesster Wert, bei Null eine zarte Linie.
@Composable
private fun Kastengrafik(zeilen: List<Kasten>, format: (Double) -> String, beschreibung: String) {
    val messer = rememberTextMeasurer()
    val achse = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val nullLinie = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    var lo = zeilen.minOf { it.spanne.min }
    var hi = zeilen.maxOf { it.spanne.max }
    if (hi - lo < 1e-9) { lo -= 0.01; hi += 0.01 }
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp).height((zeilen.size * 38 + 18).dp).semantics { contentDescription = beschreibung }) {
        val zeile = 38.dp.toPx()
        val kopf = 16.dp.toPx()
        val hoehe = 14.dp.toPx()
        fun x(v: Double) = (((v - lo) / (hi - lo)) * size.width).toFloat()
        val unten = zeilen.size * zeile
        if (lo < 0.0 && hi > 0.0) drawLine(nullLinie, Offset(x(0.0), 0f), Offset(x(0.0), unten), strokeWidth = 1.dp.toPx())
        zeilen.forEachIndexed { i, z ->
            val oben = i * zeile
            val mitte = oben + kopf + hoehe / 2
            val s = z.spanne
            val name = messer.measure(z.name, achse.copy(color = z.farbe))
            val wert = messer.measure(format(s.median), achse.copy(color = z.farbe))
            drawText(name, topLeft = Offset(0f, oben))
            drawText(wert, topLeft = Offset(size.width - wert.size.width, oben))
            drawLine(z.farbe, Offset(x(s.min), mitte), Offset(x(s.max), mitte), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(z.farbe, Offset(x(s.min), mitte - hoehe / 4), Offset(x(s.min), mitte + hoehe / 4), strokeWidth = 1.5.dp.toPx())
            drawLine(z.farbe, Offset(x(s.max), mitte - hoehe / 4), Offset(x(s.max), mitte + hoehe / 4), strokeWidth = 1.5.dp.toPx())
            val a = x(s.q1)
            val b = x(s.q3)
            drawRoundRect(z.farbe.copy(alpha = 0.45f), topLeft = Offset(a, mitte - hoehe / 2), size = Size((b - a).coerceAtLeast(2f), hoehe), cornerRadius = CornerRadius(2.dp.toPx()))
            drawLine(z.farbe, Offset(x(s.median), mitte - hoehe / 2), Offset(x(s.median), mitte + hoehe / 2), strokeWidth = 2.dp.toPx())
        }
        val tl = messer.measure(format(lo), achse)
        val th = messer.measure(format(hi), achse)
        drawText(tl, topLeft = Offset(0f, unten + 2.dp.toPx()))
        drawText(th, topLeft = Offset(size.width - th.size.width, unten + 2.dp.toPx()))
    }
}

// Sparplan-Variante der gewaehlten Regel gegen stures Sparen: Verlauf mit der Summe der Einzahlungen, darunter die
// Kennzahlen, die Rendite geldgewichtet.
@Composable
internal fun SparplanBlock(k: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel, taktik: Taktik.Reihen? = null) {
    val istHalten = regel.art == StrategieLabor.Art.HALTEN
    val ergebnis by produceState<List<Pair<SparplanRegel.Ergebnis, SparplanRegel.Ergebnis>>?>(null, k, ab, kosten, regel, taktik) {
        value = null
        value = withContext(Dispatchers.Default) { listOfNotNull(SparplanRegel.vergleichen(k, ab, kosten, regel, taktik = taktik)) }
    }
    val liste = ergebnis ?: run { ToolNote(S.toolLoading); return }
    if (liste.isEmpty()) { ToolNote(S.slZuWenig); return }
    val (r, s) = liste.first()
    val farbe = regelFarbe(regel)
    val farbeSparen = regelFarbe(HALTEN)
    val farbeEin = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val linien = remember(r, s) {
        Triple(StrategieLabor.ausduennen(r.verlauf), StrategieLabor.ausduennen(s.verlauf), StrategieLabor.ausduennen(s.einzahlungen))
    }
    LinesChart(
        listOfNotNull(linien.third to farbeEin, if (istHalten) null else linien.second to farbeSparen, (if (istHalten) linien.second else linien.first) to farbe),
        Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        baseline = null, scrubben = false,
    )
    ChartLegend(listOfNotNull(if (istHalten) null else S.slDieRegel to farbe, S.st("sp.sparen") to (if (istHalten) farbe else farbeSparen), S.st("sp.eingezahlt") to farbeEin))
    @Composable
    fun zeile(label: String, a: String, b: String, hint: String? = null, vz: Pair<Double?, Double?>? = null): VergleichZeile {
        val f = vz?.let { renditeFarben(it.first, it.second) } ?: emptyList()
        return VergleichZeile(label, if (istHalten) listOf(b) else listOf(a, b), if (istHalten) f.drop(1) else f, hint = hint)
    }
    KeyValueRow(S.st("sp.eingezahlt"), Fmt.euro(s.eingezahlt, 0))
    VergleichTabelle(
        listOfNotNull(if (istHalten) null else VergleichSpalte(S.slDieRegel, farbe), VergleichSpalte(S.st("sp.sparen"), if (istHalten) farbe else farbeSparen)),
        listOf(
            zeile(S.slEndwert, Fmt.euro(r.endwert, 0), Fmt.euro(s.endwert, 0)),
            zeile(S.ptPerYear, jahr(r.zins), jahr(s.zins), S.st("sp.geld"), r.zins to s.zins),
            zeile(S.copyMaxDrawdown, rueck(r.maxRueckgang), rueck(s.maxRueckgang), S.st("sp.bereinigt")),
            zeile(S.st("sp.kaeufe"), Fmt.num(r.kaeufe.toDouble(), 0), Fmt.num(s.kaeufe.toDouble(), 0)),
            zeile(S.slImMarkt, Fmt.pct(r.imMarkt * 100, 0), Fmt.pct(s.imMarkt * 100, 0)),
        ),
    )
    if (!istHalten) KeyValueRow(S.st("sp.verkaeufe"), Fmt.num(r.verkaeufe.toDouble(), 0))
    HinweisInfo(S.st("sp.text", listOf(Fmt.euro(SparplanRegel.RATE, 0))))
}
