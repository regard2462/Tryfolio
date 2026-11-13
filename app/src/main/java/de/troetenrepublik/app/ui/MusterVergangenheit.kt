package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Kursmuster
import de.troetenrepublik.app.i18n.S

// Muster der Vergangenheit im Ausblick, unter den Fruehsignalen: je Muster eine Zeile mit Kernaussage (Name, Mittelwert,
// wie oft im Plus, ueber wie viele Jahre), ein Tipp zeigt die Grafik. Die Zahlen stammen aus engine/Kursmuster.kt
// (mitgelieferte Auswertung langer Tagesreihen) und aus dem Warnzeichen-Rueckblick.
// saisonSprung: zaehlt die Tipps auf die Saisonzeile des Phasenzaehlers; jeder neue klappt die Saison des S&P 500 auf.
// Am Ende der Weg zu "Wirkt das?" (Rhythmus, Boersengaenge und weitere Muster mit gemessener Wirkung).
@Composable
internal fun MusterAbschnitt(state: AppState, oeffnen: (String) -> Unit = {}, saisonSprung: Int = 0) {
    val datei = remember { Kursmuster.datei }
    val sp = remember(datei) { Kursmuster.index(datei, "sp500") } ?: return
    val dax = remember(datei) { Kursmuster.index(datei, "dax") }
    val halb = remember { de.troetenrepublik.app.engine.Halbjahr.datei }
    val baer = remember { de.troetenrepublik.app.engine.Baerenmaerkte.aus(de.troetenrepublik.app.engine.WarnRueckblick.datei, "sp500") }
    val heute = remember { java.time.LocalDate.now() }
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    var sprungErledigt by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(saisonSprung) {
        if (saisonSprung != sprungErledigt) { sprungErledigt = saisonSprung; offen = "saison-sp500" }
    }
    fun tipp(k: String): () -> Unit = { offen = if (offen == k) null else k }
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.vmTitel, null)
        if (sp.zyklus.size == 4) ZyklusZeile(sp, heute.year, offen == "zyklus", tipp("zyklus"))
        SaisonZeile(sp, heute.monthValue, offen == "saison-sp500", tipp("saison-sp500"))
        dax?.let { SaisonZeile(it, heute.monthValue, offen == "saison-dax", tipp("saison-dax")) }
        // Halbjahresregel: Winter gegen Sommer, mit dem Vergleich gegen durchgehend investiert
        halb?.indizes?.forEach { h -> HalbjahrZeile(h, heute.monthValue, offen == "halbjahr-" + h.id, tipp("halbjahr-" + h.id)) }
        baer?.let { BaerZeile(state, it, offen == "baer", tipp("baer")) }
        NavRow(S.wdTitel, "") { oeffnen(CopyRouten.WIRKT_DAS) }
        Spacer(Modifier.height(10.dp))
    }
}

// Eine Zeile: Name und Wert, darunter der Satz zur Einordnung; aufgeklappt der Inhalt
@Composable
internal fun MusterZeile(name: String, wert: String, satz: String, auf: Boolean, onClick: () -> Unit, inhalt: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NameUndWert(name, wert)
                Text(satz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(auf)
        }
        if (auf) {
            Spacer(Modifier.height(6.dp))
            inhalt()
        }
    }
}

// Praesidentschaftszyklus: mittlere Jahresrendite des S&P 500 je Jahr nach einer US-Wahl, das laufende Jahr hervorgehoben
@Composable
private fun ZyklusZeile(x: Kursmuster.Index, jahr: Int, auf: Boolean, onClick: () -> Unit) {
    val z = Kursmuster.zyklusJahr(jahr)
    val w = x.zyklus[z - 1]
    MusterZeile(S.vmZyklusName(z), Fmt.signedPct(w.mittel, 1), S.vmZyklusSatz(w.positiv, w.anzahl, x.bisJahr - x.vonJahr + 1, x.vonJahr), auf, onClick) {
        MusterBalken(x.zyklus, z - 1, name = { S.vmZyklusKurz(it + 1) }) { i -> S.vmBalkenKopf(S.vmZyklusKurz(i + 1), Fmt.signedPct(x.zyklus[i].mittel, 1), x.zyklus[i].positiv, x.zyklus[i].anzahl) }
        Text(S.vmGrundlage(x.id, x.vonJahr, x.bisJahr), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Balken je Eintrag um die Nulllinie, Name unter dem Balken, der laufende Eintrag kraeftig. Oben steht der laufende
// Eintrag ausgeschrieben; Tippen oder Ziehen zeigt dort einen anderen.
@Composable
internal fun MusterBalken(werte: List<Kursmuster.Wert>, aktuell: Int, name: (Int) -> String, kopf: (Int) -> String) {
    if (werte.isEmpty()) return
    val signal = LocalSignal.current
    val linie = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val kraeftig = MaterialTheme.colorScheme.onSurface
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    var gewaehlt by remember(werte) { mutableStateOf<Int?>(null) }
    val lo = minOf(0.0, werte.minOf { it.mittel })
    val hi = maxOf(0.0, werte.maxOf { it.mittel }).let { if (it - lo < 1e-9) lo + 1 else it }
    Canvas(
        Modifier.fillMaxWidth().height(112.dp).padding(vertical = 4.dp).fingerLesen(werte) { p, groesse ->
            gewaehlt = p?.let { (it.x / (groesse.width.toFloat() / werte.size)).toInt().coerceIn(0, werte.size - 1) }
        },
    ) {
        val oben = 20.dp.toPx()
        val unten = size.height - 16.dp.toPx()
        val spalte = size.width / werte.size
        val breite = (spalte * 0.62f).coerceAtMost(40.dp.toPx())
        fun y(v: Double) = oben + (unten - oben) * (1f - ((v - lo) / (hi - lo)).toFloat())
        val zeigt = gewaehlt ?: aktuell
        werte.forEachIndexed { i, w ->
            val mitte = spalte * i + spalte / 2
            val farbe = (if (w.mittel >= 0) signal.up else signal.down).copy(alpha = if (i == zeigt) 1f else 0.4f)
            val a = y(maxOf(w.mittel, 0.0))
            val b = y(minOf(w.mittel, 0.0))
            drawRoundRect(farbe, Offset(mitte - breite / 2, a), Size(breite, maxOf(b - a, 1.5f)), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            val t = messer.measure(name(i), klein.copy(color = if (i == aktuell) kraeftig else schrift), softWrap = false, maxLines = 1)
            if (t.size.width <= spalte) drawText(t, topLeft = Offset(mitte - t.size.width / 2f, size.height - t.size.height))
        }
        drawLine(linie, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.5f)
        val m = messer.measure(kopf(zeigt), klein.copy(color = kraeftig), softWrap = false, maxLines = 1)
        val mitte = spalte * zeigt + spalte / 2
        drawText(m, topLeft = Offset((mitte - m.size.width / 2f).coerceIn(0f, (size.width - m.size.width).coerceAtLeast(0f)), 0f))
    }
}
