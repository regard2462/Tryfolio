package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Indikatoren
import de.troetenrepublik.app.i18n.S

// Bestaetigte Chartformation (0.78) unter den Kerzenmustern: Name mit Einordnung, kurze Bedeutung, Tag des Durchbruchs.
@Composable
internal fun FormationZeile(d: Indikatoren.Deutung) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                S.fmName(d.lage), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            TendenzText(Indikatoren.tendenz(d).name, style = MaterialTheme.typography.labelLarge)
        }
        Text(S.fmBedeutung(d.lage, d.stufe > 0), style = MaterialTheme.typography.bodySmall, color = grau)
        d.kreuzTag?.let { Text(S.fmDurchbruch(Fmt.date(it * 86_400_000L + 43_200_000L)), style = MaterialTheme.typography.bodySmall, color = grau) }
    }
}
