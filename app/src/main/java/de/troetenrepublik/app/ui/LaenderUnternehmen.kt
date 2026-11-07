package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.LaenderFirmen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.launch

private const val CC_LIZENZ = "https://creativecommons.org/licenses/by-sa/4.0/"

// Abschnitt Land, Unternehmen und Wikipedia im Laenderblatt: Landesdaten, Wirtschaftsartikel der Wikipedia, die groessten
// Firmen (gelistet und nicht gelistet) und die Branchen an der Boerse.
@Composable
internal fun LaenderUnternehmen(state: AppState, land: GlobusLand, onOpen: (String) -> Unit) {
    val a2 = land.a2
    if (a2.isEmpty()) return
    var wiki by remember(land) { mutableStateOf<AppState.GlobusWiki?>(null) }
    var firmen by remember(land) { mutableStateOf<AppState.GlobusFirmen?>(null) }
    var firmenGeladen by remember(land) { mutableStateOf(false) }
    // Zaehler: nach einer uebernommenen Seite (Selbstabruf) werden die Firmen neu gelesen
    var neuLaden by remember(land) { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(land) {
        wiki = ohneAbbruch { state.globusWiki(a2) }.getOrNull()
    }
    androidx.compose.runtime.LaunchedEffect(land, neuLaden) {
        firmen = ohneAbbruch { state.globusFirmen(a2) }.getOrNull()
        firmenGeladen = true
    }
    LandBlock(state, a2)
    WikiBlock(wiki)
    FirmenBlock(state, a2, firmen, firmenGeladen, onOpen, onSelbst = { neuLaden++ })
    BranchenBlock(firmen)
}

@Composable
private fun Zwischentitel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp))
}

// Landesdaten: Hauptstadt, Waehrung, Staatsoberhaupt, Regierungschef, Regierungsform, Mitgliedschaften.
@Composable
private fun LandBlock(state: AppState, a2: String) {
    val (stand, d) = remember(a2, S) { state.globusLandesdaten(a2) } ?: return
    var info by remember(a2) { mutableStateOf<Pair<String, String>?>(null) }
    val zeilen = listOfNotNull(
        d.hauptstadt?.let { Triple(S.globusHauptstadt, it, null as Pair<String, String>?) },
        d.waehrung?.let { Triple(S.globusWaehrung, it, null) },
    )
    val gleich = d.oberhaupt != null && d.oberhaupt == d.regierungschef
    if (zeilen.isEmpty() && d.oberhaupt == null && d.regierungschef == null && d.form == null && d.mitglieder.isEmpty()) return
    Zwischentitel(S.globusLandTitel)
    for ((k, w, _) in zeilen) KeyValueRow(k, w)
    if (gleich) {
        val t = S.globusOberhauptChef
        KeyValueRow(t, d.oberhaupt!!, onClick = { info = t to S.globusInfoOberhauptChef })
    } else {
        d.oberhaupt?.let { w -> val t = S.globusOberhaupt; KeyValueRow(t, w, onClick = { info = t to S.globusInfoOberhaupt }) }
        d.regierungschef?.let { w -> val t = S.globusRegierungschef; KeyValueRow(t, w, onClick = { info = t to S.globusInfoRegierungschef }) }
    }
    d.form?.let { f -> val t = S.globusRegierungsform; KeyValueRow(t, S.globusFormName(f, d.bundesstaat), onClick = { info = t to S.globusInfoRegierungsform }) }
    if (d.mitglieder.isNotEmpty()) {
        val t = S.globusMitgliedschaften
        KeyValueRow(t, d.mitglieder.joinToString(", ") { S.globusOrgName(it) }, onClick = { info = t to S.globusInfoMitgliedschaften })
    }
    Text(
        S.globusLandQuelle(stand), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    info?.let { (t, x) -> MarktInfoBlatt(t, x) { info = null } }
}

// Auszug aus dem Wikipedia-Artikel zur Wirtschaft, dazu Weiterlesen, Laenderartikel und die Pflichtangaben der Lizenz.
@Composable
private fun WikiBlock(w: AppState.GlobusWiki?) {
    if (w == null) return
    var artikel by remember(w) { mutableStateOf<Pair<String, String>?>(null) }
    val uri = externeLinks()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Zwischentitel(S.globusWikiTitel)
    Text(w.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    Text(
        S.globusWeiterlesen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable { artikel = w.titel to w.artikel }.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (w.landArtikel.isNotBlank() && w.landTitel.isNotBlank()) Text(
        S.globusLaenderartikel(w.landTitel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable { artikel = w.landTitel to w.landArtikel }.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Text(
        S.globusWikiQuelle(w.titel), style = MaterialTheme.typography.bodySmall, color = grau,
        modifier = Modifier.fillMaxWidth().clickable { artikel = w.titel to w.artikel }.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    Text(
        S.globusWikiLizenz, style = MaterialTheme.typography.bodySmall, color = grau,
        modifier = Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(CC_LIZENZ) } }.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    artikel?.let { (titel, adresse) ->
        NewsSheet(
            MarketApi.NewsItem(title = titel, link = adresse, published = 0L, source = "Wikipedia"),
            onDismiss = { artikel = null }, actionsTop = true, wikipedia = true,
        )
    }
}

// Groesste Unternehmen: gelistete nach Boersenwert (tippen oeffnet das Wertpapier), danach die nicht gelisteten.
@Composable
private fun FirmenBlock(state: AppState, a2: String, f: AppState.GlobusFirmen?, geladen: Boolean, onOpen: (String) -> Unit, onSelbst: () -> Unit) {
    val alle = remember(a2) { state.globusPrivateFirmen(a2) }
    val privat = remember(alle) { alle.filter { it.geprueft } }
    val weitere = remember(alle) { alle.filterNot { it.geprueft } }
    val hat = remember(a2) { state.globusHatFirmen(a2) }
    val zeilen = f?.zeilen.orEmpty()
    if (zeilen.isEmpty() && privat.isEmpty() && weitere.isEmpty() && (geladen || !hat) && de.troetenrepublik.app.engine.Boersenlinks.fuer(a2) == null) return
    var info by remember(a2) { mutableStateOf<Pair<String, String>?>(null) }
    Zwischentitel(S.globusUnternehmenTitel)
    if (zeilen.isNotEmpty() || (!geladen && hat)) {
        val t = S.globusBoersennotiert
        Text(
            t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { info = t to S.globusInfoGelistet }.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        if (zeilen.isEmpty()) Text(S.loading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        for (z in zeilen) FirmaZeile(z.kandidat.name, z.kandidat.branche, z.kandidat.staat, z.boersenwert, z.aenderung, if (z.kandidat.symbol.isBlank()) null else ({ onOpen(z.kandidat.symbol) }), z.kandidat.boerse, z.jahr)
        if (f?.ohneWerte == true) Text(S.globusFirmenOhneWerte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(S.globusFirmenQuelle(f?.yahoo == true, f?.moex == true, f?.wikidata == true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        f?.quellen?.takeIf { it.isNotEmpty() }?.let { q ->
            Text(S.globusQuelleBoersen(q.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
    }
    // Selbst geladene Seite mit nur einem Gesamtwert
    f?.gesamt?.let { (wert, quelle) ->
        Text(S.globusBoersenwertGesamt(Fmt.compactEuro(wert), quelle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), maxLines = 2)
    }
    // Boersen ohne eigenen Abruf: die Kurs- oder Marktseite fuer Menschen im eingebauten Browser, wie die Zeile des Auswaertigen Amts
    // Mit Selbstabruf (nur wo die App einen eigenen Browser hat) oeffnet die Seite im Browser der App, die Person uebernimmt sie danach.
    de.troetenrepublik.app.engine.Boersenlinks.fuer(a2)?.let { l ->
        val uri = appLinks()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var offen by remember(a2) { mutableStateOf(false) }
        val imBrowser = l.selbst && de.troetenrepublik.app.plattform.quellenImBrowser()
        Text(
            S.globusBoersenKurse(l.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { if (imBrowser) offen = true else runCatching { uri.openUri(l.adresse) } }.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        if (offen) SelbstSeite(
            l, onZu = { offen = false },
            onSeite = { html ->
                offen = false
                scope.launch {
                    val n = state.globusSelbstUebernehmen(a2, html)
                    state.showToast(if (n < 0) S.globusSelbstLeer else S.globusSelbstFertig(n))
                    if (n >= 0) onSelbst()
                }
            },
        )
    }
    if (privat.isNotEmpty()) {
        val t = S.globusNichtBoersennotiert
        Text(
            t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { info = t to S.globusInfoNichtGelistet }.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
        )
        for (p in privat) FirmaZeile(p.name, p.branche, p.staat, null, null, null)
        Text(S.globusPrivatQuelle(state.globusPrivatStand()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    if (weitere.isNotEmpty()) {
        val t = S.globusWeitereFirmen
        Text(
            t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { info = t to S.globusInfoWeitere }.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
        )
        for (p in weitere) FirmaZeile(p.name, p.branche, false, null, null, null)
        Text(S.globusWeitereQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    info?.let { (t, x) -> MarktInfoBlatt(t, x) { info = null } }
}

// Eine Firma: Name, darunter Branche und Staatsfirma; rechts Boersenwert und Tagesaenderung. Ohne onClick nicht antippbar.
@Composable
private fun FirmaZeile(name: String, branche: String, staat: Boolean, wert: Double?, aenderung: Double?, onClick: (() -> Unit)?, boerse: String = "", jahr: Int? = null) {
    val signal = LocalSignal.current
    val zusatz = listOfNotNull(boerse.takeIf { it.isNotBlank() }, branche.takeIf { it.isNotBlank() }?.let { S.industryName(it) }, S.globusStaatsfirma.takeIf { staat }).joinToString(", ")
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (zusatz.isNotEmpty()) Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (wert != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.compactEuro(wert) + (jahr?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                if (aenderung != null) Text(
                    Fmt.signedPct(aenderung), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                    color = if (aenderung >= 0) signal.up else signal.down,
                )
            }
        }
    }
}

// Anteil der fuenf groessten Branchen am Boersenwert der erfassten gelisteten Firmen.
@Composable
private fun BranchenBlock(f: AppState.GlobusFirmen?) {
    val b = f?.branchen?.takeIf { it.isNotEmpty() } ?: return
    var info by remember(f) { mutableStateOf<String?>(null) }
    Zwischentitel(S.globusBranchenTitel)
    for (a in b) KeyValueRow(S.industryName(a.branche), Fmt.pct(a.anteil * 100, 1), onClick = { info = S.industryName(a.branche) })
    Text(
        S.globusBranchenQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    info?.let { t -> MarktInfoBlatt(t, S.globusInfoBranchen(f.branchenFirmen)) { info = null } }
}

// Seite einer Boerse im Browser der App (Selbstabruf): Die Person bedient die Seite selbst, auch eine Pruefung der Seite, und tippt dann
// "Seite uebernehmen"; die App liest das geladene HTML. Nichts wird automatisch geloest oder im Hintergrund geladen.
@Composable
private fun SelbstSeite(l: de.troetenrepublik.app.engine.Boersenlinks.Link, onZu: () -> Unit, onSeite: (String) -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onZu,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(l.name, null, onBack = onZu)
                Text(S.globusSelbstHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                de.troetenrepublik.app.plattform.QuellenBrowser(
                    l.adresse, listOf("html"), "", Modifier.weight(1f).fillMaxWidth(),
                    onDatei = { _, daten -> onSeite(daten.toString(Charsets.UTF_8)) },
                    onFehler = onZu,
                    onSchliessen = onZu,
                )
            }
        }
    }
}
