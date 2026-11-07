package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.engine.KongressBestand
import kotlinx.coroutines.launch
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Portfolioverlauf

// Copytrading: Senat und Regierung und die gemeinsame Seite einer handelnden Person (Repraesentantenhaus,
// Senat, Praesident). Die Daten kommen aus Politik (mitgelieferte Schnappschuesse) und Kongress (Laufzeitabruf).

// Geschaefte mit Kuerzel aus den juengsten Meldungen, neueste zuerst.
private fun neuesteGeschaefte(daten: List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>>, anzahl: Int) =
    daten.flatMap { (m, g) -> g.orEmpty().filter { it.kuerzel != null && it.art != Kongress.Art.TAUSCH }.map { m to it } }
        .sortedWith(compareByDescending<Pair<Kongress.Meldung, Kongress.Geschaeft>> { it.first.datum }.thenByDescending { it.second.datum })
        .take(anzahl)

// Senat

// Ergebnisse je Datenstand merken, damit die Politik-Reiter Personen und Geschaefte nicht bei jedem Wechsel neu
// rechnen. Verglichen wird der Datenstand nach Identitaet.
internal object ListenMerker {
    private val merk = java.util.concurrent.ConcurrentHashMap<String, Pair<Any, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> von(name: String, stand: Any, rechnen: () -> T): T {
        merk[name]?.let { (s, w) -> if (s === stand) return w as T }
        val w = rechnen()
        merk[name] = stand to w
        return w
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> vorhanden(name: String, stand: Any): T? = merk[name]?.takeIf { it.first === stand }?.second as T?

    fun merken(name: String, stand: Any, wert: Any?) { merk[name] = stand to wert }
}

@Composable
internal fun SenatListe(state: AppState, gefolgt: Set<String>, liste: LazyListState, oeffnen: (String) -> Unit) {
    // Neuere Meldungen aus dem offenen Spiegel (seit 5.92), hoechstens alle zwoelf Stunden neu
    val gemischt by state.senatFluss.collectAsStateWithLifecycle()
    val spiegel by state.senatSpiegel.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { state.senatSpiegelLaden() } }
    val d = gemischt ?: state.senat
    var suche by rememberSaveable { mutableStateOf("") }
    val personen = remember(d) { d?.let { s -> ListenMerker.von("senat-personen", s) { Politik.personen(s, System.currentTimeMillis()) } }.orEmpty() }
    // Mitglieder und Ehemalige (5.96); die Suche findet auch ausgeblendete Ehemalige
    val bestand by state.kongressBestand.collectAsStateWithLifecycle()
    val verzeichnis = remember(bestand) { state.kongressVerzeichnis() }
    val (gezeigt, ehemalige) = remember(personen, suche, verzeichnis) {
        val q = suche.trim()
        val treffer = if (q.isEmpty()) personen else personen.filter { it.name.contains(q, true) }
        KongressBestand.aufteilen(
            treffer, { p -> verzeichnis?.senat(p.schluessel.substringBefore('|'), p.schluessel.substringAfter('|', "")) },
            { it.letzte }, System.currentTimeMillis(), q.isNotEmpty(),
        )
    }
    val neueste = remember(d) { d?.let { s -> ListenMerker.von("senat-neueste", s) { neuesteGeschaefte(Politik.daten(s.meldungen.take(40)), 25) } }.orEmpty() }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        val einleitung = S.ersSenatIntro(d?.stand?.let { Fmt.date(Politik.tag(it)) } ?: "")
        if (d == null || personen.isEmpty()) {
            item { EmptyHint(S.copyUnavailable) }
            item { CopyHinweis(einleitung) }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                value = suche, onValueChange = { suche = it.take(40) }, singleLine = true,
                placeholder = { Text(S.sortName) },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        if (suche.isBlank()) {
            item { NavRow(S.cuTitel, S.cuNavUnter) { oeffnen(CopyRouten.uebersicht("SENAT")) } }
            item { SectionHeader(S.copyLatestTrades) }
            itemsIndexed(neueste, key = { i, (m, _) -> "sn-$i-${m.docId}" }) { _, (m, g) ->
                GeschaeftZeile(state, g, untertitel = Fmt.date(g.datum), person = m.name) { oeffnen(CopyRouten.senator(Politik.SenatMeldung("", m.vorname, m.nachname, eingang = "").person)) }
            }
            item { SectionHeader(S.copySenators) }
        }
        items(gezeigt, key = { "sp-" + it.schluessel }) { p -> SenatorZeile(state, p, gefolgt, oeffnen) }
        // Ausgeschiedene mit ihren bisherigen Meldungen (5.96)
        if (ehemalige.isNotEmpty()) {
            item { SectionHeader(S.copyFormer) }
            items(ehemalige, key = { "se-" + it.schluessel }) { p -> SenatorZeile(state, p, gefolgt, oeffnen) }
        }
        item { CopyFuss(einleitung, S.copySenateSource + (if (spiegel.second > 0) " " + S.ersSenatQuelle else "") + " " + S.copyPhotosCongress + " " + S.copyMitgliederQuelle) }
    }
}

// Zeile eines Senators, bei Ehemaligen mit Zeitraum (5.96).
@Composable
private fun SenatorZeile(state: AppState, p: Politik.Person, gefolgt: Set<String>, oeffnen: (String) -> Unit) {
    // Bestand als Schluessel: nach dem Tagesabruf zeichnet die Zeile neu
    val bestand by state.kongressBestand.collectAsStateWithLifecycle()
    val info = remember(bestand, p.schluessel) { Infos.von(state, "n:${p.schluessel}") }
    val foto = remember(bestand, p.schluessel) { Fotos.von(state, "n:${p.schluessel}") }
    PersonZeile(
        name = p.name,
        unter = listOfNotNull(Infos.kurz(info), Infos.zeitraum(info), S.copyFilings(p.meldungen.size), S.copyLast(Fmt.date(p.letzte))).joinToString(" · "),
        gefolgt = "n:${p.schluessel}" in gefolgt,
        foto = foto,
    ) { oeffnen(CopyRouten.senator(p.schluessel)) }
}

@Composable
fun SenatorScreen(state: AppState, schluessel: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val gemischt by state.senatFluss.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { state.senatSpiegelLaden() } }
    LaunchedEffect(Unit) { runCatching { state.kongressPruefen() } }
    val meldungen = remember(schluessel, gemischt) { state.senat?.meldungen?.filter { it.person == schluessel }.orEmpty() }
    val daten = remember(meldungen) { Politik.daten(meldungen) }
    val name = meldungen.maxByOrNull { Politik.tag(it.eingang) }?.name ?: schluessel.split('|').let { "${it.getOrElse(1) { "" }} ${it[0]}".trim() }
    PolitikerSeite(
        state = state, titel = name, untertitel = S.copySenatorOf.takeIf { Infos.von(state, "n:$schluessel") == null }, folgeKey = "n:$schluessel",
        daten = daten, meldungenText = S.copyFilingsAll, papier = daten.filter { it.second == null },
        verlaufBereit = true, verlauf = { alle -> VerlaufLader.kongress(state, "n:$schluessel", alle) },
        hinweis = null, quelle = S.copySenateSource + (if (state.senatSpiegel.value.second > 0) " " + S.ersSenatQuelle else ""), onBack = onBack, onOpen = onOpen, foto = Fotos.von(state, "n:$schluessel"),
        info = Infos.von(state, "n:$schluessel"),
    )
}

// Regierung

@Composable
internal fun RegierungListe(state: AppState, gefolgt: Set<String>, liste: LazyListState, oeffnen: (String) -> Unit) {
    val d = state.regierung
    // Weitere Mitglieder ueber OGE und Weisses Haus (seit 5.91), hoechstens einmal am Tag neu
    val oge by state.ogeRegierung.collectAsStateWithLifecycle()
    var ogeLaedt by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { runCatching { state.ogeLaden() }; ogeLaedt = false }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        if (d == null) {
            item { EmptyHint(S.copyUnavailable) }
            item { CopyHinweis(S.copyGovIntro) }
            ogePersonen(state, oge, ogeLaedt, gefolgt, oeffnen)
            return@LazyColumn
        }
        item { NavRow(S.cuTitel, S.cuNavUnter) { oeffnen(CopyRouten.uebersicht("REGIERUNG")) } }
        // Personen nur mit Bestand aus dem Jahresbericht (Vizepraesident) stehen nicht in der Liste; ihr Bestand zaehlt
        // bei „Wer handelt den Wert“ mit.
        items(ListenMerker.von("regierung-personen", d) { d.personen.filter { it.bestand == null } }, key = { "r-" + it.id }) { p ->
            val anzahl = p.meldungen.sumOf { it.geschaefte.size }
            val letzte = p.meldungen.maxOfOrNull { Politik.tag(it.eingang.ifBlank { it.unterschrieben }) } ?: 0L
            // Vermoegen laut Jahresmeldung, soweit ausgewertet (seit 5.92, beim Praesidenten); Amt in eigener Zeile wie bei
            // den weiteren Mitgliedern
            val punkte = listOfNotNull(
                p.vermoegen?.let { vermoegenKurz(it) },
                if (anzahl > 0) S.copyTradesCount(anzahl) else null,
                if (letzte > 0) S.copyLast(Fmt.date(letzte)) else p.bestand?.let { S.copyHoldingsAsOf(Fmt.date(Politik.tag(it.stichtag))) },
            )
            RegierungZeile(p.name, amt(p.id), punkte, "r:${p.id}" in gefolgt, Fotos.von(state, "r:${p.id}")) { oeffnen(CopyRouten.regierung(p.id)) }
        }
        ogePersonen(state, oge, ogeLaedt, gefolgt, oeffnen)
        item { CopyFuss(S.copyGovIntro, S.copyGovSource(Fmt.date(Politik.tag(d.stand))) + " " + S.ogeQuelle + " " + S.copyPhotosCommons) }
    }
}

private fun amt(id: String): String? = when (id) {
    "trump" -> S.copyPresident
    "vance" -> S.copyVicePresident
    else -> null
}

@Composable
fun RegierungScreen(state: AppState, id: String, onBack: () -> Unit, onOpen: (String) -> Unit, oeffnen: (String) -> Unit = {}) {
    val p = state.regierung?.personen?.firstOrNull { it.id == id }
    // Mit Bestand aus dem Jahresbericht (Vizepraesident): Bestandsseite, seine wenigen Geschaefte darunter
    if (p?.bestand != null) {
        BestandSeite(state, p, onBack, onOpen)
        return
    }
    val daten = remember(p) { p?.let { Politik.daten(it) }.orEmpty() }
    // Neue Meldungen bei der OGE, die noch nicht ausgelesen in der App stehen (hoechstens einmal am Tag geprueft)
    val neu by state.regierungNeu.collectAsStateWithLifecycle()
    LaunchedEffect(id) { runCatching { state.regierungPruefen(id) } }
    val offen = neu[id].orEmpty()
    val laden = rememberPdfLaden(state)
    PolitikerSeite(
        state = state, titel = p?.name ?: id, untertitel = amt(id), folgeKey = "r:$id",
        daten = daten, meldungenText = S.copyFilingsAll, papier = emptyList(),
        verlaufBereit = p != null, verlauf = { alle -> VerlaufLader.kongress(state, "r:$id", alle) },
        hinweis = null,
        quelle = S.copyGovSource(Fmt.date(Politik.tag(state.regierung?.stand ?: ""))), onBack = onBack, onOpen = onOpen, foto = Fotos.von(state, "r:$id"),
        // Beim Praesidenten: Vermoegen laut Jahresmeldung und seine Truth-Social-Beitraege aus einem unabhaengigen Archiv
        zusatz = if (id == "trump" || offen.isNotEmpty() || p?.vermoegen != null) {
            {
                p?.vermoegen?.let { VermoegenZeile(it, S.vmHinweisOge) }
                if (offen.isNotEmpty()) {
                    NavRow(S.copyOgeNeu2(offen.size, Fmt.date(Politik.tag(offen.first().tag))), S.pdfLaden) { laden(offen.first().pdf) }
                }
                if (id == "trump") NavRow(S.truthTitle, S.truthSubtitle) { oeffnen(CopyRouten.TRUTH) }
            }
        } else null,
        info = Infos.von(state, "r:$id"),
        // Meldungs-Kopf r-<id>-<Nummer>: Nummer in der Liste der Meldungen
        pdf = { m -> m.docId.substringAfterLast('-').toIntOrNull()?.let { p?.meldungen?.getOrNull(it)?.datei }?.takeIf { it.startsWith("http") } },
    )
}

// Bestand laut Jahresbericht (Vizepraesident): Werte mit Spanne, Anteil nach der Mitte der Spanne, Nachbilden.
@Composable
private fun BestandSeite(state: AppState, p: Politik.RPerson, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val b = p.bestand ?: return
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    var nachbilden by remember { mutableStateOf(false) }
    val summe = b.positionen.sumOf { it.mitte }.coerceAtLeast(1.0)
    val folgeKey = "r:${p.id}"
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item {
            ScreenHeader(p.name, amt(p.id), onBack, trailing = {
                Row {
                    GlockenKnopf(state, folgeKey, p.name)
                    FolgenKnopf(folgeKey in state.gefolgt(settings)) { state.folgen(folgeKey, it) }
                }
            })
        }
        item { PersonKopfbild(Fotos.von(state, "r:${p.id}"), p.name) }
        item { CopyHinweis(S.copyVanceNote(Fmt.date(Politik.tag(b.stichtag)))) }
        item {
            Kennzahlen(
                listOf(
                    S.copyAsOf to Fmt.date(Politik.tag(b.stichtag)),
                    S.positions to b.positionen.size.toString(),
                    S.value to spanne(b.positionen.sumOf { it.von }, if (b.positionen.any { it.bis == null }) null else b.positionen.sumOf { it.bis ?: 0L }),
                    S.calTrades to p.meldungen.sumOf { it.geschaefte.size }.toString(),
                ),
            )
        }
        if (Politik.gewichte(b, 20).isNotEmpty()) item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
        item { SectionHeader(S.copyHoldings) }
        items(b.positionen.sortedByDescending { it.mitte }, key = { "b-" + it.n + it.k }) { pos ->
            val k = Politik.kuerzel(pos.k)
            val sym = k?.let { CopyRouten.symbol(it) }
            val name = CopyRouten.name(k, pos.n)
            val anteil = pos.mitte / summe
            Row(
                Modifier.fillMaxWidth().clickable(enabled = sym != null) { sym?.let(onOpen) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sym != null) InstrumentLogo(state, sym, name, size = 36) else InstrumentAvatar(name, name, size = 36)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    NamenText(name, style = MaterialTheme.typography.titleSmall, zusatz = k?.let { " ($it)" } ?: "")
                    Text(spanne(pos.von, pos.bis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth(anteil.toFloat().coerceIn(0.01f, 1f)).fillMaxSize().background(MaterialTheme.colorScheme.onSurface))
                    }
                }
                Text(Fmt.pct(anteil * 100, 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
            }
        }
        val daten = Politik.daten(p).filter { !it.second.isNullOrEmpty() }
        if (daten.isNotEmpty()) item { SectionHeader(S.copyAllTrades) }
        daten.forEach { (m, g) ->
            itemsIndexed(g.orEmpty(), key = { i, _ -> "bg-${m.docId}-$i" }) { _, t ->
                GeschaeftZeile(state, t, untertitel = Fmt.date(t.datum)) { t.kuerzel?.let { onOpen(CopyRouten.symbol(it)) } }
            }
        }
        item { Quelle(S.copyGovSource(Fmt.date(Politik.tag(state.regierung?.stand ?: "")))) }
    }
    if (nachbilden) {
        NachbildenBlatt(
            state = state, titel = p.name, mitAnzahl = false, notiz = S.copyNote(p.name),
            gewichte = { n -> Politik.gewichte(b, maxOf(n, 20)).map { CopyRouten.symbol(it.first) to it.second } },
            namen = { sym -> b.positionen.firstOrNull { Politik.kuerzel(it.k)?.let { k -> CopyRouten.symbol(k) } == sym }?.let { CopyRouten.name(Politik.kuerzel(it.k), it.n) } },
            onDismiss = { nachbilden = false },
        )
    }
}

// Gemeinsame Personenseite

// Vorsatz fuer onOpen: oeffnet die Derivateseite statt einer Wertpapierseite.
internal const val DERIVAT_ROUTE = "derivat:"

// Kurzbeschreibung eines Derivats: Art, Basispreis, Verfall und Zahl der Kontrakte, soweit gemeldet.
internal fun derivatText(d: de.troetenrepublik.app.market.Derivate.Derivat): String = when (d.art) {
    de.troetenrepublik.app.market.Derivate.Art.STRUKTUR -> S.derivStructured
    de.troetenrepublik.app.market.Derivate.Art.SONST -> S.derivOther
    else -> listOfNotNull(
        if (d.art == de.troetenrepublik.app.market.Derivate.Art.CALL) S.call else S.put,
        d.strike?.let { S.derivStrike(Fmt.num(it, if (it % 1.0 == 0.0) 0 else 2)) },
        d.verfall?.let { S.derivExpiry(Fmt.date(it)) },
        d.kontrakte?.let { S.derivContracts(Fmt.num(it.toDouble(), 0)) },
    ).joinToString(" · ")
}

// Seite einer handelnden Person (Repraesentantenhaus, Senat, Praesident): Kennzahlen, Verlauf aus den Geschaeften,
// meistgehandelte Werte, alle Geschaefte je Meldung, Nachbilden. daten null heisst: wird noch geladen.
@Composable
internal fun PolitikerSeite(
    state: AppState,
    titel: String,
    untertitel: String?,
    folgeKey: String,
    daten: List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>>?,
    meldungenText: String,
    papier: List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>>,
    verlaufBereit: Boolean,
    verlauf: suspend (List<Kongress.Geschaeft>) -> Portfolioverlauf.KongressVerlauf?,
    hinweis: String?,
    quelle: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    meldungenAnzahl: Int = daten?.size ?: 0,
    leer: Boolean = false,
    foto: de.troetenrepublik.app.market.Personenbilder.Foto? = null,
    // Eigene Zeile unter den Kennzahlen.
    zusatz: (@Composable () -> Unit)? = null,
    // Partei, Staat und Ausschuesse (seit 5.92).
    info: de.troetenrepublik.app.market.Personeninfo.Info? = null,
    // Adresse des Originals einer Meldung als PDF, null ohne (seit 5.92).
    pdf: ((Kongress.Meldung) -> String?)? = null,
) {
    katalogBereit()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val alle = remember(daten) {
        daten.orEmpty().flatMap { (m, g) -> g.orEmpty().map { if (it.eingereicht > 0 || m.datum <= 0) it else it.copy(eingereicht = m.datum) } }
    }
    val posten = remember(alle) { Kongress.posten(alle) }
    // Geschaefte je Meldung, neueste zuerst; einmal sortiert (beim Praesidenten fast 10.000)
    val nachMeldung = remember(daten) { daten.orEmpty().filter { (_, g) -> !g.isNullOrEmpty() }.map { (m, g) -> m to g.orEmpty().sortedByDescending { it.datum } } }
    val kaufSumme = remember(alle) { alle.filter { it.kauf }.sumOf { it.mitte } }
    // Optionen stehen als Unterzweige bei den Aktuellen Positionen; in der Liste der Geschaefte oeffnen sie den Schein
    val derivatScope = androidx.compose.runtime.rememberCoroutineScope()
    val verkaufSumme = remember(alle) { alle.filter { it.verkauf }.sumOf { it.mitte } }
    var nachbilden by remember { mutableStateOf(false) }
    var pv by remember { mutableStateOf<Portfolioverlauf.KongressVerlauf?>(null) }
    var pvLaedt by remember { mutableStateOf(true) }
    LaunchedEffect(folgeKey, verlaufBereit, daten != null) {
        if (!verlaufBereit || daten == null) return@LaunchedEffect
        pv = runCatching { verlauf(alle) }.getOrNull()
        pvLaedt = false
    }
    val pvMarken = remember(pv) { pv?.let { kongressMarken(it) }.orEmpty() }
    var werkzeuge by remember { mutableStateOf(false) }
    var personWerkzeug by remember { mutableStateOf<PortfolioTool?>(null) }
    val werkzeugRueck = rememberWiederkehr()
    val fremdesDepot = rememberFremdesDepot(state, titel, pv, (kaufSumme - verkaufSumme).takeIf { it > 0 } ?: kaufSumme)
    val signal = LocalSignal.current
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val laden = rememberPdfLaden(state)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item {
            ScreenHeader(titel, untertitel, onBack, trailing = {
                Row {
                    GlockenKnopf(state, folgeKey, titel)
                    FolgenKnopf(folgeKey in state.gefolgt(settings)) { state.folgen(folgeKey, it) }
                }
            })
        }
        item { PersonKopfbild(foto, titel) }
        info?.let { i -> item(key = "info") { PersonInfoBlock(i) } }
        hinweis?.let { item { CopyHinweis(it) } }
        item {
            Kennzahlen(
                listOf(
                    meldungenText to meldungenAnzahl.toString(),
                    S.calTrades to (if (daten == null) "…" else alle.size.toString()),
                    S.copyBuysEst to Fmt.dollarKompakt(kaufSumme),
                    S.copySalesEst to Fmt.dollarKompakt(verkaufSumme),
                ),
            )
        }
        zusatz?.let { z -> item { z() } }
        if (daten == null && meldungenAnzahl > 0) item { CopyLade() }
        if (posten.any { it.netto > 0 }) item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
        if (meldungenAnzahl > 0) {
            item { SectionHeader(S.pvTitle) }
            item {
                val v = pv
                PortfolioKarte(
                    state, titel = titel, haupt = v?.reihe?.takeIf { it.size >= 2 }?.let { it to Portfolioverlauf.Waehrung.USD },
                    laedt = pvLaedt && v == null, hinweis = v?.hinweis ?: S.pvNoteCongress(v?.handel?.size ?: 0, v?.ohneKurs ?: 0, v?.ohneBestand ?: 0),
                    eigener = folgeKey, marken = pvMarken, standardZeitraum = VerlaufZeitraum.MAX,
                    leerText = if (v != null && v.reihe.isEmpty()) S.pvCongressNoBuys else S.pvNone,
                    kalender = if (alle.isEmpty()) null else { { GeschaeftsKalender(state, alle, onOpen) } },
                )
            }
        }
        // Aktuelle Positionen und Werkzeuge mit dem geschaetzten Bestand
        pv?.let { v ->
            if (v.werte.isNotEmpty() || v.bestand.isNotEmpty()) {
                item(key = "positionen") { AktuellePositionen(state, v, onOpen) }
                item(key = "werkzeuge") { NavRow(S.toolsTitle, S.copyToolsRow) { werkzeuge = true } }
            }
        }
        // Auswertung der Geschaefte, sobald sie geladen sind
        if (daten != null && alle.isNotEmpty()) item(key = "auswertung") { AuswertungKarte(state, alle) }
        // Im Preis? (5.92): Lauf der juengsten Kaeufe seit der Meldung
        if (daten != null && alle.any { it.kauf }) item(key = "impreis") { KaeufeImPreis(state, alle, onOpen) }
        // Gekaufte Werte, die heute unter dem Einstiegskurs stehen
        if (daten != null && alle.any { it.kauf }) item(key = "einstieg") { Column { UnterEinstiegskurs(state, alle, onOpen) } }
        if (posten.isNotEmpty()) {
            item { SectionHeader(S.copyMostTraded) }
            items(posten.take(12), key = { "m-" + it.kuerzel }) { p ->
                val gesamt = (p.kaeufe + p.verkaeufe).coerceAtLeast(1.0)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(CopyRouten.symbol(p.kuerzel)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstrumentLogo(state, CopyRouten.symbol(p.kuerzel), CopyRouten.name(p.kuerzel, p.name), size = 36)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        NamenText(CopyRouten.name(p.kuerzel, p.name), zusatz = " (${p.kuerzel})", style = MaterialTheme.typography.titleSmall)
                        PunktText(
                            S.copyBuySellEst(Fmt.dollarKompakt(p.kaeufe), Fmt.dollarKompakt(p.verkaeufe), p.anzahl) + if (p.optionen) " · " + S.copyTypeOption else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp))) {
                            if (p.kaeufe > 0) Box(Modifier.weight((p.kaeufe / gesamt).toFloat()).fillMaxSize().background(signal.up))
                            if (p.verkaeufe > 0) Box(Modifier.weight((p.verkaeufe / gesamt).toFloat()).fillMaxSize().background(signal.down))
                        }
                    }
                }
            }
        }
        if (nachMeldung.isNotEmpty()) item { SectionHeader(S.copyAllTrades) }
        nachMeldung.forEach { (m, g) ->
            item(key = "h-${m.docId}") {
                // Mit bekanntem Original: Tipp laedt das PDF herunter
                val url = pdf?.invoke(m)
                Row(
                    Modifier.fillMaxWidth().then(if (url != null) Modifier.clickable { laden(url) } else Modifier)
                        .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        S.copyFiledOn(Fmt.date(m.datum)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (url != null) {
                        Text(S.pdfKurz, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link)
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.FileDownload, S.pdfLaden,
                            tint = LocalSignal.current.link, modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        )
                    }
                }
            }
            itemsIndexed(g.orEmpty(), key = { i, _ -> "g-${m.docId}-$i" }) { _, t ->
                val d = remember(t) { de.troetenrepublik.app.market.Derivate.aus(t) }
                GeschaeftZeile(state, t, untertitel = Fmt.date(t.datum)) {
                    val basis = (d?.basis ?: t.kuerzel)?.let { CopyRouten.symbol(it) }
                    // Option mit Basispreis und Verfall: der passende Optionsschein der App, sonst der Basiswert
                    if (basis != null && d != null && d.bewertbar) derivatScope.launch {
                        val id = state.derivatFuerOption(basis, d.art == de.troetenrepublik.app.market.Derivate.Art.CALL, d.strike!!, d.verfall!!)
                        onOpen(if (id != null) DERIVAT_ROUTE + id else basis)
                    } else basis?.let { onOpen(it) }
                }
            }
        }
        if (papier.isNotEmpty()) item { CopyHinweis(S.copyPaper(papier.size, papier.joinToString(", ") { Fmt.date(it.first.datum) })) }
        if (leer) item { EmptyHint(S.copyNoFilings) }
        item { Quelle(quelle) }
    }

    // Werkzeuge der Person: nach einem geoeffneten Wert wieder dasselbe Werkzeug
    if (werkzeuge) fremdesDepot?.let { d ->
        if (!werkzeugRueck.wartet) PersonWerkzeuge(
            state, d, onDismiss = { werkzeuge = false; personWerkzeug = null; werkzeugRueck.vergessen() },
            onOpen = { werkzeugRueck.verlassen(); onOpen(it) },
            werkzeug = personWerkzeug, onWerkzeug = { personWerkzeug = it }, sofort = werkzeugRueck.sofort,
        )
        BlattBild(werkzeugRueck, "personwerkzeuge") { PersonWerkzeugeBild(state, d, personWerkzeug) }
    }
    if (nachbilden) {
        NachbildenBlatt(
            state = state,
            titel = titel,
            gewichte = { n -> Kongress.gewichte(posten, n).map { CopyRouten.symbol(it.first) to it.second } },
            namen = { sym -> posten.firstOrNull { CopyRouten.symbol(it.kuerzel) == sym }?.let { CopyRouten.name(it.kuerzel, it.name) } },
            mitAnzahl = true,
            notiz = S.copyNote(titel),
            onDismiss = { nachbilden = false },
        )
    }
}

// PDF einer Meldung in den Download-Ordner laden (seit 5.92), mit kurzer Rueckmeldung.
@Composable
internal fun rememberPdfLaden(state: AppState): (String) -> Unit {
    val laden = de.troetenrepublik.app.plattform.rememberHerunterladen { ok -> state.showToast(if (ok) S.pdfGestartet else S.pdfFehler) }
    return { url -> laden(url, Politik.dateiname(url)) }
}
