package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// US-Aktie laut Katalog oder, wenn der Katalog sie nicht kennt, ein Kuerzel ohne Boersenendung mit Dollarkurs.
internal fun istUsAktie(symbol: String, waehrung: String?): Boolean {
    Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }?.let { return it.c == "US" }
    return !symbol.contains('.') && !symbol.startsWith("^") && waehrung == "USD"
}

// Abgeordnete und Senatoren, die den Wert in den letzten zwoelf Monaten gehandelt haben (mitgelieferte Daten).
private data class Haendler(val schluessel: String, val name: String, val kaeufe: Int, val verkaeufe: Int, val haus: Boolean)

private fun haendler(state: AppState, symbol: String): List<Haendler> {
    val ab = System.currentTimeMillis() - 365L * 86_400_000L
    val zaehler = HashMap<String, Triple<String, IntArray, Boolean>>()
    fun dazu(key: String, name: String, haus: Boolean, g: Kongress.Geschaeft) {
        if (g.datum < ab || g.kuerzel == null || CopyRouten.symbol(g.kuerzel) != symbol) return
        val z = zaehler.getOrPut(key) { Triple(name, IntArray(2), haus) }
        if (g.kauf) z.second[0]++ else if (g.verkauf) z.second[1]++
    }
    state.haus?.let { h -> h.meldungen.forEach { m -> h.geschaefte[m.docId]?.forEach { dazu(m.person, m.name, true, it) } } }
    state.senat?.meldungen?.forEach { sm ->
        val t = Politik.tag(sm.eingang)
        sm.geschaefte.forEach { dazu(sm.person, sm.name, false, Politik.geschaeft(it, t)) }
    }
    return zaehler.map { (k, v) -> Haendler(k, v.first, v.second[0], v.second[1], v.third) }.sortedByDescending { it.kaeufe + it.verkaeufe }
}

// Abschnitt „Politik und Behoerden“ auf der Seite einer US-Aktie: Lobbying, Gesetze, Regierungsauftraege,
// Wahlkampfspenden, Leerverkaeufe, Entlassungen und (mit eigenem Schluessel) Patente. Jede Zeile klappt auf.
@Composable
internal fun BehoerdenKarte(state: AppState, symbol: String, name: String, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val repo = state.behoerden
    val uri = LocalUriHandler.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Die mitgelieferten Dateien (Lobbying, Entlassungen, alle Kongressmeldungen) im Hintergrund lesen: beim ersten
    // Mal sind das einige Megabyte, auf dem Hauptthread stockte die Seite
    var lobbyingGeladen by remember(symbol) { mutableStateOf<Behoerden.Lobbying?>(null) }
    var lobbyStand by remember { mutableStateOf("") }
    var gemeldet by remember(symbol) { mutableStateOf<List<Behoerden.Entlassung>>(emptyList()) }
    var personen by remember(symbol) { mutableStateOf<List<Haendler>>(emptyList()) }
    LaunchedEffect(symbol) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            lobbyingGeladen = repo.lobbying?.firmen?.get(symbol)
            lobbyStand = repo.lobbying?.stand.orEmpty()
            gemeldet = repo.entlassungen?.firmen?.get(symbol).orEmpty()
            personen = haendler(state, symbol)
        }
    }
    var auftraege by remember(symbol) { mutableStateOf<BehoerdenRepository.Auftraege?>(null) }
    var leer by remember(symbol) { mutableStateOf<BehoerdenRepository.Leerverkauf?>(null) }
    var texas by remember(symbol) { mutableStateOf<List<Behoerden.Entlassung>?>(null) }
    var patente by remember(symbol) { mutableStateOf<Behoerden.Patente?>(null) }
    var spenden by remember(symbol) { mutableStateOf<BehoerdenRepository.Spenden?>(null) }
    var spendenGeladen by remember(symbol) { mutableStateOf(false) }
    var offen by remember(symbol) { mutableStateOf<Set<String>>(emptySet()) }
    val katalogName = remember(symbol, name) { Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }?.n ?: name }
    val suchname = remember(katalogName) { Behoerden.stamm(katalogName) }
    LaunchedEffect(symbol) {
        coroutineScope {
            async { auftraege = repo.auftraege(suchname) }
            async { leer = repo.leerverkauf(symbol.replace('-', '.')) }
            async { texas = repo.texas(suchname) }
            async { patente = repo.patente(suchname, settings.keyUspto) }
        }
    }
    // Mit eigenem Schluessel gleich, mit dem gemeinsamen Testschluessel erst beim Aufklappen
    val spendenLaden = settings.keyFec.isNotBlank() || "spenden" in offen
    LaunchedEffect(symbol, spendenLaden) {
        if (!spendenLaden || spendenGeladen) return@LaunchedEffect
        spenden = repo.spenden(suchname, settings.keyFec)
        spendenGeladen = true
    }
    val entlassungen = remember(gemeldet, texas) {
        (gemeldet + texas.orEmpty()).distinctBy { it.d + "|" + it.s + "|" + it.n }.sortedByDescending { it.d }
    }
    val jahrAb = remember { java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(1).toString() }
    val entlassungenJahr = entlassungen.filter { it.d >= jahrAb }

    @Composable
    fun Zeile(schluessel: String, titel: String, wert: String, inhalt: @Composable () -> Unit) {
        val auf = schluessel in offen
        Column(Modifier.fillMaxWidth().clickable { offen = if (auf) offen - schluessel else offen + schluessel }.padding(horizontal = 20.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                ChevronDown(width = 9.dp, color = grau.copy(alpha = 0.45f), modifier = Modifier.rotate(if (auf) 180f else 0f))
            }
            if (auf) Column(Modifier.padding(top = 4.dp)) { inhalt() }
        }
    }

    @Composable
    fun Klein(text: String, link: String? = null, onClick: (() -> Unit)? = null) {
        val m = when {
            link != null -> Modifier.clickable { runCatching { uri.openUri(link) } }
            onClick != null -> Modifier.clickable(onClick = onClick)
            else -> Modifier
        }
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            color = if (link != null || onClick != null) LocalSignal.current.link else grau,
            modifier = m.padding(vertical = 2.dp),
        )
    }

    val a = auftraege
    val l = leer
    val p = patente
    val lobbying = lobbyingGeladen
    val hatEtwas = lobbying != null || personen.isNotEmpty() || a != null || l != null || entlassungenJahr.isNotEmpty() || p != null
    if (!hatEtwas && settings.keyFec.isBlank()) return
    SectionHeader(S.bhTitel)

    if (lobbying != null && lobbying.quartale.isNotEmpty()) {
        val letztes = lobbying.quartale.last()
        Zeile("lobbying", S.bhLobbying, S.bhJeQuartal(Fmt.dollarKompakt(letztes.b.toDouble()))) {
            lobbying.quartale.takeLast(6).reversed().forEach { q ->
                Klein(S.bhQuartal(q.q, q.j, Fmt.dollarKompakt(q.b.toDouble()), q.f))
            }
            if (lobbying.themen.isNotEmpty()) Klein(S.bhThemen(lobbying.themen.take(6).joinToString(", ") { S.bhThema(it.c) }))
            lobbying.beispiele.firstOrNull()?.let { Klein(if (de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE) "„${it.take(280)}“" else "“${it.take(280)}”") }
            Klein(S.bhLobbyingQuelle(Politik.tag(lobbyStand).takeIf { it > 0 }?.let { Fmt.date(it) } ?: lobbyStand))
        }
    }

    if (lobbying?.gesetze?.isNotEmpty() == true || personen.isNotEmpty()) {
        Zeile("gesetze", S.bhGesetze, S.bhGesetzeWert(lobbying?.gesetze?.size ?: 0, personen.size)) {
            val jahr = lobbying?.quartale?.lastOrNull()?.j ?: java.time.LocalDate.now().year
            lobbying?.gesetze?.take(8)?.forEach { g -> Klein(S.bhGesetz(g.c, g.n), link = Behoerden.gesetzAdresse(g.c, jahr)) }
            if (personen.isNotEmpty()) {
                Klein(S.bhGehandelt)
                personen.take(10).forEach { h ->
                    Klein(S.bhHaendler(h.name + if (h.haus) "" else " · " + S.copySenate, h.kaeufe, h.verkaeufe)) {
                        oeffnen(if (h.haus) CopyRouten.person(h.schluessel) else CopyRouten.senator(h.schluessel))
                    }
                }
            }
            Klein(S.bhGesetzeHinweis)
        }
    }

    if (a != null) {
        // Das laufende Fiskaljahr (ab 1. Oktober) ist noch unvollstaendig: oben steht das letzte abgeschlossene
        val laufend = a.jahre.maxOfOrNull { it.first }
        val letztes = a.jahre.lastOrNull { it.first != laufend && it.second != 0.0 }
        Zeile("auftraege", S.bhAuftraege, letztes?.let { S.bhFiskaljahr(Fmt.dollarKompakt(it.second), it.first) } ?: "–") {
            a.jahre.reversed().forEach { (j, b) -> Klein(S.bhFiskaljahrZeile(j, Fmt.dollarKompakt(b), j == laufend)) }
            a.groesste.take(5).forEach { x ->
                Klein(
                    listOf(Fmt.dollarKompakt(x.betrag), x.behoerde, x.beschreibung.take(90)).filter { it.isNotBlank() }.joinToString(" · "),
                    link = x.intern.takeIf { it.isNotBlank() }?.let { Behoerden.auftragAdresse(it) },
                )
            }
            Klein(S.bhAuftraegeQuelle)
        }
    }

    if (settings.keyFec.isNotBlank() || hatEtwas) {
        val s = spenden
        val wert = when {
            s?.summen != null -> S.bhSpendenWert(Fmt.dollarKompakt(s.summen.anKandidaten), s.zyklus)
            spendenGeladen -> "–"
            else -> ""
        }
        val gesperrt = spendenGeladen && s == null && repo.fecGesperrt
        if (!(spendenGeladen && s == null) || gesperrt) Zeile("spenden", S.bhSpenden, wert) {
            when {
                s == null && !spendenGeladen -> Klein(S.bhLaedt)
                gesperrt -> Klein(S.bhSpendenLimit)
                s == null -> Klein(S.bhKeinPac)
                else -> {
                    Klein(s.pac.name)
                    s.summen?.let { Klein(S.bhPacSummen(s.zyklus, Fmt.dollarKompakt(it.einnahmen), Fmt.dollarKompakt(it.anKandidaten))) }
                    s.empfaenger.take(8).forEach { (n, b) -> Klein("$n · ${Fmt.dollarKompakt(b)}") }
                }
            }
            Klein(S.bhSpendenQuelle(settings.keyFec.isBlank()))
        }
    }

    if (l != null) {
        val tag = l.tage.lastOrNull()
        val bestand = l.bestand.lastOrNull()
        Zeile("leer", S.bhLeer, tag?.let { Fmt.pct(it.anteil * 100, 0) } ?: bestand?.let { Fmt.num(it.aktien.toDouble() / 1_000_000.0, 1) + S.copyMillion } ?: "–") {
            if (l.tage.isNotEmpty()) {
                val zehn = l.tage.takeLast(10)
                Klein(S.bhLeerTage(Fmt.pct(zehn.sumOf { it.leer } / zehn.sumOf { it.gesamt }.coerceAtLeast(1.0) * 100, 0), zehn.size))
            }
            bestand?.let { b ->
                val aenderung = if (b.vorher > 0) (b.aktien.toDouble() / b.vorher - 1) * 100 else null
                Klein(S.bhLeerBestand(Fmt.date(Politik.tag(b.stichtag)), Fmt.num(b.aktien.toDouble(), 0), aenderung?.let { Fmt.signedPct(it, 1) }, b.tageEindecken?.let { Fmt.num(it, 1) }))
            }
            Klein(S.bhLeerErklaerung)
        }
    }

    if (entlassungenJahr.isNotEmpty()) {
        Zeile("entlassungen", S.bhEntlassungen, S.bhEntlassungenWert(entlassungenJahr.size, Fmt.num(entlassungenJahr.sumOf { it.n }.toDouble(), 0))) {
            entlassungenJahr.take(12).forEach { e ->
                Klein(listOf(Fmt.date(Politik.tag(e.d)), e.s, e.o, S.bhBetroffene(e.n)).filter { it.isNotBlank() }.joinToString(" · "))
            }
            Klein(S.bhEntlassungenQuelle)
        }
    }

    if (p != null) {
        Zeile("patente", S.bhPatente, S.bhPatenteWert(p.anzahl)) {
            p.neueste.take(6).forEach { x -> Klein(listOf(x.titel, x.erteilt.take(10)).filter { it.isNotBlank() }.joinToString(" · ")) }
            Klein(S.bhPatenteQuelle)
        }
    }
}
