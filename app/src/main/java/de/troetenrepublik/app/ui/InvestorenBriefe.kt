package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Investorenbriefe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Was Investoren schreiben (5.92): Auszuege aus Briefen von Fondsmanagern zu einer Aktie mit Richtung und Einordnung
// des Eigeninteresses (Position laut 13F, Grossaktionaersmeldung oder eigener Angabe, passt die Aussage dazu, Art des
// Absenders, Werbung). Oben eine Zusammenfassung. Ohne Fundstelle erscheint bei US-Aktien nur der Link zur Suche auf
// hfbestideas.com und der Hinweis auf das SEC-Feld, sonst nichts. Antippen von ‚Mehr aus dem Brief‘ zeigt die
// Textstelle aus dem gespeicherten Brief.

private fun quartalAus(stichtag: String): String =
    runCatching { java.time.LocalDate.parse(stichtag.take(10)) }.getOrNull()?.let { "Q${(it.monthValue - 1) / 3 + 1} ${it.year}" } ?: stichtag

private fun f13(w: Investoren.Wandel?): Investorenbriefe.F13 = when (w) {
    Investoren.Wandel.NEU -> Investorenbriefe.F13.NEU
    Investoren.Wandel.ERHOEHT -> Investorenbriefe.F13.ERHOEHT
    Investoren.Wandel.VERRINGERT -> Investorenbriefe.F13.VERRINGERT
    Investoren.Wandel.VERKAUFT -> Investorenbriefe.F13.VERKAUFT
    else -> Investorenbriefe.F13.HAELT
}

// Domain fuer die Quellenzeile: „givernycapital.com“.
private fun domain(link: String): String =
    Regex("^https?://(?:www\\.|info\\.)?([^/:?#]+)").find(link)?.groupValues?.get(1) ?: link

// So viele Berichte zeigt die Tabelle, der Rest steht hinter dem Listen-Pfeil.
private const val ANZAHL_ZEILEN = 8

private class Zeile(val f: Investorenbriefe.Fundstelle, val e: Investorenbriefe.Einordnung, val stand13f: String)

// Lader der Karte: holt die Briefe, sucht die Fundstellen und rechnet die Zeilen einmal (alles im Merker der Seite) und gibt
// sie als Modell an die LazyColumn der Seite (investorenBriefeAbschnitt): je Brief ein Eintrag. Zeichnet selbst nichts;
// Info und Textstelle stehen in InvestorenBriefeDialoge.
@Composable
internal fun InvestorenBriefeKarte(state: AppState, symbol: String, name: String, usWert: Boolean, onOpenRoute: (String) -> Unit, merker: SeitenMerker) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val secFormen by state.secFormen.collectAsStateWithLifecycle()
    val uri = appLinks()
    val mitSec = usWert && SecInsider.kennung(settings.secKontakt) != null
    var eigene by gemerkt<List<Investorenbriefe.Fundstelle>?>(merker, "briefe/eigene") { null }
    var briefe by gemerkt<List<Investorenbriefe.Brief>>(merker, "briefe/briefe") { emptyList() }
    var sec by gemerkt<List<Investorenbriefe.Fundstelle>>(merker, "briefe/sec") { emptyList() }
    // Fuer welchen Namen die Fundstellen gesucht sind: Beim Zurueckscrollen wird nichts neu gesucht
    var eigeneFuer by gemerkt<String?>(merker, "briefe/eigeneFuer") { null }
    var secFuer by gemerkt<String?>(merker, "briefe/secFuer") { null }
    val alleZustand = merker.zustand("briefe/alle") { false }
    val wahlZustand = merker.zustand<Int?>("briefe/wahl") { null }
    val geoeffnet = merker.zustand("briefe/geoeffnet") { emptySet<String>() }
    val info = merker.zustand("briefe/info") { false }
    val offen = merker.zustand<Zeile?>("briefe/offen") { null }

    LaunchedEffect(symbol, name) {
        if (eigeneFuer == name && eigene != null) return@LaunchedEffect
        val suche = Investorenbriefe.Suche(name, symbol)
        suspend fun suchen(briefe: List<Investorenbriefe.Brief>) = withContext(Dispatchers.Default) {
            briefe.mapNotNull { b -> runCatching { Investorenbriefe.fundstelle(b, suche, Investorenbriefe.absender(b.absender)?.name ?: b.absender) }.getOrNull() }
        }
        // Erst der Stand auf dem Geraet, dann faellige Briefe nachholen
        val vorhanden = state.investorenbriefe(netz = false)
        if (vorhanden.isNotEmpty()) {
            briefe = vorhanden
            eigene = suchen(vorhanden)
        }
        val geladen = state.investorenbriefe()
        briefe = geladen
        eigene = suchen(geladen)
        eigeneFuer = name
    }
    LaunchedEffect(symbol, name, mitSec) {
        if (!mitSec) return@LaunchedEffect
        val schluessel = "$name|$mitSec"
        if (secFuer == schluessel) return@LaunchedEffect
        sec = state.investorenbriefeSec(symbol, name)
        secFuer = schluessel
    }

    LaunchedEffect(symbol) { runCatching { state.splitsHolen(listOf(symbol)) } }
    val splitStand = de.troetenrepublik.app.engine.SplitFaktoren.stand.collectAsState().value
    val zeilen = merker.gerechnet("briefe/zeilen", listOf(eigene, sec, secFormen[symbol], splitStand)) {
        val heute = java.time.LocalDate.now()
        val passt = HandelndeLader.passt(symbol)
        val halter = secFormen[symbol]?.halter.orEmpty()
        val basis = symbol.uppercase()
        Investorenbriefe.aktuelle(eigene.orEmpty() + sec, heute).map { f ->
            val a = Investorenbriefe.absender(f.absender)
            val inv = a?.investor?.let { id -> state.investoren.firstOrNull { it.id == id } }
            val eintrag = inv?.let { Handelnde.investoren(listOf(it), passt).firstOrNull() }
            val f13 = when {
                inv == null -> null
                eintrag == null -> Investorenbriefe.F13.KEINE
                else -> f13(eintrag.wandel)
            }
            // Grossaktionaer: der erste Teil des Absendernamens steht in einer Meldung nach 13D/13G
            val kern = (a?.name ?: f.name).substringBefore(" (").substringBefore(",").split(' ').first().lowercase()
            val schedule13 = kern.length >= 4 && halter.any { it.name.lowercase().contains(kern) }
            val firma = a != null && (basis in a.kuerzel || basis.substringBefore('.') in a.kuerzel)
            Zeile(f, Investorenbriefe.einordnung(f, a?.art ?: Investorenbriefe.Art.FONDSGESELLSCHAFT, f13, schedule13, firma), quartalAus(inv?.aktuell?.stichtag.orEmpty()))
        }
    }

    if (eigene == null || (zeilen.isEmpty() && !usWert)) {
        ModellMelden(merker, "briefe", null)
        return
    }
    val alle = alleZustand.value
    val wahl = wahlZustand.value
    val secOhneKennung = SecInsider.kennung(settings.secKontakt) == null
    val modell = remember(zeilen, alle, wahl, usWert, secOhneKennung, symbol) {
        briefeModell(merker, zeilen, alle, wahl, usWert, secOhneKennung, symbol, uri, onOpenRoute, alleZustand, wahlZustand, geoeffnet, info, offen)
    }
    ModellMelden(merker, "briefe", modell)
}

// Die Briefe als Eintraege der LazyColumn: Kopf, Uebersicht, je Brief ein Eintrag, Pfeil und Fuss.
internal fun LazyListScope.investorenBriefeAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "briefe")
}

private fun briefeModell(
    merker: SeitenMerker, zeilen: List<Zeile>, alle: Boolean, wahl: Int?, usWert: Boolean, secOhneKennung: Boolean, symbol: String,
    uri: androidx.compose.ui.platform.UriHandler, onOpenRoute: (String) -> Unit,
    alleZustand: MutableState<Boolean>, wahlZustand: MutableState<Int?>, geoeffnet: MutableState<Set<String>>, info: MutableState<Boolean>,
    offen: MutableState<Zeile?>,
): ListenModell {
    val rows = ArrayList<ZeilenDaten>()
    if (zeilen.isEmpty()) {
        rows.add(ZeilenDaten("leer") { Text(S.ibLeer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
    } else {
        val richtungen = Investorenbriefe.richtungen(zeilen.map { it.f })
        rows.add(ZeilenDaten("uebersicht") {
            Column {
                RichtungsUebersicht(richtungen, wahlZustand.value) { r -> wahlZustand.value = if (wahlZustand.value == r) null else r; alleZustand.value = false }
                Spacer(Modifier.height(4.dp))
            }
        })
        val sicht = Investorenbriefe.auswahl(zeilen, wahl) { it.f.richtung }
        val deutsch = de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE
        val benutzt = HashSet<String>()
        (if (alle) sicht else sicht.take(ANZAHL_ZEILEN)).forEachIndexed { i, zl ->
            val f = zl.f
            val schluessel = f.absender + "|" + f.name + "|" + f.link
            // Gleiche Briefe (selten) bekommen eine eigene Kennung, damit die Eintraege der Liste eindeutig bleiben
            val kennung = if (benutzt.add(schluessel)) "b-$schluessel" else "b-$schluessel#$i"
            rows.add(ZeilenDaten(kennung) { BriefZeile(zl, i > 0, schluessel in geoeffnet.value, deutsch, uri, { geoeffnet.value = if (schluessel in geoeffnet.value) geoeffnet.value - schluessel else geoeffnet.value + schluessel }) { offen.value = zl } })
        }
        if (sicht.size > ANZAHL_ZEILEN) rows.add(ZeilenDaten("mehr") { ListenPfeil(alleZustand.value) { alleZustand.value = !alleZustand.value } })
    }
    // Suche auf einer Sammelseite nur als Link, dazu der Hinweis auf das SEC-Feld
    rows.add(ZeilenDaten("fuss") {
        Column {
            if (usWert) {
                QuellenZeile(S.ibSuchen) { runCatching { uri.openUri("https://www.hfbestideas.com/stock/" + symbol.uppercase().replace('-', '.')) } }
                if (secOhneKennung) {
                    Text(
                        S.ibSecHinweis, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                        modifier = Modifier.textKlick(20.dp) { onOpenRoute("zugaenge") }.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    })
    return ListenModell(
        titel = "", gross = true, kern = null, zeilen = rows, immerOffen = true,
        // Kopf wie SectionHeader, rechts das Info-Symbol
        kopf = {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.ibTitel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { info.value = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        },
    )
}

// Ein Brief: Name, Quartal, Richtung, Pfeil; alles Weitere erst nach dem Aufklappen. Der Zustand liegt im Merker der Seite.
@Composable
private fun BriefZeile(zl: Zeile, mitLinie: Boolean, auf: Boolean, deutsch: Boolean, uri: androidx.compose.ui.platform.UriHandler, umschalten: () -> Unit, mehrAusBrief: () -> Unit) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val f = zl.f
    val e = zl.e
    Column {
        if (mitLinie) Trennlinie()
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = umschalten)
                .heightIn(min = 44.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NamenText(f.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (f.quartal.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(f.quartal, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(10.dp))
            ChangeValue(f.richtung.toDouble(), S.ibRichtung(f.richtung), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(auf)
        }
        if (auf) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp)) {
                (if (f.sec) S.ibSecBericht else e.art?.let { S.ibArt(it.name) })?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = grau) }
                MitBlende(f.gekuerzt, Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text(if (deutsch) "„${f.auszug}“" else "“${f.auszug}”", style = MaterialTheme.typography.bodyMedium)
                }
                // Einordnung: Position, Passung, Werbung
                e.belege.forEach { b ->
                    Text(S.ibBeleg(b.name, zl.stand13f), style = MaterialTheme.typography.bodySmall, color = grau)
                }
                e.passt?.let { p ->
                    Text(S.ibPasst(p.name), style = MaterialTheme.typography.bodySmall, color = if (e.inEigenerSache) signal.warn else grau)
                }
                if (e.werbung) Text(S.ibWerbung, style = MaterialTheme.typography.bodySmall, color = signal.warn)
            }
            // Die ganze Textstelle aus dem gespeicherten Brief, im Blatt
            if (!f.sec) {
                Text(
                    S.ibMehrBrief, style = MaterialTheme.typography.bodySmall, color = signal.link,
                    modifier = Modifier.textKlick(20.dp, onClick = mehrAusBrief).heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 20.dp),
                )
            }
            QuellenZeile(S.ibQuelle(if (f.sec) "SEC" else domain(f.link))) { runCatching { uri.openUri(f.link) } }
        }
    }
}

// Info und Textstelle der Karte, ausserhalb der Liste: Sie bleiben, auch wenn die Eintraege aus dem Bild rollen.
@Composable
internal fun InvestorenBriefeDialoge(merker: SeitenMerker) {
    var info by merker.zustand("briefe/info") { false }
    var offen by merker.zustand<Zeile?>("briefe/offen") { null }
    val briefe = merker.zustand<List<Investorenbriefe.Brief>>("briefe/briefe") { emptyList() }.value
    val uri = appLinks()
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(S.ibTitel) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    StichpunktText(S.ibInfoKurz, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text(S.ok) } },
        )
    }

    // Blatt mit der ganzen Textstelle zur Aktie aus dem gespeicherten Brief; ohne gespeicherten Text bleibt der Auszug
    offen?.let { zl ->
        val text = briefe.firstOrNull { it.absender == zl.f.absender && it.link == zl.f.link }?.text
        val stelle = text?.let { Investorenbriefe.umfeldStueck(it, zl.f.auszug) } ?: Investorenbriefe.Stueck(zl.f.auszug, zl.f.gekuerzt)
        AlertDialog(
            onDismissRequest = { offen = null },
            title = { Text(zl.f.name) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    MitBlende(stelle.gekuerzt, flaeche = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(stelle.text, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(8.dp))
                    QuellenZeile(S.ibQuelle(domain(zl.f.link))) { runCatching { uri.openUri(zl.f.link) } }
                }
            },
            confirmButton = { TextButton(onClick = { offen = null }) { Text(S.ok) } },
        )
    }
}

// Geteilter Balken mit den Anzahlen positiv, neutral, negativ; ein Tipp auf einen Abschnitt waehlt diese Berichte,
// ein zweiter hebt die Auswahl auf. Die Zahl steht direkt am Abschnitt.
@Composable
private fun RichtungsUebersicht(z: Investorenbriefe.Richtungen, wahl: Int?, onWahl: (Int) -> Unit) {
    val signal = LocalSignal.current
    val abschnitte = listOf(1 to signal.up, 0 to signal.neutral, -1 to signal.down)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            abschnitte.filter { (r, _) -> z.von(r) > 0 }.forEach { (r, farbe) ->
                Box(
                    Modifier.weight(z.von(r).toFloat()).fillMaxHeight()
                        .background(farbe.copy(alpha = if (wahl == null || wahl == r) 1f else 0.3f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            abschnitte.forEach { (r, farbe) ->
                Row(
                    Modifier.weight(1f).clickable(role = Role.Button) { onWahl(r) }.heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r != 0) {
                        DirectionTriangle(r.toDouble(), color = farbe)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        "${z.von(r)} ${S.ibRichtungKurz(r)}", style = MaterialTheme.typography.bodySmall,
                        color = farbe, fontWeight = if (wahl == r) FontWeight.Bold else null, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}
