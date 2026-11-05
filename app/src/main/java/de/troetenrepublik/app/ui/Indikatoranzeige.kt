package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Info
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
import de.troetenrepublik.app.engine.IndikatorTreffer
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
// Das Kerzenmuster (5.94) zeichnet keine Linie, es steht nur in der Deutung.
internal val INDIKATOREN = listOf("sma50", "sma200", "bb", "ichimoku", "sar", "rsi", "stoch", "macd", "atr", "obv", "kerzen")

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
                Indikatoren.deuten(tage, werte, hoch, tief, umsatz) + listOfNotNull(
                    de.troetenrepublik.app.engine.Kerzenmuster.deutung(tage, l.map { if (it.open > 0 && it.open.isFinite()) it.open else Double.NaN }, hoch, tief, werte),
                ),
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


// Trefferbilanzen je Wert (5.92), solange die App laeuft: die Rechnung ueber zehn Jahre Tageskurse dauert einen
// Moment und aendert sich ueber den Tag kaum.
// Allgemeine Trefferbilanz ueber viele Aktien und Indizes (mitgeliefert), einmal gelesen.
internal val ALLGEMEIN: de.troetenrepublik.app.engine.IndikatorAllgemein.Datei? by lazy {
    runCatching {
        de.troetenrepublik.app.engine.IndikatorAllgemein.lesen(de.troetenrepublik.app.plattform.Plattform.asset("indikatoren/allgemein.json").decodeToString())
    }.getOrNull()
}

internal object TrefferSpeicher {
    private val speicher = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Map<String, IndikatorTreffer.Bilanz>>>()

    fun lesen(symbol: String): Map<String, IndikatorTreffer.Bilanz>? =
        speicher[symbol]?.takeIf { System.currentTimeMillis() - it.first < 12 * 3_600_000L }?.second

    // Rechnet im aufrufenden Thread (aus einem Hintergrund-Kontext aufrufen) und merkt sich das Ergebnis.
    fun rechnen(symbol: String, kerzen: List<Candle>): Map<String, IndikatorTreffer.Bilanz> =
        IndikatorTreffer.alle(kerzen, INDIKATOREN).also { speicher[symbol] = System.currentTimeMillis() to it }
}

// Grauer Knopf „Indikatoren“ unter dem Chart; aufgeklappt folgen darunter die Namen (IndikatorNamen). Die
// Tippflaeche ist mindestens 48 dp hoch.
@Composable
internal fun IndikatorKnopf(offen: Boolean, aufZu: () -> Unit, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clickable(onClick = aufZu).heightIn(min = 48.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.indButton, style = MaterialTheme.typography.labelSmall, color = grau)
    }
}

// Namen aller Indikatoren in einer umbrechenden Reihe ueber die ganze Breite; jeder schaltet seinen Indikator ein
// oder aus. Eingeschaltete stehen hell mit Strich in ihrer Linienfarbe. Jede Tippflaeche ist mindestens 48 dp gross.
// imChart = false (1T, 1W, kurzer eigener Zeitraum): Der Chart zeigt keine Linien, die Namen stehen gedaempft und ein
// Hinweis sagt, ab wann. Die Deutung darunter gilt trotzdem.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IndikatorNamen(aktiv: Set<String>, umschalten: (String) -> Unit, modifier: Modifier = Modifier, imChart: Boolean = true) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val hell = MaterialTheme.colorScheme.onSurface
    val daempfung = if (imChart) 1f else 0.5f
    FlowRow(modifier, verticalArrangement = Arrangement.Center) {
        INDIKATOREN.forEach { k ->
            val an = k in aktiv
            val strich = indikatorFarbe(k, hell).copy(alpha = daempfung)
            Box(
                Modifier.clickable { umschalten(k) }.sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    S.indName(k), style = MaterialTheme.typography.labelSmall,
                    color = (if (an) hell else grau.copy(alpha = 0.7f)).let { it.copy(alpha = it.alpha * daempfung) },
                    fontWeight = if (an) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.drawBehind {
                        if (an) {
                            val y = size.height + 1.dp.toPx()
                            drawLine(strich, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                        }
                    },
                )
            }
        }
        if (!imChart) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                Text(S.auIndikatorenAbMonat, style = MaterialTheme.typography.labelSmall, color = grau)
            }
        }
    }
}

// Was die eingeschalteten Indikatoren zum letzten Kurs sagen: je Indikator eine Zeile mit Einordnung und darunter,
// was das heisst, ohne Farben. Darueber ein Ueberblick. Seit 5.92 steht je Indikator eine Zeile zur Aussagekraft
// seiner Signale bei diesem Wert dabei (bilanzen; null = wird noch gerechnet). Grund, Erklaerung und die Zahlen
// hinter der Bilanz stehen erst nach einem Tipp auf die Zeile.
@Composable
internal fun IndikatorDeutung(
    deutungen: List<Indikatoren.Deutung>,
    aktiv: Set<String>,
    modifier: Modifier = Modifier,
    bilanzen: Map<String, IndikatorTreffer.Bilanz>? = null,
) {
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
            val b = bilanzen?.get(d.art)
            Column(Modifier.fillMaxWidth().clickable { erklaert = if (offen) erklaert - d.art else erklaert + d.art }.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(S.indName(d.art), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    // Einordnung rechts, damit sie beim Ueberfliegen untereinander steht
                    Text(S.indTendenz(Indikatoren.tendenz(d).name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(S.indLage(d.art, d.lage), style = MaterialTheme.typography.bodySmall, color = grau)
                // Eine Zeile Trefferbilanz; ohne genug Kurse (junge Werte) bleibt sie weg
                val bilanzText = when {
                    bilanzen == null -> S.p1BilanzLaedt
                    b == null -> null
                    b.treffer == null || b.vergleich == null -> S.p1BilanzWenig
                    else -> S.p1BilanzZeile(b.stufe.name, Fmt.pct(b.treffer * 100, 0), Fmt.pct(b.vergleich * 100, 0), d.art == "atr") +
                        S.p1BilanzSeit(java.time.LocalDate.ofEpochDay(b.ab).year, b.signale)
                }?.let { t ->
                    // Darunter die allgemeine Bilanz ueber viele Werte, damit man den Einzelwert einordnen kann
                    val a = ALLGEMEIN?.let { f -> f.arten[d.art]?.let { e -> f to e } }
                    if (a == null) t else t + S.p1BilanzAllgemein(Fmt.pct(a.second.treffer * 100, 0), Fmt.pct(a.second.vergleich * 100, 0), a.first.werte, a.first.ab)
                } ?: ALLGEMEIN?.let { f -> f.arten[d.art]?.let { e -> S.p1BilanzAllgemein(Fmt.pct(e.treffer * 100, 0), Fmt.pct(e.vergleich * 100, 0), f.werte, f.ab).trimStart('\n') } }
                if (bilanzText != null) AuslaufText(bilanzText, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = grau)
                if (offen) {
                    val kreuz = d.kreuzTag?.let { Fmt.date(it * 86_400_000L) }
                    val grund = S.indGrund(d.art, d.lage, Fmt.pct(kotlin.math.abs(d.wert), 1), Fmt.num(d.wert, 0), kreuz, d.kreuzAuf, d.eng)
                    if (grund.isNotBlank()) Text(grund, style = MaterialTheme.typography.bodySmall, color = grau)
                    Text(S.indErklaerung(d.art), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
                    if (b != null) Text(
                        S.p1BilanzDetail(
                            d.art, java.time.LocalDate.ofEpochDay(b.ab).year, b.signale, b.richtig,
                            b.treffer?.let { Fmt.pct(it * 100, 0) }, b.vergleich?.let { Fmt.pct(it * 100, 0) }, IndikatorTreffer.TAGE,
                        ),
                        style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp),
                    )
                    S.indForschung(d.art).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        var lesart by remember { mutableStateOf(false) }
        Row(
            Modifier.heightIn(min = 40.dp).clickable { lesart = true }.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.indFussTitel, style = MaterialTheme.typography.labelSmall, color = grau)
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = grau,
                modifier = Modifier.sizeIn(maxWidth = 16.dp, maxHeight = 16.dp),
            )
        }
        if (lesart) MarktInfoBlatt(S.indFussTitel, S.indFuss + "\n\n" + S.indForschungAllgemein) { lesart = false }
    }
}
