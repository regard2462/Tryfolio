package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.GdpNow
import de.troetenrepublik.app.gdpNow
import de.troetenrepublik.app.i18n.GdpNowTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

private fun gnText(key: String, vararg w: String): String = GdpNowTexte.text(I18n.lang == Lang.DE, key, w.toList())

private const val GN_TAG_MS = 86_400_000L

// Quelle der Zeile fuer den Anhang der Seite (die Reihe verlangt die Nennung)
internal fun gdpNowQuelle(s: GdpNow.Stand): QuelleLink =
    if (s.quelle == GdpNow.Quelle.ATLANTA) QuelleLink("© Federal Reserve Bank of Atlanta, GDPNow", de.troetenrepublik.app.engine.GdpNowAtlanta.SEITE)
    else fredQuelle(GdpNow.REIHE)

// GDPNow als Zeile der Fruehsignale im Ausblick: Quartal und Schaetzung, ein Satz zum Zustand, der Verlauf der
// Schaetzung fuer das laufende Quartal als Linie, darunter Stand und die letzte Schaetzung des Vorquartals. Die
// Erklaerung steht hinter dem Info-Symbol. Ohne Antwort der Quelle fehlt die Zeile.
@Composable
internal fun GdpNowZeile(state: AppState) {
    var stand by remember { mutableStateOf<GdpNow.Stand?>(null) }
    LaunchedEffect(Unit) { runCatching { state.gdpNow() }.getOrNull()?.let { stand = it } }
    val s = stand ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = when (GdpNow.stufe(s.wert)) {
        0 -> LocalSignal.current.down
        1 -> LocalSignal.current.warn
        else -> LocalSignal.current.up
    }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        NameUndWert(gnText("name", gnText("quartal", s.nummer.toString(), s.jahr.toString())), Fmt.signedPct(s.wert, 1))
        Text(gnText("satz." + GdpNow.stufe(s.wert)), style = MaterialTheme.typography.bodySmall, color = farbe)
        val punkte = remember(s) { s.verlauf.map { (tag, w) -> tag * GN_TAG_MS + GN_TAG_MS / 2 to w } }
        // Grundlinie bei null, sobald die Schaetzung im Quartal negativ war, sonst beim tiefsten Wert
        val tief = remember(punkte) { punkte.minOfOrNull { it.second } ?: 0.0 }
        if (punkte.size >= 2) VerlaufFrei(punkte, schwelle = if (tief < 0) 0.0 else tief, wertText = { Fmt.signedPct(it, 1) }, rezessionen = false)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val vor = s.vorquartal?.let {
                val q = java.time.LocalDate.ofEpochDay(s.quartal).minusMonths(3)
                gnText("vorquartal", gnText("quartal", ((q.monthValue - 1) / 3 + 1).toString(), q.year.toString()), Fmt.signedPct(it, 1))
            }
            PunktText(
                listOfNotNull(gnText("stand", Fmt.date(s.stand * GN_TAG_MS + GN_TAG_MS / 2)), vor).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { info = true }) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (info) MarktInfoBlatt(gnText("info.titel"), gnText("info")) { info = false }
}
