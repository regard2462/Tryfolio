package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// Knappe Verweiszeile innerhalb eines Abschnitts oder einer aufgeklappten Zeile: Name der ausfuehrlichen Stelle in der
// Farbe der Verweise mit Pfeil. Der Aufrufer setzt den seitlichen Abstand ueber modifier.
@Composable
internal fun VerweisText(titel: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val farbe = LocalSignal.current.link
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.labelLarge, color = farbe, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        ChevronRight(color = farbe)
    }
}
