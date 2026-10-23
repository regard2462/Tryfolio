package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Stimmungsbarometer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer
import de.troetenrepublik.app.engine.Stimmungsbarometer.Stufe
import de.troetenrepublik.app.i18n.S
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Marktlage oben in den Trends. Stimmung: Fear & Greed von CNN und alternative.me, AAII-Umfrage, Tradestie fuer
// r/wallstreetbets und mit eigenem Reddit-Zugang die deutschen Subreddits. Fruehwarnzeichen als Ampeln.
// Ein Tipp zeigt Verlauf und Einzelheiten. Die Quelle steht jeweils direkt bei den Daten.
@Composable
internal fun StimmungsAbschnitt(state: AppState, onOpen: (String) -> Unit) {
    var st by remember { mutableStateOf<AppState.Stimmungen?>(null) }
    var fw by remember { mutableStateOf<List<Fruehwarnung.Zeichen>?>(null) }
    LaunchedEffect(Unit) { st = runCatching { state.stimmungen() }.getOrNull() ?: AppState.Stimmungen(null, null, null, null) }
    LaunchedEffect(Unit) { fw = runCatching { state.fruehwarnung() }.getOrNull().orEmpty() }
    var pos by remember { mutableStateOf<List<de.troetenrepublik.app.engine.Positionierung.Lage>?>(null) }
    LaunchedEffect(Unit) { pos = runCatching { state.positionierung() }.getOrNull().orEmpty() }
    var phasen by remember { mutableStateOf<List<de.troetenrepublik.app.engine.Marktphase.Phase>?>(null) }
    LaunchedEffect(Unit) { phasen = runCatching { state.marktphase() }.getOrNull().orEmpty() }
    StimmungsInhalt(st, onOpen)
    MarktphaseInhalt(phasen)
    FruehwarnInhalt(fw)
    PositionierungInhalt(pos)
}

// Bullen- oder Baerenmarkt (seit 5.9): je Index die Phase mit Beginn, Abstand zum Hoch, 200-Tage-Linie und letztem
// Golden oder Death Cross. Ein Tipp zeigt zwei Jahre Kursverlauf mit der Schwelle, an der die Phase kippen wuerde.
@Composable
internal fun MarktphaseInhalt(phasen: List<de.troetenrepublik.app.engine.Marktphase.Phase>?) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.mpTitel)
        when {
            phasen == null -> EmptyHint(S.trLaedt)
            phasen.isEmpty() -> EmptyHint(S.trKeineDaten)
            else -> {
                val bullen = phasen.count { it.art == de.troetenrepublik.app.engine.Marktphase.Art.BULLE && !it.korrektur }
                Text(S.mpGesamt(bullen, phasen.size), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                var offen by remember { mutableStateOf<String?>(null) }
                for (p in phasen) {
                    val auf = offen == p.schluessel
                    val farbe = when { p.art == de.troetenrepublik.app.engine.Marktphase.Art.BAER -> signal.down; p.korrektur -> MaterialTheme.colorScheme.onSurfaceVariant; else -> signal.up }
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else p.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(S.mpIndex(p.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.mpPhase(p.art.name, p.korrektur, Fmt.date(p.seit)), style = MaterialTheme.typography.bodySmall, color = farbe)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(S.mpVomHoch(Fmt.signedPct(p.vomHoch, 1)), style = MaterialTheme.typography.titleSmall, color = farbe)
                                p.zur200?.let {
                                    Text(S.mp200(Fmt.signedPct(it, 1)), style = MaterialTheme.typography.labelSmall,
                                        color = if (it >= 0) signal.up else signal.down)
                                }
                            }
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(p.verlauf, p.schwelle)
                            Text(S.mpDetail(p.art.name, Fmt.signedPct(p.vomTief, 1), p.golden, p.kreuzSeit?.let { Fmt.date(it) }),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        AufklappText(S.mpHinweis, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp))
    }
}

// Positionierung an den Terminmaerkten (seit 5.8): je Markt die Nettoposition grosser Spekulanten in Prozent der
// offenen Kontrakte, die Veraenderung zur Vorwoche und die Einordnung gegenueber drei Jahren. Ein Tipp zeigt den Verlauf.
@Composable
internal fun PositionierungInhalt(lagen: List<de.troetenrepublik.app.engine.Positionierung.Lage>?) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.cotTitel)
        when {
            lagen == null -> EmptyHint(S.trLaedt)
            lagen.isEmpty() -> EmptyHint(S.trKeineDaten)
            else -> {
                var offen by remember { mutableStateOf<String?>(null) }
                for (l in lagen) {
                    val auf = offen == l.schluessel
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else l.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(S.cotMarkt(l.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.cotLage(l.index), style = MaterialTheme.typography.bodySmall,
                                    color = when { l.index >= 80 -> signal.up; l.index <= 20 -> signal.down; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.signedPct(l.anteil, 1), style = MaterialTheme.typography.titleSmall, color = if (l.anteil >= 0) signal.up else signal.down)
                                Text(S.cotVorwoche((if (l.anteil - l.vorwoche >= 0.05) "+" else if (l.anteil - l.vorwoche <= -0.05) "−" else "±") + Fmt.num(kotlin.math.abs(l.anteil - l.vorwoche), 1)), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(l.verlauf, 0.0)
                            Text(S.cotErklaerung(l.index), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(S.cotQuelle(lagen.maxOf { it.stand }.let { Fmt.date(it) }), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        AufklappText(S.cotHinweis, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp))
    }
}

// Eine Kachel der Stimmung: Titel, Barometer, Zeile unter der Skala, Quelle, Einzelheiten beim Aufklappen.
private class StimmungsKachel(
    val schluessel: String, val name: String, val b: Barometer, val zeile: String, val quelle: String, val link: String?,
    val mehr: @Composable () -> Unit,
)

// null: wird noch geladen.
@Composable
internal fun StimmungsInhalt(s: AppState.Stimmungen?, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.sbTitel)
        val kacheln = if (s == null) emptyList() else listOfNotNull(
            s.aktien?.let { b ->
                StimmungsKachel("aktien", S.sbAktien, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleCnn, Stimmungsbarometer.CNN_SEITE) {
                    Verlaufslinie(b.verlauf)
                    for (t in b.teile) Text(
                        S.sbTeil(t.schluessel) + ": " + Fmt.num(t.wert, 0) + " · " + S.sbStufe(t.stufe.ordinal),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            s.aaii?.let { a ->
                StimmungsKachel("aaii", S.sbAaii, a.barometer, S.sbStufe(a.barometer.stufe.ordinal) + " · " + Fmt.num(a.barometer.wert, 0), S.sbQuelleAaii, Fruehwarnung.AAII_SEITE) {
                    Verlaufslinie(a.barometer.verlauf)
                    Text(S.sbAaiiMehr(Fmt.num(a.woche.bullisch, 1), Fmt.num(a.woche.neutral, 1), Fmt.num(a.woche.baerisch, 1), Fmt.date(a.woche.zeit)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            s.krypto?.let { b ->
                StimmungsKachel("krypto", S.sbKrypto, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleKrypto, Stimmungsbarometer.KRYPTO_SEITE) {
                    Verlaufslinie(b.verlauf)
                }
            },
            s.wsb?.let { r ->
                StimmungsKachel("wsb", S.sbWsb, r.barometer, S.sbSteigend(Math.round(r.barometer.wert).toInt()), S.sbQuelleWsb, Stimmungsbarometer.WSB_SEITE) {
                    Text(S.sbRedditWert(Math.round(r.barometer.wert).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val steigend = r.werte.filter { it.steigend }.take(5)
                    val fallend = r.werte.filter { !it.steigend }.take(5)
                    if (steigend.isNotEmpty()) WsbListe(S.sbWsbSteigend, steigend, onOpen)
                    if (fallend.isNotEmpty()) WsbListe(S.sbWsbFallend, fallend, onOpen)
                }
            },
            s.deutsch?.let { b ->
                StimmungsKachel("deutsch", S.sbDeutsch, b, S.sbSteigend(Math.round(b.wert).toInt()), S.sbQuelleDeutsch, null) {
                    Text(S.sbBeitragWert(Math.round(b.wert).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        when {
            s == null -> EmptyHint(S.trLaedt)
            kacheln.isEmpty() -> EmptyHint(S.trKeineDaten)
            else -> {
                var offen by remember { mutableStateOf(-1) }
                val uri = LocalUriHandler.current
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kacheln.chunked(2).forEachIndexed { reihe, paar ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            paar.forEachIndexed { j, k ->
                                val i = reihe * 2 + j
                                KachelBarometer(k, i == offen, Modifier.weight(1f)) { offen = if (offen == i) -1 else i }
                            }
                            if (paar.size == 1) Spacer(Modifier.weight(1f))
                        }
                        // Einzelheiten der gewaehlten Kachel direkt unter ihrer Reihe
                        val k = kacheln.getOrNull(offen)
                        if (k != null && offen / 2 == reihe) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .animateContentSize().padding(14.dp),
                            ) {
                                NamenText(k.name, style = MaterialTheme.typography.titleSmall)
                                vergleich(k.b)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Spacer(Modifier.height(6.dp))
                                k.mehr()
                                // Quelle direkt bei den Daten, bei alternative.me Bedingung fuer die Nutzung
                                Text(
                                    k.quelle, style = MaterialTheme.typography.labelSmall,
                                    color = if (k.link != null) LocalSignal.current.link else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp).then(if (k.link != null) Modifier.clickable { runCatching { uri.openUri(k.link) } } else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
        AufklappText(S.sbHinweis, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp))
    }
}

@Composable
private fun KachelBarometer(k: StimmungsKachel, gewaehlt: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.card))
            .background(if (gewaehlt) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Skala(k.b.wert, Modifier.size(width = 72.dp, height = 44.dp))
        Spacer(Modifier.height(8.dp))
        NamenText(S.sbKachel(k.schluessel), style = MaterialTheme.typography.labelLarge)
        NamenText(k.zeile, style = MaterialTheme.typography.bodySmall, color = stufenFarbe(k.b.stufe))
        NamenText(S.sbAnbieter(k.schluessel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// null: wird noch geladen.
@Composable
internal fun FruehwarnInhalt(zeichen: List<Fruehwarnung.Zeichen>?) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.fwTitel)
        when {
            zeichen == null -> EmptyHint(S.trLaedt)
            zeichen.isEmpty() -> EmptyHint(S.trKeineDaten)
            else -> {
                val rot = zeichen.count { it.ampel == Fruehwarnung.Ampel.ROT }
                val gelb = zeichen.count { it.ampel == Fruehwarnung.Ampel.GELB }
                Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (z in zeichen) {
                        Box(Modifier.padding(end = 6.dp).size(12.dp).clip(CircleShape).background(ampelFarbe(z.ampel)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(S.fwZusammen(rot, gelb, zeichen.size), style = MaterialTheme.typography.titleSmall)
                }
                var offen by remember { mutableStateOf<String?>(null) }
                for (z in zeichen) {
                    val auf = offen == z.schluessel
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else z.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(ampelFarbe(z.ampel)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(S.fwName(z.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.fwLage(z.schluessel, z.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(fwWert(z), style = MaterialTheme.typography.titleSmall)
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(z.verlauf, if (z.schluessel == Fruehwarnung.VIX_STRUKTUR) 1.0 else if (z.schluessel == Fruehwarnung.VIX) 20.0 else 0.0)
                            Text(S.fwErklaerung(z.schluessel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(S.fwQuelle(z.schluessel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        AufklappText(S.fwHinweis, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp))
    }
}

// Wert eines Zeichens in der passenden Einheit.
private fun fwWert(z: Fruehwarnung.Zeichen): String = when (z.schluessel) {
    Fruehwarnung.ZINSKURVE, Fruehwarnung.SAHM -> (if (z.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(z.wert), 2) + " " + S.fwPunkte
    Fruehwarnung.VIX -> Fmt.num(z.wert, 1)
    Fruehwarnung.VIX_STRUKTUR -> Fmt.num(z.wert, 2)
    else -> (if (z.wert >= 0) "+" else "−") + Fmt.pct(kotlin.math.abs(z.wert), 1)
}

@Composable
private fun ampelFarbe(a: Fruehwarnung.Ampel): Color = when (a) {
    Fruehwarnung.Ampel.GRUEN -> LocalSignal.current.up
    Fruehwarnung.Ampel.GELB -> Color(0xFFE0B020)
    Fruehwarnung.Ampel.ROT -> LocalSignal.current.down
}

// Verlauf mit eigener Skala und einer Linie bei schwelle.
@Composable
internal fun VerlaufFrei(punkte: List<Pair<Long, Double>>, schwelle: Double) {
    if (punkte.size < 2) return
    val farbe = MaterialTheme.colorScheme.onSurface
    val linie = MaterialTheme.colorScheme.outlineVariant
    val lo = minOf(punkte.minOf { it.second }, schwelle)
    val hi = maxOf(punkte.maxOf { it.second }, schwelle).let { if (it - lo < 1e-9) lo + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(52.dp).padding(vertical = 4.dp)) {
        val t0 = punkte.first().first.toFloat()
        val t1 = punkte.last().first.toFloat().coerceAtLeast(t0 + 1f)
        fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
        fun y(v: Double) = size.height * (1f - ((v - lo) / (hi - lo)).toFloat())
        drawLine(linie, Offset(0f, y(schwelle)), Offset(size.width, y(schwelle)), strokeWidth = 1.5f)
        val p = Path()
        punkte.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
        drawPath(p, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun vergleich(b: Barometer): String? = listOfNotNull(
    b.vortag?.let { S.sbVortag + " " + Fmt.num(it, 0) },
    b.woche?.let { S.sbWoche + " " + Fmt.num(it, 0) },
    b.monat?.let { S.sbMonat + " " + Fmt.num(it, 0) },
    b.jahr?.let { S.sbJahr + " " + Fmt.num(it, 0) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun stufenFarbe(s: Stufe): Color {
    val sig = LocalSignal.current
    return when (s) {
        Stufe.EXTREME_ANGST, Stufe.ANGST -> sig.down
        Stufe.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        Stufe.GIER, Stufe.EXTREME_GIER -> sig.up
    }
}

// Halbrunde Skala von 0 (links, Angst) bis 100 (rechts, Gier) mit Zeiger.
@Composable
private fun Skala(wert: Double, modifier: Modifier) {
    val sig = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val zeiger = MaterialTheme.colorScheme.onSurface
    val farben = listOf(sig.down, sig.down.copy(alpha = 0.55f), grau.copy(alpha = 0.6f), sig.up.copy(alpha = 0.55f), sig.up)
    // Grenzen der Stufen wie bei CNN
    val grenzen = listOf(0.0, 25.0, 45.0, 55.0, 75.0, 100.0)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val dicke = size.height * 0.16f
            val r = minOf(size.width / 2f, size.height) - dicke / 2f
            val mitte = Offset(size.width / 2f, size.height - dicke / 4f)
            val ecke = Offset(mitte.x - r, mitte.y - r)
            for (i in 0 until 5) {
                val von = 180f + (grenzen[i] / 100.0 * 180.0).toFloat() + 1.5f
                val weite = ((grenzen[i + 1] - grenzen[i]) / 100.0 * 180.0).toFloat() - 3f
                drawArc(farben[i], von, weite, useCenter = false, topLeft = ecke, size = Size(2 * r, 2 * r), style = Stroke(dicke, cap = StrokeCap.Butt))
            }
            val w = (PI * (1.0 - wert.coerceIn(0.0, 100.0) / 100.0)).toFloat()
            val spitze = Offset(mitte.x + cos(w) * r * 0.82f, mitte.y - sin(w) * r * 0.82f)
            drawLine(zeiger, mitte, spitze, strokeWidth = dicke * 0.45f, cap = StrokeCap.Round)
            drawCircle(zeiger, radius = dicke * 0.45f, center = mitte)
        }
    }
}

// Verlauf als duenne Linie von 0 bis 100.
@Composable
private fun Verlaufslinie(punkte: List<Pair<Long, Double>>) {
    if (punkte.size < 2) return
    val farbe = MaterialTheme.colorScheme.onSurface
    val mitte = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp)) {
        val t0 = punkte.first().first.toFloat()
        val t1 = punkte.last().first.toFloat().coerceAtLeast(t0 + 1f)
        fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
        fun y(v: Double) = size.height * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
        drawLine(mitte, Offset(0f, y(50.0)), Offset(size.width, y(50.0)), strokeWidth = 1f)
        val p = Path()
        punkte.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
        drawPath(p, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun WsbListe(titel: String, werte: List<Stimmungsbarometer.WsbWert>, onOpen: (String) -> Unit) {
    Text(titel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.fillMaxWidth()) {
        for (w in werte) Text(
            w.ticker, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link, textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onOpen(w.ticker) }.padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}
