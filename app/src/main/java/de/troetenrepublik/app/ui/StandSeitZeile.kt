package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import de.troetenrepublik.app.engine.StandSeit
import de.troetenrepublik.app.i18n.S

// „Hoechster Stand seit 12.03.2020“ als gedaempfte Zeile unter einem Wert (engine/StandSeit.kt). Steht in einer eigenen
// Zeile, der Wert darueber bleibt unberuehrt. Ohne Aussage (Abstand unter einem Jahr) erscheint nichts.
@Composable
internal fun StandSeitZeile(e: StandSeit.Ergebnis?, modifier: Modifier = Modifier) {
    e ?: return
    Text(
        standSeitText(e), modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun standSeitText(e: StandSeit.Ergebnis): String = S.standSeit(
    e.art == StandSeit.Art.HOCH,
    if (e.rekord) java.time.Instant.ofEpochMilli(e.seit).atZone(java.time.ZoneOffset.UTC).year.toString() else Fmt.date(e.seit),
    e.rekord,
)

// Aus einer Reihe (Zeit in ms, Wert; der letzte Punkt ist der heutige Wert)
@Composable
internal fun StandSeitZeile(punkte: List<Pair<Long, Double>>, modifier: Modifier = Modifier, vollstaendig: Boolean = false) {
    val e = androidx.compose.runtime.remember(punkte, vollstaendig) { StandSeit.pruefen(punkte, vollstaendig) }
    StandSeitZeile(e, modifier)
}
