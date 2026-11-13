package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Risiko
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf

// Hoehe der Kopfzeile mit den Namen der Linien.
private val KOPF = 40.dp

// Kennzahlen, die beim eigenen Depot den Zusatz „(nur Wertpapiere)“ tragen.
private val NUR_WERTPAPIERE = setOf("schwankung", "rueckgang")

// Zeilen sind die Kennzahlen, Spalten die Linien. Erst Rendite, Schwankung und Rueckgang, zuschaltbar die
// Risikokennzahlen und Beta, Alpha und Korrelation gegen die erste Linie. nurWertpapiere: eigenes Depot, dessen Linie
// kein Cash enthaelt; Schwankung und Rueckgang tragen dann den Zusatz im Namen.
// onJahr: Die Zeile „Pro Jahr“ heisst „zeitgewichtet“ und fuehrt per Tipp weiter (Depotverlauf: zur Portfolio Performance).
@Composable
internal fun KennzahlTabelle(linien: List<Linie>, hauptLinie: Linie?, modifier: Modifier = Modifier, zins: Double = 0.0, nurWertpapiere: Boolean = false, onJahr: (() -> Unit)? = null) {
    if (linien.isEmpty()) return
    var mehr by remember { mutableStateOf(false) }
    var erklaert by remember { mutableStateOf<String?>(null) }
    val einfach = remember(linien) { linien.associateWith { Portfolioverlauf.kennzahlen(it.punkte) } }
    val risiko = remember(linien, mehr) { if (mehr) linien.associateWith { Risiko.kennzahlen(it.punkte, zins) } else emptyMap() }
    val gegen = remember(linien, hauptLinie, mehr) {
        if (!mehr || hauptLinie == null) emptyMap() else linien.filter { it !== hauptLinie }.associateWith { Risiko.vergleich(hauptLinie.punkte, it.punkte, zins) }
    }
    val zeilen = buildList<Pair<String, (Linie) -> String?>> {
        add("gesamt" to { l -> einfach[l]?.let { Fmt.signedPct(it.gesamt * 100, 1) } })
        add("jahr" to { l -> einfach[l]?.proJahr?.let { Fmt.signedPct(it * 100, 1) } })
        add("schwankung" to { l -> einfach[l]?.schwankung?.let { Fmt.pct(it * 100, 1) } })
        add("rueckgang" to { l -> einfach[l]?.let { Fmt.signedPct(it.maxVerlust * 100, 1) } })
        if (mehr) {
            add("sharpe" to { l -> risiko[l]?.sharpe?.let { Fmt.num(it, 2) } })
            add("sortino" to { l -> risiko[l]?.sortino?.let { Fmt.num(it, 2) } })
            add("calmar" to { l -> risiko[l]?.calmar?.let { Fmt.num(it, 2) } })
            add("ulcer" to { l -> risiko[l]?.let { Fmt.num(it.ulcer, 1) } })
            add("var" to { l -> risiko[l]?.var95?.let { Fmt.pct(it * 100, 1) } })
            add("es" to { l -> risiko[l]?.es95?.let { Fmt.pct(it * 100, 1) } })
            add("dauer" to { l -> risiko[l]?.let { it.rueckgangTage.toString() + if (it.laufend) "+" else "" } })
            if (gegen.isNotEmpty()) {
                add("beta" to { l -> gegen[l]?.let { Fmt.num(it.beta, 2) } })
                add("alpha" to { l -> gegen[l]?.let { Fmt.signedPct(it.alphaProJahr * 100, 1) } })
                add("korrelation" to { l -> gegen[l]?.let { Fmt.num(it.korrelation, 2) } })
            }
        }
    }
    // VaR und ES gelten je Abschnitt der Reihe: ein Tag oder bei langen Zeitraeumen eine Woche
    val wochen = risiko.values.firstOrNull()?.let { it.proJahr < 100.0 } ?: false
    fun name(k: String) = S.pvMetric(
        when {
            nurWertpapiere && k in NUR_WERTPAPIERE -> "$k.depot"
            wochen && (k == "var" || k == "es") -> "$k.woche"
            else -> k
        },
    )
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val spalte = 84.dp
    // Ein Name mit Zusatz kann umbrechen: die Werte der Zeile bekommen die gemessene Hoehe des Namens und stehen auf
    // Hoehe seiner ersten Zeile
    val hoehen = remember { mutableStateMapOf<String, Int>() }
    val dichte = LocalDensity.current
    fun hoehe(k: String) = hoehen[k]?.let { Modifier.height(with(dichte) { it.toDp() }) } ?: Modifier
    // Kopfhoehe: mindestens KOPF, waechst mit dem laengsten Linienamen (Namen brechen um, nie abgeschnitten)
    var kopfHoehe by remember(linien.size) { mutableStateOf(KOPF) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            // Spalte mit den Namen der Kennzahlen bleibt stehen, die Werte lassen sich bei vielen Linien seitlich schieben
            Column(Modifier.width(if (onJahr != null) 156.dp else 132.dp)) {
                Spacer(Modifier.height(kopfHoehe))
                zeilen.forEach { (k, _) ->
                    val weiter = onJahr != null && k == "jahr"
                    Beschriftung(
                        if (weiter) S.dp("dv.zeitgewichtet") else name(k), style = MaterialTheme.typography.bodySmall,
                        color = if (weiter) LocalSignal.current.link else if (erklaert == k) MaterialTheme.colorScheme.onSurface else grau,
                        modifier = Modifier.onSizeChanged { if (hoehen[k] != it.height) hoehen[k] = it.height }.textKlick(0.dp) { if (weiter) onJahr?.invoke() else erklaert = if (erklaert == k) null else k }.padding(vertical = 6.dp),
                    )
                }
            }
            Row(Modifier.weight(1f).horizontalScrollMaus(rememberScrollState())) {
                linien.forEach { l ->
                    Column(Modifier.width(spalte), horizontalAlignment = Alignment.End) {
                        // Kopf gleich hoch wie links, Namen brechen an Wortgrenzen um
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.heightIn(min = kopfHoehe).onSizeChanged { h -> val d = with(dichte) { h.height.toDp() }; if (d > kopfHoehe) kopfHoehe = d }
                                .padding(top = 4.dp, start = 6.dp),
                        ) {
                            Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(l.farbe))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                                NamenText(l.name, modifier = Modifier.wrapContentWidth(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                        zeilen.forEach { (k, wert) ->
                            // Werte sind Zahlen, sie bleiben in einer Zeile
                            Text(wert(l) ?: "–", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.then(hoehe(k)).padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
        erklaert?.let { k -> StichpunktText(S.pvMetricExplain(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mehr) S.pvLessStats else S.pvMoreStats, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                modifier = Modifier.textKlick(0.dp) { mehr = !mehr }.padding(vertical = 4.dp),
            )
        }
    }
}
