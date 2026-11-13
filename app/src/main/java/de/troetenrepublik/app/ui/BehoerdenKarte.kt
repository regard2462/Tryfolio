package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.BehoerdenRepository
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import androidx.compose.foundation.lazy.LazyListScope

// US-Aktie laut Katalog oder, wenn der Katalog sie nicht kennt, ein Kuerzel ohne Boersenendung mit Dollarkurs.
internal fun istUsAktie(symbol: String, waehrung: String?): Boolean {
    Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }?.let { return it.c == "US" }
    return !symbol.contains('.') && !symbol.startsWith("^") && waehrung == "USD"
}

// Abschnitt „Politik und Behoerden“ auf der Seite einer US-Aktie: Lobbying, Gesetze,
// Wahlkampfspenden. Jede Zeile klappt auf. Entlassungen, Patente und Emissionen stehen in eigenen Bausteinen
// (FirmaEntlassungenKarte, FirmaPatenteKarte, FirmaEmissionenKarte). Wer aus dem Kongress
// den Wert handelt, steht unter „Wer handelt den Wert“, Leerverkaeufe in einer eigenen Karte (5.91).
@Composable
internal fun BehoerdenKarte(state: AppState, symbol: String, name: String, oeffnen: (String) -> Unit, merker: SeitenMerker? = null, industrie: String = "") {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val repo = state.behoerden
    val uri = appLinks()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Die mitgelieferten Dateien (Lobbying, alle Kongressmeldungen) im Hintergrund lesen: beim ersten
    // Mal sind das einige Megabyte, auf dem Hauptthread stockte die Seite
    // Alles im Merker der Seite: Beim Zurueckscrollen wird nichts neu gelesen oder geholt
    var lobbyingGeladen by gemerkt<Behoerden.Lobbying?>(merker, "beh/lobbying") { null }
    var lobbyStand by gemerkt(merker, "beh/lobbyStand") { "" }
    var dateienGelesen by gemerkt(merker, "beh/dateien") { false }
    LaunchedEffect(symbol) {
        if (dateienGelesen) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            lobbyingGeladen = repo.lobbying?.firmen?.get(symbol)
            lobbyStand = repo.lobbying?.stand.orEmpty()
        }
        dateienGelesen = true
    }
    var spenden by gemerkt<BehoerdenRepository.Spenden?>(merker, "beh/spenden") { null }
    var spendenGeladen by gemerkt(merker, "beh/spendenGeladen") { false }
    val offenZustand = gemerkt(merker, "beh/offen") { emptySet<String>() }
    val offen by offenZustand
    val katalogName = remember(symbol, name) { Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }?.n ?: name }
    val suchname = remember(katalogName) { Behoerden.stamm(katalogName) }
    // Mit eigenem Schluessel gleich, mit dem gemeinsamen Testschluessel erst beim Aufklappen
    val spendenLaden = settings.keyFec.isNotBlank() || "spenden" in offen
    LaunchedEffect(symbol, spendenLaden) {
        if (!spendenLaden || spendenGeladen) return@LaunchedEffect
        spenden = repo.spenden(suchname, settings.keyFec)
        spendenGeladen = true
    }

    @Composable
    fun Zeile(schluessel: String, titel: String, wert: String, inhalt: @Composable () -> Unit) {
        val auf = schluessel in offenZustand.value
        Column(Modifier.fillMaxWidth().clickable { offenZustand.value = if (auf) offenZustand.value - schluessel else offenZustand.value + schluessel }.padding(horizontal = 20.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
                AufklappPfeil(auf)
            }
            if (auf) Column(Modifier.padding(top = 4.dp)) { inhalt() }
        }
    }

    @Composable
    fun Klein(text: String, link: String? = null, onClick: (() -> Unit)? = null) {
        val m = when {
            link != null -> Modifier.textKlick(0.dp) { runCatching { uri.openUri(link) } }
            onClick != null -> Modifier.textKlick(0.dp, onClick = onClick)
            else -> Modifier
        }
        PunktText(
            text, style = MaterialTheme.typography.bodySmall,
            color = if (link != null || onClick != null) LocalSignal.current.link else grau,
            modifier = m.padding(vertical = 2.dp),
        )
    }

    val praesidial = praesidialFuerWert(symbol, name, industrie, merker)
    val lobbying = lobbyingGeladen
    val hatEtwas = lobbying != null
    if (!hatEtwas && praesidial.isEmpty() && settings.keyFec.isBlank()) {
        ModellAusgabe(merker, "beh", null)
        return
    }
    val rows = ArrayList<ZeilenDaten>()
    // Hinweise zu den Quellen und Grenzen der Daten stehen einmal hinter dem Info-Symbol am Abschnittskopf
    val hinweise = ArrayList<String>()

    if (lobbying != null && lobbying.quartale.isNotEmpty()) {
        val letztes = lobbying.quartale.last()
        hinweise.add(S.bhLobbyingHinweis(Politik.tag(lobbyStand).takeIf { it > 0 }?.let { Fmt.date(it) } ?: lobbyStand))
        rows.add(ZeilenDaten("lobbying") { Zeile("lobbying", S.bhLobbying, S.bhJeQuartal(Fmt.dollarKompakt(letztes.b.toDouble()))) {
            lobbying.quartale.takeLast(6).reversed().forEach { q ->
                Klein(S.bhQuartal(q.q, q.j, Fmt.dollarKompakt(q.b.toDouble()), q.f))
            }
            if (lobbying.themen.isNotEmpty()) Klein(S.bhThemen(lobbying.themen.take(6).joinToString(", ") { S.bhThema(it.c) }))
            lobbying.beispiele.firstOrNull()?.let { AufklappText(S.bhZitat(it), Modifier.padding(vertical = 2.dp)) }
        } })
    }

    if (lobbying?.gesetze?.isNotEmpty() == true) {
        rows.add(ZeilenDaten("gesetze") { Zeile("gesetze", S.bwGesetze, S.bhGesetzeWert(lobbying.gesetze.size, 0)) {
            val jahr = lobbying.quartale.lastOrNull()?.j ?: de.troetenrepublik.app.engine.Uhr.heute().year
            lobbying.gesetze.forEach { g -> Klein(S.bhGesetz(g.c, g.n), link = Behoerden.gesetzAdresse(g.c, jahr)) }
        } })
    }

    if (settings.keyFec.isNotBlank() || hatEtwas) {
        val s = spenden
        val wert = when {
            s?.summen != null -> S.bhSpendenWert(Fmt.dollarKompakt(s.summen.anKandidaten), s.zyklus)
            spendenGeladen -> "–"
            else -> ""
        }
        val gesperrt = spendenGeladen && s == null && repo.fecGesperrt
        hinweise.add(S.bhSpendenHinweis(settings.keyFec.isBlank()))
        if (!(spendenGeladen && s == null) || gesperrt) rows.add(ZeilenDaten("spenden") { Zeile("spenden", S.bhSpenden, wert) {
            when {
                s == null && !spendenGeladen -> Klein(S.loading)
                gesperrt -> Klein(S.bhSpendenLimit)
                s == null -> Klein(S.bhKeinPac)
                else -> {
                    Klein(s.pac.name)
                    s.summen?.let { Klein(S.bhPacSummen(s.zyklus, Fmt.dollarKompakt(it.einnahmen), Fmt.dollarKompakt(it.anKandidaten))) }
                    s.empfaenger.forEach { (n, b) -> Klein("$n · ${Fmt.dollarKompakt(b)}") }
                }
            }
        } })
    }

    if (praesidial.isNotEmpty()) {
        rows.add(ZeilenDaten("praesidial") { Zeile("praesidial", S.pdTitel, S.pdAnzahl(praesidial.size)) {
            praesidial.forEach { (d, genannt) ->
                Klein(praesidialMeta(d, genannt))
                Klein(d.titel, link = d.adresse)
            }
        } })
    }
    ModellAusgabe(merker, "beh", ListenModell(titel = "", gross = true, kern = null, zeilen = rows, immerOffen = true, kopf = { BehoerdenKopf(hinweise) }))
}

internal fun LazyListScope.behoerdenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "beh")
}

// Kopf des Abschnitts; die Hinweise zu Quelle und Grenzen der Daten liegen hinter dem Info-Symbol
@Composable
private fun BehoerdenKopf(hinweise: List<String>) {
    if (hinweise.isEmpty()) SectionHeader(S.bhTitel) else SectionHeaderMitInfo(S.bhTitel, S.bhTitel, hinweise.joinToString("\n\n"))
}
