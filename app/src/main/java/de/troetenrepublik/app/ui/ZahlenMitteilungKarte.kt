package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Zahlenmitteilung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.ohneAbbruch

// Pressemitteilung zu den Zahlen (0.78) in der Quartalskarte: aufklappbar, geholt wird erst beim Aufklappen. Zuerst die
// erkannten Kennzahlen mit Vorquartal, dann der Text der Mitteilung. Ohne Kontaktadresse unter Zugaenge steht nur ein
// Hinweis.
@Composable
internal fun ZahlenMitteilungKarte(state: AppState, symbol: String) {
    val einstellungen by state.settingsFlow.collectAsState()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    if (einstellungen.secKontakt.isBlank()) {
        SectionHeader(S.zmTitel)
        Text(S.zmOhneKontakt, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        return
    }
    var auf by remember(symbol) { mutableStateOf(false) }
    var daten by remember(symbol) { mutableStateOf<AppState.ZahlenMitteilung?>(null) }
    LaunchedEffect(symbol, auf) {
        if (auf && daten == null) daten = ohneAbbruch { state.zahlenMitteilung(symbol) }.getOrNull() ?: AppState.ZahlenMitteilung(AppState.ZahlenMitteilung.Status.KEINE)
    }
    val d = daten
    SectionHeader(S.zmTitel)
    Row(
        Modifier.fillMaxWidth().clickable { auf = !auf }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val vom = d?.takeIf { it.status == AppState.ZahlenMitteilung.Status.OK }?.let { m -> Politik.tag(m.tag).takeIf { it > 0 }?.let { Fmt.date(it) } ?: m.tag }
        Text(vom?.let { S.zmVom(it) } ?: S.zmInhalt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        AufklappPfeil(auf)
    }
    if (!auf) return
    if (d == null) {
        Text(S.loading, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        return
    }
    val m = d.jetzt
    if (d.status != AppState.ZahlenMitteilung.Status.OK || m == null) {
        Text(S.zmKeine, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        return
    }
    val signal = LocalSignal.current
    Zahlenmitteilung.vergleichen(m.kennzahlen, d.vorher?.kennzahlen).forEach { v ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.zmArt(v.kennzahl.art.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(v.kennzahl.wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            if (v.vorher != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.zmVorquartal(v.vorher.wert), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    v.aenderung?.let {
                        Text(
                            Fmt.signedPct(it, 1), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                            color = when { it > 0.05 -> signal.up; it < -0.05 -> signal.down; else -> grau },
                        )
                    }
                }
            }
        }
    }
    m.ausblick.forEach { a ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            PunktText(S.zmAusblick + " · " + a.label, style = MaterialTheme.typography.bodySmall, color = grau)
            Text(a.werte, style = MaterialTheme.typography.bodyMedium)
        }
    }
    m.absaetze.forEach { a ->
        Text(a, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    }
    ErklaerKnopf(state, { de.troetenrepublik.app.engine.Erklaerer.kontextMitteilung(symbol, d.tag, m, d.vorher) })
    val uri = appLinks()
    QuellenZeile(S.zmOriginal, onClick = { runCatching { uri.openUri(d.link) } })
}
