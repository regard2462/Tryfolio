package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.PopulaerKurzname
import kotlin.math.abs

// „Beliebt“: zwei gleich breite Zellen je Zeile mit Logo und Kurzname, jede Zelle eine Zeile hoch. Passt ein Kurzname
// nicht in eine halbe Zeile (auch bei grosser Schrift), steht „Beliebt“ als Liste mit Logo, Name und Tagesaenderung.
@Composable
internal fun BeliebtBlock(state: AppState, werte: List<Pair<String, String>>, kurse: Map<String, Quote>, onOpen: (String) -> Unit) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val stil = MaterialTheme.typography.titleSmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zelle = (maxWidth - 40.dp - 10.dp) / 2
        val textBreite = with(dichte) { (zelle - 28.dp - 26.dp - 10.dp).roundToPx() }
        val namen = remember(werte) { werte.map { (_, name) -> PopulaerKurzname.kandidaten(namensKuerzungen(name)) } }
        val passt: (String) -> Boolean = { messer.measure(it, stil, maxLines = 1, softWrap = false).size.width <= textBreite }
        val raster = remember(namen, textBreite, stil) { PopulaerKurzname.alsRaster(namen, passt) }
        if (raster) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                werte.indices.chunked(2).forEach { paar ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        paar.forEach { i ->
                            val (symbol, name) = werte[i]
                            val text = PopulaerKurzname.waehlen(namen[i], passt).orEmpty()
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onOpen(symbol) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InstrumentLogo(symbol, name, size = 26)
                                Spacer(Modifier.width(10.dp))
                                Text(text, style = stil, maxLines = 1, softWrap = false)
                            }
                        }
                        if (paar.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                werte.forEachIndexed { i, (symbol, name) ->
                    if (i > 0) Trennlinie()
                    val q = kurse[symbol]
                    AssetRow(
                        state = state, symbol = symbol, name = name,
                        change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                        onClick = { onOpen(symbol) },
                    )
                }
            }
        }
    }
}
