package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Indikatoren
import de.troetenrepublik.app.i18n.S
import java.util.TreeMap

// Farben der Indikatorlinien im Kurschart: gedeckte Toene, getrennt von Gruen und Rot. Die Texte darunter bleiben grau.
internal val SMA50_FARBE = Color(0xFF6F9BE8)
internal val SMA200_FARBE = Color(0xFFD9A55B)
internal val BOLLINGER_FARBE = Color(0xFFA48FD6)
internal val MACD_FARBE = Color(0xFF5FB3BF)
internal val MACD_SIGNAL_FARBE = Color(0xFFD9927A)
internal val STOCH_FARBE = Color(0xFFC98DB5)
internal val ATR_FARBE = Color(0xFF9FB0BA)
internal val OBV_FARBE = Color(0xFFB59F8C)
internal val ICHIMOKU_FARBE = Color(0xFF7FB8A8)
internal val SAR_FARBE = Color(0xFFC9C28A)

// Alle Indikatoren in der Reihenfolge der Auswahl; Schluessel wie in Indikatoren.Deutung.art.
internal val INDIKATOREN = listOf("sma50", "sma200", "bb", "ichimoku", "sar", "rsi", "stoch", "macd", "atr", "obv")

// Indikatoren aus der Tagesreihe eines Werts, je Tag (Tagesnummer) ein Wert; dazu der letzte Kurs zum Angleichen.
internal class IndikatorReihen(
    val sma50: TreeMap<Long, Double>,
    val sma200: TreeMap<Long, Double>,
    val bbOben: TreeMap<Long, Double>,
    val bbUnten: TreeMap<Long, Double>,
    val rsi: TreeMap<Long, Double>,
    val macd: TreeMap<Long, Double>,
    val macdSignal: TreeMap<Long, Double>,
    val macdHist: TreeMap<Long, Double>,
    val stochK: TreeMap<Long, Double>,
    val stochD: TreeMap<Long, Double>,
    // ATR in Prozent des Kurses.
    val atr: TreeMap<Long, Double>,
    // On-Balance-Volume, leer ohne Umsaetze.
    val obv: TreeMap<Long, Double>,
    val ichimokuKijun: TreeMap<Long, Double>,
    val ichimokuA: TreeMap<Long, Double>,
    val ichimokuB: TreeMap<Long, Double>,
    // Parabolic SAR getrennt nach Seite, damit die Linie beim Wechsel abbricht.
    val sarUnten: TreeMap<Long, Double>,
    val sarOben: TreeMap<Long, Double>,
    val letzterKurs: Double,
    // Was die Indikatoren zum letzten Kurs sagen.
    val deutungen: List<Indikatoren.Deutung>,
) {
    companion object {
        fun aus(kerzen: List<Candle>): IndikatorReihen {
            val l = kerzen.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
            val tage = l.map { Math.floorDiv(it.t, 86_400_000L) }
            val werte = l.map { it.close }
            // Fehlen Hoch und Tief (manche Reihen haben nur Schlusskurse), zaehlt der Schlusskurs
            val hoch = l.map { if (it.high > 0 && it.high.isFinite()) maxOf(it.high, it.close) else it.close }
            val tief = l.map { if (it.low > 0 && it.low.isFinite()) minOf(it.low, it.close) else it.close }
            val umsatz = l.map { if (it.volume.isFinite() && it.volume > 0) it.volume else 0.0 }
            fun karte(r: List<Double?>) = TreeMap<Long, Double>().apply { r.forEachIndexed { i, v -> if (v != null) put(tage[i], v) } }
            val bb = Indikatoren.bollinger(werte)
            val m = Indikatoren.macd(werte)
            val s = Indikatoren.stochastik(hoch, tief, werte)
            val a = Indikatoren.atr(hoch, tief, werte)
            val mitUmsatz = umsatz.takeLast(60).count { it > 0 } >= 40
            val ich = Indikatoren.ichimoku(hoch, tief)
            val sar = Indikatoren.sar(hoch, tief)
            return IndikatorReihen(
                karte(Indikatoren.sma(werte, 50)),
                karte(Indikatoren.sma(werte, 200)),
                karte(bb.oben),
                karte(bb.unten),
                karte(Indikatoren.rsi(werte, 14)),
                karte(m.linie),
                karte(m.signal),
                karte(m.histogramm),
                karte(s.k),
                karte(s.d),
                karte(werte.indices.map { i -> a[i]?.let { it / werte[i] * 100 } }),
                if (mitUmsatz) karte(Indikatoren.obv(werte, umsatz)) else TreeMap(),
                karte(ich.kijun),
                karte(ich.spanA),
                karte(ich.spanB),
                karte(sar.wert.mapIndexed { i, v -> v.takeIf { sar.aufwaerts[i] } }),
                karte(sar.wert.mapIndexed { i, v -> v.takeIf { !sar.aufwaerts[i] } }),
                werte.lastOrNull() ?: 0.0,
                Indikatoren.deuten(tage, werte, hoch, tief, umsatz),
            )
        }
    }
}

internal fun indikatorFarbe(art: String, standard: Color): Color = when (art) {
    "sma50" -> SMA50_FARBE
    "sma200" -> SMA200_FARBE
    "bb" -> BOLLINGER_FARBE
    "macd" -> MACD_FARBE
    "stoch" -> STOCH_FARBE
    "atr" -> ATR_FARBE
    "obv" -> OBV_FARBE
    "ichimoku" -> ICHIMOKU_FARBE
    "sar" -> SAR_FARBE
    else -> standard
}

// Grauer Knopf „Indikatoren“ unter dem Chart. Aufgeklappt folgen die Namen aller Indikatoren in einer umbrechenden
// Reihe; jeder schaltet seinen Indikator ein oder aus. Eingeschaltete stehen hell mit Strich in ihrer Linienfarbe.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IndikatorKnopf(offen: Boolean, aufZu: () -> Unit, aktiv: Set<String>, umschalten: (String) -> Unit, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val hell = MaterialTheme.colorScheme.onSurface
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.clickable(onClick = aufZu).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.indButton, style = MaterialTheme.typography.labelSmall, color = grau.copy(alpha = 0.75f))
            Spacer(Modifier.width(4.dp))
            ChevronDown(width = 8.dp, color = grau.copy(alpha = 0.45f), modifier = Modifier.rotate(if (offen) 180f else 0f))
        }
        if (offen) {
            INDIKATOREN.forEach { k ->
                val an = k in aktiv
                val strich = indikatorFarbe(k, hell)
                Text(
                    S.indName(k), style = MaterialTheme.typography.labelSmall,
                    color = if (an) hell else grau.copy(alpha = 0.6f),
                    fontWeight = if (an) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clickable { umschalten(k) }.padding(vertical = 6.dp).drawBehind {
                        if (an) {
                            val y = size.height + 1.dp.toPx()
                            drawLine(strich, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                        }
                    },
                )
            }
        }
    }
}

// Was die eingeschalteten Indikatoren zum letzten Kurs sagen: je Indikator eine Zeile mit Einordnung und darunter,
// was das heisst, ohne Farben. Darueber ein Ueberblick. Grund und Erklaerung stehen erst nach einem Tipp auf die Zeile.
@Composable
internal fun IndikatorDeutung(deutungen: List<Indikatoren.Deutung>, aktiv: Set<String>, modifier: Modifier = Modifier) {
    val gezeigt = deutungen.filter { it.art in aktiv }.sortedBy { INDIKATOREN.indexOf(it.art) }
    if (gezeigt.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var erklaert by remember { mutableStateOf<Set<String>>(emptySet()) }
    Column(modifier) {
        if (gezeigt.size > 1) {
            val zahl = gezeigt.groupingBy { Indikatoren.tendenz(it) }.eachCount()
            Text(
                S.indUeberblick(
                    zahl[Indikatoren.Tendenz.POSITIV] ?: 0, zahl[Indikatoren.Tendenz.NEGATIV] ?: 0,
                    zahl[Indikatoren.Tendenz.NEUTRAL] ?: 0, zahl[Indikatoren.Tendenz.GEMISCHT] ?: 0,
                ),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
        gezeigt.forEach { d ->
            val offen = d.art in erklaert
            Column(Modifier.fillMaxWidth().clickable { erklaert = if (offen) erklaert - d.art else erklaert + d.art }.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(S.indName(d.art), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    // Einordnung rechts, damit sie beim Ueberfliegen untereinander steht
                    Text(S.indTendenz(Indikatoren.tendenz(d).name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    // Kleines V: die Zeile laesst sich aufklappen
                    Spacer(Modifier.width(6.dp))
                    ChevronDown(width = 9.dp, color = grau.copy(alpha = 0.45f), modifier = Modifier.rotate(if (offen) 180f else 0f))
                }
                Text(S.indLage(d.art, d.lage), style = MaterialTheme.typography.bodySmall, color = grau)
                if (offen) {
                    val kreuz = d.kreuzTag?.let { Fmt.date(it * 86_400_000L) }
                    val grund = S.indGrund(d.art, d.lage, Fmt.pct(kotlin.math.abs(d.wert), 1), Fmt.num(d.wert, 0), kreuz, d.kreuzAuf, d.eng)
                    if (grund.isNotBlank()) Text(grund, style = MaterialTheme.typography.bodySmall, color = grau)
                    Text(S.indErklaerung(d.art), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        AufklappText(S.indFuss, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = grau.copy(alpha = 0.7f))
    }
}
