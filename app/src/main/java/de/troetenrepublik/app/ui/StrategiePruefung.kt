package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Ueberanpassung
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Strategietest 0.78: Wahrscheinlichkeit der Ueberanpassung, Test der besten von vielen Varianten und Mindestlaufzeit.

private class Schluessel(val k: List<Candle>, val ab: Int, val kosten: StrategieLabor.Kosten, val familie: StrategieLabor.Familie) {
    fun gleich(o: Schluessel) = k === o.k && ab == o.ab && kosten == o.kosten && familie == o.familie
}

// Zuletzt gerechnete Pruefungen, damit Wischen zwischen den Familien nicht neu rechnet. Nur im UI-Thread benutzt.
private val pruefSpeicher = ArrayList<Pair<Schluessel, Ueberanpassung.Pruefung?>>()

private fun gespeichert(s: Schluessel): Pair<Schluessel, Ueberanpassung.Pruefung?>? = pruefSpeicher.firstOrNull { it.first.gleich(s) }

private fun merken(s: Schluessel, p: Ueberanpassung.Pruefung?) {
    pruefSpeicher.removeAll { it.first.gleich(s) }
    pruefSpeicher.add(0, s to p)
    while (pruefSpeicher.size > 8) pruefSpeicher.removeAt(pruefSpeicher.size - 1)
}

// Kernzeile Ueberanpassung und Satz zum Test der besten Variante, je mit Erklaerung auf Tipp. Gerechnet wird erst,
// wenn die Seite des Zufallstests ihr Ergebnis hat.
@Composable
internal fun UeberanpassungZeilen(k: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, familie: StrategieLabor.Familie) {
    val signal = LocalSignal.current
    val ergebnis by produceState<Pair<Boolean, Ueberanpassung.Pruefung?>>(false to null, k, ab, kosten, familie) {
        val s = Schluessel(k, ab, kosten, familie)
        val alt = gespeichert(s)
        if (alt != null) { value = true to alt.second; return@produceState }
        value = false to null
        // Kurz warten: schnelles Wischen startet keine Berechnung
        delay(300)
        val p = withContext(Dispatchers.Default) { Ueberanpassung.pruefen(k, ab, kosten, familie) { ensureActive() } }
        merken(s, p)
        value = true to p
    }
    val (fertig, p) = ergebnis
    if (!fertig || p == null) return
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    p.pbo?.let { b ->
        val stufe = Ueberanpassung.stufe(b.wahrscheinlichkeit)
        KeyValueRow(
            S.st("pbo.zeile"), Fmt.pct(b.wahrscheinlichkeit * 100, 0),
            valueColor = when (stufe) {
                Ueberanpassung.Stufe.GERING -> signal.up
                Ueberanpassung.Stufe.HOCH -> signal.down
                Ueberanpassung.Stufe.ERHEBLICH -> null
            },
            onClick = { info = S.st("pbo.zeile") to S.st("pbo.text", listOf(b.bloecke.toString(), Fmt.num(b.kombinationen.toDouble(), 0))) },
            hint = S.st("pbo." + stufe.name),
        )
    }
    p.spa?.let { s ->
        val wert = if (s.p < 0.01) "< " + Fmt.num(0.01, 2) else "= " + Fmt.num(s.p, 2)
        val vorn = s.p < Ueberanpassung.SPA_GRENZE
        Text(
            S.st(if (vorn) "spa.ja" else "spa.nein", listOf(Fmt.num(s.varianten.toDouble(), 0), wert)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (vorn) signal.up else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
                .clickable { info = S.st("spa.titel") to S.st("spa.text", listOf(Fmt.num(s.ziehungen.toDouble(), 0), Ueberanpassung.SPA_BLOCK.toString())) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Satz zur Mindestlaufzeit einer Regel; ein Tipp erklaert ihn.
@Composable
internal fun MindestlaufzeitZeile(e: StrategieLabor.Ergebnis) {
    val signal = LocalSignal.current
    val l = remember(e) { Ueberanpassung.laufzeit(e) } ?: return
    var info by remember { mutableStateOf(false) }
    val vorhanden = Fmt.num(l.vorhandenJahre, 1)
    val noetig = l.noetigJahre
    val satz = when {
        noetig == null -> S.st("mt.keine", listOf(vorhanden))
        noetig > 100 -> S.st("mt.viel", listOf(vorhanden))
        else -> S.st("mt.satz", listOf(Fmt.num(noetig, 1), vorhanden))
    }
    Text(
        satz, style = MaterialTheme.typography.bodyMedium,
        color = if (noetig != null && noetig <= l.vorhandenJahre) signal.up else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (info) MarktInfoBlatt(S.st("mt.titel"), S.st("mt.text", listOf(Fmt.num(l.sharpeJahr, 2)))) { info = false }
}

// Papiere hinter den Pruefungen des Strategietests, am Ende des Blatts.
@Composable
internal fun StrategieQuellen() {
    QuellenAnhang(
        listOf(
            QuelleLink("Bailey, Borwein, López de Prado, Zhu (2017): The Probability of Backtest Overfitting", "https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2326253"),
            QuelleLink("Bailey, López de Prado (2012): The Sharpe Ratio Efficient Frontier", "https://papers.ssrn.com/sol3/papers.cfm?abstract_id=1821643"),
            QuelleLink("Bailey, López de Prado (2014): The Deflated Sharpe Ratio", "https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2460551"),
            QuelleLink("Hansen (2005): A Test for Superior Predictive Ability", "https://doi.org/10.1198/073500105000000063"),
            QuelleLink("Politis, Romano (1994): The Stationary Bootstrap", "https://doi.org/10.1080/01621459.1994.10476870"),
        ),
        Modifier.padding(top = 16.dp),
    )
}
