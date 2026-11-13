package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.LangeReihen
import de.troetenrepublik.app.engine.WaehrungReihen
import de.troetenrepublik.app.engine.WaehrungReihen.Waehrung
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.WaehrungTexte
import java.time.YearMonth

private fun wText(key: String, vararg w: String): String = WaehrungTexte.text(I18n.lang == Lang.DE, key, w.toList())

private fun wMonat(m: Int): String = Fmt.monthShortYear(YearMonth.of(WaehrungReihen.jahr(m), WaehrungReihen.monatImJahr(m)))

// Kurse mit zwei Stellen, ab 50 Einheiten (Yen) mit einer
private fun wKurs(v: Double): String = Fmt.num(v, if (v >= 50.0) 1 else 2)

// Abschnitt Wechselkurse der Boersengeschichte: Wert eines US-Dollars in Euro, Pfund, Yen, Franken oder kanadischen
// Dollar ab 1971 (Federal Reserve, H.10). Kopfzeile mit der Kernaussage fuer die gewaehlte Waehrung, aufgeklappt Linie,
// Hoch, Tief und Veraenderung. Ohne die Reihen in der Datei erscheint nichts.
@Composable
internal fun WaehrungAbschnitt(datei: LangeReihen.Datei?) {
    val liste = remember(datei) { datei?.let { WaehrungReihen.verfuegbar(it) }.orEmpty() }
    if (datei == null || liste.isEmpty()) return
    var offen by rememberSaveable { mutableStateOf(false) }
    var gewaehlt by rememberSaveable { mutableStateOf(liste.first().name) }
    val w = liste.firstOrNull { it.name == gewaehlt } ?: liste.first()
    val reihe = remember(datei, w) { WaehrungReihen.dollarIn(datei, w) }
    val spanne = remember(reihe) { reihe?.let { WaehrungReihen.spanne(it) } }
    if (reihe == null || spanne == null) return
    val signal = LocalSignal.current
    val farbe = if (spanne.aenderung >= 0) signal.up else signal.down
    val seit = WaehrungReihen.jahr(spanne.erster.monat).toString()

    AufklappKopf(
        wText("titel"),
        wText("kurz", wText(w.name), wKurs(spanne.letzter.wert), seit, Fmt.signedPct(spanne.aenderung, 0)),
        offen, maxZeilen = 2,
    ) { offen = !offen }
    if (!offen) return

    ToolChoice(null, liste.map { wText(it.name) to it.name }, w.name) { gewaehlt = it }
    WaehrungLinie(reihe, spanne, farbe)
    val einheit = w.kuerzel
    KeyValueRow(wText("aktuell"), wKurs(spanne.letzter.wert) + " " + einheit, hint = wMonat(spanne.letzter.monat))
    KeyValueRow(wText("hoch"), wKurs(spanne.hoch.wert) + " " + einheit, hint = wMonat(spanne.hoch.monat))
    KeyValueRow(wText("tief"), wKurs(spanne.tief.wert) + " " + einheit, hint = wMonat(spanne.tief.monat))
    KeyValueRow(wText("seit", seit), Fmt.signedPct(spanne.aenderung, 1), farbe)
    HinweisInfo(wText("info") + if (w == Waehrung.EUR) " " + wText("info.eur") else "")
}

// Linie der Monatswerte auf der Breite, Hoch und Tief als graue Punkte, der letzte Wert in der Farbe der Linie;
// darunter erstes und letztes Jahr.
@Composable
private fun WaehrungLinie(r: LangeReihen.Monatsreihe, s: WaehrungReihen.Spanne, farbe: androidx.compose.ui.graphics.Color) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp).height(140.dp)) {
        val n = r.werte.size
        if (n < 2) return@Canvas
        val rand = 4.dp.toPx()
        val breite = size.width - 2 * rand
        val hoehe = size.height - 2 * rand
        val lo = s.tief.wert
        val spanne = (s.hoch.wert - lo).takeIf { it > 0.0 } ?: return@Canvas
        fun punkt(monat: Int, wert: Double) =
            Offset(rand + breite * (monat - r.ab) / (n - 1), rand + hoehe * (1.0 - (wert - lo) / spanne).toFloat())
        val pfad = Path()
        var offenerZug = false
        r.werte.forEachIndexed { i, v ->
            if (v == null) { offenerZug = false; return@forEachIndexed }
            val p = punkt(r.ab + i, v)
            if (!offenerZug) { pfad.moveTo(p.x, p.y); offenerZug = true } else pfad.lineTo(p.x, p.y)
        }
        drawLine(grau.copy(alpha = 0.6f), Offset(rand, size.height - rand / 2), Offset(size.width - rand, size.height - rand / 2), strokeWidth = 1.dp.toPx())
        drawPath(pfad, farbe, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(grau, 3.dp.toPx(), punkt(s.hoch.monat, s.hoch.wert))
        drawCircle(grau, 3.dp.toPx(), punkt(s.tief.monat, s.tief.wert))
        drawCircle(farbe, 3.dp.toPx(), punkt(s.letzter.monat, s.letzter.wert))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val stil = MaterialTheme.typography.labelSmall
        Text(WaehrungReihen.jahr(s.erster.monat).toString(), style = stil, color = grau, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Text(WaehrungReihen.jahr(s.letzter.monat).toString(), style = stil, color = grau, maxLines = 1, softWrap = false)
    }
}
