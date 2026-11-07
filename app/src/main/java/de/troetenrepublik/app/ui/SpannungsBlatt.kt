package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.Konflikte
import de.troetenrepublik.app.engine.Sanktionen
import de.troetenrepublik.app.engine.Spannungen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch

// [Engpaesse] Spannungsgebiete in der Weltlage: Berichtsdichte der letzten sieben Tage gegen die Wochen davor (GDELT), Blatt
// mit Konflikten (UCDP), Sanktionen, Werten und Schlagzeilen. Geladen wird erst, wenn der Abschnitt aufgeklappt ist.
@Composable
internal fun SpannungsAbschnitt(state: AppState, onOpen: (String) -> Unit) {
    var offen by rememberSaveable { mutableStateOf(false) }
    var werte by remember { mutableStateOf<Map<String, Spannungen.Wert>>(emptyMap()) }
    var fertig by remember { mutableStateOf(false) }
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(offen) {
        if (!offen) return@LaunchedEffect
        fertig = false
        // GDELT erlaubt eine Anfrage je fuenf Sekunden: Gebiet fuer Gebiet, jedes erscheint, sobald es da ist
        for (g in Spannungen.GEBIETE) ohneAbbruch { state.spannungWert(g) }.getOrNull()?.let { werte = werte + (g.id to it) }
        fertig = true
    }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.spTitel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
    if (offen) {
        Text(S.spErklaerung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        for (w in Spannungen.sortiert(werte.values.toList())) {
            KeyValueRow(S.spName(w.id), "×" + Fmt.num(w.faktor, 1), onClick = { gewaehlt = w.id })
        }
        if (!fertig) Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        else if (werte.isEmpty()) Text(S.engKeineDaten, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        else Text(S.spQuelleKurz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    gewaehlt?.let { id -> SpannungsBlatt(state, id, werte[id], onOpen) { gewaehlt = null } }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

private class SpannungsDaten(
    val karte: GlobusKarte?,
    val konflikte: Konflikte.Stand?,
    val sanktionen: Sanktionen.Liste?,
    val meldungen: List<de.troetenrepublik.app.engine.LaenderZusatz.Meldung>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpannungsBlatt(state: AppState, id: String, wert: Spannungen.Wert?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val g = Spannungen.gebiet(id) ?: return
    val deutsch = I18n.locale.language == "de"
    var daten by remember(id) { mutableStateOf<SpannungsDaten?>(null) }
    var info by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var artikel by remember(id) { mutableStateOf<MarketApi.NewsItem?>(null) }
    LaunchedEffect(id) {
        val k = ohneAbbruch { state.globusKarte() }.getOrNull()
        val c = ohneAbbruch { state.risikoKonflikte() }.getOrNull()
        val s = ohneAbbruch { state.risikoSanktionenListe() }.getOrNull()
        daten = SpannungsDaten(k, c, s, emptyList())
        val m = ohneAbbruch { state.engpassMeldungen("sp$id", S.spSuche(id)) }.getOrDefault(emptyList())
        daten = SpannungsDaten(k, c, s, m)
    }
    val d = daten
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(S.spName(id), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (wert != null) {
                Text(
                    S.spGegenDavor("×" + Fmt.num(wert.faktor, 1)), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), maxLines = 1, softWrap = false,
                )
                KeyValueRow(S.spFaktor, "×" + Fmt.num(wert.faktor, 1), onClick = { info = S.spFaktor to S.spFaktorInfo })
                KeyValueRow(S.spArtikel, Fmt.num(wert.artikel.toDouble(), 0), onClick = { info = S.spArtikel to S.spArtikelInfo })
                Text(
                    S.engStand(Fmt.date(wert.bis * 86_400_000L + 43_200_000L)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            // Konflikte der beteiligten Staaten (gebuendelte Daten von UCDP)
            val c = d?.konflikte
            val karte = d?.karte
            if (c != null && karte != null) {
                val zeilen = g.laender.mapNotNull { a3 -> karte.nachA3(a3)?.let { l -> Konflikte.fuer(c, l).takeIf { it.isNotEmpty() }?.let { l to it } } }
                if (zeilen.isNotEmpty()) {
                    AbschnittKopf(S.spKonflikte(c.jahr))
                    for ((l, liste) in zeilen) {
                        val name = l.name(deutsch)
                        val text = liste.joinToString("\n") { S.spKonfliktZeile(Konflikte.seite(it.seiteA, deutsch), Konflikte.seite(it.seiteB, deutsch), it.stufe) }
                        KeyValueRow(name, S.spKonfliktAnzahl(liste.size), onClick = { info = name to (text + "\n" + S.spKonfliktInfo) })
                    }
                }
            }
            SanktionsZeilen(g.laender, karte, d?.sanktionen) { info = it }
            if (g.werte.isNotEmpty()) {
                AbschnittKopf(S.engWerte)
                WerteListe(g.werte, onOpen)
            }
            SchlagzeilenListe(d?.meldungen.orEmpty()) { artikel = it }
            Text(S.spQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}
