package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.launch

private const val CC_LIZENZ = "https://creativecommons.org/licenses/by-sa/4.0/"

// Anfang der Adresse bis zum Ende des Hosts, fuer die Quellenzeile der Boersen mit eigenem Abruf
private fun herkunft(adresse: String): String {
    val i = adresse.indexOf("://")
    if (i < 0) return adresse
    val j = adresse.indexOf('/', i + 3)
    return (if (j < 0) adresse else adresse.substring(0, j)) + "/"
}

// Abschnitte Unternehmen (Treemap, Liste, Branchen) und Wikipedia im Laenderblatt. Die Landesdaten stehen im Abschnitt Land.
@Composable
internal fun LaenderUnternehmen(state: AppState, land: GlobusLand, onOpen: (String) -> Unit) {
    val a2 = land.a2
    if (a2.isEmpty()) return
    // [0.78] Der gemerkte Stand steht sofort da: Kommt das Blatt nach einer Aktienseite wieder, hat der Abschnitt gleich seine
    // volle Hoehe und die Seite bleibt an ihrer Stelle
    var wiki by remember(land) { mutableStateOf(state.globusWikiGemerkt(a2)) }
    var firmen by remember(land) { mutableStateOf(state.globusFirmenGemerkt(a2)) }
    var firmenGeladen by remember(land) { mutableStateOf(firmen != null) }
    // Zaehler: nach einer uebernommenen Seite (Selbstabruf) werden die Firmen neu gelesen
    var neuLaden by remember(land) { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(land) {
        ohneAbbruch { state.globusWiki(a2) }.getOrNull()?.let { wiki = it }
    }
    androidx.compose.runtime.LaunchedEffect(land, neuLaden) {
        val neu = ohneAbbruch { state.globusFirmen(a2) }
        if (neu.isSuccess || firmen == null) firmen = neu.getOrNull()
        firmenGeladen = true
    }
    FirmenBlock(state, land, firmen, firmenGeladen, onOpen, onSelbst = { neuLaden++ })
    WikiBlock(wiki)
}

// Auszug aus dem Wikipedia-Artikel zur Wirtschaft, aufklappbar; dazu Weiterlesen und der Laenderartikel. Wikipedia verlangt die Lizenzangabe.
@Composable
private fun WikiBlock(w: AppState.GlobusWiki?) {
    if (w == null) return
    var artikel by remember(w) { mutableStateOf<Pair<String, String>?>(null) }
    val mitText = w.text.isNotBlank() && w.artikel.isNotBlank()
    val mitLand = w.landArtikel.isNotBlank() && w.landTitel.isNotBlank()
    if (!mitText && !mitLand) return
    // [0.78] Ohne Wirtschaftsartikel und ohne Abschnitt Wirtschaft steht nur der Verweis auf den Laenderartikel
    LaenderAbschnitt(S.globusWikiTitel, "wiki", startOffen = false) {
        if (mitText) {
            LbAbsatz(w.text)
            Text(
                S.globusWeiterlesen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { artikel = w.titel to w.artikel }.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (mitLand) Text(
            S.globusLaenderartikel(w.landTitel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { artikel = w.landTitel to w.landArtikel }.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (mitText) QuellenLeiste(listOf(LaenderQuellen.wikipedia(w.artikel), "CC BY-SA 4.0" to CC_LIZENZ))
    }
    artikel?.let { (titel, adresse) ->
        NewsSheet(
            MarketApi.NewsItem(title = titel, link = adresse, published = 0L, source = "Wikipedia"),
            onDismiss = { artikel = null }, actionsTop = true, wikipedia = true,
        )
    }
}

// Groesste Unternehmen: Treemap der gelisteten (Flaeche Boersenwert, Farbe Tagesaenderung), darunter die Liste und die Branchen
// als Ring; die nicht gelisteten und weiteren Firmen sind aufklappbar.
@Composable
private fun FirmenBlock(state: AppState, land: GlobusLand, f: AppState.GlobusFirmen?, geladen: Boolean, onOpen: (String) -> Unit, onSelbst: () -> Unit) {
    val a2 = land.a2
    val alle = remember(a2) { state.globusPrivateFirmen(a2) }
    val privat = remember(alle) { alle.filter { it.geprueft } }
    val weitere = remember(alle) { alle.filterNot { it.geprueft } }
    val hat = remember(a2) { state.globusHatFirmen(a2) }
    val zeilen = f?.zeilen.orEmpty()
    if (zeilen.isEmpty() && privat.isEmpty() && weitere.isEmpty() && (geladen || !hat) && de.troetenrepublik.app.engine.Boersenlinks.fuer(a2) == null) return
    var info by remember(a2) { mutableStateOf<Pair<String, String>?>(null) }
    var firma by remember(a2) { mutableStateOf<FirmaDaten?>(null) }
    var mehr by remember(a2) { mutableStateOf(false) }
    // [0.73] Firma ohne Aktienseite: Blatt mit den Angaben; russische Firmen zeigen Kurs und Verlauf der Moskauer Boerse.
    // Mit Yahoo-Kuerzel bleibt der Tipp auf der Aktienseite.
    fun firmaBlatt(name: String, branche: String, boerse: String, staat: Boolean, wert: Double?, jahr: Int?, kontext: String) {
        firma = FirmaDaten(name, branche, boerse, staat, wert, jahr, land, kontext)
    }
    LaenderAbschnitt(S.globusUnternehmenTitel, "firmen") {
        if (zeilen.isNotEmpty() || (!geladen && hat)) {
            val t = S.globusBoersennotiert
            Text(
                t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { info = t to S.globusInfoGelistet }.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (zeilen.isEmpty()) Text(S.loading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            val mitWert = zeilen.filter { (it.boersenwert ?: 0.0) > 0.0 }
            if (mitWert.size >= 3 && f?.ohneWerte != true) {
                // Jede Firma einmal (LaenderFirmen.rangliste); die Kachel nennt den vollen Name oder das Kuerzel, nie einen Teil davon
                val oeffnen by androidx.compose.runtime.rememberUpdatedState(onOpen)
                val kacheln = remember(mitWert) {
                    mitWert.map { z ->
                        val k = z.kandidat
                        FirmaKachel(HeatmapBeschriftung.firmenKuerzel(k.symbol, k.kennung), k.name, z.boersenwert ?: 0.0, z.aenderung, z.kgv) {
                            val ziel = k.symbol.ifBlank { de.troetenrepublik.app.market.KursQuellen.symbolFuerLand(a2, k.kennung).orEmpty() }
                            if (ziel.isNotBlank()) oeffnen(ziel)
                            else firmaBlatt(k.name, k.branche, k.boerse, k.staat, z.boersenwert, z.jahr, S.globusFirmaKeinVerlauf)
                        }
                    }
                }
                FirmenTreemap(kacheln)
                LbHinweis(if (mitWert.any { HeatmapBeschriftung.kgvWert(it.kgv) != null }) S.lbTreemapHinweisKgv else S.lbTreemapHinweis)
            }
            val sichtbar = if (mehr) zeilen else zeilen.take(5)
            // Das gemeinsame Jahr der Boersenwerte steht einmal darueber, an der Zeile nur ein abweichendes
            val hauptJahr = remember(zeilen) { zeilen.filter { it.boersenwert != null }.mapNotNull { it.jahr }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key }
            if (hauptJahr != null) LbHinweis(S.globusBoersenwertJahr(hauptJahr))
            for (z in sichtbar) {
                val k = z.kandidat
                FirmaZeile(k.name, k.branche, k.staat, z.boersenwert, z.aenderung, {
                    val ziel = k.symbol.ifBlank { de.troetenrepublik.app.market.KursQuellen.symbolFuerLand(a2, k.kennung).orEmpty() }
                    if (ziel.isNotBlank()) onOpen(ziel)
                    else firmaBlatt(k.name, k.branche, k.boerse, k.staat, z.boersenwert, z.jahr, S.globusFirmaKeinVerlauf)
                }, k.boerse, z.jahr?.takeIf { it != hauptJahr })
            }
            if (zeilen.size > 5) ListenPfeil(mehr) { mehr = !mehr }
            if (f?.ohneWerte == true) LbHinweis(S.globusFirmenOhneWerte)
        }
        // Selbst geladene Seite mit nur einem Gesamtwert
        f?.gesamt?.let { (wert, quelle) -> LbAbsatz(S.globusBoersenwertGesamt(Fmt.compactMoney(wert), quelle)) }
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
        // Branchen: Anteil der groessten Branchen am Boersenwert der erfassten Firmen als Ring
        f?.branchen?.takeIf { it.isNotEmpty() }?.let { b ->
            LaenderAbschnitt(S.globusBranchenTitel, "branchen", tief = true) {
                val rest = 100.0 - b.sumOf { it.anteil * 100 }
                val teile = b.mapIndexed { i, a ->
                    RingTeil(S.industryName(a.branche), a.anteil * 100, Kategorie.farbe(i), Fmt.pct(a.anteil * 100, 1)) {
                        info = S.industryName(a.branche) to S.globusInfoBranchen(f.branchenFirmen)
                    }
                }.toMutableList()
                if (rest >= 2.0) teile += RingTeil(S.lbUebrige, rest, MaterialTheme.colorScheme.outlineVariant, Fmt.pct(rest, 1))
                RingDiagramm(teile)
            }
        }
        if (privat.isNotEmpty()) LaenderAbschnitt(S.globusNichtBoersennotiert, "firmen-privat", startOffen = false, tief = true) {
            for (p in privat) FirmaZeile(p.name, p.branche, p.staat, null, null, { firmaBlatt(p.name, p.branche, "", p.staat, null, null, S.globusInfoNichtGelistet) })
            LbHinweis(S.globusInfoNichtGelistet)
        }
        if (weitere.isNotEmpty()) LaenderAbschnitt(S.globusWeitereFirmen, "firmen-weitere", startOffen = false, tief = true) {
            for (p in weitere) FirmaZeile(p.name, p.branche, false, null, null, { firmaBlatt(p.name, p.branche, "", false, null, null, S.globusInfoWeitere) })
            LbHinweis(S.globusInfoWeitere)
        }
        val quellen = ArrayList<Pair<String, String>>()
        if (f?.yahoo == true) quellen += LaenderQuellen.yahoo()
        if (f?.moex == true) quellen += S.boerseMoskau to "https://www.moex.com/"
        f?.quellen?.forEach { q ->
            val bf = de.troetenrepublik.app.engine.Boersenfeeds
            val url = bf.quellenFuer(a2).firstOrNull { it.name == q }?.adresse ?: when (q) {
                bf.DHAKA_NAME -> "https://www.dse.com.bd/"
                bf.CHITTAGONG_NAME -> "https://www.cse.com.bd/"
                else -> de.troetenrepublik.app.engine.Boersenlinks.fuer(a2)?.adresse
            }
            if (url != null) quellen += q to herkunft(url)
        }
        if (f?.wikidata == true) quellen += LaenderQuellen.wikipedia()
        if (f?.wikidata == true || privat.isNotEmpty() || weitere.isNotEmpty() || zeilen.isNotEmpty()) quellen += LaenderQuellen.wikidata()
        QuellenLeiste(quellen.distinctBy { it.first })
    }
    info?.let { (t, x) -> MarktInfoBlatt(t, x) { info = null } }
    firma?.let { FirmaBlatt(state, it) { firma = null } }
}

// Eine Firma: Name, darunter Boerse, Branche und Staatsfirma; rechts Boersenwert und Tagesaenderung auf Hoehe der ersten
// Zeile. Name und Zusatz stehen ganz da und brechen bei Platzmangel um. Ohne onClick nicht antippbar.
@Composable
private fun FirmaZeile(name: String, branche: String, staat: Boolean, wert: Double?, aenderung: Double?, onClick: (() -> Unit)?, boerse: String = "", jahr: Int? = null) {
    val signal = LocalSignal.current
    val zusatz = listOfNotNull(boerse.takeIf { it.isNotBlank() }, branche.takeIf { it.isNotBlank() }?.let { S.industryName(it) }, S.globusStaatsfirma.takeIf { staat }).joinToString(", ")
    // Name und Zusatz stehen ganz da und brechen bei Bedarf um; der Wert bleibt rechts auf Hoehe der ersten Zeile
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (zusatz.isNotEmpty()) Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (wert != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.compactMoney(wert) + (jahr?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                if (aenderung != null) Text(
                    Fmt.signedPct(aenderung), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                    color = if (aenderung >= 0) signal.up else signal.down,
                )
            }
        }
    }
}

// Seite einer Boerse im Browser der App (Selbstabruf): Die Person bedient die Seite selbst, auch eine Pruefung der Seite, und tippt dann
// "Seite uebernehmen"; die App liest das geladene HTML. Nichts wird automatisch geloest oder im Hintergrund geladen.
@Composable
internal fun SelbstSeite(l: de.troetenrepublik.app.engine.Boersenlinks.Link, onZu: () -> Unit, onSeite: (String) -> Unit) {
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
