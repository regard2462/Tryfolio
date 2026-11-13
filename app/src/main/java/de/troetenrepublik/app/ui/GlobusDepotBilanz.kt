package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.market.Symbols
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.DepotStreuung
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.Taetigkeit
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

// [Globus Depot 0.70] Bereich unter dem Globus der Ebene Mein Depot: Schalter fuer ETFs, Zuordnung der Aktien zu Laendern
// und die Bilanz der Streuung. [0.73] Das Land je Aktie laesst sich selbst festlegen (wahl: Symbol zu Laenderkuerzel).
@Composable
internal fun DepotStreuungBereich(
    etfEin: Boolean, onEtf: (Boolean) -> Unit, bilanz: DepotStreuung.Bilanz?, unzuordenbar: Double,
    herkunft: Map<String, Taetigkeit.Herkunft>, positionen: List<ValuedPosition>, karte: GlobusKarte?,
    wahl: Map<String, String>, onLand: (String, String?) -> Unit,
) {
    val deutsch = I18n.locale.language == "de"
    fun name(a2: String?) = a2?.let { karte?.nachA2(it)?.name(deutsch) ?: it }.orEmpty()
    var alle by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var auswahl by remember { mutableStateOf<ValuedPosition?>(null) }
    Column(Modifier.fillMaxWidth()) {
        ToggleRow(S.globusDepotEtfs, etfEin, S.globusDepotEtfsInfo, onEtf)
        val aktien = positionen.filter { Symbols.typeOf(it.position.symbol) == AssetType.STOCK }.distinctBy { it.position.symbol }.sortedByDescending { it.value }
        // Zuerst Werte, deren Sitz vom Land der Zaehlung abweicht oder die selbst festgelegt sind; alle Aktien auf Wunsch
        val auffaellig = aktien.filter { herkunft[it.position.symbol]?.let { h -> h.abweichend || h.weltweit } == true || it.position.symbol in wahl }
        val sichtbar = if (alle) aktien else auffaellig
        if (aktien.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.globusHerkunftTitel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            for (p in sichtbar) {
                val h = herkunft[p.position.symbol]
                val gewaehlt = p.position.symbol in wahl
                Column(Modifier.fillMaxWidth().clickable { auswahl = p }.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(p.position.name, style = MaterialTheme.typography.bodyMedium)
                    val boerse = h?.boerse?.takeIf { it != h.sitz && it != h.taetig }
                    val zeile = when {
                        h?.land == null -> S.globusLandUnbekannt
                        h.weltweit && !gewaehlt -> S.globusWeltweit(name(h.land))
                        h.abweichend -> S.globusHerkunft(name(h.sitz), name(h.taetig), boerse?.let { name(it) })
                        else -> name(h.land)
                    } + if (gewaehlt) ", " + S.globusLandGewaehlt else ""
                    Text(zeile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (aktien.size > auffaellig.size) Text(
                if (alle) S.globusWenigerAktien else S.globusAlleAktien,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { alle = !alle }.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (bilanz != null) Bilanz(bilanz, unzuordenbar, ::name)
    }
    if (info) MarktInfoBlatt(S.globusHerkunftTitel, S.globusHerkunftInfo) { info = false }
    auswahl?.let { p ->
        LandWahl(p.position.name, karte, wahl[p.position.symbol]) { a2, geaendert ->
            auswahl = null
            if (geaendert) onLand(p.position.symbol, a2)
        }
    }
}

// Liste der Laender mit Suchfeld; oben "Automatisch zuordnen". onWahl: Kuerzel (null = automatisch) und ob etwas gewaehlt wurde.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandWahl(titel: String, karte: GlobusKarte?, aktuell: String?, onWahl: (String?, Boolean) -> Unit) {
    val deutsch = I18n.locale.language == "de"
    var suche by remember { mutableStateOf("") }
    fun schluessel(t: String) = t.lowercase().replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss").replace("é", "e").replace("ç", "c")
    val alle = remember(karte, deutsch) { karte?.laender.orEmpty().filter { it.a2.isNotEmpty() }.sortedBy { schluessel(it.name(deutsch)) } }
    val treffer = remember(alle, suche) {
        val q = schluessel(suche.trim())
        if (q.isEmpty()) alle else alle.filter { schluessel(it.name(deutsch)).contains(q) }
    }
    ModalBottomSheet(
        onDismissRequest = { onWahl(null, false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(S.globusLandFuer(titel), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            OutlinedTextField(
                value = suche, onValueChange = { suche = it }, singleLine = true, label = { Text(S.trendSuche) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                if (aktuell != null && suche.isBlank()) item(key = "automatisch") {
                    Text(
                        S.globusLandAutomatisch, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { onWahl(null, true) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(treffer, key = { it.a3 }) { l ->
                    Text(
                        l.name(deutsch), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (l.a2 == aktuell) FontWeight.SemiBold else null,
                        modifier = Modifier.fillMaxWidth().clickable { onWahl(l.a2, true) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bilanz(b: DepotStreuung.Bilanz, unzuordenbar: Double, name: (String?) -> String) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    Column(Modifier.fillMaxWidth()) {
        Text(S.globusBilanzTitel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 2.dp))
        KeyValueRow(S.globusBilanzLaender, b.laender.toString())
        // Der Name steht in der Bezeichnung, wenn er kurz ist; sonst im Infotext. Der Prozentwert bleibt allein in der Zeile.
        fun mitName(label: String, land: String?): String = name(land).let { n -> if (n.length in 1..14) "$label ($n)" else label }
        KeyValueRow(mitName(S.globusBilanzGroesstes, b.groesstes.first), Fmt.pct(b.groesstes.second * 100, 0), onClick = { info = S.globusBilanzGroesstes to name(b.groesstes.first) })
        KeyValueRow(mitName(S.globusBilanzHeim, b.heim), Fmt.pct(b.heimAnteil * 100, 0), onClick = { info = S.globusBilanzHeim to name(b.heim) })
        KeyValueRow(S.globusBilanzKonz, S.globusBilanzStufe(b.konzentration))
        Text(
            S.globusBilanzEffektiv(Fmt.num(b.effektiv, 1)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
        )
        // Industrie gegen Schwellenlaender: ein Balken, Strich beim Weltanteil
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Balken(b.industrie, b.weltIndustrie, AnteilsFarben.folge(0), Modifier.fillMaxWidth().height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.globusBilanzIndustrie + " " + Fmt.pct(b.industrie * 100, 0), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                Text(S.globusBilanzSchwellen + " " + Fmt.pct((1 - b.industrie) * 100, 0), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
        }
        // Regionen und die groessten Laender als Balken mit Strich fuer den Weltindex
        for ((r, a) in b.regionen.entries.sortedByDescending { it.value }) {
            BalkenZeile(S.globusBilanzRegion(r), a, b.weltRegionen[r] ?: 0.0, AnteilsFarben.region(r))
        }
        Text("", Modifier.height(4.dp))
        for ((l, a, w) in b.top) BalkenZeile(name(l), a, w, AnteilsFarben.region(DepotStreuung.region(l)))
        Text(
            S.globusBilanzWelt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        if (unzuordenbar > 0.005) {
            Text(
                S.globusBilanzZugeordnet(Fmt.pct((1 - unzuordenbar) * 100, 0)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

// Beschriftung links, Balken in der Mitte, Prozent rechts
@Composable
private fun BalkenZeile(titel: String, anteil: Double, welt: Double, farbe: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(148.dp).padding(end = 8.dp))
        Balken(anteil, welt, farbe, Modifier.weight(1f).height(8.dp))
        Text(
            Fmt.pct(anteil * 100, 0), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
            textAlign = TextAlign.End, modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun Balken(anteil: Double, welt: Double, fuell: Color, modifier: Modifier) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHigh
    val strich = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(bahn, Offset.Zero, size, r)
        val w = (size.width * anteil.toFloat().coerceIn(0f, 1f))
        if (w > 0f) drawRoundRect(fuell, Offset.Zero, Size(w, size.height), r)
        if (welt > 0.0) {
            val x = size.width * welt.toFloat().coerceIn(0f, 1f)
            drawLine(strich, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
        }
    }
}
