package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.InsiderBetraege
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.Investegate
import de.troetenrepublik.app.market.NasdaqNordic
import de.troetenrepublik.app.market.Politik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Insidergeschaefte einer Aktie aus den Registern weiterer Laender (Schweden, Frankreich, Niederlande, Grossbritannien,
// Schweiz, Indien, Hongkong, Australien, Nordeuropa, Baltikum, Tschechien, Luxemburg, Italien, Zypern und Ungarn):
// Insider-Trend und die letzten Geschaefte. Europa und die Schweiz kommen aus den Landesstaenden, die uebrigen Laender
// werden je Aktie geholt (Australien und Indien nur ueber Import, Hongkong nur als Link). Bei kanadischen Aktien steht
// nur ein Link zu SEDI. Grossbritannien ueber Investegate und Nordeuropa/Baltikum ueber Nasdaq Nordic.
@Composable
internal fun EuInsiderKarte(state: AppState, symbol: String, isin: String, name: String = "", merker: SeitenMerker? = null) {
    val alle by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    // Seit 5.91 auch Sri Lanka, Oman und importierte Laender (ueber Endung oder ISIN)
    val boerse = remember(symbol, isin) { InsiderWelt.ort(symbol, isin) }
    // Bis zum Ende dieses Ladens steht der Platzhalter; danach der Inhalt oder, ohne Geschaefte, wirklich nichts
    var geladen by gemerkt<Boolean>(merker, "eu/$symbol/$isin/geladen") { false }
    LaunchedEffect(symbol, isin) {
        // Schon geladen (Merker der Seite): beim Zurueckscrollen nichts neu holen
        if (geladen) return@LaunchedEffect
        try {
            if (boerse != null) {
                runCatching { state.weltLaden(symbol, isin) }
                return@LaunchedEffect
            }
            if (isin.length != 12) return@LaunchedEffect
            // Selbst importierte MAR-Formulare (seit 5.91) liegen im Land der ISIN
            isin.take(2).uppercase().takeIf { it !in EuInsider.LAENDER }?.let { runCatching { state.euLaden(it, netz = false) } }
            // Nur das Register des Landes der ISIN nachladen; ohne bekanntes Land alle ohne Netz pruefen (weniger Flow-Updates)
            val land0 = EuInsider.landFuerIsin(isin)
            for (land in listOfNotNull(land0).ifEmpty { EuInsider.LAENDER }) {
                val d = runCatching { state.euLaden(land, netz = false) }.getOrNull() ?: continue
                if (land0 == land || d.geschaefte.any { it.isin == isin }) runCatching { state.euLaden(land) }
            }
        } finally {
            // Beim Schlüsselwechsel abgebrochene Aufrufe duerfen das neue Flag nicht vorzeitig setzen
            if (isActive) geladen = true
        }
    }
    // Boersen, deren Meldungen sich nur im Browser ansehen lassen (5.92)
    val nurBrowser = remember(symbol, isin) { InsiderWelt.nurBrowser(symbol, isin) }
    // Land und Geschaefte: je Aktie geholt oder aus einem Register, das die Aktie fuehrt (bei mehreren das mit den
    // meisten). Ausserhalb des Hauptthreads gerechnet, weil das Zaehlen ueber alle Laender geht (5.93); Symbol und ISIN
    // im Ergebnis verhindern beim Wechsel kurz Daten des vorherigen Werts, bereit markiert das Ende der Rechnung
    // Letztes Ergebnis aus dem Merker: beim Zurueckscrollen steht die Karte sofort, die Rechnung laeuft dahinter
    val ergMerker = gemerkt(merker, "eu/$symbol/$isin/erg") { EuErgebnis("", "", emptyList(), symbol, isin) }
    val erg by produceState(ergMerker.value, alle, welt, symbol, isin) {
        value = withContext(Dispatchers.Default) {
            val r = welt[symbol]?.let { (l, liste) -> Triple(l, "", liste.sortedByDescending { it.d }) }
                ?: alle.values.filter { isin.isNotEmpty() }.maxByOrNull { l -> l.geschaefte.count { it.isin == isin } }
                    ?.let { l -> Triple(l.land, l.stand, l.geschaefte.filter { it.isin == isin }) }
                ?: Triple("", "", emptyList())
            EuErgebnis(r.first, r.second, r.third, symbol, isin, bereit = true)
        }
        ergMerker.value = value
    }
    val gueltig = erg.symbol == symbol && erg.isin == isin && erg.bereit
    val (land, stand, eigen) = if (gueltig) erg else EuErgebnis("", "", emptyList(), symbol, isin)
    // Fester Platzhalter bis zum Ende des Ladens und der Rechnung: Die Karte aendert ihre Hoehe danach nur einmal
    if (!geladen || !gueltig) { Spacer(Modifier.height(96.dp)); return }
    // Kanada: Stand aus den SEDI-Wochenberichten; steht die Aktie nicht darin, bleibt der Link zu SEDI
    if (boerse?.first == "CA" && welt[symbol]?.second.isNullOrEmpty()) { SediHinweis(); return }
    if (eigen.isEmpty()) {
        // 5.95: Grossbritannien ueber Investegate, Nordeuropa und Baltikum ueber Nasdaq Nordic, Ungarn als Links (MNB)
        if (nurBrowser == "GB") Investegate.ticker(symbol)?.let { InvestegateKarte(state, it); return }
        if (boerse?.first == "HU") {
            val firma = de.troetenrepublik.app.market.Symbols.find(symbol)?.name ?: name
            if (firma.isNotBlank()) { MnbKarte(state, firma); return }
        }
        if (nurBrowser != null && nurBrowser in NasdaqNordic.LAENDER) {
            val firma = de.troetenrepublik.app.market.Symbols.find(symbol)?.name ?: name
            if (firma.isNotBlank()) { NasdaqNordicKarte(state, nurBrowser, firma); return }
        }
        nurBrowser?.let { InsiderBrowserHinweis(it) }
        return
    }
    val geld = rememberInsiderGeld(state)
    val links = appLinks()
    fun betrag(v: Double, w: String) = Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w))
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    Column {
        // Oman meldet Halter ab 5 %, nicht unbedingt die Fuehrung
        SectionHeader(if (land == "OM") S.omTitel else S.vdKarteTitel)
        if (land == "OM") AufklappText(S.omHinweis, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        // Ein Jahr Zeitraum (seit 5.93); ist darin keine Meldung, bleiben die letzten Meldungen stehen
        val ab = remember { InsiderBetraege.abTag(java.time.LocalDate.now()) }
        val liste = remember(eigen, ab) { eigen.filter { it.d >= ab } }.ifEmpty { eigen }
        var alleZeigen by gemerkt<Boolean>(merker, "eu/$symbol/alle") { false }
        remember(liste) { EuInsider.trend(liste, ab) }?.let { InsiderTrendBlock(it) }
        val kaeufe = liste.filter { it.kauf }
        val verkaeufe = liste.filter { it.verkauf }
        val w = liste.firstOrNull { it.w.isNotBlank() }?.w.orEmpty()
        // Quellen ohne Preis (Oman, Katar) haben keine Summen
        val ohneBetrag = land in InsiderWelt.NEU && liste.none { it.wert > 0 }
        // Summen in der Anzeigewaehrung; ohne noetigen Kurs in der Waehrung der Meldungen
        fun summe(l: List<EuInsider.Geschaeft>): String {
            val betraege = l.filter { it.wert > 0 }.map { geld.umrechnen(it.wert, it.w) }
            val s = InsiderBetraege.summe(betraege, geld.ziel)
            if (s > 0 || betraege.isEmpty()) return Fmt.mitSymbol(Fmt.compact(s), Derivatives.currencySymbol(geld.ziel))
            return betrag(l.filter { it.w == w }.sumOf { it.wert }, w)
        }
        if (!ohneBetrag) Text(
            S.vdZusammenfassung(kaeufe.size, summe(kaeufe), verkaeufe.size, summe(verkaeufe)),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        // Zuerst echte Kaeufe und Verkaeufe; Zuteilungen und Aehnliches nur, wenn es nichts anderes gibt
        val zeilen = liste.filter { it.kauf || it.verkauf }.ifEmpty { liste }
        zeilen.take(if (alleZeigen) 30 else 5).forEach { g ->
            // Betrag in der Anzeigewaehrung, Originalwaehrung darunter
            val (betragText, orig) = if (g.wert > 0) geld.text(g.wert, g.w)
                else if (ohneBetrag && g.n > 0) S.f144Stueck(Fmt.compact(g.n)) to null else "–" to null
            InsiderZeile(
                // Schweizer Meldungen nennen keine Namen, dann steht die Rolle oben
                name = g.p.ifBlank { S.euRolle(g.r) },
                angaben = if (land == "OM") {
                    listOf(listOf(S.omAnteil(g.t), S.euArt(g.art)).filter { it.isNotBlank() }.joinToString(" · "))
                } else {
                    listOf(
                        g.fuer,
                        listOf(if (g.p.isBlank()) g.t else S.euRolle(g.r), S.euArt(g.art)).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                },
                kauf = g.kauf,
                verkauf = g.verkauf,
                betrag = betragText,
                datum = tag(g.d),
                zusatz = orig,
                onClick = if (g.q.isNotBlank()) {
                    { runCatching { links.openUri(g.q) } }
                } else null,
            )
        }
        if (zeilen.size > 5) ListenPfeil(alleZeigen) { alleZeigen = !alleZeigen }
        Text(
            (if (land == "KW") S.wqKwQuelle(if (stand.isBlank()) "" else tag(stand)) else if (land in InsiderWelt.NEU) S.weltQuelle(land, if (stand.isBlank()) "" else tag(stand)) else S.euQuelle(land, if (stand.isBlank()) "" else tag(stand))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// Kanada: SEDI laesst sich nur im Browser abfragen.
@Composable
private fun SediHinweis() {
    val links = appLinks()
    Column {
        SectionHeader(S.vdKarteTitel)
        Text(S.euSedi, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(
            S.euSediLink, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link,
            modifier = Modifier.clickable { runCatching { links.openUri(InsiderWelt.SEDI) } }.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// Land, Stand und Geschaefte einer Aktie aus der Rechnung der Karte; Symbol und ISIN gehoeren dazu, damit beim
// Wechsel nie kurz Daten des vorherigen Werts stehen, bereit erst nach der Rechnung.
private data class EuErgebnis(
    val land: String, val stand: String, val eigen: List<EuInsider.Geschaeft>, val symbol: String, val isin: String, val bereit: Boolean = false,
)
