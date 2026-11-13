package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.NyFedMessung
import de.troetenrepublik.app.engine.RezessionNyFed
import de.troetenrepublik.app.engine.WarnRueckblick
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.nyFedRezession
import de.troetenrepublik.app.nyFedRezessionBekannt

// Rezessionswahrscheinlichkeit der New York Fed aus der Zinskurve, als Zeile der Fruehsignale im Ausblick: Name und Wert
// (Wahrscheinlichkeit einer US-Rezession in zwoelf Monaten), ein Satz mit dem Monat, fuer den sie gilt. Aufgeklappt der
// Verlauf, wie man ihn jeweils sah (mit Rezessionsbaendern), die Trefferbilanz aus den Daten und der Stand.
@Composable
internal fun NyFedZeile(state: AppState) {
    var reihe by remember { mutableStateOf(nyFedRezessionBekannt()) }
    LaunchedEffect(Unit) { runCatching { state.nyFedRezession() }.getOrNull()?.let { reihe = it } }
    val r = reihe ?: return
    val jetzt = remember(r) { RezessionNyFed.aktuell(r) } ?: return
    val (monat, wert) = jetzt
    var auf by rememberSaveable { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = when (RezessionNyFed.stufe(wert)) {
        0 -> LocalSignal.current.up
        1 -> LocalSignal.current.warn
        else -> LocalSignal.current.down
    }
    Column(Modifier.fillMaxWidth().clickable { auf = !auf }.animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        NameUndWert(S.nyName, Fmt.num(wert, 0) + " %")
        Text(S.nySatz(RezessionNyFed.stufe(wert), Fmt.monthYear(java.time.YearMonth.of(RezessionNyFed.jahr(monat), RezessionNyFed.monatImJahr(monat)))), style = MaterialTheme.typography.bodySmall, color = farbe)
        // Richtung fuer Aktien mit der gemessenen Staerke, wie bei den anderen Fruehsignalen
        TendenzZeile(remember(wert) { NyFedMessung.lage(wert) }, Modifier.padding(top = 2.dp), ausblickStaerke(NyFedMessung.SCHLUESSEL))
        val verlauf = remember(r) { RezessionNyFed.gesehen(r) }
        StandSeitZeile(verlauf, vollstaendig = true)
        if (auf) {
            Spacer(Modifier.height(6.dp))
            VerlaufFrei(verlauf, RezessionNyFed.SCHWELLE, wertText = { Fmt.num(it, 0) + " %" }, zeitText = { nyMonatMs(it) })
            remember(r) { RezessionNyFed.bilanz(r) }?.let { b ->
                Text(
                    S.nyBilanz(Fmt.num(b.schwelle, 0), b.treffer, b.von, b.fehlalarme, b.abJahr), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            remember(r) { NyFedMessung.einbrueche(r, NyFedMessung.hochs(WarnRueckblick.datei)) }?.let { m ->
                Text(
                    S.nyEinbrueche(Fmt.num(m.schwelle, 0), m.treffer, m.von, m.abJahr, Fmt.num(m.grund * 100, 0)),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp),
                )
            }
            var erklaerung by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(S.nyStand(nyMonat(r.stand)), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
                IconButton(onClick = { erklaerung = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(20.dp))
                }
            }
            if (erklaerung) MarktInfoBlatt(S.nyName, S.nyErklaerung) { erklaerung = false }
        }
    }
}

// Monat als „10/2027“ (Monatszahl der Reihe)
private fun nyMonat(m: Int): String = "%02d/%d".format(RezessionNyFed.monatImJahr(m), RezessionNyFed.jahr(m))

// Zeit in ms als „10/2027“
private fun nyMonatMs(ms: Long): String = java.time.LocalDate.ofEpochDay(ms / 86_400_000L).let { "%02d/%d".format(it.monthValue, it.year) }
