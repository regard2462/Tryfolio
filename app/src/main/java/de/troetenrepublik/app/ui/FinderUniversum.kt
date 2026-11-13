package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.FinderRueckblickMessung
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.UniversumTexte
import kotlin.math.abs
import kotlin.math.max

// Zweites Universum im Rueckblick des Aktienfinders (0.78): dieselbe Messung gegen die damaligen Mitglieder des
// Nasdaq-100 (indizes/ndx100_mitglieder.json). Die Zusammenfassung indikatoren/finder_rueckblick_ndx100.json entsteht
// mit :desktop:finderRueckblick -Puniversum=ndx100; solange sie fehlt, gibt es keine Auswahl.

internal const val UNIVERSUM_NDX100 = "ndx100"
internal const val NDX100_QUELLE = "https://github.com/jmccarrell/n100tickers"

private fun unText(key: String, vararg w: String): String = UniversumTexte.text(I18n.lang == Lang.DE, key, w.toList())

internal val FINDER_RUECKBLICK_NDX100: FinderRueckblickMessung.Datei? by lazy {
    runCatching {
        FinderRueckblickMessung.lesen(de.troetenrepublik.app.plattform.Plattform.asset("indikatoren/finder_rueckblick_ndx100.json").decodeToString())
    }.getOrNull()?.takeIf { it.stichtage > 0 }
}

// Auswahl des Universums; gewaehlt ist leer fuer den S&P 500.
@Composable
internal fun FinderUniversumWahl(gewaehlt: String, onWahl: (String) -> Unit) {
    ToolChoice(unText("wahl"), listOf("S&P 500" to "", "Nasdaq-100" to UNIVERSUM_NDX100), gewaehlt, onWahl)
}

// Rueckblick gegen das zweite Universum: Kernzeile, mittlere Rendite ueber zwoelf Monate als Balken, Abdeckung,
// Zufallsdepots.
@Composable
internal fun FinderUniversumBlock(r: FinderRueckblickMessung.Datei) {
    val h = r.horizont(12) ?: return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Text(
            unText(if (h.mehr >= 0) "kern.vor" else "kern.hinter", Fmt.num(abs(h.mehr) * 100, 1)), style = MaterialTheme.typography.titleMedium,
            color = if (abs(h.mehr) <= 2 * h.mehrSe) MaterialTheme.colorScheme.onSurface else signal.forChange(h.mehr),
        )
        Text(unText("zeitraum", r.von.take(4), r.bis.take(4), r.werte.toString()), style = MaterialTheme.typography.bodySmall, color = grau)
        val hoechste = max(max(h.top20, h.universum), max(h.msci, h.spx)).takeIf { it > 0 } ?: 1.0
        fun anteil(v: Double) = (v / hoechste).coerceIn(0.0, 1.0)
        LageBalkenZeile(unText("top20"), Fmt.signedPct(h.top20 * 100, 1), anteil(h.top20), signal.forChange(h.top20), null)
        LageBalkenZeile(unText("alle"), Fmt.signedPct(h.universum * 100, 1), anteil(h.universum), Kategorie.blau, null)
        LageBalkenZeile("MSCI World", Fmt.signedPct(h.msci * 100, 1), anteil(h.msci), Kategorie.blau, null)
        LageBalkenZeile("S&P 500", Fmt.signedPct(h.spx * 100, 1), anteil(h.spx), Kategorie.blau, null)
    }
    KeyValueRow(unText("treffer"), Fmt.pct(h.treffer * 100, 0), hint = unText("trefferHinweis", h.stichtage.toString()))
    r.abdeckungSchnitt()?.let { KeyValueRow(unText("abdeckung"), Fmt.pct(it * 100, 0), hint = unText("abdeckungHinweis")) }
    FinderZufallAbschnitt(r.zufall)
    HinweisInfo(unText("hinweis"), kurz = hinweisText("histPrognose"))
}
