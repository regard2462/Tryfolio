package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Taktik
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.TaktikTexte
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.delay

// Wahl eines Werts fuer Signalwert, Ausweichwert und das Universum der Rotation (0.78): Suche der App mit
// Vorschlaegen aus dem Katalog.

internal fun taktikText(key: String, vararg w: String): String = TaktikTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Namen von Suchtreffern, die der Katalog nicht kennt, solange die App laeuft.
private val gemerkteNamen = mutableStateMapOf<String, String>()

internal fun wertName(symbol: String): String = Symbols.find(symbol)?.name ?: gemerkteNamen[symbol] ?: symbol

// Vorschlaege fuer den Signalwert: die Indizes der Suche und die ungehebelten ETFs der Vergleichsliste.
private val SIGNAL_VORSCHLAEGE: List<String> by lazy { (Symbols.indices.map { it.symbol } + Portfolioverlauf.MASSSTAEBE.take(5).map { it.symbol }).distinct() }

// Blatt mit Suchfeld. Ohne Eingabe stehen die Wahl „ohne“ (etwa Bargeld) und die Vorschlaege da, sonst die Treffer aus
// Katalog und Suche. alleArten: auch Indizes, Rohstoffe und Waehrungen (fuer den Signalwert), sonst nur Handelbares.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WertWahlBlatt(
    state: AppState, titel: String, vorschlaege: List<String>, ohne: Set<String>, ohneWahl: String?, alleArten: Boolean,
    onWahl: (String?) -> Unit, onDismiss: () -> Unit,
) {
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    fun passt(symbol: String) = symbol !in ohne && (alleArten || Symbols.typeOf(symbol).directlyTradable)
    val lokal = remember(suche) { Symbols.search(suche).filter { passt(it.symbol) }.take(20) }
    LaunchedEffect(suche) {
        treffer = emptyList()
        if (suche.trim().length < 2) return@LaunchedEffect
        delay(350)
        val bekannt = lokal.map { it.symbol }.toSet()
        treffer = runCatching { state.market.search(suche.trim()) }.getOrDefault(emptyList()).distinctBy { it.symbol }
            .onEach { Symbols.merkeArt(it.symbol, Symbols.artAusYahoo(it.type)) }
            .filter { it.symbol !in bekannt && passt(it.symbol) }.take(12)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(titel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            OutlinedTextField(
                value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, label = { Text(taktikText("wahl.suche")) },
                shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (suche.isBlank()) {
                if (ohneWahl != null) ActionLineWide(ohneWahl) { onWahl(null) }
                val liste = vorschlaege.filter { passt(it) }
                if (liste.isNotEmpty()) Text(
                    taktikText("wahl.vorschlaege"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp),
                )
                liste.forEach { s -> AssetRow(symbol = s, name = wertName(s), subtitle = s) { onWahl(s) } }
            } else {
                lokal.forEach { i -> AssetRow(symbol = i.symbol, name = i.name, subtitle = i.symbol) { onWahl(i.symbol) } }
                treffer.forEach { h ->
                    AssetRow(symbol = h.symbol, name = h.name.ifBlank { h.symbol }, subtitle = listOf(h.symbol, h.exchange).filter { it.isNotBlank() }.joinToString(" · ")) {
                        if (h.name.isNotBlank()) gemerkteNamen[h.symbol] = h.name
                        onWahl(h.symbol)
                    }
                }
            }
        }
    }
}

// Einstellung im Strategietest, eingeklappt: Signalwert und Ausweichwert. Ohne Wahl rechnet der Test wie zuvor.
@Composable
internal fun TaktikEinstellung(
    state: AppState, symbol: String, signal: String?, ausweich: String?, offen: SnapshotStateMap<String, Boolean>,
    onSignal: (String?) -> Unit, onAusweich: (String?) -> Unit,
) {
    val auf = offen["taktik"] == true
    // 1 = Signalwert, 2 = Ausweichwert
    var wahl by remember { mutableStateOf(0) }
    val signalName = signal?.let { wertName(it) } ?: taktikText("tk.gleich")
    val ausweichName = ausweich?.let { wertName(it) } ?: taktikText("tk.bargeld")
    EinklappKopf(taktikText("tk.titel"), taktikText("tk.kurz", signalName, ausweichName), auf) { offen["taktik"] = !auf }
    if (auf) {
        NavRow(taktikText("tk.signal"), signalName) { wahl = 1 }
        NavRow(taktikText("tk.ausweich"), ausweichName) { wahl = 2 }
        HinweisInfo(taktikText("tk.info"))
    }
    when (wahl) {
        1 -> WertWahlBlatt(
            state, taktikText("tk.signal"), SIGNAL_VORSCHLAEGE, setOf(symbol), taktikText("tk.gleich"), alleArten = true,
            onWahl = { onSignal(it); wahl = 0 }, onDismiss = { wahl = 0 },
        )
        2 -> WertWahlBlatt(
            state, taktikText("tk.ausweich"), remember { Taktik.ausweichVorschlaege(Catalog.get().etfs) }, setOf(symbol), taktikText("tk.bargeld"), alleArten = false,
            onWahl = { onAusweich(it); wahl = 0 }, onDismiss = { wahl = 0 },
        )
    }
}

// Kopf einer eingeklappten Einstellung: Bezeichnung, darunter die Zusammenfassung, rechts der Pfeil.
@Composable
internal fun EinklappKopf(titel: String, kurz: String, auf: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (auf) S.less else S.more, onClick = onClick)
            .padding(horizontal = 20.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            if (kurz.isNotBlank()) Text(kurz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        AufklappPfeil(auf)
    }
}
