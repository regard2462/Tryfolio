package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
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
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.i18n.S

// El Nino und La Nina als Zeile der Fruehsignale im Ausblick: Zustand nach NOAA, darunter die Kernaussage (welche
// Rohstoffpreise die laufende Phase typisch stuetzt oder drueckt). Aufgeklappt: Erklaerung und je Rohstoff die Richtung
// mit Grund; ohne laufende Phase beide Listen.
@Composable
internal fun EnsoZeile(state: AppState, oeffnen: ((String) -> Unit)? = null) {
    var status by remember { mutableStateOf<Enso.Status?>(null) }
    LaunchedEffect(Unit) { status = runCatching { state.ensoStatus() }.getOrNull() }
    val st = status ?: return
    val phase = Enso.phase(st.zustand)
    var auf by rememberSaveable { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.fillMaxWidth().clickable { auf = !auf }.padding(horizontal = 20.dp, vertical = 8.dp)) {
            NameUndWert(S.ensoTitel, S.ensoZustand(st.zustand))
            val liste = if (phase > 0) Enso.WIRKUNG_NINO else if (phase < 0) Enso.WIRKUNG_NINA else emptyList()
            val kern = if (phase == 0) S.ensoKernNeutral else S.ensoKern(
                liste.filter { it.richtung > 0 }.joinToString(", ") { S.ensoWirkName(it.schluessel) },
                liste.filter { it.richtung < 0 }.joinToString(", ") { S.ensoWirkName(it.schluessel) },
            )
            Text(kern, style = MaterialTheme.typography.bodySmall, color = grau)
        }
        if (auf) {
            Text(S.ensoKurz, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
            if (phase >= 0) EnsoListe(S.ensoBeiNino.takeIf { phase == 0 }, 1, Enso.WIRKUNG_NINO)
            if (phase <= 0) EnsoListe(S.ensoBeiNina.takeIf { phase == 0 }, -1, Enso.WIRKUNG_NINA)
            Text(S.ensoMuster, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp))
            // Index, Staerke, Folgen und betroffene Werte stehen in der Weltlage des Globus
            if (oeffnen != null) VerweisText(S.weltlageTitel, Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) { oeffnen("globus/engpaesse/weltlage") }
        }
    }
}

@Composable
private fun EnsoListe(kopf: String?, phase: Int, liste: List<Enso.Wirkung>) {
    if (kopf != null) Zwischenkopf(kopf)
    for (w in liste) {
        // Richtung als gezeichnetes Dreieck mit Wort, Grund als kurze Zeile unter dem Rohstoff
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(S.ensoWirkName(w.schluessel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(S.ensoWirkGrund(phase, w.schluessel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            ChangeValue(w.richtung.toDouble(), if (w.richtung > 0) S.ensoBullish else S.ensoBearish, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
