package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.FlexLauf
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.engine.FlexAbgleich
import de.troetenrepublik.app.engine.FlexAbruf
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Kennungen von Token und Abfragenummer des Flex-Berichts in AppState.schluesselSetzen (0.78).
const val FLEX_TOKEN = "flex_token"
const val FLEX_ABFRAGE = "flex_abfrage"

// Zugang zum Flex-Bericht von Interactive Brokers: Token, Abfragenummer, Abgleich beim Start. Die Anleitung liegt
// hinter dem Info-Symbol.
@Composable
internal fun FlexZugang(state: AppState, s: Settings) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.flex("name"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { info = true }) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Text(S.flex("zweck"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        FlexFeld(s.flexToken, S.flex("token"), verdeckt = true, gueltig = { FlexAbruf.tokenGueltig(it) }) { state.schluesselSetzen(FLEX_TOKEN, it) }
        FlexFeld(s.flexAbfrage, S.flex("abfrage"), verdeckt = false, gueltig = { FlexAbruf.nummerGueltig(it) }) { state.schluesselSetzen(FLEX_ABFRAGE, it) }
        SchalterZeile(S.flex("beimStart"), s.flexBeimStart) { an -> scope.launch { state.settings.update { it.copy(flexBeimStart = an) } } }
        if (s.flexToken.isNotBlank() && s.flexAbfrage.isNotBlank()) Text(
            S.flex("jetzt"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { state.flex.starten() }.padding(8.dp),
        )
    }
    if (info) MarktInfoBlatt(S.flex("anleitung.titel"), S.flex("anleitung") + "\n\n" + S.flex("punkte")) { info = false }
}

// Eingabefeld, das kurz nach dem Tippen speichert; nur Werte in gueltiger Form.
@Composable
private fun FlexFeld(gespeichert: String, titel: String, verdeckt: Boolean, gueltig: (String) -> Boolean, speichern: (String) -> Unit) {
    var eingabe by remember { mutableStateOf(gespeichert) }
    var zeigen by remember { mutableStateOf(!verdeckt) }
    val ok = eingabe.isBlank() || gueltig(eingabe)
    LaunchedEffect(eingabe) {
        if (eingabe.trim() == gespeichert || !ok) return@LaunchedEffect
        delay(700)
        speichern(eingabe)
    }
    OutlinedTextField(
        value = eingabe,
        onValueChange = { eingabe = it.trim().take(120) },
        label = { Text(titel) },
        singleLine = true,
        visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (verdeckt) KeyboardType.Password else KeyboardType.Number),
        trailingIcon = if (verdeckt) { { TextButton(onClick = { zeigen = !zeigen }) { Text(if (zeigen) S.zgHide else S.zgShow) } } } else null,
        shape = RoundedCornerShape(Radius.field),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
    if (!ok) Text(S.flex("formFalsch"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

// Ablauf des Abgleichs ueber allen Seiten: Laden, Meldung, Vorschau mit den Unterschieden und Bestaetigung.
@Composable
internal fun FlexAbgleichDialog(state: AppState, zuZugaengen: () -> Unit) {
    val stand by state.flex.stand.collectAsStateWithLifecycle()
    val zu = { state.flex.schliessen() }
    when (val st = stand) {
        FlexLauf.Stand.Ruht -> {}
        FlexLauf.Stand.Laedt -> AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.flex("titel")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp))
                    Text(S.flex("laedt"))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = zu) { Text(S.cancel) } },
        )
        FlexLauf.Stand.OhneZugang -> {
            var info by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = zu,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(Radius.card),
                title = { Text(S.flex("titel")) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.flex("ohneZugang"), modifier = Modifier.weight(1f))
                        IconButton(onClick = { info = true }) {
                            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { zu(); zuZugaengen() }) { Text(S.flex("zugaenge")) } },
                dismissButton = { TextButton(onClick = zu) { Text(S.cancel) } },
            )
            if (info) MarktInfoBlatt(S.flex("anleitung.titel"), S.flex("anleitung")) { info = false }
        }
        is FlexLauf.Stand.Meldung -> AlertDialog(
            onDismissRequest = zu,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.flex("titel")) },
            text = { Text(st.text) },
            confirmButton = { TextButton(onClick = zu) { Text(S.ok) } },
        )
        is FlexLauf.Stand.Vorschau -> FlexVorschau(st, onUebernehmen = { state.flex.uebernehmen() }, onZu = zu)
    }
}

// Kernaussage in einem Satz: Zahl der neuen, geaenderten und entfallenden Positionen, sonst der neuen Buchungen.
internal fun flexKernsatz(p: FlexAbgleich.Plan): String {
    val teile = FlexAbgleich.Art.entries.mapNotNull { a -> p.unterschiede.count { it.art == a }.takeIf { it > 0 }?.let { S.flex("n." + a.name, listOf(it.toString())) } }
    return when {
        teile.isNotEmpty() -> teile.joinToString(", ")
        p.buchungen > 0 -> S.flex("n.buchungen", listOf(Fmt.num(p.buchungen.toDouble(), 0)))
        else -> S.flex("gleich")
    }
}

@Composable
private fun FlexVorschau(v: FlexLauf.Stand.Vorschau, onUebernehmen: () -> Unit, onZu: () -> Unit) {
    val p = v.plan
    val signal = LocalSignal.current
    val kern = remember(p) { flexKernsatz(p) }
    // Lange Listen: wenige Zeilen, der Rest hinter "Mehr"
    var alle by remember(p) { mutableStateOf(false) }
    val sichtbar = if (alle || p.unterschiede.size <= ZEILEN_ZU + 1) p.unterschiede else p.unterschiede.take(ZEILEN_ZU)
    val linie = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    AlertDialog(
        onDismissRequest = onZu,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.card),
        title = { Text(S.flex("titel")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item(key = "kern") {
                    Column {
                        if (v.hinweis.isNotBlank()) Text(v.hinweis, style = MaterialTheme.typography.bodySmall, color = signal.warn, modifier = Modifier.padding(bottom = 6.dp))
                        Text(kern, style = MaterialTheme.typography.titleMedium)
                        if (p.von > 0 && p.bis > 0) Text(S.flex("zeitraum", listOf(Fmt.date(p.von), Fmt.date(p.bis))), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        // Anteile neu, geaendert, entfaellt als Balken in den Signalfarben
                        if (p.unterschiede.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
                            FlexAbgleich.Art.entries.forEach { a ->
                                val n = p.unterschiede.count { it.art == a }
                                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(
                                    when (a) { FlexAbgleich.Art.NEU -> signal.up; FlexAbgleich.Art.ENTFAELLT -> signal.down; else -> signal.warn }))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(sichtbar, key = { it.art.name + it.key }, contentType = { "u" }) { u ->
                    val farbe = when (u.art) { FlexAbgleich.Art.NEU -> signal.up; FlexAbgleich.Art.ENTFAELLT -> signal.down; else -> signal.warn }
                    Trennlinie(Modifier, thickness = 1.5.dp, color = linie)
                    FlexZeile(
                        u.name.ifBlank { u.key },
                        S.flex("art." + u.art.name) + if (u.art == FlexAbgleich.Art.NEU) "" else ", " + S.flex("vorher", listOf(Fmt.qty(u.vorher))),
                        Fmt.qty(u.nachher), farbe,
                    )
                }
                if (sichtbar.size < p.unterschiede.size) item(key = "mehr") {
                    Text(S.more + " (" + (p.unterschiede.size - sichtbar.size) + ")", style = MaterialTheme.typography.labelMedium, color = signal.link,
                        modifier = Modifier.textKlick(8.dp) { alle = true }.padding(vertical = 8.dp))
                }
                item(key = "zahlen") {
                    Column {
                        Trennlinie(Modifier, thickness = 1.5.dp, color = linie)
                        Spacer(Modifier.height(6.dp))
                        if (p.buchungen > 0) FlexZeile(S.flex("buchungen"), null, Fmt.num(p.buchungen.toDouble(), 0))
                        if (p.bekannt > 0) FlexZeile(S.flex("bekannt"), null, Fmt.num(p.bekannt.toDouble(), 0))
                        if (p.einzahlung > 0) FlexZeile(S.flex("einzahlung"), null, Fmt.euro(p.einzahlung))
                        p.bargeld?.let { FlexZeile(S.flex("bargeld"), null, Fmt.euro(it)) }
                    }
                }
                item(key = "hinweise") {
                    Column {
                        val fehler = MaterialTheme.colorScheme.error
                        val klein = MaterialTheme.typography.bodySmall
                        if (p.offen.isNotEmpty()) AufklappText(S.flex("offen", listOf(p.offen.joinToString(", ") { "${it.name} (${Fmt.qty(it.stueck)})" })), Modifier.padding(top = 8.dp), color = fehler, flaeche = MaterialTheme.colorScheme.surfaceContainer)
                        if (p.ohnePositionen) Text(S.flex("ohnePositionen"), style = klein, color = fehler, modifier = Modifier.padding(top = 8.dp))
                        if (p.ohneKauf.isNotEmpty()) Text(S.trImportOhneKauf(p.ohneKauf.joinToString(", ")), style = klein, color = fehler, modifier = Modifier.padding(top = 8.dp))
                        if (p.ungelesen > 0) Text(S.flex("ungelesen", listOf(Fmt.num(p.ungelesen.toDouble(), 0))), style = klein, color = fehler, modifier = Modifier.padding(top = 8.dp))
                        if (p.ohneWechselkurs > 0) Text(S.trImportWechselkurs(p.ohneWechselkurs), style = klein, color = fehler, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = { if (!p.leer) TextButton(onClick = onUebernehmen) { Text(S.flex("uebernehmen")) } else TextButton(onClick = onZu) { Text(S.ok) } },
        dismissButton = { if (!p.leer) TextButton(onClick = onZu) { Text(S.cancel) } },
    )
}

private const val ZEILEN_ZU = 6

// Bezeichnung links (bricht um), Zusatz klein darunter, Wert rechts in einer Zeile.
@Composable
private fun FlexZeile(name: String, zusatz: String?, wert: String, farbe: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (zusatz != null) Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, color = farbe, maxLines = 1, softWrap = false)
    }
}
