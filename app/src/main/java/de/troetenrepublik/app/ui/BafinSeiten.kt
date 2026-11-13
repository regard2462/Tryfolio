package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.InsiderBetraege
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.Politik
import kotlinx.coroutines.isActive

// Eigengeschaefte von Vorstaenden und Aufsichtsraeten deutscher Emittenten und bedeutende Stimmrechtsanteile aus
// den Datenbanken der BaFin (seit 5.5, siehe market/Bafin.kt).

// Betrag einer Meldung oder Firma: Euro wie ueberall, andere Waehrungen mit ihrem Zeichen.
internal fun bafinBetrag(v: Double, w: String): String =
    if (w.isBlank() || w == "EUR") Fmt.compactEuro(v) else Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w))

private fun tag(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

// Reiter „Insider“ im Copytrading: Insidergeschaefte an US-Boersen (SEC). Die deutschen Meldungen stehen auf der
// Seite der jeweiligen Aktie.
@Composable
internal fun VorstaendeListe(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    // 5.95: Director Dealings Grossbritanniens ueber Investegate
    var igOffen by remember { mutableStateOf(false) }
    VorstaendeUsa(state, liste, oeffnen) {
        Column {
            NavRow(S.cuTitel, S.cuNavUnter) { oeffnen(CopyRouten.uebersicht("INSIDER")) }
            NavRow(S.q5IgAlleTitel, S.q5IgAlleUnter) { igOffen = true }
        }
    }
    if (igOffen) InvestegateListeBlatt(state, onDismiss = { igOffen = false })
}

// Eine Meldung als Tabellenzeile: Aufbau wie InsiderZeile, links Firma (oder bei mitFirma = false die Person), Person,
// Rolle und Art, rechts Betrag und Datum, darunter der Originalbetrag.
@Composable
private fun MeldungZeile(state: AppState, g: Bafin.Geschaeft, mitFirma: Boolean, geld: InsiderGeld = rememberInsiderGeld(state), onClick: (() -> Unit)? = null) {
    val (betrag, orig) = g.vol?.let { geld.text(it, g.w.ifBlank { "EUR" }) } ?: ("–" to null)
    InsiderZeile(
        name = if (mitFirma) g.e else g.p,
        angaben = listOfNotNull(g.p.takeIf { mitFirma && it.isNotBlank() }, S.vdRolle(g.r) + " · " + S.vdArt(g.art)),
        kauf = g.kauf,
        verkauf = g.verkauf,
        betrag = betrag,
        datum = tag(g.d),
        zusatz = orig,
        vorne = if (mitFirma) {
            { InstrumentLogo(state, g.k ?: g.isin, g.e, size = 36) }
        } else null,
        onClick = onClick,
    )
}

// Betrag mit dem gleichen gezeichneten Richtungsdreieck wie im Portfolio (ChangeValue); ohne Richtung nur der Text.
@Composable
internal fun RichtungsBetrag(kauf: Boolean, verkauf: Boolean, text: String, style: TextStyle = MaterialTheme.typography.titleSmall) {
    when {
        kauf -> ChangeValue(1.0, text, style = style, nullAusText = false)
        verkauf -> ChangeValue(-1.0, text, style = style, nullAusText = false)
        // Betraege bleiben in einer Zeile
        else -> Text(text, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

// Zeile einer Insiderliste (0.68), ueberall gleich aufgebaut: links der Name und direkt darunter die Angaben (Person,
// Rolle und Art, Bestand), rechts der Betrag und darunter das Datum. Beide Spalten stehen oben, Werte bleiben einzeilig.
// zusatz: Originalbetrag, wenn umgerechnet wurde; vorne: Logo der Firma.
@Composable
internal fun InsiderZeile(
    name: String,
    angaben: List<String>,
    kauf: Boolean,
    verkauf: Boolean,
    betrag: String,
    datum: String,
    zusatz: String? = null,
    vorne: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    // Zeilen mit Zahlen unter der Zeile ueber die ganze Breite (0.77), damit kein Wert umbricht
    unten: List<String> = emptyList(),
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val klein = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (vorne != null) {
                vorne()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                NamenText(name, style = MaterialTheme.typography.titleSmall)
                angaben.filter { it.isNotBlank() }.forEach { PunktText(it, style = klein, color = grau) }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                RichtungsBetrag(kauf, verkauf, betrag, MaterialTheme.typography.titleSmall)
                if (datum.isNotBlank()) Text(datum, style = klein, color = grau, maxLines = 1, softWrap = false)
                zusatz?.let { Text(it, style = klein, color = grau, maxLines = 1, softWrap = false) }
            }
        }
        val zeilen = unten.filter { it.isNotBlank() }
        if (zeilen.isNotEmpty()) Column(Modifier.padding(start = if (vorne != null) 48.dp else 0.dp)) {
            zeilen.forEach { PunktText(it, style = klein, color = grau) }
        }
    }
}

@Composable
internal fun trendFarbe(wert: Int): androidx.compose.ui.graphics.Color {
    val signal = LocalSignal.current
    return when {
        wert > 15 -> signal.up
        wert < -15 -> signal.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// Insider-Trend auf einen Blick: Balken von verkaufend bis kaufend, Personen je Rolle, Cluster-Kauf, Erklaerung.
// titel nennt den Zeitraum der Rechnung; onCluster fuehrt vom Hinweis auf den Cluster-Kauf zu „Wirkt das?“.
@Composable
internal fun InsiderTrendBlock(t: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis, titel: String = S.itTitel, onCluster: (() -> Unit)? = null) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(S.itStufe(t.stufe.ordinal), style = MaterialTheme.typography.titleSmall, color = trendFarbe(t.wert), maxLines = 1, softWrap = false)
        }
        // Anteil der gewichteten Kaeufer links gruen, Verkaeufer rechts rot
        val plus = ((t.wert + 100) / 200f).coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            if (plus > 0f) Box(Modifier.weight(plus).fillMaxHeight().background(signal.up))
            if (plus < 1f) Box(Modifier.weight(1f - plus).fillMaxHeight().background(signal.down))
        }
        Text(S.itAnzahl(t.kaeufer, t.verkaeufer), style = MaterialTheme.typography.bodyMedium)
        if (t.cluster) {
            Row(
                Modifier.fillMaxWidth().then(if (onCluster != null) Modifier.zeilenKlick(onClick = onCluster) else Modifier).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.itCluster, style = MaterialTheme.typography.bodySmall, color = signal.up, modifier = Modifier.weight(1f))
                if (onCluster != null) {
                    Spacer(Modifier.width(8.dp))
                    ChevronRight()
                }
            }
        }
        // Vorn ein Satz in einfachen Worten; wer wie gehandelt hat und wie gerechnet wird, steht hinter „mehr“
        val rollen = t.nachRolle.entries.joinToString(" ") { (rolle, kv) -> S.itRolleZeile(S.itRolle(rolle.ordinal), kv.first, kv.second) + "." }
        AufklappText(
            listOf(S.itSatz(t.stufe.ordinal), rollen, S.itHinweis).filter { it.isNotBlank() }.joinToString("\n\n"),
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Trend der getippten Zeile (90 Tage) zuerst, darunter die laengere Sicht (12 Monate), wenn sie etwas anderes sagt.
@Composable
internal fun InsiderTrends(kurz: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis?, lang: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis?, onCluster: (() -> Unit)?) {
    kurz?.let { InsiderTrendBlock(it, S.itTitelTage(de.troetenrepublik.app.engine.Signale.INSIDER_TAGE), onCluster) }
    lang?.takeIf { it != kurz }?.let { InsiderTrendBlock(it, onCluster = onCluster) }
}

/// Auf der Seite einer Aktie: Eigengeschaefte der Fuehrung (zwoelf Monate) und Grossaktionaere, nur mit Daten. Die Karte
// laedt und rechnet; die Zeilen kommen als Eintraege der Seite (insiderAbschnitte, grossaktionaerAbschnitte).
@Composable
// zeile: Die Karte steht im Blatt hinter einer 90-Tage-Zeile und zeigt deren Trend zuerst. mitGross: mit den
// Grossaktionaeren (auf der Seite stehen sie unter „Grosse Halter“, in den Blaettern entfallen sie).
internal fun BafinKarte(
    state: AppState, symbol: String, isin: String, name: String, merker: SeitenMerker? = null,
    zeile: Boolean = false, mitGross: Boolean = true, onCluster: (() -> Unit)? = null,
) {
    val daten by state.bafin.collectAsStateWithLifecycle()
    var fertig by gemerkt<Boolean>(merker, "bafin/$symbol/fertig") { false }
    LaunchedEffect(Unit) {
        // Einmal je Seite: beim Zurueckscrollen steht der Stand schon da
        if (fertig) return@LaunchedEffect
        runCatching { state.bafinLaden(netz = false) }
        if (isin.isNotBlank() && !isin.startsWith("US")) runCatching { state.bafinLaden() }
        fertig = true
    }
    val geld = rememberInsiderGeld(state)
    val d = daten
    val modelle = remember(d, isin, symbol, name, geld, zeile, mitGross) {
        if (d == null) null to null else bafinModelle(state, d, isin, symbol, name, geld, zeile, onCluster).let { if (mitGross) it else it.first to null }
    }
    if (modelle.first != null || modelle.second != null) QuellenMelden(NennQuellen.BAFIN)
    ModellAusgabe(merker, "bafin-eigen", modelle.first)
    // Kaeufer, die laut Meldungen noch halten und unter ihrem Einstieg stehen (0.78); nur Aktien in der Handelswaehrung
    val kurse by state.quotes.collectAsStateWithLifecycle()
    val waehrung = kurse[symbol]?.nativeCurrency.orEmpty()
    val handel = remember(d, isin, symbol, waehrung) {
        d?.eigengeschaefte.orEmpty().filter { ((isin.isNotBlank() && it.isin == isin) || it.k == symbol) && it.i.equals("Aktie", true) }.mapNotNull {
            de.troetenrepublik.app.engine.CopyUebersicht.insiderGeschaeft(it.p, symbol, it.kauf, it.verkauf, it.d, it.vol, it.preis, it.w.ifBlank { "EUR" }, waehrung)
        }
    }
    InsiderEinstiegKarte(state, symbol, "bafin", handel, waehrung, merker)
    ModellAusgabe(merker, "bafin-gross", modelle.second)
}

private fun bafinModelle(
    state: AppState, d: Bafin.Datei, isin: String, symbol: String, name: String, geld: InsiderGeld, zeile: Boolean, onCluster: (() -> Unit)?,
): Pair<ListenModell?, ListenModell?> {
    val eigen = d.eigengeschaefte.filter { (isin.isNotBlank() && it.isin == isin) || it.k == symbol }
    val anteile = Bafin.anteileAn(name, d.stimmrechte)
    var meldungen: ListenModell? = null
    if (eigen.isNotEmpty()) {
        // Ein Jahr Zeitraum (seit 5.93); ist darin keine Meldung, bleiben die letzten Meldungen stehen
        val ab = InsiderBetraege.abTag(java.time.LocalDate.now())
        val imJahr = eigen.filter { it.d >= ab }
        val liste = imJahr.ifEmpty { eigen }
        val trend = Bafin.trend(liste, ab)
        // Im Blatt hinter einer 90-Tage-Zeile zuerst deren Trend (gleiches Fenster, gleiche Zaehlung)
        val kurz = if (zeile) Bafin.trend(eigen, de.troetenrepublik.app.engine.Signale.insiderAb(null, de.troetenrepublik.app.engine.Uhr.heute())) else null
        val kaeufe = liste.filter { it.kauf }
        val verkaeufe = liste.filter { it.verkauf }
        val w = liste.firstOrNull { it.w.isNotBlank() }?.w.orEmpty()
        fun betraege(l: List<Bafin.Geschaeft>) = l.mapNotNull { g -> g.vol?.let { geld.umrechnen(it, g.w.ifBlank { "EUR" }) } }
        // Summen in der Anzeigewaehrung; ohne noetigen Kurs in der Waehrung der Meldungen
        fun summe(l: List<Bafin.Geschaeft>): String {
            val b = betraege(l)
            val s = InsiderBetraege.summe(b, geld.ziel)
            val fehlt = InsiderBetraege.ohneKurs(b, geld.ziel)
            if (s > 0 || b.isEmpty()) return Fmt.mitSymbol(Fmt.compact(s), Derivatives.currencySymbol(geld.ziel)) + if (fehlt > 0) " " + S.vdOhneKurs(fehlt) else ""
            val andere = b.count { it.second != w && it.second.isNotBlank() }
            return bafinBetrag(l.filter { it.w == w || it.w.isBlank() }.sumOf { it.vol ?: 0.0 }, w) + if (andere > 0) " " + S.vdOhneKurs(andere) else ""
        }
        // Ohne Meldung im Jahr stehen aeltere da: dann ohne Zeitraum
        val zusammen = if (imJahr.isEmpty()) S.vdZusammenfassungAelter(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe))
            else S.vdZusammenfassung(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe))
        val zeilen = liste.mapIndexed { i, g -> ZeilenDaten("$i") { MeldungZeile(state, g, mitFirma = false, geld = geld) } }
        meldungen = ListenModell(
            titel = if (ListenKern.lohnt(zeilen.size)) S.kernMeldungen else "",
            gross = false,
            kern = meldungsKern(kaeufe.size, verkaeufe.size, summeOderNull(betraege(kaeufe), geld.ziel), summeOderNull(betraege(verkaeufe), geld.ziel), geld.ziel),
            zeilen = zeilen,
            kopf = {
                Column {
                    SectionHeader(S.vdKarteTitel)
                    InsiderStand(d.stand, d.luecke())
                    InsiderTrends(kurz, trend, onCluster)
                }
            },
            vorspann = { Text(zusammen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) },
        )
    }
    var gross: ListenModell? = null
    if (anteile.isNotEmpty()) {
        val zeilen = anteile.mapIndexed { i, a -> ZeilenDaten("$i") { AktionaerZeile(a) } }
        gross = ListenModell(
            titel = S.grTitel,
            gross = true,
            kern = KernZeile(listOf(KernTeil(S.kernGross(anteile.size, Fmt.pct(ListenKern.groesster(anteile.map { it.gesamt }) ?: 0.0))))),
            zeilen = zeilen,
            info = S.grHinweis,
        )
    }
    return meldungen to gross
}

@Composable
private fun AktionaerZeile(a: Bafin.Anteil) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            NamenText(a.m, style = MaterialTheme.typography.titleSmall)
            PunktText(
                listOf(a.ml, tag(a.d)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Fmt.pct(a.gesamt), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
    }
}

