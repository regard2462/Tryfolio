package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.NOTIZ_MAX
import de.troetenrepublik.app.i18n.S

// Eigene Notiz zu einem Wert (seit 5.4), etwa warum man ihn gekauft hat oder beobachtet. Leer gespeichert loescht sie.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotizSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val bisher = account.notizen[symbol].orEmpty()
    var text by remember { mutableStateOf(bisher) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(state, symbol, name, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(S.notiz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(name, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(S.notizHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NOTIZ_MAX) },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bisher.isNotBlank()) FloatingPill(label = S.notizLoeschen, filled = false, modifier = Modifier.weight(1f)) {
                    state.notizSetzen(symbol, "")
                    onDismiss()
                }
                FloatingPill(label = S.save, enabled = text.trim() != bisher.trim(), modifier = Modifier.weight(1f)) {
                    state.notizSetzen(symbol, text)
                    onDismiss()
                }
            }
        }
    }
}
