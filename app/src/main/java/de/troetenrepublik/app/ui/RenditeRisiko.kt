package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.RegelTexte
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs

// Wie tief kann es fallen: zuerst der groesste Rueckgang und wie lange es bis zum
// alten Hoch dauerte, damit klar ist, welche Durststrecke man mit dem Wert aushalten muesste. Darunter Rendite pro Jahr
// ueber 1, 5 und 10 Jahre, Beta und Korrelation zum Index der Heimatboerse. Jeder Begriff hat eine kurze Erklaerung
// als Unterzeile. Ohne Sharpe-Ratio und aehnliche Kennzahlen, die bei einem einzelnen Wert mehr versprechen, als sie halten.
// Eingeklappt als Zeile unter Werkzeuge (5.93): die Kurse werden erst beim Aufklappen geladen. Die Schwankung steht in den Kennzahlen.
@Composable
internal fun RenditeRisiko(state: AppState, symbol: String, land: String?, merker: SeitenMerker) {
    val offenZustand = merker.zustand("rr/offen") { false }
    val offen = offenZustand.value
    val kopf: @Composable () -> Unit = { AufklappZeile(S.rrTitel, "", offen) { offenZustand.value = !offenZustand.value } }
    if (!offen) {
        ModellMelden(merker, "rr", ListenModell(titel = "", gross = true, kern = null, zeilen = emptyList(), kopf = kopf, immerOffen = true))
        return
    }
    // Ergebnis und Kurse im Merker der Seite: Beim Zurueckscrollen wird nichts neu geholt und gerechnet
    var ergebnis by gemerkt<Pair<Aktienkennzahlen.Ergebnis?, String?>?>(merker, "rr/ergebnis") { null }
    var kurse by gemerkt<List<Candle>>(merker, "rr/kurse") { emptyList() }
    LaunchedEffect(symbol, land) {
        if (ergebnis != null) return@LaunchedEffect
        val erg = runCatching { state.aktienkennzahlen(symbol, land) }.getOrDefault(null to null)
        val k = state.dailyCloses(symbol)
        kurse = k
        ergebnis = erg
    }
    val geladen = ergebnis
    val e = geladen?.first
    val rows = ArrayList<ZeilenDaten>()
    if (e == null) {
        rows.add(ZeilenDaten("status") {
            Text(if (geladen == null) S.rrLaedt else S.rrKeine, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        })
    } else {
        // Groesster Rueckgang ueber alle Kurse und die Zeit vom Tief zurueck zum alten Hoch
        val tage = if (Symbols.typeOf(symbol) == AssetType.CRYPTO) 365 else 252
        val r = merker.gerechnet("rr/risiko", listOf(Ident(kurse), tage)) { kurse.firstOrNull()?.let { Analysis.risk(kurse, it.t, tage) } }
        if (r != null) {
            // Größter Rückgang: Prozent groß, Zeitraum klein darunter, der Kursverlauf mit markiertem Hoch und Tief
            rows.add(ZeilenDaten("drawdown") { RueckgangBlock(r, kurse) })
            rows.add(ZeilenDaten("erholung") { KeyValueRow(S.rrErholung, r.recoveredAt?.let { S.rrDauer((it - r.troughAt) / 86_400_000L) } ?: S.rrNochNicht) })
        } else {
            rows.add(ZeilenDaten("drawdown") { KeyValueRow(S.copyMaxDrawdown, Fmt.signedPct(e.maxRueckgang * 100, 1) + " (" + Fmt.date(e.maxRueckgangTag) + ")", LocalSignal.current.down) })
        }
        rows.add(ZeilenDaten("laengster") { KeyValueRow(S.rrLaengster, S.rrDauer(e.laengsterRueckgangTage) + if (e.laufend) " " + S.rrLaeuft else "") })
        // Rendite pro Jahr als Balken nebeneinander, fehlende Zeiträume entfallen
        val renditen = listOf(1 to e.rendite1, 5 to e.rendite5, 10 to e.rendite10).mapNotNull { (j, v) -> v?.let { j to it } }
        if (renditen.isNotEmpty()) rows.add(ZeilenDaten("renditen") { RenditeBalken(renditen) })
        val index = geladen.second
        if (index != null && e.beta != null) rows.add(ZeilenDaten("beta") { SkalaZeile(S.rrBeta(index), Fmt.num(e.beta, 2), e.beta, 0.0, 2.0, 1.0) })
        if (index != null && e.korrelation != null) rows.add(ZeilenDaten("korrelation") { SkalaZeile(S.rrKorrelation(index), Fmt.num(e.korrelation, 2), e.korrelation, -1.0, 1.0, 0.0) })
        // Methode und Quelle der Kurse hinter dem Info-Symbol
        rows.add(ZeilenDaten("hinweis") { InfoZeile(null, S.rrTitel, S.rrHinweis(Fmt.date(e.ab), index, e.beta?.let { Fmt.num(it, 2) })) })
        // [0.73] Unterwasser-Diagramm, Renditeverteilung und rollierende Werte, zu beim Oeffnen
        val reihe = merker.gerechnet("rr/reihe", listOf(Ident(kurse))) { kurse.map { it.t to it.close } }
        rows.add(ZeilenDaten("abschnitte") { RisikoAbschnitte("wert-$symbol", reihe, tage.toDouble()) { RiskTool(state, symbol, kompakt = true) } })
    }
    ModellMelden(merker, "rr", ListenModell(titel = "", gross = true, kern = null, zeilen = rows, kopf = kopf, immerOffen = true))
}

internal fun LazyListScope.renditeRisikoAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "rr")
}

// Größter Rückgang: Bezeichnung und Zeitraum links, Prozent groß rechts in einer Zeile; darunter der Kursverlauf mit
// dem Rückgang in Rot, Punkt am Hoch und am Tief.
@Composable
private fun RueckgangBlock(r: Analysis.Risk, kurse: List<Candle>) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(S.copyMaxDrawdown, style = MaterialTheme.typography.titleSmall)
                Text(S.rrVonBis(Fmt.date(r.peakAt), Fmt.date(r.troughAt)), style = MaterialTheme.typography.bodySmall, color = grau)
            }
            Spacer(Modifier.width(12.dp))
            Text("−" + Fmt.pct(abs(r.maxDrawdown), 1), style = MaterialTheme.typography.titleLarge, color = signal.down, maxLines = 1, softWrap = false)
        }
        val punkte = remember(kurse) {
            val k = kurse.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
            val schritt = (k.size / 160).coerceAtLeast(1)
            k.filterIndexed { i, _ -> i % schritt == 0 || i == k.size - 1 }
        }
        if (punkte.size >= 2) {
            val linie = grau.copy(alpha = 0.55f)
            Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(64.dp)) {
                val t0 = punkte.first().t.toDouble(); val t1 = punkte.last().t.toDouble()
                val lo = punkte.minOf { it.close }; val hi = punkte.maxOf { it.close }
                val pad = 6.dp.toPx()
                fun x(t: Long) = ((t - t0) / (t1 - t0).coerceAtLeast(1.0)).toFloat() * size.width
                fun y(c: Double) = pad + (1f - ((c - lo) / (hi - lo).coerceAtLeast(1e-9)).toFloat()) * (size.height - 2 * pad)
                fun pfad(von: Long, bis: Long) = Path().apply {
                    var erster = true
                    punkte.filter { it.t in von..bis }.forEach { c -> if (erster) { moveTo(x(c.t), y(c.close)); erster = false } else lineTo(x(c.t), y(c.close)) }
                }
                drawPath(pfad(Long.MIN_VALUE, Long.MAX_VALUE), linie, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                drawPath(pfad(r.peakAt, r.troughAt), signal.down, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
                val hoch = punkte.minByOrNull { abs(it.t - r.peakAt) }; val tief = punkte.minByOrNull { abs(it.t - r.troughAt) }
                if (hoch != null) drawCircle(linie, 3.5.dp.toPx(), Offset(x(hoch.t), y(hoch.close)))
                if (tief != null) drawCircle(signal.down, 4.dp.toPx(), Offset(x(tief.t), y(tief.close)))
            }
        }
    }
}

// Rendite pro Jahr über 1, 5 und 10 Jahre als Balken nebeneinander: Zahl am Balken, Zeitraum darunter.
@Composable
private fun RenditeBalken(werte: List<Pair<Int, Double>>) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val gross = werte.maxOf { abs(it.second) }.coerceAtLeast(1e-9)
    val ruecklauf = werte.any { it.second < 0 }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(RegelTexte.text(I18n.lang == Lang.DE, "rendite"), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            werte.forEach { (j, v) ->
                val farbe = if (v >= 0) signal.up else signal.down
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.signedPct(v * 100, 1), style = MaterialTheme.typography.bodyMedium, color = farbe, maxLines = 1, softWrap = false)
                    Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(56.dp)) {
                        val nullLinie = if (ruecklauf) size.height / 2f else size.height
                        val hoehe = (abs(v) / gross).toFloat() * nullLinie
                        val breite = size.width * 0.5f
                        val links = (size.width - breite) / 2f
                        drawRoundRect(
                            farbe.copy(alpha = 0.85f), Offset(links, if (v >= 0) nullLinie - hoehe else nullLinie), Size(breite, hoehe.coerceAtLeast(2f)),
                            CornerRadius(3.dp.toPx()),
                        )
                        if (ruecklauf) drawLine(grau.copy(alpha = 0.4f), Offset(0f, nullLinie), Offset(size.width, nullLinie), 1.dp.toPx())
                    }
                    Text(S.toolYearsKurz(j), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// Kennzahl in einer Zeile (Bezeichnung links, Wert rechts) mit einer kleinen Skala darunter: Punkt an der Stelle des
// Werts, feine Marke bei der Mitte oder dem Normalwert.
@Composable
private fun SkalaZeile(titel: String, wert: String, v: Double, von: Double, bis: Double, marke: Double) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val punkt = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(12.dp)) {
            val mitte = size.height / 2f
            drawRoundRect(bahn, Offset(0f, mitte - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            val mx = (((marke - von) / (bis - von)).toFloat()).coerceIn(0f, 1f) * size.width
            drawLine(grau.copy(alpha = 0.6f), Offset(mx, mitte - 5.dp.toPx()), Offset(mx, mitte + 5.dp.toPx()), 1.5.dp.toPx())
            val px = (((v - von) / (bis - von)).toFloat()).coerceIn(0f, 1f) * size.width
            drawCircle(punkt, 5.dp.toPx(), Offset(px, mitte))
        }
    }
}
