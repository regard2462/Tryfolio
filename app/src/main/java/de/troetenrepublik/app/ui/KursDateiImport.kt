package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KursDatei
import de.troetenrepublik.app.i18n.KursDateiTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.EigeneKurse
import de.troetenrepublik.app.plattform.rememberOeffnenRoh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Eigene Kursdateien (engine/KursDatei.kt, market/EigeneKurse.kt): Dateiwahl, Vorschau mit Symbol zum Aendern, Speichern.
// Zwei Einstiege: Zugaenge (Liste der Reihen mit Loeschen) und die Wertpapierseite ohne Kurse (Symbol der Seite vorbelegt).

private val TAG_MS = 86_400_000L

private class Vorschau(val ergebnis: KursDatei.Ergebnis?, val fehler: String?)

// Datum eines Kurstags in der Anzeige; die Kerzen liegen auf UTC-Mitternacht, mittags faellt es in jeder Zeitzone auf den Tag
private fun tagText(tag: Long): String = Fmt.date(tag * TAG_MS + TAG_MS / 2)

// Liefert die Funktion, die die Dateiwahl oeffnet; der Dialog nach der Wahl haengt an der aufrufenden Stelle.
// vorgabe: Symbol, das bei genau einer Reihe vorbelegt wird (leer: Symbol aus Datei oder Dateiname).
// gespeichert: wird nach erfolgreichem Speichern aufgerufen.
@Composable
fun rememberKursdateiEinlesen(state: AppState, vorgabe: String = "", gespeichert: () -> Unit = {}): () -> Unit {
    val scope = rememberCoroutineScope()
    var vorschau by remember { mutableStateOf<Vorschau?>(null) }
    val t = KursDateiTexte
    val oeffnen = rememberOeffnenRoh { name, daten ->
        if (daten == null) {
            state.showToast(t.t("unlesbar"))
            return@rememberOeffnenRoh
        }
        scope.launch {
            val e = withContext(Dispatchers.Default) { KursDatei.einlesen(name, daten) }
            vorschau = when {
                e.zuGross -> Vorschau(null, t.t("zuGross"))
                e.reihen.isEmpty() -> Vorschau(null, t.t("nichtErkannt"))
                else -> Vorschau(e, null)
            }
        }
    }
    vorschau?.let { v ->
        KursdateiDialog(
            v, vorgabe,
            speichern = { symbol ->
                val e = v.ergebnis ?: return@KursdateiDialog
                vorschau = null
                scope.launch {
                    val n = withContext(Dispatchers.Default) { EigeneKurse.speichern(e.reihen, symbol) }
                    state.showToast(
                        when (n) {
                            0 -> t.t("nichtGespeichert")
                            1 -> t.t("gespeichert1")
                            else -> t.t("gespeichert", Fmt.num(n.toDouble(), 0))
                        },
                    )
                    if (n > 0) gespeichert()
                }
            },
            zu = { vorschau = null },
        )
    }
    return oeffnen
}

@Composable
private fun DialogZeile(label: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
    }
}

// Ergebnis der Datei in drei Zeilen (Von, Bis, Tage); bei genau einer Reihe steht das Symbol zum Aendern oben.
@Composable
private fun KursdateiDialog(v: Vorschau, vorgabe: String, speichern: (symbol: String?) -> Unit, zu: () -> Unit) {
    val t = KursDateiTexte
    val e = v.ergebnis
    val einzeln = e != null && e.reihen.size == 1
    var symbol by remember(v) { mutableStateOf(if (e == null) "" else vorgabe.ifBlank { e.reihen.first().symbol }) }
    // Kleinste und groesste Tageszahl und Summe der Tage, einmal je Datei
    val spanne = remember(v) {
        e?.let { r ->
            Triple(
                r.reihen.minOf { Math.floorDiv(it.kerzen.first().t, TAG_MS) },
                r.reihen.maxOf { Math.floorDiv(it.kerzen.last().t, TAG_MS) },
                r.reihen.sumOf { it.kerzen.size },
            )
        }
    }
    AlertDialog(
        onDismissRequest = zu,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.card),
        title = { Text(t.t("einlesen")) },
        text = {
            Column {
                if (e == null || spanne == null) {
                    Text(v.fehler.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (einzeln) {
                        OutlinedTextField(
                            value = symbol,
                            onValueChange = { symbol = EigeneKurse.symbolSauber(it) },
                            label = { Text(t.t("symbol")) },
                            singleLine = true,
                            shape = RoundedCornerShape(Radius.field),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        )
                    } else {
                        DialogZeile(t.t("reihen"), Fmt.num(e.reihen.size.toDouble(), 0))
                    }
                    DialogZeile(t.t("von"), tagText(spanne.first))
                    DialogZeile(t.t("bis"), tagText(spanne.second))
                    DialogZeile(t.t("tage"), Fmt.num(spanne.third.toDouble(), 0))
                    val grau = MaterialTheme.colorScheme.onSurfaceVariant
                    if (e.uebersprungen > 0) Text(
                        if (e.uebersprungen == 1) t.t("uebersprungen1") else t.t("uebersprungen", Fmt.num(e.uebersprungen.toDouble(), 0)),
                        style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 10.dp),
                    )
                    if (e.reihenZuViel > 0) Text(
                        t.t("zuViel", Fmt.num(e.reihenZuViel.toDouble(), 0)),
                        style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (e == null) TextButton(onClick = zu) { Text(S.ok) }
            else TextButton(onClick = { speichern(if (einzeln) symbol else null) }, enabled = !einzeln || symbol.isNotBlank()) {
                Text(if (einzeln) t.t("speichern") else t.t("alleSpeichern"))
            }
        },
        dismissButton = { if (e != null) TextButton(onClick = zu) { Text(S.cancel) } },
    )
}

// Zeile zum Oeffnen der Dateiwahl in der Schrift der Links
@Composable
fun KursdateiLink(einlesen: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        KursDateiTexte.t("einlesen"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
        modifier = modifier.padding(horizontal = 12.dp).textKlick(8.dp, role = Role.Button, onClick = einlesen).padding(8.dp),
    )
}

// Abschnitt der Seite Zugaenge: Kopf mit den erlaubten Formaten, Einlesen, darunter die Zahl der Reihen eingeklappt und
// aufgeklappt die Liste mit Zeitraum, Zahl der Tage und Loeschen. Die Liste steht als einzelne Eintraege in der LazyColumn.
fun LazyListScope.eigeneKursdateien(
    einlesen: () -> Unit, eintraege: List<EigeneKurse.Eintrag>, offen: Boolean, umschalten: () -> Unit,
) {
    item(key = "kd-kopf", contentType = "kd-kopf") {
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                KursDateiTexte.t("titel"), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 8.dp),
            )
            Text(
                KursDateiTexte.t("formate"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            KursdateiLink(einlesen)
        }
    }
    if (eintraege.isNotEmpty()) {
        item(key = "kd-summe", contentType = "kd-summe") {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = umschalten).heightIn(min = 48.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (eintraege.size == 1) KursDateiTexte.t("reihenZahl1") else KursDateiTexte.t("reihenZahl", Fmt.num(eintraege.size.toDouble(), 0)),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                AufklappPfeil(offen)
            }
        }
    }
    if (offen) items(eintraege, key = { "kd-" + it.symbol }, contentType = { "kd-reihe" }) { e ->
        Column(Modifier.fillMaxWidth()) {
            Trennlinie(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(e.symbol, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        KursDateiTexte.t("zeitraum", tagText(e.vonTag), tagText(e.bisTag)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    KursDateiTexte.t("tageZahl", Fmt.num(e.anzahl.toDouble(), 0)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                )
                TextButton(onClick = { EigeneKurse.loeschen(e.symbol) }) {
                    Text(KursDateiTexte.t("loeschen"), color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
