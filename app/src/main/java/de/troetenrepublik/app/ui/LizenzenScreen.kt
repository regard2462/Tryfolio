package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Lizenztext
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Route der Lizenzseite
const val LIZENZEN = "lizenzen"

// Zeigt die Sammeldatei mit den Lizenzen der mitgelieferten Bibliotheken
// (seit 5.95), Text im englischen Original; Datei neben dem Hauptthread lesen.
@Composable
fun LizenzenScreen(onBack: () -> Unit) {
    val absaetze = produceState<List<Lizenztext.Absatz>?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { Lizenztext.absaetze(Plattform.asset(Lizenztext.DATEI).decodeToString()) }
                .getOrDefault(emptyList())
        }
    }.value
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { !liste.canScrollBackward },
        state = liste
    ) {
        item { ScreenHeader(S.lizenzenTitel, null, onBack) }
        item { HinweisInfo(S.lizenzenHinweis) }
        if (absaetze == null) {
            // Laden laeuft: nur Kopf und Hinweis, kein Platzhalter
        } else if (absaetze.isEmpty()) {
            item { EmptyHint(S.lizenzenFehlt) }
        } else {
            items(absaetze) { absatz ->
                if (absatz.titel) {
                    Text(
                        absatz.text,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                } else {
                    Text(
                        absatz.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
