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
    val firmen = remember(alle, auswahl, cluster) {
        SecInsider.firmen(InsiderFilter.filtern(alle, auswahl.copy(richtung = InsiderFilter.Richtung.ALLE), heute, ::insiderBranche, ::insiderLand, cluster), ab)
    }
    val ohneKontakt = SecInsider.kennung(settings.secKontakt) == null
    // Einmal fuer die ganze Liste statt je Zeile (5.94): neue Zeilen beim schnellen Scrollen bleiben leicht
    val geld = rememberInsiderGeld(state)

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "kopf", contentType = "kopf") { kopf() }
        item(key = "hinweis", contentType = "hinweis") { AufklappText(S.usHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (ohneKontakt) item(key = "kontakt", contentType = "kontakt") {
            Text(
                S.usOhneKontakt, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                modifier = Modifier.clickable { oeffnen("zugaenge") }.padding(horizontal = 20.dp, vertical = 6.dp),
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
                Chip(if (auswahl.aktiv > 0) S.ifFilterZahl(auswahl.aktiv) else S.filterTitle, active = auswahl.aktiv > 0) { blatt = true }
            }
        }
        if (laedt && alle.isEmpty()) item { CopyLade() }
        val kaeufer = if (auswahl.richtung == InsiderFilter.Richtung.VERKAEUFE) emptyList() else firmen.filter { it.netto > 0 }.take(10)
        val verkaeufer = if (auswahl.richtung == InsiderFilter.Richtung.KAEUFE) emptyList() else firmen.filter { it.netto < 0 }.sortedBy { it.netto }.take(5)
        if (kaeufer.isNotEmpty()) {
            item(key = "uk-kopf") { SectionHeader(S.vdMeistgekauft) }
            items(kaeufer, key = { "uk-" + it.kuerzel }, contentType = { "firma" }) { f -> UsFirmaZeile(state, f, trends[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
        }
        if (verkaeufer.isNotEmpty()) {
            item(key = "uv-kopf") { SectionHeader(S.vdMeistverkauft) }
            items(verkaeufer, key = { "uv-" + it.kuerzel }, contentType = { "firma" }) { f -> UsFirmaZeile(state, f, trends[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
        }
        if (alle.isNotEmpty()) {
            item(key = "un-kopf") { SectionHeader(if (auswahl.aktiv > 0 || auswahl.richtung != InsiderFilter.Richtung.ALLE) S.ifTreffer(gefiltert.size) else S.vdNeueste) }
            items(gefiltert.take(300), key = { "um-" + it.a + it.kauf }, contentType = { "meldung" }) { g -> UsMeldungZeile(state, g, mitFirma = true, geld = geld) { oeffnen("instrument/" + g.k) } }
            if (gefiltert.isEmpty()) item { EmptyHint(S.ifKeine, S.resetFilters to { auswahl = InsiderFilter.Auswahl() }) }
        }
        if (!laedt && alle.isEmpty()) item { EmptyHint(S.vdLeer) }
        item { QuellenZeile(S.usQuelleKurz(daten?.stand.orEmpty()), Modifier.padding(top = 12.dp, bottom = 40.dp)) }
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InstrumentLogo(state, f.kuerzel, f.name, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(f.name, style = MaterialTheme.typography.titleSmall)
            PunktText(f.kuerzel + " · " + S.vdAnzahl(f.anzahlKaeufe, f.anzahlVerkaeufe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trend?.let { t ->
                PunktText(S.itStufe(t.stufe.ordinal) + if (t.cluster) " · " + S.itClusterKurz else "", style = MaterialTheme.typography.labelSmall, color = trendFarbe(t.wert))
            }
        }
        Text((if (f.netto > 0) "+" else "−") + dollar(kotlin.math.abs(f.netto)), style = MaterialTheme.typography.titleSmall, color = if (f.netto > 0) signal.up else signal.down)
    }
}

// Eine Meldung als Tabellenzeile: Aufbau wie InsiderZeile, links Firma, Person, Rolle und Bestand, rechts Betrag und
// Datum. Kauf oder Verkauf zeigt das Dreieck am Betrag, ein Plankauf steht bei der Rolle.
@Composable
private fun UsMeldungZeile(state: AppState, g: SecInsider.Geschaeft, mitFirma: Boolean, geld: InsiderGeld = rememberInsiderGeld(state), onClick: (() -> Unit)? = null) {
    val (betragText, orig) = geld.text(g.wert, "USD")
    // Bestand danach und wie stark er sich veraendert hat: macht sichtbar, ob jemand ein paar Prozent oder fast alles bewegt
    val bestand = g.nach?.let { nach ->
        val vor = g.vor
        val aenderung = if (vor != null && vor > 0) Fmt.signedPct((nach - vor) / vor * 100, 0) else null
        S.usBestandDanach(Fmt.qty(Math.round(nach).toDouble()), aenderung)
    }
    InsiderZeile(
        name = if (mitFirma) g.e else g.p,
        angaben = listOfNotNull(
            g.p.takeIf { mitFirma && it.isNotBlank() },
            g.t.ifBlank { S.usRolle(g.r) } + if (g.plan) " · " + S.plan else "",
            bestand,
        ),
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

// Wie viel die Insider nach ihrer letzten Meldung direkt halten, bewertet zum juengsten gemeldeten Kurs: die fuenf
// groessten Bestaende und die Summe.
@Composable
private fun UsBestaende(eigen: List<SecInsider.Geschaeft>) {
    val bestaende = remember(eigen) { SecInsider.bestaende(eigen) }
    val kurs = remember(eigen) { eigen.firstOrNull { it.preis > 0 }?.preis ?: 0.0 }
    if (bestaende.isEmpty()) return
    Zwischenkopf(S.usBestaendeTitel)
    bestaende.take(5).forEach { b ->
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
                Text(if (kurs > 0) dollar(b.stueck * kurs) else S.usStueck(Fmt.qty(Math.round(b.stueck).toDouble())), style = MaterialTheme.typography.bodyMedium)
                if (kurs > 0) Text(S.usStueck(Fmt.compact(b.stueck)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val summe = bestaende.sumOf { it.stueck } * kurs
    AufklappText(
        listOfNotNull(if (kurs > 0) S.usBestaendeSumme(dollar(summe), bestaende.size) else null, S.usBestaendeHinweis).joinToString(" "),
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// Auf der Seite einer US-Aktie: Insider-Trend und die Geschaefte der Fuehrung aus zwoelf Monaten. Mit Kontaktadresse
// holt die Seite neue Meldungen dieses Werts nach.
@Composable
internal fun SecKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    val daten by state.sec.collectAsStateWithLifecycle()
    val geld = rememberInsiderGeld(state)
    // Platzhalter nur, solange der erste Ladeversuch laeuft (5.93): Schlaegt er fehl, bleibt kein Leerraum
    var geladen by gemerkt<Boolean>(merker, "sec/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        // Einmal je Seite: beim Zurueckscrollen nicht erneut bei der SEC nachfragen
        if (geladen) return@LaunchedEffect
        try { runCatching { state.secLaden(symbol = symbol) } } finally { if (isActive) geladen = true }
    }
    val d = daten ?: run { if (!geladen) Spacer(Modifier.height(96.dp)); return }
    val eigen = remember(d, symbol) { d.geschaefte.filter { it.k.equals(symbol, true) } }
    if (eigen.isEmpty()) return
    Column {
        SectionHeader(S.vdKarteTitel)
        // Ein Jahr Zeitraum (seit 5.93); ist darin keine Meldung, bleiben die letzten Meldungen stehen
        val ab = remember { InsiderBetraege.abTag(java.time.LocalDate.now()) }
        val liste = remember(eigen, ab) { eigen.filter { it.d >= ab } }.ifEmpty { eigen }
        var alle by gemerkt<Boolean>(merker, "sec/$symbol/alle") { false }
        remember(liste) { SecInsider.trend(liste, ab) }?.let { InsiderTrendBlock(it) }
        val kaeufe = liste.filter { it.kauf }
        val verkaeufe = liste.filter { !it.kauf }
        // Summen in der Anzeigewaehrung; ohne Wechselkurs in Dollar
        fun summe(l: List<SecInsider.Geschaeft>): String {
            val betraege = l.filter { it.wert > 0 }.map { geld.umrechnen(it.wert, "USD") }
            val s = InsiderBetraege.summe(betraege, geld.ziel)
            if (s > 0 || betraege.isEmpty()) return Fmt.mitSymbol(Fmt.compact(s), Derivatives.currencySymbol(geld.ziel))
            return dollar(l.sumOf { it.wert })
        }
        Text(
            S.vdZusammenfassung(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe)),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        liste.take(if (alle) 30 else 5).forEach { g -> UsMeldungZeile(state, g, mitFirma = false) }
        if (liste.size > 5) ListenPfeil(alle) { alle = !alle }
        UsBestaende(eigen)
        Text(
            S.usQuelle(d.stand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
