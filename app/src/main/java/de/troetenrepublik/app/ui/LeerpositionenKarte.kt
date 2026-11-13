package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Leerpositionen
import de.troetenrepublik.app.market.Politik

// Offene Leerverkaufspositionen einer Aktie (seit 5.8, Paris; seit 5.9 auch Stockholm und London): Summe, Veraenderung zu
// vor vier Wochen, Verlauf ueber ein Jahr und, wo die Liste sie nennt, die Halter. Erscheint nur, wenn eine Liste die
// Aktie fuehrt.
@Composable
internal fun LeerpositionenKarte(state: AppState, isin: String, kopf: Boolean = true) {
    val daten by state.leerpositionen.collectAsStateWithLifecycle()
    LaunchedEffect(isin) {
        for (land in Leerpositionen.LAENDER) {
            val d = runCatching { state.lpLaden(land, netz = false) }.getOrNull() ?: continue
            // Nur fuer Aktien, die die Liste fuehrt oder fuehren koennte, die Liste neu holen
            if (isin in d.titel || Leerpositionen.landFuerIsin(isin) == land) runCatching { state.lpLaden(land) }
        }
    }
    val d = Leerpositionen.LAENDER.firstNotNullOfOrNull { land -> daten[land]?.takeIf { isin in it.titel } } ?: return
    val t = d.titel[isin] ?: return
    val signal = LocalSignal.current
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    fun pct(v: Double) = Fmt.pct(v, 2)
    NennQuellen.aufsicht(d.land)?.let { QuellenMelden(it) }
    Column {
        if (kopf) SectionHeader(S.lpTitel)
        if (t.gesamt != null) {
            Text(if (t.gesamt > 0) S.lpGesamt(pct(t.gesamt)) else S.lpKeine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else if (t.positionen.isEmpty()) {
            Text(S.lpKeine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else {
            Text(S.lpSumme(pct(t.summe), t.positionen.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        val vor = t.verlauf.getOrNull(t.verlauf.size - 5)?.s
        if (vor != null) {
            val diff = t.summe - vor
            Text(
                S.lpVeraenderung((if (diff >= 0.005) "+" else if (diff <= -0.005) "−" else "±") + Fmt.num(kotlin.math.abs(diff), 2)),
                style = MaterialTheme.typography.bodySmall,
                color = when { diff > 0.005 -> signal.down; diff < -0.005 -> signal.up; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        val punkte = remember(t) { t.verlauf.mapNotNull { p -> Politik.tag(p.t).takeIf { it > 0 }?.let { it to p.s } } }
        Column(Modifier.padding(horizontal = 20.dp)) {
            StandSeitZeile(punkte)
            VerlaufFrei(punkte, 0.0)
        }
        if (t.positionen.isNotEmpty()) {
            val pl = t.positionen
            KernAbschnitt(
                S.lpLeerverkaeufer,
                KernZeile(listOf(KernTeil(S.kernPositionen(t.positionen.size))), KernTeil(pct(t.summe), umgekehrt = true)),
                merker = null, schluessel = "leerpositionen/$isin", gross = false, lohnt = ListenKern.lohnt(pl.size),
            ) {
            pl.forEachIndexed { i, p ->
                if (i > 0) Trennlinie()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NamenText(p.h, style = MaterialTheme.typography.titleSmall)
                        Text(S.lpSeit(tag(p.d)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(pct(p.r), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                }
            }
            }
        }
        Text(S.lpNotiz(d.land, tag(d.stand)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        HinweisInfo(S.lpHinweis(d.land))
    }
}
