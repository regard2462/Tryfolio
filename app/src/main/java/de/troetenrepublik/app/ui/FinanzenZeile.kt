package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.r2

// Zeile „Finanzen“ der Wertpapierseite: Ampel und Titel, darunter Umsatz und Gewinn des letzten Geschaeftsjahrs in je
// einer Zeile. Bezeichnung links, rechts der Betrag und in fester Spalte die Veraenderung zum Vorjahr als farbiges
// Dreieck mit Prozent (oder, bei einem Vorzeichenwechsel, in Worten). Ein Tipp oeffnet die Finanzen.
@Composable
internal fun FinanzenZeile(
    ampel: Bewertungsblick.Ampel?,
    umsatz: String, umsatzPct: Double?,
    gewinn: String?, gewinnPct: Double?, gewinnWechsel: String?, gewinnWechselRichtung: Double?,
    unter: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AmpelPunkt(ampelZeichen(ampel), ampelFarbe(ampel), Modifier.padding(end = 12.dp, top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(S.bkWachstum, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanzenWert(S.revenue, umsatz, umsatzPct?.let { Fmt.signedPct(it, 0) }, umsatzPct)
            if (gewinn != null) {
                if (gewinnWechsel != null && gewinnWechselRichtung != null) FinanzenWert(S.bkGewinn, gewinn, r2(if (gewinnWechselRichtung > 0) "wechsel.aus" else "wechsel.in"), gewinnWechselRichtung)
                else FinanzenWert(S.bkGewinn, gewinn, gewinnPct?.let { Fmt.signedPct(it, 0) }, gewinnPct)
            }
            if (!unter.isNullOrBlank()) Text(unter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.padding(top = 2.dp)) { ChevronRight() }
    }
}

// Eine Zeile: Bezeichnung links, Betrag, dann die Veraenderung in einer Spalte von gleicher Mindestbreite. Ein Vorzeichenwechsel
// steht kurz („Aus Verlust“) mit Dreieck in derselben Spalte.
@Composable
private fun FinanzenWert(name: String, betrag: String, text: String?, richtung: Double?) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(betrag, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp))
        Box(Modifier.widthIn(min = 84.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterEnd) {
            if (text != null && richtung != null) ChangeValue(richtung, text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
