package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Erklaerer
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.launch

// [0.77] Knopf "Erklaeren": steht nur, wenn unter Zugaenge ein Schluessel mit vollstaendiger Wahl hinterlegt ist. Er oeffnet
// das Blatt, das die Zahlen der Seite (kontext, erst beim Oeffnen gebaut) an den gewaehlten Anbieter schickt.
@Composable
internal fun ErklaerKnopf(state: AppState, kontext: () -> String, modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
    val settings by state.settingsFlow.collectAsState()
    if (!state.erklaererEinstellung(settings).bereit) return
    var offen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth()) {
        Box(
            Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { offen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(S.erkKnopf, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) }
    }
    if (offen) ErklaerBlatt(state, kontext()) { offen = false }
}

// Kurze Zeile fuer einen Fehler des Anbieters; der Schluessel steht nie darin.
internal fun erklaerMeldung(f: Erklaerer.Ergebnis.Fehler): String = when (f.art) {
    Erklaerer.Fehlerart.SCHLUESSEL -> S.erkSchluessel
    Erklaerer.Fehlerart.LIMIT -> S.erkLimit
    Erklaerer.Fehlerart.UEBERLASTET -> S.erkUeberlastet
    Erklaerer.Fehlerart.NETZ -> S.erkNetz
    Erklaerer.Fehlerart.LEER -> S.erkLeer
    Erklaerer.Fehlerart.ANBIETER -> S.erkAnbieterSagt(f.meldung)
}

// Blatt mit der Antwort und einem Feld fuer Nachfragen. Die erste Nachricht (Kontext) bleibt unsichtbar.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ErklaerBlatt(state: AppState, kontext: String, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsState()
    val deutsch = istDeutsch()
    val e = remember(settings) { state.erklaererEinstellung(settings) }
    val verlauf = remember { mutableStateListOf(Erklaerer.Nachricht("user", Erklaerer.ersteNachricht(deutsch, kontext))) }
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<Erklaerer.Ergebnis.Fehler?>(null) }
    var eingabe by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun senden() {
        if (laeuft) return
        laeuft = true
        fehler = null
        val stand = verlauf.toList()
        scope.launch {
            when (val r = state.erklaeren(stand)) {
                is Erklaerer.Ergebnis.Text -> verlauf.add(Erklaerer.Nachricht("assistant", r.text))
                is Erklaerer.Ergebnis.Fehler -> fehler = r
            }
            laeuft = false
        }
    }
    LaunchedEffect(Unit) { senden() }

    val anbieter = if (e.anbieter == Erklaerer.OPENAI) Erklaerer.basisadresse(e.adresse)?.substringAfter("://").orEmpty() else "Anthropic"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(S.erkBlatt, style = MaterialTheme.typography.headlineLarge)
                PunktText(anbieter + " · " + e.modellName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f, fill = false), overscrollEffect = null) {
                itemsIndexed(verlauf.drop(1), key = { i, _ -> i }, contentType = { _, n -> n.rolle }) { _, n ->
                    if (n.rolle == "user") {
                        Text(n.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    } else {
                        Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
                item(key = "stand", contentType = "stand") {
                    if (laeuft) Text(S.loading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    fehler?.let { f ->
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(erklaerMeldung(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { senden() }) { Text(S.erkNochmal) }
                        }
                    }
                }
            }
            if (!laeuft && fehler == null && verlauf.last().rolle == "assistant") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = eingabe,
                        onValueChange = { eingabe = it.take(500) },
                        label = { Text(S.erkNachfrage) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.field),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val t = eingabe.trim()
                            if (t.isNotEmpty()) {
                                verlauf.add(Erklaerer.Nachricht("user", t))
                                eingabe = ""
                                senden()
                            }
                        },
                    ) { Text(S.erkSenden) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
