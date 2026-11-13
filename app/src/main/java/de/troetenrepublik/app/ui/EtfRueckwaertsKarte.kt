package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.EtfRueckwaerts
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Daten
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// In welchen ETFs steckt die Aktie: Zeile je ETF mit Gewicht der Aktie und Balken, das groesste Gewicht zuerst, ab der
// fuenften Zeile eingeklappt („Alle n“), ein Tipp oeffnet den ETF. Ohne Treffer bleibt die Karte weg. Die Zeilen kommen als
// Eintraege der Seite (etfRueckwaertsAbschnitt).
@Composable
internal fun EtfRueckwaertsKarte(symbol: String, merker: SeitenMerker? = null, onOpenEtf: (String) -> Unit) {
    var daten by gemerkt<EtfRueckwaerts.Daten?>(merker, "etfrw/daten") { zwischen }
    LaunchedEffect(Unit) {
        if (daten != null) return@LaunchedEffect
        daten = withContext(Dispatchers.Default) { laden() }
    }
    var mehr by gemerkt(merker, "etfrw/mehr/$symbol") { false }
    val d = daten
    val treffer = remember(d, symbol) { d?.let { EtfRueckwaerts.finden(it, symbol) }.orEmpty() }
    val modell = remember(d, treffer, mehr) {
        if (d == null || treffer.isEmpty()) null else etfRueckwaertsModell(d, treffer, mehr, { mehr = !mehr }, onOpenEtf)
    }
    ModellAusgabe(merker, "etfrw", modell)
}

internal fun LazyListScope.etfRueckwaertsAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "etfrw")
}

@Volatile private var zwischen: EtfRueckwaerts.Daten? = null

private fun laden(): EtfRueckwaerts.Daten? =
    zwischen ?: runCatching { EtfRueckwaerts.lesen(Daten.text(EtfRueckwaerts.DATEI)) }.getOrNull()?.also { zwischen = it }

private const val SICHTBAR = 4

private fun etfRueckwaertsModell(d: EtfRueckwaerts.Daten, treffer: List<EtfRueckwaerts.Treffer>, mehr: Boolean, umschalten: () -> Unit, onOpenEtf: (String) -> Unit): ListenModell {
    val spitze = treffer.first().gewicht
    val zeilen = ArrayList<ZeilenDaten>()
    treffer.take(if (mehr) treffer.size else SICHTBAR).forEach { t ->
        zeilen.add(ZeilenDaten("e-${t.etf}") { EtfRueckwaertsZeile(t, spitze) { onOpenEtf(t.etf) } })
    }
    if (treffer.size > SICHTBAR) zeilen.add(ZeilenDaten("mehr") { ListenPfeil(mehr, label = if (mehr) S.less else "${S.all} ${treffer.size}", onClick = umschalten) })
    // Nur wenn die Quelle je ETF die groessten Positionen liefert, steht das als Etikett da
    val etikett = if (d.tiefe > 0) S.erEtikett(d.tiefe, Alter.tag(d.stand)?.let { Fmt.date(it * 86_400_000L) }.orEmpty()) else null
    return ListenModell(
        titel = S.erTitel(treffer.size),
        gross = true,
        kern = null,
        zeilen = zeilen,
        immerOffen = true,
        nachspann = etikett?.let { e ->
            {
                Text(
                    e, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp),
                )
            }
        },
    )
}

// Name des ETF links (darf in die zweite Zeile laufen), Balken und Gewicht in festen Spalten rechts
@Composable
private fun EtfRueckwaertsZeile(t: EtfRueckwaerts.Treffer, spitze: Double, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(64.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth(de.troetenrepublik.app.engine.Blattaufbau.balkenAnteil(t.gewicht).coerceAtLeast(0.03f)).fillMaxHeight()
                    .background(AnteilsFarben.folge(0)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Fmt.pct(t.gewicht, 1), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false,
            textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}
