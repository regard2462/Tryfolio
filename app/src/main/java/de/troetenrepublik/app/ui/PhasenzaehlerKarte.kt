package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.MarktzeichenSpeicher
import de.troetenrepublik.app.engine.Phasenzaehler
import de.troetenrepublik.app.engine.Phasenzaehler.Groesse
import de.troetenrepublik.app.engine.Phasenzaehler.Lage
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.phasenzaehler

// [0.78] Phasenzaehler im Gesamtausblick: eine Zeile mit der Lage (voll, teilweise, nicht investiert) und der Zahl der
// positiven Groessen; ein Tipp klappt die Groessen mit Wert, Vorjahr und Stand auf. Rechnung in engine/Phasenzaehler.kt.
// Ohne genug Daten erscheint die Karte nicht.
@Composable
// zurSaison: Tipp auf die Saisonzeile (Saison in den Mustern der Vergangenheit).
internal fun PhasenzaehlerKarte(state: AppState, zurSaison: (() -> Unit)? = null) {
    val ergebnis by produceState(MarktzeichenSpeicher.phasen?.second) { value = runCatching { state.phasenzaehler() }.getOrNull() ?: value }
    val e = ergebnis ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    var offen by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    if (info) MarktInfoBlatt(S.ml("pz.titel"), S.ml("pz.regel")) { info = false }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(),
    ) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.ml("pz.titel"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                    // Regel und Erklaerung der Groessen hinter dem Info-Symbol
                    Icon(
                        Icons.Outlined.Info, S.infoErklaerung, tint = grau,
                        modifier = Modifier.padding(start = 4.dp).clickable(role = Role.Button) { info = true }.padding(6.dp).size(18.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.ml("pz.zahl", listOf(e.positiv.toString(), e.zeilen.size.toString())), style = MaterialTheme.typography.labelSmall, color = grau)
                    // Ein Punkt je Groesse auf einen Blick: gruen positiv, rot negativ
                    Spacer(Modifier.width(8.dp))
                    for (z in e.zeilen) {
                        Box(Modifier.padding(end = 3.dp).size(7.dp).clip(CircleShape).background(if (z.positiv) signal.up else signal.down))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                S.ml("pz.lage." + e.lage.name), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false,
                color = when (e.lage) { Lage.VOLL -> signal.up; Lage.NICHT -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
            )
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(offen)
        }
        if (offen) {
            for (z in e.zeilen) Row(
                Modifier.fillMaxWidth().then(if (z.groesse == Groesse.SAISON && zurSaison != null) Modifier.clickable(role = Role.Button, onClick = zurSaison) else Modifier)
                    .heightIn(min = 44.dp).padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmpelPunkt(if (z.positiv) AmpelZeichen.GUT else AmpelZeichen.KRITISCH, if (z.positiv) signal.up else signal.down)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.ml("pz." + z.groesse.name + "." + (if (z.positiv) "1" else "0")), style = MaterialTheme.typography.bodyMedium)
                    Text(pzUnter(z), style = MaterialTheme.typography.labelSmall, color = grau)
                }
                Spacer(Modifier.width(8.dp))
                Text(pzWert(z.groesse, z.wert), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                if (z.groesse == Groesse.SAISON && zurSaison != null) {
                    Spacer(Modifier.width(8.dp))
                    ChevronRight()
                }
            }
        }
    }
}

// Wert einer Groesse: Zinsen in Prozent, Aenderungen mit Vorzeichen, die Saison als Monat.
private fun pzWert(g: Groesse, v: Double): String = when (g) {
    Groesse.RENDITE -> Fmt.pct(v, 2)
    Groesse.RELATIVZINS -> Fmt.signedPct(v, 1)
    Groesse.ZINSSTRUKTUR -> Fmt.signedPunkte(v, 2)
    Groesse.SAISON -> java.time.YearMonth.of(2000, v.toInt().coerceIn(1, 12)).format(java.time.format.DateTimeFormatter.ofPattern("MMMM", I18n.locale))
    else -> Fmt.signedPct(v, 1)
}

private fun pzUnter(z: Phasenzaehler.Zeile): String {
    if (z.groesse == Groesse.SAISON) return S.ml("pz.saison")
    // Mittag, damit der Tag in jeder Zeitzone derselbe bleibt
    val stand = Fmt.date(z.tag * 86_400_000L + 43_200_000L)
    val vor = z.vorjahr ?: return S.ml(if (z.groesse == Groesse.ZINSSTRUKTUR) "pz.stand" else "pz.jahr", listOf(stand))
    return S.ml("pz.vorjahr", listOf(pzWert(z.groesse, vor), stand))
}
