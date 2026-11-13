package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.InsiderBetraege
import de.troetenrepublik.app.engine.InsiderFilter
import de.troetenrepublik.app.engine.InsiderTrend
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.engine.Signale
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.isActive

// Insidergeschaefte an US-Boersen (SEC Form 4, seit 5.6): Liste im Reiter „Vorstände“ und Karte auf der Aktienseite.

private fun dollar(v: Double): String = Fmt.mitSymbol(Fmt.compact(v), "$")

private fun tagUs(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

@Composable
internal fun VorstaendeUsa(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit, kopf: @Composable () -> Unit) {
    val daten by state.sec.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var laedt by remember { mutableStateOf(daten == null) }
    LaunchedEffect(Unit) { runCatching { state.secLaden() }; laedt = false }
    katalogBereit()
    // Filter (seit 5.92): Richtung in der Leiste, alles Weitere im Blatt hinter „Filter“
    var auswahl by remember { mutableStateOf(InsiderFilter.Auswahl()) }
    var blatt by remember { mutableStateOf(false) }
    val alle = daten?.geschaefte.orEmpty()
    val heute = remember { java.time.LocalDate.now().toString() }
    val ab = remember { java.time.LocalDate.now().minusDays(90).toString() }
    val trends = remember(alle) { alle.filter { it.d >= ab }.groupBy { it.k }.mapValues { (_, g) -> SecInsider.trend(g, ab) } }
    val cluster = remember(trends) { trends.filterValues { it?.cluster == true }.keys }
    val gefiltert = remember(alle, auswahl, cluster) {
        InsiderFilter.filtern(alle, auswahl, heute, ::insiderBranche, ::insiderLand, cluster)
    }
    // Firmen aus den gefilterten Meldungen, Richtung ausgenommen, damit Kaeufer und Verkaeufer gemeinsam stehen
    val firmenBasis = remember(alle, auswahl, cluster) {
        InsiderFilter.filtern(alle, auswahl.copy(richtung = InsiderFilter.Richtung.ALLE), heute, ::insiderBranche, ::insiderLand, cluster)
    }
    val firmen = remember(firmenBasis) { SecInsider.firmen(firmenBasis, ab) }
    // Trend je Firma aus denselben Meldungen wie die Liste (0.77), damit Etikett und Summe zusammenpassen
    val trendsGezeigt = remember(firmenBasis) { firmenBasis.filter { it.d >= ab }.groupBy { it.k }.mapValues { (_, g) -> SecInsider.trend(g, ab) } }
    val ohneKontakt = SecInsider.kennung(settings.secKontakt) == null
    // Kurse und Marktwerte der gezeigten Firmen (0.76) fuer „seit Geschaeft“ und „vom Marktwert“
    val gezeigt = remember(gefiltert) { gefiltert.take(300) }
    var kurse by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    val symbole = remember(gezeigt) { gezeigt.map { it.k }.distinct() }
    LaunchedEffect(symbole) { kurse = runCatching { state.market.snapshot(symbole) }.getOrDefault(emptyMap()) }
    val planAnteil = remember(gefiltert) { Signale.planAnteil(gefiltert) }
    // Einmal fuer die ganze Liste statt je Zeile (5.94): neue Zeilen beim schnellen Scrollen bleiben leicht
    val geld = rememberInsiderGeld(state)
    // Seltene Kaeufe der letzten 30 Tage, im Hintergrund gerechnet (0.77)
    val selten = rememberSelten(daten)
    var seltenOffen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "kopf", contentType = "kopf") { kopf() }
        daten?.let { d -> item(key = "stand", contentType = "stand") { InsiderStand(d.stand, d.luecke()) } }
        item(key = "hinweis", contentType = "hinweis") { HinweisInfo(S.usHinweis) }
        if (ohneKontakt) item(key = "kontakt", contentType = "kontakt") {
            Text(
                S.usOhneKontakt, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                modifier = Modifier.textKlick(20.dp) { oeffnen("zugaenge") }.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        if (alle.isNotEmpty()) item(key = "filter") {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(InsiderFilter.Richtung.ALLE to S.all, InsiderFilter.Richtung.KAEUFE to S.vdKaeufe, InsiderFilter.Richtung.VERKAEUFE to S.vdVerkaeufe).forEach { (r, titel) ->
                    Chip(titel, active = auswahl.richtung == r) { auswahl = auswahl.copy(richtung = r) }
                }
                Chip(S.ifNurFuehrung, active = de.troetenrepublik.app.engine.InsiderFuehrung.aktiv(auswahl.rollen)) {
                    auswahl = auswahl.copy(rollen = if (de.troetenrepublik.app.engine.InsiderFuehrung.aktiv(auswahl.rollen)) emptySet() else de.troetenrepublik.app.engine.InsiderFuehrung.ROLLEN)
                }
                Chip(if (auswahl.aktiv > 0) S.ifFilterZahl(auswahl.aktiv) else S.filterTitle, active = auswahl.aktiv > 0) { blatt = true }
            }
        }
        if (laedt && alle.isEmpty()) item { CopyLade() }
        val kaeufer = if (auswahl.richtung == InsiderFilter.Richtung.VERKAEUFE) emptyList() else firmen.filter { it.netto > 0 }.take(10)
        val verkaeufer = if (auswahl.richtung == InsiderFilter.Richtung.KAEUFE) emptyList() else firmen.filter { it.netto < 0 }.sortedBy { it.netto }.take(5)
        if (kaeufer.isNotEmpty()) {
            item(key = "uk-kopf") { SectionHeader(S.vdMeistgekauft) }
            items(kaeufer, key = { "uk-" + it.kuerzel }, contentType = { "firma" }) { f -> UsFirmaZeile(state, f, trendsGezeigt[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
            // Cluster-Kauf beim ersten Auftreten auf der Seite kurz erklaeren
            if (kaeufer.any { trendsGezeigt[it.kuerzel]?.cluster == true }) item(key = "uk-cluster", contentType = "kern-text") {
                Row(Modifier.fillMaxWidth().clickable { oeffnen(CopyRouten.WIRKT_DAS) }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(S.itCluster, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    AufklappPfeil(false)
                }
            }
        }
        if (verkaeufer.isNotEmpty()) {
            item(key = "uv-kopf") { SectionHeader(S.vdMeistverkauft) }
            items(verkaeufer, key = { "uv-" + it.kuerzel }, contentType = { "firma" }) { f -> UsFirmaZeile(state, f, trendsGezeigt[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
        }
        if (planAnteil != null && !auswahl.ohnePlan) item(key = "plan", contentType = "plan") { KeyValueRow(S.usPlanLabel, Fmt.pct(planAnteil * 100, 0)) }
        item(key = "konvergenz", contentType = "nav") {
            WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.konvergenz(), Modifier.fillMaxWidth()) { NavRow(S.konTitel, S.kvNav) { oeffnen(CopyRouten.KONVERGENZ) } }
        }
        item(key = "wirkt-das", contentType = "nav") { NavRow(S.wdTitel, S.wdNav) { oeffnen(CopyRouten.WIRKT_DAS) } }
        seltenAbschnitt(state, selten, seltenOffen, { seltenOffen = !seltenOffen }, oeffnen)
        if (alle.isNotEmpty()) {
            item(key = "un-kopf") { SectionHeader(if (auswahl.aktiv > 0 || auswahl.richtung != InsiderFilter.Richtung.ALLE) S.ifTreffer(gefiltert.size) else S.vdNeueste) }
            items(gezeigt, key = { "um-" + it.a + it.kauf }, contentType = { "meldung" }) { g -> UsMeldungZeile(state, g, mitFirma = true, geld = geld, kurs = kurse[g.k]) { oeffnen("instrument/" + g.k) } }
            if (gefiltert.isEmpty()) item { EmptyHint(S.ifKeine, S.resetFilters to { auswahl = InsiderFilter.Auswahl() }) }
        }
        if (!laedt && alle.isEmpty()) item { EmptyHint(S.vdLeer) }
        item { Spacer(Modifier.height(40.dp)) }
    }
    if (blatt) InsiderFilterBlatt(alle, auswahl, onChange = { auswahl = it }, onDismiss = { blatt = false })
}

// Branche und Land eines US-Kuerzels laut Katalog der App; null, wenn der Wert nicht im Katalog steht.
private fun insiderBranche(k: String): String? = de.troetenrepublik.app.market.Catalog.industryOf(k)

private fun insiderLand(k: String): String? =
    de.troetenrepublik.app.market.Catalog.loaded()?.aktien?.firstOrNull { it.s == k }?.c?.takeIf { it.isNotBlank() }

// Gruppe von Chips im Filterblatt mit Zwischenueberschrift.
@Composable
private fun FilterGruppe(titel: String, inhalt: @Composable () -> Unit) {
    Zwischenkopf(titel, Modifier.padding(top = 8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { inhalt() }
}

// Blatt mit allen Filtern ausser der Richtung: Rolle, Groesse, Zeitraum, Branche, Land, Cluster, ohne Verkaufsplaene.
// Branchen und Laender nur, soweit sie in den Meldungen vorkommen.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InsiderFilterBlatt(alle: List<SecInsider.Geschaeft>, auswahl: InsiderFilter.Auswahl, onChange: (InsiderFilter.Auswahl) -> Unit, onDismiss: () -> Unit) {
    val branchen = remember(alle) { InsiderFilter.haeufigste(alle, ::insiderBranche) }
    val laender = remember(alle) { InsiderFilter.haeufigste(alle) { k -> insiderLand(k) ?: "US" } }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.filterTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            FilterGruppe(S.ifRolle) {
                Chip(S.all, active = auswahl.rollen.isEmpty()) { onChange(auswahl.copy(rollen = emptySet())) }
                InsiderTrend.Rolle.entries.forEach { r ->
                    Chip(S.itRolle(r.ordinal), active = r in auswahl.rollen) {
                        onChange(auswahl.copy(rollen = if (r in auswahl.rollen) auswahl.rollen - r else auswahl.rollen + r))
                    }
                }
            }
            FilterGruppe(S.ifGroesse) {
                InsiderFilter.GROESSEN.forEach { g ->
                    Chip(if (g <= 0) S.all else S.ifAb(dollar(g)), active = auswahl.mindestens == g) { onChange(auswahl.copy(mindestens = g)) }
                }
            }
            FilterGruppe(S.widgetZeitraum) {
                InsiderFilter.ZEITRAEUME.forEach { t ->
                    Chip(S.ifTage(t), active = auswahl.tage == t) { onChange(auswahl.copy(tage = t)) }
                }
            }
            if (branchen.size > 1) FilterGruppe(S.industry) {
                Chip(S.all, active = auswahl.branche == null) { onChange(auswahl.copy(branche = null)) }
                branchen.forEach { b -> Chip(S.industryName(b), active = auswahl.branche == b) { onChange(auswahl.copy(branche = b)) } }
            }
            if (laender.size > 1) FilterGruppe(S.country) {
                Chip(S.all, active = auswahl.land == null) { onChange(auswahl.copy(land = null)) }
                laender.forEach { l -> Chip(S.countryLabel(l), active = auswahl.land == l) { onChange(auswahl.copy(land = l)) } }
            }
            FilterGruppe(S.ifMehr) {
                Chip(S.itClusterKurz, active = auswahl.nurCluster) { onChange(auswahl.copy(nurCluster = !auswahl.nurCluster)) }
                Chip(S.ifOhnePlan, active = auswahl.ohnePlan) { onChange(auswahl.copy(ohnePlan = !auswahl.ohnePlan)) }
            }
            Text(
                S.ifHinweis + " " + S.itCluster, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (auswahl.aktiv > 0) ActionLineWide(S.resetFilters) { onChange(InsiderFilter.Auswahl(richtung = auswahl.richtung)) }
        }
    }
}

@Composable
private fun UsFirmaZeile(state: AppState, f: SecInsider.Firma, trend: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis?, onClick: () -> Unit) {
    val signal = LocalSignal.current
    // Betrag in der Anzeigewaehrung wie auf der Seite des Werts; ohne Wechselkurs in Dollar
    val geld = rememberInsiderGeld(state)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InstrumentLogo(state, f.kuerzel, f.name, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(f.name, style = MaterialTheme.typography.titleSmall)
            PunktText(f.kuerzel + " · " + S.vdAnzahl(f.anzahlKaeufe, f.anzahlVerkaeufe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trend?.let { t ->
                // Die Stufe zaehlt Personen, der Betrag rechts Dollar. Zeigen beide in verschiedene Richtungen (ein
                // Grossaktionaer kauft, zwei Vorstaende verkaufen), stehen statt der Stufe die Personen da (0.77).
                val gegen = t.wert != 0 && f.netto != 0.0 && (t.wert > 0) != (f.netto > 0)
                if (gegen) PunktText(S.sgInsiderWert(t.kaeufer, t.verkaeufer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else PunktText(S.itStufe(t.stufe.ordinal) + if (t.cluster) " · " + S.itClusterKurz else "", style = MaterialTheme.typography.labelSmall, color = trendFarbe(t.wert))
            }
        }
        Text((if (f.netto > 0) "+" else "−") + geld.text(kotlin.math.abs(f.netto), "USD").first, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, color = if (f.netto > 0) signal.up else signal.down)
    }
}

// Eine Meldung als Tabellenzeile: Aufbau wie InsiderZeile, links Firma, Person, Rolle und Bestand, rechts Betrag und
// Datum. Kauf oder Verkauf zeigt das Dreieck am Betrag, ein Plankauf steht bei der Rolle.
@Composable
private fun UsMeldungZeile(state: AppState, g: SecInsider.Geschaeft, mitFirma: Boolean, geld: InsiderGeld = rememberInsiderGeld(state), kurs: Quote? = null, onClick: (() -> Unit)? = null) {
    val (betragText, orig) = geld.text(g.wert, "USD")
    // Bestand danach und wie stark er sich veraendert hat: macht sichtbar, ob jemand ein paar Prozent oder fast alles bewegt
    val bestand = g.nach?.let { nach ->
        val vor = g.vor
        val aenderung = if (vor != null && vor > 0) Fmt.signedPct((nach - vor) / vor * 100, 0) else null
        S.usBestandDanach(Fmt.num(Math.round(nach).toDouble(), 0), aenderung)
    }
    // Kursaenderung seit dem Geschaeft und Anteil des Betrags am Marktwert (nur bei Notierung in Dollar)
    val dollar = Signale.dollar(kurs)
    val seit = dollar?.let { Signale.seitKauf(g.preis, it.kurs) }?.let { S.usSeitKauf(Fmt.signedPct(it * 100, 1)) }
    val anteil = dollar?.let { Signale.vomMarktwert(g.wert, it.marktwert) }?.let { S.usVomMarktwert(Fmt.pct(it * 100, if (it < 0.001) 3 else 2)) }
    InsiderZeile(
        name = if (mitFirma) g.e else g.p,
        angaben = listOfNotNull(
            g.p.takeIf { mitFirma && it.isNotBlank() },
            g.t.ifBlank { S.usRolle(g.r) } + if (g.plan) " · " + S.plan else "",
        ),
        unten = listOfNotNull(bestand, listOfNotNull(seit, anteil).joinToString(" · ").takeIf { it.isNotBlank() }),
        kauf = g.kauf,
        verkauf = !g.kauf,
        betrag = betragText,
        datum = tagUs(g.d),
        zusatz = orig,
        vorne = if (mitFirma) {
            { InstrumentLogo(state, g.k, g.e, size = 36) }
        } else null,
        onClick = onClick,
    )
}

/// Wie viel die Insider nach ihrer letzten Meldung direkt halten, bewertet zum juengsten gemeldeten Kurs: die fuenf
// groessten Bestaende und die Summe.
@Composable
private fun BestandZeile(b: SecInsider.Bestand, kurs: Double, geld: InsiderGeld) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            NamenText(b.person, style = MaterialTheme.typography.bodyMedium)
            Text(
                b.titel.ifBlank { S.usRolle(b.rolle) }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (kurs > 0) geld.text(b.stueck * kurs, "USD").first else S.usStueck(Fmt.qty(Math.round(b.stueck).toDouble())), style = MaterialTheme.typography.bodyMedium)
            if (kurs > 0) Text(S.usStueck(Fmt.compact(b.stueck)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun bestaendeModell(eigen: List<SecInsider.Geschaeft>, geld: InsiderGeld): ListenModell? {
    // Betraege in der Anzeigewaehrung; ohne Wechselkurs in Dollar
    fun dollar(v: Double) = geld.text(v, "USD").first
    val bestaende = SecInsider.bestaende(eigen)
    val kurs = eigen.firstOrNull { it.preis > 0 }?.preis ?: 0.0
    if (bestaende.isEmpty()) return null
    val summe = bestaende.sumOf { it.stueck } * kurs
    val text = listOfNotNull(if (kurs > 0) S.usBestaendeSumme(dollar(summe), bestaende.size) else null, S.usBestaendeHinweis).joinToString(" ")
    return ListenModell(
        titel = S.usBestaendeTitel,
        gross = false,
        kern = KernZeile(listOfNotNull(if (kurs > 0) KernTeil(dollar(summe)) else null, KernTeil(S.kernPersonen(bestaende.size)))),
        zeilen = bestaende.take(5).mapIndexed { i, b -> ZeilenDaten("$i") { BestandZeile(b, kurs, geld) } },
        nachspann = { AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) },
    )
}

// Auf der Seite einer US-Aktie: Insider-Trend und die Geschaefte der Fuehrung aus zwoelf Monaten. Mit Kontaktadresse
// holt die Seite neue Meldungen dieses Werts nach. Die Karte laedt und rechnet, die Zeilen kommen als Eintraege der
// Seite (secAbschnitte).
@Composable
// zeile: Die Karte steht im Blatt hinter einer 90-Tage-Zeile und zeigt deren Trend zuerst.
internal fun SecKarte(state: AppState, symbol: String, merker: SeitenMerker? = null, zeile: Boolean = false, onCluster: (() -> Unit)? = null) {
    val daten by state.sec.collectAsStateWithLifecycle()
    val geld = rememberInsiderGeld(state)
    var geladen by gemerkt<Boolean>(merker, "sec/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        // Einmal je Seite: beim Zurueckscrollen nicht erneut bei der SEC nachfragen
        if (geladen) return@LaunchedEffect
        try { runCatching { state.secLaden(symbol = symbol) } } finally { if (isActive) geladen = true }
    }
    val kurse by state.quotes.collectAsStateWithLifecycle()
    val kurs = kurse[symbol]
    val d = daten
    val modelle = remember(d, symbol, kurs, geld, zeile) {
        val eigen = d?.geschaefte?.filter { it.k.equals(symbol, true) }.orEmpty()
        if (d == null || eigen.isEmpty()) null to null else secModell(state, d, eigen, geld, kurs, zeile, onCluster) to bestaendeModell(eigen, geld)
    }
    ModellAusgabe(merker, "sec-eigen", modelle.first)
    // Kaeufer am Markt (Code P), die laut Meldungen noch halten und unter ihrem Einstieg stehen (0.78)
    val handel = remember(d, symbol) {
        de.troetenrepublik.app.engine.CopyUebersicht.ausInsidern(d?.geschaefte?.filter { it.k.equals(symbol, true) }.orEmpty()) { symbol }.first
    }
    InsiderEinstiegKarte(state, symbol, "sec", handel, "USD", merker)
    ModellAusgabe(merker, "sec-best", modelle.second)
}

internal fun LazyListScope.secAbschnitte(merker: SeitenMerker) {
    listenAbschnitt(merker, "sec-eigen")
    listenAbschnitt(merker, "sec-einstieg")
    listenAbschnitt(merker, "sec-verkauft")
    listenAbschnitt(merker, "sec-best")
}

private fun secModell(
    state: AppState, d: SecInsider.Datei, eigen: List<SecInsider.Geschaeft>, geld: InsiderGeld, kurs: Quote?, zeile: Boolean = false, onCluster: (() -> Unit)? = null,
): ListenModell {
    // Ein Jahr Zeitraum (seit 5.93); ist darin keine Meldung, bleiben die letzten Meldungen stehen
    val ab = InsiderBetraege.abTag(java.time.LocalDate.now())
    val imJahr = eigen.filter { it.d >= ab }
    val liste = imJahr.ifEmpty { eigen }
    val trend = SecInsider.trend(liste, ab)
    // Im Blatt hinter einer 90-Tage-Zeile zuerst deren Trend (gleiches Fenster, gleiche Zaehlung)
    val kurz = if (zeile) SecInsider.trend(eigen, Signale.insiderAb(standEnde(d), de.troetenrepublik.app.engine.Uhr.heute())) else null
    val kaeufe = liste.filter { it.kauf }
    val verkaeufe = liste.filter { !it.kauf }
    fun betraege(l: List<SecInsider.Geschaeft>) = l.filter { it.wert > 0 }.map { geld.umrechnen(it.wert, "USD") }
    // Summen in der Anzeigewaehrung; ohne Wechselkurs in Dollar
    fun summe(l: List<SecInsider.Geschaeft>): String {
        val b = betraege(l)
        val s = InsiderBetraege.summe(b, geld.ziel)
        val fehlt = InsiderBetraege.ohneKurs(b, geld.ziel)
        if (s > 0 || b.isEmpty()) return Fmt.mitSymbol(Fmt.compact(s), Derivatives.currencySymbol(geld.ziel)) + if (fehlt > 0) " " + S.vdOhneKurs(fehlt) else ""
        return dollar(l.sumOf { it.wert })
    }
    // Ohne Meldung im Jahr stehen aeltere da: dann ohne Zeitraum
    val zusammen = if (imJahr.isEmpty()) S.vdZusammenfassungAelter(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe))
        else S.vdZusammenfassung(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe))
    val plan = Signale.planAnteil(liste)
    val selten = seltenFuerWert(d, eigen)
    val zeilen = liste.mapIndexed { i, g -> ZeilenDaten("$i") { UsMeldungZeile(state, g, mitFirma = false, geld = geld, kurs = kurs) } }
    return ListenModell(
        titel = if (ListenKern.lohnt(zeilen.size)) S.kernMeldungen else "",
        gross = false,
        kern = meldungsKern(kaeufe.size, verkaeufe.size, summeOderNull(betraege(kaeufe), geld.ziel), summeOderNull(betraege(verkaeufe), geld.ziel), geld.ziel),
        zeilen = zeilen,
        kopf = {
            Column {
                SectionHeader(S.vdKarteTitel)
                // Stand dieses Werts: Tag, an dem seine Meldungen zuletzt gelesen wurden (SEC-Liste oder Nasdaq)
                val (standWert, lueckeWert) = d.standFuer(eigen.firstOrNull()?.k.orEmpty())
                InsiderStand(standWert, lueckeWert)
                SeltenKern(selten)
                InsiderTrends(kurz, trend, onCluster)
            }
        },
        vorspann = {
            Column {
                Text(zusammen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                plan?.let { KeyValueRow(S.usPlanLabel, Fmt.pct(it * 100, 0)) }
            }
        },
    )
}
