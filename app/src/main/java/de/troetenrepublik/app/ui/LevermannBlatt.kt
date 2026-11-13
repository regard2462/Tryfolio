package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.Levermann
import de.troetenrepublik.app.i18n.S

// Blatt der Levermann-Punkte (0.79): Kopf mit Punktzahl und Balken bis zur Zahl der Kriterien, die Schwelle der Methode
// markiert; darunter die Kriterien in vier Gruppen (Qualitaet, Bewertung, Stimmung, Kurs), je Gruppe die Summe. Jedes
// Kriterium steht in einer Zeile: Name links, Wert und Punkt rechts in Signalfarbe, darunter eine kleine Skala mit
// den Zonen schlecht, neutral und gut und dem Wert als Marke. Die Schwellen sind die der Rechnung (engine/Levermann).

// Zonen einer Skala: ab gut gibt es einen Punkt, unter schlecht einen Abzug (oder umgekehrt, wenn niedriger besser ist).
internal class LevSkala(
    val von: Double, val bis: Double, val schlecht: Double, val gut: Double, val gutOben: Boolean, val text: (Double) -> String,
)

private fun vorz(v: Double): String = if (v > 0) "+${Fmt.num(v, 0)}" else if (v < 0) "−${Fmt.num(-v, 0)}" else "0"

internal fun levSkala(k: Levermann.Kriterium, variante: Boolean): LevSkala? = when (k) {
    Levermann.Kriterium.EK_RENDITE -> LevSkala(0.0, 40.0, Levermann.EK_RENDITE_SCHLECHT, Levermann.EK_RENDITE_GUT, true) { Fmt.pct(it, 0) }
    Levermann.Kriterium.EBIT_MARGE -> LevSkala(0.0, 24.0, Levermann.EBIT_MARGE_SCHLECHT, Levermann.EBIT_MARGE_GUT, true) { Fmt.pct(it, 0) }
    Levermann.Kriterium.EK_QUOTE ->
        if (variante) LevSkala(0.0, 20.0, Levermann.EK_QUOTE_FINANZ_SCHLECHT, Levermann.EK_QUOTE_FINANZ_GUT, true) { Fmt.pct(it, 0) }
        else LevSkala(0.0, 50.0, Levermann.EK_QUOTE_SCHLECHT, Levermann.EK_QUOTE_GUT, true) { Fmt.pct(it, 0) }
    Levermann.Kriterium.KGV, Levermann.Kriterium.KGV_SCHNITT ->
        LevSkala(0.0, 30.0, Levermann.KGV_SCHLECHT, Levermann.KGV_GUT, false) { Fmt.num(it, 0) }
    // Gegenzeichen: eine schlechte Note gibt den Punkt; bei wenigen Analysten in mittleren und kleinen Werten umgekehrt
    Levermann.Kriterium.ANALYSTEN ->
        LevSkala(1.0, 5.0, Levermann.NOTE_KAUFEN, Levermann.NOTE_VERKAUFEN, !variante) { Fmt.num(it, 1) }
    Levermann.Kriterium.ZAHLEN -> LevSkala(-5.0, 5.0, -Levermann.ZAHLEN_SCHWELLE, Levermann.ZAHLEN_SCHWELLE, true) { vorz(it) }
    Levermann.Kriterium.REVISIONEN -> LevSkala(-15.0, 15.0, -Levermann.SCHWELLE, Levermann.SCHWELLE, true) { Fmt.signedPct(it, 0) }
    Levermann.Kriterium.KURS6, Levermann.Kriterium.KURS12 ->
        LevSkala(-20.0, 20.0, -Levermann.SCHWELLE, Levermann.SCHWELLE, true) { Fmt.signedPct(it, 0) }
    Levermann.Kriterium.GEWINNWACHSTUM -> LevSkala(-20.0, 20.0, -Levermann.SCHWELLE, Levermann.SCHWELLE, true) { Fmt.signedPct(it, 0) }
    Levermann.Kriterium.MOMENTUM, Levermann.Kriterium.UMKEHR -> null
}

private enum class LevGruppe(val kriterien: List<Levermann.Kriterium>) {
    QUALITAET(listOf(Levermann.Kriterium.EK_RENDITE, Levermann.Kriterium.EBIT_MARGE, Levermann.Kriterium.EK_QUOTE)),
    BEWERTUNG(listOf(Levermann.Kriterium.KGV, Levermann.Kriterium.KGV_SCHNITT)),
    STIMMUNG(listOf(Levermann.Kriterium.ANALYSTEN, Levermann.Kriterium.ZAHLEN, Levermann.Kriterium.REVISIONEN, Levermann.Kriterium.GEWINNWACHSTUM)),
    KURS(listOf(Levermann.Kriterium.KURS6, Levermann.Kriterium.KURS12, Levermann.Kriterium.MOMENTUM, Levermann.Kriterium.UMKEHR)),
}

@Composable
private fun punkteFarbe(punkte: Int?): Color {
    val signal = LocalSignal.current
    return when {
        punkte == null -> MaterialTheme.colorScheme.onSurfaceVariant
        punkte > 0 -> signal.up
        punkte < 0 -> signal.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun LevermannBlatt(name: String, e: Levermann.Ergebnis, analysten: Int, onDismiss: () -> Unit) {
    DetailBlatt(S.levTitel, name, onDismiss) {
        LevermannKopf(e)
        val klein = e.groesse == Levermann.Groesse.MITTEL_KLEIN
        for (g in LevGruppe.entries) {
            val zeilen = e.zeilen.filter { it.kriterium in g.kriterien }
            if (zeilen.isEmpty()) continue
            val summe = zeilen.sumOf { it.punkte ?: 0 }
            val bewertet = zeilen.any { it.punkte != null }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.levGruppe(g.name), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (bewertet) Text(
                    punkteText(summe), style = MaterialTheme.typography.titleSmall, color = punkteFarbe(summe), maxLines = 1, softWrap = false,
                )
            }
            zeilen.forEachIndexed { i, z ->
                if (i > 0) Trennlinie(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                val variante = when (z.kriterium) {
                    Levermann.Kriterium.EK_QUOTE -> e.moeglich < Levermann.Kriterium.entries.size
                    Levermann.Kriterium.ANALYSTEN -> klein && analysten <= Levermann.WENIG_ANALYSTEN
                    Levermann.Kriterium.UMKEHR -> klein
                    else -> false
                }
                LevKriterium(z, variante)
            }
        }
    }
}

// Punktzahl gross, Balken von 0 bis zur Zahl der moeglichen Punkte, Schwelle der Methode als Strich mit Zahl.
@Composable
private fun LevermannKopf(e: Levermann.Ergebnis) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = when (Levermann.ampel(e)) {
        Bewertungsblick.Ampel.GRUEN -> signal.up
        Bewertungsblick.Ampel.ROT -> signal.down
        Bewertungsblick.Ampel.GELB -> signal.warn
        null -> grau
    }
    val unter = listOfNotNull(
        e.kaufAb?.let { S.levKaufAb(it, e.groesse == Levermann.Groesse.GROSS) },
        (e.moeglich - e.bewertet).takeIf { it > 0 }?.let { S.levOhne(it) },
        S.levFinanz.takeIf { e.moeglich < Levermann.Kriterium.entries.size },
    ).joinToString(PUNKT).ifEmpty { null }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(punkteText(e.punkte), style = MaterialTheme.typography.displaySmall, color = farbe, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(
                S.levVon(e.moeglich),
                style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1, softWrap = false,
            )
        }
        val spur = grau.copy(alpha = 0.18f)
        val strich = MaterialTheme.colorScheme.onSurface
        val schwelle = e.kaufAb
        Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(14.dp)) {
            val h = 8.dp.toPx()
            val y = (size.height - h) / 2f
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(spur, Offset(0f, y), Size(size.width, h), r)
            val anteil = (e.punkte.coerceAtLeast(0).toFloat() / e.moeglich).coerceIn(0f, 1f)
            if (anteil > 0f) drawRoundRect(farbe, Offset(0f, y), Size(size.width * anteil, h), r)
            if (schwelle != null) {
                val x = size.width * (schwelle.toFloat() / e.moeglich).coerceIn(0f, 1f)
                drawRect(strich, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            }
        }
        if (schwelle != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val x = (maxWidth * (schwelle.toFloat() / e.moeglich) - 20.dp).coerceIn(0.dp, maxWidth - 40.dp)
                Text(
                    schwelle.toString(), style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x = x).width(40.dp),
                )
            }
        }
        if (unter != null) Text(unter, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 6.dp))
    }
}

// Eine Zeile je Kriterium: Name links, Wert und Punkt rechts, darunter die Skala oder eine kurze Beschreibung.
@Composable
private fun LevKriterium(z: Levermann.Zeile, variante: Boolean) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val w = z.wert
    val punkte = z.punkte
    val wert: String? = if (punkte == null) null else if (w == null) {
        if (z.kriterium == Levermann.Kriterium.MOMENTUM || z.kriterium == Levermann.Kriterium.UMKEHR) S.levWert(z.kriterium.name, punkte, variante) else null
    } else when (z.kriterium) {
        Levermann.Kriterium.EK_RENDITE, Levermann.Kriterium.EBIT_MARGE, Levermann.Kriterium.EK_QUOTE -> Fmt.pct(w, 1)
        Levermann.Kriterium.KGV, Levermann.Kriterium.KGV_SCHNITT -> if (w > 0) "${Fmt.num(w, 1)}×" else null
        Levermann.Kriterium.ANALYSTEN -> Fmt.num(w, 1)
        Levermann.Kriterium.ZAHLEN -> Fmt.signedPunkte(w, 1)
        else -> Fmt.signedPct(w, 1)
    }
    val skala = levSkala(z.kriterium, variante)
    val kurz = S.levKurz(z.kriterium.name, variante).ifEmpty { null }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(S.levName(z.kriterium.name), style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                wert ?: if (punkte == null) S.levKeine else "", style = MaterialTheme.typography.bodyMedium,
                color = if (wert == null) grau else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
            )
            Spacer(Modifier.width(10.dp))
            PunktMarke(punkte)
        }
        // Verlust-KGV hat keinen Wert auf der Skala und steht im Wert als Zahl nicht da; die Skala zeigt dann keine Marke
        if (skala != null && punkte != null) LevSkalaBild(skala, w?.takeIf { z.kriterium != Levermann.Kriterium.KGV && z.kriterium != Levermann.Kriterium.KGV_SCHNITT || it > 0 }, Modifier.padding(top = 6.dp))
        if (kurz != null) Text(kurz, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 2.dp))
    }
}

// Punkt in Signalfarbe: Kreis und Zahl in fester Breite, damit alle Zeilen in einer Spalte enden.
@Composable
private fun PunktMarke(punkte: Int?) {
    val farbe = punkteFarbe(punkte)
    Row(Modifier.width(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        if (punkte != null) {
            Canvas(Modifier.size(8.dp)) { drawCircle(farbe) }
            Spacer(Modifier.width(6.dp))
            Text(punkteText(punkte), style = MaterialTheme.typography.bodyMedium, color = farbe, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

// Leiste mit drei Zonen (schlecht, neutral, gut) in dezenten Signalfarben, der Wert als Marke, die Schwellenzahlen klein
// an den Grenzen. Eine Marke ausserhalb der Leiste sitzt am Rand.
@Composable
private fun LevSkalaBild(s: LevSkala, wert: Double?, modifier: Modifier = Modifier) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val marke = MaterialTheme.colorScheme.onSurface
    val grund = MaterialTheme.colorScheme.background
    fun anteil(v: Double): Float = ((v - s.von) / (s.bis - s.von)).coerceIn(0.0, 1.0).toFloat()
    val unten = minOf(s.schlecht, s.gut)
    val oben = maxOf(s.schlecht, s.gut)
    val a1 = anteil(unten)
    val a2 = anteil(oben)
    val links = (if (s.gutOben) signal.down else signal.up).copy(alpha = 0.3f)
    val rechts = (if (s.gutOben) signal.up else signal.down).copy(alpha = 0.3f)
    val mitte = grau.copy(alpha = 0.16f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = 6.dp.toPx()
            val y = (size.height - h) / 2f
            val form = Path().apply { addRoundRect(RoundRect(0f, y, size.width, y + h, CornerRadius(h / 2f, h / 2f))) }
            clipPath(form) {
                drawRect(links, Offset(0f, y), Size(size.width * a1, h))
                drawRect(mitte, Offset(size.width * a1, y), Size(size.width * (a2 - a1), h))
                drawRect(rechts, Offset(size.width * a2, y), Size(size.width * (1f - a2), h))
            }
            if (wert != null) {
                val x = size.width * anteil(wert)
                drawCircle(grund, radius = 6.dp.toPx(), center = Offset(x, size.height / 2f))
                drawCircle(marke, radius = 4.dp.toPx(), center = Offset(x, size.height / 2f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            fun lage(a: Float): Dp = (maxWidth * a - 24.dp).coerceIn(0.dp, maxWidth - 48.dp)
            for ((a, v) in listOf(a1 to unten, a2 to oben)) {
                Text(
                    s.text(v), style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = lage(a)).width(48.dp),
                )
            }
            // Platz fuer die Zeile der Zahlen, damit die Box Hoehe hat
            Spacer(Modifier.height(16.dp))
        }
    }
}
