package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.Lagebild
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// [Lage 0.77] Karten der Lage: je Thema eine Karte mit einer kleinen Grafik aus den echten Werten (Skala mit Marke, Balken,
// Verlauf, Segmentleiste) und ein bis zwei Zahlenzeilen. [0.78] Ein Tipp auf einen Wert oder eine Grafik klappt den
// passenden Unterabschnitt der Tabelle darunter auf; ein Tipp auf ein Land, einen Engpass, ein Gebiet oder ein Thema
// oeffnet dessen Blatt ueber der Lage. Die Seite wechselt nur ueber den Weg am Ende der Tabelle (LageTabelle.kt).

// Ziele der Karten und Tabellen; karte liefert die Laendernamen
internal class LageZiele(val tippen: (LageTipp) -> Unit, val karte: GlobusKarte?) {
    // Laenderblatt des Landes (alpha-3) ueber der Lage
    fun land(a3: String): () -> Unit = { tippen(LageTipp.Land(a3)) }
    fun engpass(id: String) = tippen(LageTipp.Engpass(id))
    fun spannung(id: String) = tippen(LageTipp.Spannung(id))
    fun durchbruch(id: String) = tippen(LageTipp.Durchbruch(id))
    // Unterabschnitt der Tabelle eines Themas auf- oder zuklappen
    fun abschnitt(t: Lagebild.Thema, tabelle: String): () -> Unit = { tippen(LageTipp.Abschnitt(LageWege.abschnitt(t, tabelle))) }
    fun landName(a3: String): String = karte?.nachA3(a3)?.let { landKurz(if (I18n.lang == Lang.DE) it.de else it.en) } ?: a3
}

// Scrollt die Liste zum Eintrag mit diesem Schluessel, auch wenn er noch nicht aufgebaut ist (Eintrag fuer Eintrag nach unten).
internal suspend fun LazyListState.zuSchluessel(schluessel: Any) {
    val n = layoutInfo.totalItemsCount
    for (i in 0 until n) {
        scrollToItem(i)
        val da = layoutInfo.visibleItemsInfo.firstOrNull { it.key == schluessel }
        if (da != null) { scrollToItem(da.index); return }
    }
}

private fun tagText(tag: Long): String = Fmt.date(tag * 86_400_000L)

@Composable
internal fun stufeFarbe(stufe: Int): Color = when (stufe) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> LocalSignal.current.warn
    else -> LocalSignal.current.down
}

// Kopf einer Karte: Punkt in der Farbe der Stufe, Titel, Stand, darunter die Kernaussage. unten: die Karte endet hier;
// sonst folgt die Tabelle als eigene Eintraege der Liste (KartenStueck).
@Composable
internal fun LageKarte(titel: String, stufe: Int, stand: Long?, unten: Boolean = true, inhalt: @Composable ColumnScope.() -> Unit) {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    KartenStueck(oben = true, unten = unten) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(stufeFarbe(stufe)))
            Spacer(Modifier.width(10.dp))
            Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (stand != null) Text(tagText(stand), style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (unten) 14.dp else 10.dp), content = inhalt)
    }
}

// Zahlenzeile einer Karte: Bezeichnung links, Wert rechts (bricht nie um)
@Composable
internal fun LageWert(label: String, wert: String, farbe: Color = MaterialTheme.colorScheme.onSurface, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.zeilenKlick(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(wert, style = MaterialTheme.typography.titleSmall, color = farbe, textAlign = TextAlign.End, softWrap = false, maxLines = 1)
    }
}

// Skala von min bis max: Fuellung von basis bis wert, Punkt am Wert, Ring am Vorjahreswert, Strich an der Marke (Schwelle)
@Composable
internal fun LageSkala(
    wert: Double, vorher: Double?, min: Double, max: Double, basis: Double, marke: Double?, farbe: Color, onClick: (() -> Unit)? = null,
) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val markeFarbe = LocalSignal.current.warn.takeIf { marke != null && marke != basis } ?: grau
    Canvas(Modifier.fillMaxWidth().height(22.dp).then(if (onClick != null) Modifier.zeilenKlick(onClick = onClick) else Modifier)) {
        val rand = 8.dp.toPx()
        val breit = size.width - 2 * rand
        fun x(v: Double) = rand + breit * ((v.coerceIn(min, max) - min) / (max - min)).toFloat()
        val mitte = size.height / 2
        val dick = 6.dp.toPx()
        drawRoundRect(bahn, Offset(0f, mitte - dick / 2), Size(size.width, dick), CornerRadius(dick / 2))
        marke?.let { drawLine(markeFarbe, Offset(x(it), 2.dp.toPx()), Offset(x(it), size.height - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx()) }
        val a = x(basis)
        val b = x(wert)
        drawRoundRect(farbe, Offset(min(a, b), mitte - dick / 2), Size(abs(b - a).coerceAtLeast(dick), dick), CornerRadius(dick / 2))
        vorher?.let { drawCircle(grau, 5.dp.toPx(), Offset(x(it), mitte), style = Stroke(1.5.dp.toPx())) }
        drawCircle(farbe, 7.dp.toPx(), Offset(x(wert), mitte))
    }
}

// Ring mit Text: erklaert den Ring der Skala (Vergleichswert)
@Composable
internal fun LageRingText(text: String?, strich: String? = null, strichFarbe: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (text != null) {
            Canvas(Modifier.size(10.dp)) { drawCircle(grau, 4.dp.toPx(), style = Stroke(1.5.dp.toPx())) }
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        }
        // Erklaert den senkrechten Strich der Skala (Nulllinie oder Schwelle): rechts, in der Farbe des Strichs
        if (strich != null) {
            Spacer(Modifier.weight(1f))
            Text(strich, style = MaterialTheme.typography.labelSmall, color = strichFarbe, maxLines = 1, softWrap = false)
        }
    }
}

// Ein Feld je Einzelwert (Engpass, Gebiet): die ersten `hoch` gefuellt in der Warnfarbe, der Rest ruhig
@Composable
internal fun LageSegmente(gesamt: Int, hoch: Int, farbe: Color, modifier: Modifier = Modifier) {
    if (gesamt <= 0) return
    val ruhig = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val n = gesamt.coerceAtMost(48)
        val luecke = 2.dp.toPx()
        val w = (size.width - luecke * (n - 1)) / n
        for (i in 0 until n) drawRoundRect(if (i < hoch) farbe else ruhig, Offset(i * (w + luecke), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
    }
}

// Balken von links; anteil 0 bis 1, optional ein zweiter Anteil (rot) vorn und eine Marke
@Composable
internal fun LageBalken(anteil: Double, farbe: Color, modifier: Modifier = Modifier, vorn: Double = 0.0, vornFarbe: Color = farbe, marke: Double? = null) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(bahn, cornerRadius = r)
        val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
        if (w > 0f) drawRoundRect(farbe, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        val v = (size.width * vorn.coerceIn(0.0, 1.0)).toFloat()
        if (v > 0f) drawRoundRect(vornFarbe, size = Size(v.coerceAtLeast(size.height), size.height), cornerRadius = r)
        marke?.let { drawLine(grau, Offset((size.width * it.coerceIn(0.0, 1.0)).toFloat(), -2.dp.toPx()), Offset((size.width * it.coerceIn(0.0, 1.0)).toFloat(), size.height + 2.dp.toPx()), strokeWidth = 1.5.dp.toPx()) }
    }
}

// Zeile mit Name, Wert und Balken; ein Tipp oeffnet das Gemeinte
@Composable
internal fun LageBalkenZeile(
    name: String, wert: String, anteil: Double, farbe: Color, onClick: (() -> Unit)?, vorn: Double = 0.0, vornFarbe: Color = farbe, marke: Double? = null,
    balken: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.zeilenKlick(onClick = onClick) else Modifier).padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(wert, style = MaterialTheme.typography.labelLarge, color = farbe, softWrap = false, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        if (balken != null) balken() else LageBalken(anteil, farbe, Modifier, vorn, vornFarbe, marke)
    }
}

// Verlauf aus Punkten (x, y): Linie mit Punkten, Werte oben und Namen unten an den Punkten, Hilfslinien und Baender
internal class LageHilfslinie(val y: Double, val farbe: Color, val text: String)
internal class LageBand(val von: Double, val bis: Double, val farbe: Color)

@Composable
internal fun LageVerlauf(
    xs: List<Double>, ys: List<Double>, oben: List<String>, unten: List<String>, farbe: Color,
    hilfslinien: List<LageHilfslinie> = emptyList(), baender: List<LageBand> = emptyList(), onClick: (() -> Unit)? = null,
) {
    if (xs.size < 2 || xs.size != ys.size) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = rememberTextMeasurer()
    val klein = MaterialTheme.typography.labelSmall
    val hoehe = 112.dp
    Canvas(Modifier.fillMaxWidth().height(hoehe).then(if (onClick != null) Modifier.zeilenKlick(onClick = onClick) else Modifier)) {
        val kopf = 6.dp.toPx()
        val fuss = 32.dp.toPx()
        val rand = 4.dp.toPx()
        val flaeche = size.height - kopf - fuss
        val alle = ys + hilfslinien.map { it.y }
        var lo = alle.min()
        var hi = alle.max()
        if (hi - lo < 1e-6) { lo -= 0.5; hi += 0.5 }
        val pad = (hi - lo) * 0.12
        lo -= pad; hi += pad
        val x0 = xs.min()
        val x1 = xs.max()
        fun px(x: Double) = rand + (size.width - 2 * rand) * (if (x1 > x0) ((x - x0) / (x1 - x0)).toFloat() else 0f)
        fun py(y: Double) = kopf + flaeche * (1f - ((y - lo) / (hi - lo)).toFloat())
        for (b in baender) {
            val a = py(min(max(b.von, b.bis), hi)); val z = py(max(min(b.von, b.bis), lo))
            if (z > a) drawRect(b.farbe, Offset(0f, a), Size(size.width, z - a))
        }
        for (h in hilfslinien) {
            drawLine(grau.copy(alpha = 0.7f), Offset(0f, py(h.y)), Offset(size.width, py(h.y)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            if (h.text.isNotEmpty()) {
                val g = messer.measure(h.text, klein.copy(color = grau))
                drawText(g, topLeft = Offset(rand, py(h.y) - g.size.height - 1.dp.toPx()))
            }
        }
        val weg = Path()
        xs.indices.forEach { i -> if (i == 0) weg.moveTo(px(xs[i]), py(ys[i])) else weg.lineTo(px(xs[i]), py(ys[i])) }
        drawPath(weg, farbe, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        xs.indices.forEach { i -> drawCircle(farbe, if (i == xs.lastIndex) 5.dp.toPx() else 3.dp.toPx(), Offset(px(xs[i]), py(ys[i]))) }
        fun beschriften(t: String, mitte: Float, oben: Float, f: Color) {
            if (t.isEmpty()) return
            val g = messer.measure(t, klein.copy(color = f))
            drawText(g, topLeft = Offset((mitte - g.size.width / 2).coerceIn(0f, size.width - g.size.width), oben))
        }
        oben.forEachIndexed { i, t -> beschriften(t, px(xs[i]), size.height - fuss + 17.dp.toPx(), farbe) }
        unten.forEachIndexed { i, t -> beschriften(t, px(xs[i]), size.height - fuss + 2.dp.toPx(), grau) }
    }
}

// Waagerechter Balken um die Mitte (Wachstum mit Vorzeichen), max = Betrag am Rand
@Composable
private fun LageMitteBalken(wert: Double?, max: Double, hoch: Color, tief: Color, modifier: Modifier = Modifier) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val strich = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(bahn, cornerRadius = r)
        if (wert == null) return@Canvas
        val halb = size.width / 2
        val w = (halb * (abs(wert) / max).coerceIn(0.0, 1.0)).toFloat().coerceAtLeast(2.dp.toPx())
        drawRoundRect(if (wert < 0) tief else hoch, Offset(if (wert < 0) halb - w else halb, 0f), Size(w, size.height), r)
        drawLine(strich, Offset(halb, -1.dp.toPx()), Offset(halb, size.height + 1.dp.toPx()), strokeWidth = 1.dp.toPx())
    }
}

private typealias T = Lagebild.Thema

private fun kennzahl(a: Lagebild.Aussage, schluessel: String) = a.kennzahlen.firstOrNull { it.schluessel == schluessel }

private fun anteil(wert: Double, groesst: Double) = if (groesst > 0) abs(wert) / groesst else 0.0

// Konjunktur und Inflation: zwei Skalen mit Vorjahresring, darunter die groessten Volkswirtschaften mit Wachstum und Inflation
@Composable
internal fun KonjunkturInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val signal = LocalSignal.current
    val primary = MaterialTheme.colorScheme.primary
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val w = kennzahl(a, "WACHSTUM")
    val inf = kennzahl(a, "INFLATION")
    w?.let {
        val farbe = if (it.wert < 0) signal.down else signal.up
        LageWert(S.lbName("WACHSTUM", it.zusatz.orEmpty()).trim(), lbWertText(it), farbe, onClick = z.abschnitt(T.KONJUNKTUR, "WACHSTUM_LAND"))
        LageSkala(it.wert, it.vorher, -4.0, 8.0, 0.0, 0.0, farbe, onClick = z.abschnitt(T.KONJUNKTUR, "WACHSTUM_LAND"))
        LageRingText(it.vorher?.let { v -> S.lbVorjahr(Fmt.pct(v, 1)) })
        Spacer(Modifier.height(10.dp))
    }
    inf?.let {
        val hoch = it.wert >= Imf.SCHWELLE_INFLATION_ERHOEHT
        val farbe = if (hoch) signal.warn else primary
        LageWert(S.lbName("INFLATION", it.zusatz.orEmpty()).trim(), lbWertText(it), farbe, onClick = z.abschnitt(T.KONJUNKTUR, "INFLATION_LAND"))
        LageSkala(it.wert, it.vorher, 0.0, 10.0, 0.0, Imf.SCHWELLE_INFLATION_ERHOEHT, farbe, onClick = z.abschnitt(T.KONJUNKTUR, "INFLATION_LAND"))
        LageRingText(it.vorher?.let { v -> S.lbVorjahr(Fmt.pct(v, 1)) }, S.lbErhoehtAb(Fmt.pct(Imf.SCHWELLE_INFLATION_ERHOEHT, 0)), signal.warn)
    }
    val laender = (w?.posten.orEmpty().map { it.name } + inf?.posten.orEmpty().map { it.name }).distinct()
    if (laender.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp + 8.dp))
            Text(S.globusWachstum, style = MaterialTheme.typography.labelSmall, color = sv, textAlign = TextAlign.End, softWrap = false, maxLines = 1, modifier = Modifier.width(64.dp).wrapContentWidth(Alignment.End, unbounded = true))
            Spacer(Modifier.width(8.dp))
            Text(S.globusInflation, style = MaterialTheme.typography.labelSmall, color = sv, textAlign = TextAlign.End, softWrap = false, maxLines = 1, modifier = Modifier.width(56.dp))
        }
        for (c in laender) {
            val g = w?.posten?.firstOrNull { it.name == c }?.wert
            val i = inf?.posten?.firstOrNull { it.name == c }?.wert
            Row(
                Modifier.fillMaxWidth().zeilenKlick(onClick = z.land(c)).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(z.landName(c), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                LageMitteBalken(g, 8.0, signal.up, signal.down, Modifier.width(44.dp))
                Text(
                    g?.let { Fmt.pct(it, 1) } ?: "–", style = MaterialTheme.typography.labelLarge, color = if (g != null && g < 0) signal.down else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End, softWrap = false, maxLines = 1, modifier = Modifier.width(64.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    i?.let { Fmt.pct(it, 1) } ?: "–", style = MaterialTheme.typography.labelLarge,
                    color = if (i != null && i >= Imf.SCHWELLE_INFLATION_ERHOEHT) signal.warn else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End, softWrap = false, maxLines = 1, modifier = Modifier.width(56.dp),
                )
            }
        }
    }
}

// Zinsen: Leitzins heute, darunter der erwartete Satz je Monat als Verlauf, dann der erwartete Schritt
@Composable
internal fun ZinsenInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val primary = MaterialTheme.colorScheme.primary
    val ziel: () -> Unit = { z.tippen(LageWege.weg(T.ZINSEN)) }
    val heute = kennzahl(a, "ZINS_HEUTE")
    val schritte = kennzahl(a, "ZINS_SCHRITTE")
    heute?.let { LageWert(S.lbName("ZINS_HEUTE", "").trim(), lbWertText(it), onClick = ziel) }
    val pfad = schritte?.posten.orEmpty()
    if (pfad.size >= 2) {
        val monate = pfad.map { it.name.toIntOrNull() ?: 0 }
        LageVerlauf(
            monate.map { it.toDouble() }, pfad.map { it.wert }, pfad.map { Fmt.num(it.wert, 2) },
            monate.map { if (it == 0) S.zeHeute else S.zeIn(it) }, primary, onClick = ziel,
        )
    } else heute?.let { LageSkala(it.wert, null, 0.0, 8.0, 0.0, null, primary, onClick = ziel) }
    schritte?.let { LageWert(S.lbName("ZINS_SCHRITTE", "").trim(), lbWertText(it), if (it.auffaellig) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurface, onClick = ziel) }
}

// Geopolitik: Kriege je Land als Balken, Spannungsgebiete mit Segmentleiste und Schwelle
@Composable
internal fun GeopolitikInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val signal = LocalSignal.current
    val primary = MaterialTheme.colorScheme.primary
    kennzahl(a, "KRIEGE")?.let { k ->
        LageWert(S.lbName("KRIEGE", k.zusatz.orEmpty()).trim(), lbWertText(k), if (k.wert > 0) signal.down else primary, onClick = z.abschnitt(T.GEOPOLITIK, "KRIEGE"))
        val groesst = k.posten.maxOfOrNull { it.wert } ?: 1.0
        for (p in k.posten) LageBalkenZeile(z.landName(p.name), p.wert.toInt().toString(), anteil(p.wert, groesst), signal.down, z.land(p.name))
    }
    kennzahl(a, "SPANNUNG")?.let { k ->
        Spacer(Modifier.height(8.dp))
        LageWert(S.lbName("SPANNUNG", "").trim(), lbWertText(k), if (k.auffaellig) signal.warn else primary, onClick = z.abschnitt(T.GEOPOLITIK, "SPANNUNG"))
        LageSegmente(k.zusatz?.toIntOrNull() ?: 0, k.wert.toInt(), signal.warn)
        val skala = max(2.0 * Lagebild.SPANNUNG_HOCH, k.posten.maxOfOrNull { it.wert } ?: 0.0)
        for (p in k.posten) {
            val hoch = p.wert >= Lagebild.SPANNUNG_HOCH
            LageBalkenZeile(
                S.spName(p.name), Fmt.num(p.wert, 1) + "×", anteil(p.wert, skala), if (hoch) signal.warn else primary,
                { z.spannung(p.name) }, marke = Lagebild.SPANNUNG_HOCH / skala,
            )
        }
    }
}

// Rohstoffe und Energie: Segmentleiste der gestoerten Engpaesse, der staerkste Rueckgang, Balken der groessten Rueckgaenge
@Composable
internal fun RohstoffeInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val signal = LocalSignal.current
    val g = kennzahl(a, "ENGPASS_GESTOERT")
    val s = kennzahl(a, "ENGPASS_SCHLECHTESTER")
    val posten = s?.posten?.takeIf { it.isNotEmpty() } ?: g?.posten.orEmpty()
    val tabelle = z.abschnitt(T.ROHSTOFFE, "ENGPAESSE")
    g?.let {
        LageWert(S.lbName("ENGPASS_GESTOERT", "").trim(), lbWertText(it), if (it.auffaellig) signal.down else MaterialTheme.colorScheme.onSurface, onClick = tabelle)
        LageSegmente(it.zusatz?.toIntOrNull() ?: 0, it.wert.toInt(), signal.down)
        Spacer(Modifier.height(6.dp))
    }
    s?.let { LageWert(S.engName(it.zusatz.orEmpty()), lbWertText(it), Color(GlobusFarbe.engpass(it.wert, signal.up.toArgb(), signal.down.toArgb())), onClick = { z.engpass(it.zusatz.orEmpty()) }) }
    for (p in posten) {
        val farbe = Color(GlobusFarbe.engpass(p.wert, signal.up.toArgb(), signal.down.toArgb()))
        LageBalkenZeile(S.engName(p.name), Fmt.signedPct(p.wert, 0), 0.0, farbe, { z.engpass(p.name) }) { EngpassBalken(p.wert, farbe) }
    }
}

// Klima: ENSO-Zustand mit Verlauf des Index (Schwellen gestrichelt, Baender fuer El Nino und La Nina), Wetterkatastrophen je Land
@Composable
internal fun KlimaInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val signal = LocalSignal.current
    val primary = MaterialTheme.colorScheme.primary
    val enso = z.abschnitt(T.KLIMA, "ENSO")
    kennzahl(a, "ENSO_ZUSTAND")?.let { LageWert(S.lbName("ENSO_ZUSTAND", "").trim(), lbWertText(it), if (it.auffaellig) signal.warn else MaterialTheme.colorScheme.onSurface, onClick = enso) }
    kennzahl(a, "ENSO_INDEX")?.let { k ->
        val p = k.posten
        if (p.size >= 2) {
            val s = Enso.SCHWELLE
            val unten = p.indices.map { i -> if (i == 0 || i == p.lastIndex) p[i].name else "" }
            val oben = p.indices.map { i -> if (i == p.lastIndex) lbWertText(k) else "" }
            LageVerlauf(
                p.indices.map { it.toDouble() }, p.map { it.wert }, oben, unten, primary,
                hilfslinien = listOf(LageHilfslinie(s, primary, ""), LageHilfslinie(-s, primary, "")),
                baender = listOf(LageBand(s, 99.0, signal.warn.copy(alpha = 0.16f)), LageBand(-99.0, -s, signal.link.copy(alpha = 0.16f))),
                onClick = enso,
            )
        } else {
            LageWert(S.lbName("ENSO_INDEX", "").trim(), lbWertText(k), onClick = enso)
            LageSkala(k.wert, k.vorher, -3.0, 3.0, 0.0, 0.0, primary, onClick = enso)
        }
    }
    kennzahl(a, "KATASTROPHEN")?.let { k ->
        Spacer(Modifier.height(8.dp))
        val rot = k.zusatz?.toIntOrNull() ?: 0
        LageWert(S.lbName("KATASTROPHEN", "").trim(), lbWertText(k), if (rot > 0) signal.down else primary, onClick = z.abschnitt(T.KLIMA, "KATASTROPHEN_LAND"))
        val groesst = k.posten.maxOfOrNull { it.wert } ?: 1.0
        for (p in k.posten) {
            val r = p.zusatz?.toIntOrNull() ?: 0
            LageBalkenZeile(
                z.landName(p.name), if (r > 0) S.lbRot(p.wert.toInt().toString(), r.toString()) else p.wert.toInt().toString(),
                anteil(p.wert, groesst), signal.warn, z.land(p.name), vorn = anteil(r.toDouble(), groesst), vornFarbe = signal.down,
            )
        }
    }
}

// Technik: neue Durchbruchmeldungen der Woche, Balken je Thema
@Composable
internal fun TechnikInhalt(a: Lagebild.Aussage, z: LageZiele) {
    val primary = MaterialTheme.colorScheme.primary
    val ziel = z.abschnitt(T.TECHNIK, "DURCHBRUCH_THEMA")
    kennzahl(a, "DURCHBRUCH_NEU")?.let { LageWert(S.lbName("DURCHBRUCH_NEU", "").trim(), lbWertText(it), onClick = ziel) }
    kennzahl(a, "DURCHBRUCH_THEMEN")?.let { k ->
        LageWert(S.lbName("DURCHBRUCH_THEMEN", "").trim(), lbWertText(k), onClick = ziel)
        val groesst = k.posten.maxOfOrNull { it.wert } ?: 1.0
        for (p in k.posten) {
            val name = Durchbrueche.thema(p.name)?.let { if (I18n.lang == Lang.DE) it.de else it.en } ?: p.name
            LageBalkenZeile(name, p.wert.toInt().toString(), anteil(p.wert, groesst), primary, { z.durchbruch(p.name) })
        }
    }
}
