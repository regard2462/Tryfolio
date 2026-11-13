package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.SeitenFehler
import de.troetenrepublik.app.i18n.S

// Kurze Zeile zum Fehler der Seite: Status ab 400 oder 0 fuer einen Ladefehler.
internal fun seitenFehlerZeile(status: Int): String = when (SeitenFehler.art(status)) {
    SeitenFehler.Art.WEG -> S.newsSeiteWeg(status)
    SeitenFehler.Art.ZU_OFT -> S.newsSeiteZuOft(status)
    SeitenFehler.Art.NICHT_ERREICHBAR -> S.newsSeiteNichtErreichbar(status)
    SeitenFehler.Art.LADEFEHLER, null -> S.ohneVerbindung
    SeitenFehler.Art.ABGELEHNT -> S.newsSeiteAbgelehnt(status)
}

// Die Zeilen zum Fehler, ohne Knopf.
@Composable
internal fun SeitenFehlerZeilen(status: Int) {
    Column {
        Text(seitenFehlerZeile(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (SeitenFehler.mitVpnZeile(status)) {
            Text(S.newsSeiteVpn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Ruhiger Zustand an der Stelle einer abgewiesenen Seite: Zeilen, Anriss der Meldung (falls vorhanden) und Hauptknopf.
@Composable
internal fun SeiteAbgewiesen(status: Int, anriss: String, onBrowser: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 16.dp)) {
        SeitenFehlerZeilen(status)
        if (anriss.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(anriss, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        if (onBrowser != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onBrowser() }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(S.openInBrowser, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) }
        }
    }
}
