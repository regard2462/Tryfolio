package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Risiko
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf

// Hoehe der Kopfzeile mit den Namen der Linien.
private val KOPF = 40.dp

// Zeilen sind die Kennzahlen, Spalten die Linien. Erst Rendite, Schwankung und Rueckgang, zuschaltbar die
// Risikokennzahlen und Beta, Alpha und Korrelation gegen die erste Linie.
@Composable
internal fun KennzahlTabelle(linien: List<Linie>, hauptLinie: Linie?, modifier: Modifier = Modifier) {
    if (linien.isEmpty()) return
    var mehr by remember { mutableStateOf(false) }
    var erklaert by remember { mutableStateOf<String?>(null) }
    val einfach = remember(linien) { linien.associateWith { Portfolioverlauf.kennzahlen(it.punkte) } }
    val risiko = remember(linien, mehr) { if (mehr) linien.associateWith { Risiko.kennzahlen(it.punkte) } else emptyMap() }
    val gegen = remember(linien, hauptLinie, mehr) {
        if (!mehr || hauptLinie == null) emptyMap() else linien.filter { it !== hauptLinie }.associateWith { Risiko.vergleich(hauptLinie.punkte, it.punkte) }
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
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val spalte = 84.dp
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            // Spalte mit den Namen der Kennzahlen bleibt stehen, die Werte lassen sich bei vielen Linien seitlich schieben
            Column(Modifier.width(132.dp)) {
                Spacer(Modifier.height(KOPF))
                zeilen.forEach { (k, _) ->
                    Text(
                        S.pvMetric(k), style = MaterialTheme.typography.bodySmall,
                        color = if (erklaert == k) LocalSignal.current.link else grau,
                        modifier = Modifier.clickable { erklaert = if (erklaert == k) null else k }.padding(vertical = 6.dp),
                    )
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                linien.forEach { l ->
                    Column(Modifier.width(spalte), horizontalAlignment = Alignment.End) {
                        // Kopf gleich hoch wie links, Namen bis zwei Zeilen
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.height(KOPF).padding(top = 4.dp, start = 6.dp)) {
                            Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(l.farbe))
                            Spacer(Modifier.width(4.dp))
                            Text(l.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                        zeilen.forEach { (_, wert) ->
                            Text(wert(l) ?: "–", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
        erklaert?.let { k -> Text(S.pvMetricExplain(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mehr) S.pvLessStats else S.pvMoreStats, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                modifier = Modifier.clickable { mehr = !mehr }.padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (erklaert == null) Text(S.pvMetricHint, style = MaterialTheme.typography.labelSmall, color = grau.copy(alpha = 0.7f))
        }
    }
}
