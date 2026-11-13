package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.engine.Alarmarten
import de.troetenrepublik.app.engine.TradeCalendar
import de.troetenrepublik.app.engine.TradeCalendar.Art
import de.troetenrepublik.app.i18n.KalenderTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// Weitere Markierungen des Handelskalenders (0.79): Laden der Daten aus den vorhandenen Quellen und Zwischenspeichern,
// Zeichen der Markierungen, antippbare Legende und die Abschnitte im Tagesblatt. Die Rechnung steht in TradeCalendar.

// Ex-Tage aus dem Dividendenplan: vergangene je Depotschluessel (ms), erwartete mit Symbol, Name und Ex-Tag (ms).
internal class ExQuelle(val historie: Map<String, List<Long>>, val erwartet: List<Triple<String, String, Long>>)

// Zusatz-Markierungen der gehaltenen Werte; alles im Hintergrund, der Kalender steht sofort. Fehlt eine Quelle oder ist
// sie gesperrt, bleibt die Markierung still weg.
@Composable
internal fun rememberKalenderZusatz(state: AppState, account: Account, aktien: List<Position>, exQuelle: ExQuelle?): TradeCalendar.Zusatz {
    val zone = remember { ZoneId.systemDefault() }
    val gehalten = aktien.associate { it.symbol to (Symbols.find(it.symbol)?.name ?: it.name) }
    val schluessel = aktien.joinToString("|") { it.symbol }
    val kurse by state.quotes.collectAsState()
    val us = aktien.map { it.symbol }.filter { istUsAktie(it, kurse[it]?.nativeCurrency) }

    var zahlen by remember { mutableStateOf<List<TradeCalendar.Zahlen>>(emptyList()) }
    LaunchedEffect(schluessel) { zahlen = runCatching { zahlenLaden(state, gehalten) }.getOrDefault(emptyList()) }

    var kongress by remember { mutableStateOf<List<TradeCalendar.Kongress>>(emptyList()) }
    LaunchedEffect(schluessel) {
        kongress = if (us.isEmpty()) emptyList()
        else runCatching { TradeCalendar.kongress(state.kongressHandel(), gehalten.filterKeys { it in us }) }.getOrDefault(emptyList())
    }

    var stufungen by remember { mutableStateOf<List<TradeCalendar.Stufung>>(emptyList()) }
    LaunchedEffect(schluessel) { stufungen = runCatching { analystenLaden(state, gehalten.filterKeys { it in us }, zone) }.getOrDefault(emptyList()) }

    val buchungen = account.transactions
    val kaeufe = remember(buchungen) { buchungen.filter { TradeCalendar.istKauf(it) } }
    val verfaelle = remember(account.positions) { TradeCalendar.verfaelle(account.positions, zone) }
    val exDiv = remember(buchungen, exQuelle) {
        exQuelle?.let { q ->
            TradeCalendar.exTage(buchungen.filter { it.type == TxType.DIVIDEND }.map { TradeCalendar.dividende(it) }, q.historie, q.erwartet)
        }.orEmpty()
    }
    return remember(zahlen, kongress, stufungen, kaeufe, verfaelle, exDiv) {
        TradeCalendar.Zusatz(zahlen = zahlen, exDiv = exDiv, kongress = kongress, stufungen = stufungen, verfaelle = verfaelle, kaeufe = kaeufe)
    }
}

// Zahlentermine: fruehere Ergebnisse je Wert (Nasdaq, zwischengespeichert), kommende 21 Tage aus dem Ergebniskalender
// (je Tag zwischengespeichert), danach und fuer alle uebrigen Werte der Termin aus dem Kursabruf. Ohne Nasdaq (Pause nach
// Fehlern) bleiben die Termine aus dem Kursabruf.
private suspend fun zahlenLaden(state: AppState, gehalten: Map<String, String>): List<TradeCalendar.Zahlen> = withContext(Dispatchers.Default) {
    if (gehalten.isEmpty()) return@withContext emptyList()
    val heute = LocalDate.now(ZoneId.of("America/New_York"))
    val ueber = HashMap<String, List<NasdaqKalender.Ueberraschung>>()
    for (s in gehalten.keys) {
        if (state.nasdaqPausiert()) break
        ohneAbbruch { state.nasdaqUeberraschung(s) }.getOrNull()?.let { ueber[s] = it }
    }
    val termine = ArrayList<Quartalszahlen.Termin>()
    if (!state.nasdaqPausiert()) {
        ohneAbbruch { state.quartalszahlen((0L..20L).map { heute.plusDays(it) }) }.getOrNull()
            ?.values?.forEach { l -> l?.let { termine += it.filter { t -> t.symbol in gehalten } } }
    }
    val bekannt = termine.map { it.symbol }.toSet()
    termine += ohneAbbruch { state.eigeneZahlentermine((0L..180L).map { heute.plusDays(it) }, bekannt) }.getOrNull().orEmpty()
    TradeCalendar.zahlen(termine, ueber, gehalten)
}

// Urteile der Analysten je US-Wert aus dem Zwischenspeicher der Stufen-Seite, sonst nacheinander mit Pause geholt; nach
// zwei Fehlern in Folge ist Schluss.
private suspend fun analystenLaden(state: AppState, gehalten: Map<String, String>, zone: ZoneId): List<TradeCalendar.Stufung> = withContext(Dispatchers.Default) {
    val aus = ArrayList<TradeCalendar.Stufung>()
    var fehler = 0
    for ((s, name) in gehalten) {
        val alt = StufenUrteile.karte[s]
        var urteile = alt?.takeIf { System.currentTimeMillis() - it.first < StufenUrteile.FRIST_MS }?.second
        if (urteile == null) {
            if (fehler >= 2) continue
            delay(StufenUrteile.PAUSE_MS)
            val neu = ohneAbbruch { state.market.analysten(s)?.urteile }.getOrNull()
            if (neu != null) { StufenUrteile.karte[s] = System.currentTimeMillis() to neu; fehler = 0 } else fehler++
            urteile = neu ?: alt?.second
        }
        if (urteile != null) aus += TradeCalendar.stufungen(s, name, urteile, zone)
    }
    aus
}

// Boersenlaender der gehaltenen Werte fuer die Feiertage.
internal fun feiertagLaender(symbole: List<String>): Set<String> = symbole.map { Alarmarten.land(it) }.filter { it.isNotEmpty() }.toSet()

// ------------------------------------------------------------ Zeichen

private val KONGRESS_FARBE = Color(0xFF9F57E8)
private val ANALYSTEN_FARBE = Color(0xFF0E8A8A)
private val VERFALL_FARBE = Color(0xFFDB7432)
private val KAUF_FARBE = Color(0xFF5B7CF0)
private val MAKRO_FARBE = Color(0xFF7F8BA0)

// Zeichen einer Art: Punkt (Rueckblick, Dividende erhalten), Ring (Dividende erwartet), Quadrat (Zahlen), Raute (Verfall),
// Balken (Ex-Dividende), Plus (Kauf), Rahmen (Makro), Dreiecke fuer Insider, Kongress und Analysten (hoch gruen oder in
// eigener Farbe, runter rot oder in eigener Farbe). hoch und runter sagen, welche Richtungen vorkommen.
@Composable
internal fun MarkenGlyph(art: Art, hoch: Boolean = false, runter: Boolean = false, groesse: Dp = 5.dp) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    when (art) {
        Art.RUECKBLICK -> Canvas(Modifier.size(groesse)) { drawCircle(signal.link) }
        Art.DIV_ERHALTEN -> Canvas(Modifier.size(groesse)) { drawCircle(signal.warn) }
        Art.DIV_ERWARTET -> Canvas(Modifier.size(groesse)) { drawCircle(signal.warn, radius = size.minDimension / 2f - 0.6.dp.toPx(), style = Stroke(1.2.dp.toPx())) }
        Art.ZAHLEN -> Canvas(Modifier.size(groesse)) { drawRoundRect(Kategorie.blau, cornerRadius = CornerRadius(size.width * 0.2f)) }
        Art.VERFALL -> Canvas(Modifier.size(groesse)) {
            val m = size.width / 2f
            drawPath(Path().apply { moveTo(m, 0f); lineTo(size.width, m); lineTo(m, size.height); lineTo(0f, m); close() }, VERFALL_FARBE)
        }
        Art.EXDIV -> Canvas(Modifier.size(groesse * 1.5f, groesse * 0.6f)) { drawRoundRect(signal.warn, cornerRadius = CornerRadius(size.height / 2f)) }
        Art.KAUF -> Canvas(Modifier.size(groesse)) {
            val d = size.width * 0.34f
            drawRoundRect(KAUF_FARBE, Offset(0f, (size.height - d) / 2f), Size(size.width, d), CornerRadius(d / 3f))
            drawRoundRect(KAUF_FARBE, Offset((size.width - d) / 2f, 0f), Size(d, size.height), CornerRadius(d / 3f))
        }
        Art.MAKRO -> Canvas(Modifier.size(groesse)) {
            val w = 1.2.dp.toPx()
            drawRoundRect(MAKRO_FARBE, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), CornerRadius(size.width * 0.2f), Stroke(w))
        }
        Art.INSIDER, Art.KONGRESS, Art.ANALYSTEN -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val b = groesse + 1.dp
            if (hoch) DirectionTriangle(1.0, width = b, color = if (art == Art.INSIDER) signal.up else if (art == Art.KONGRESS) KONGRESS_FARBE else ANALYSTEN_FARBE)
            if (runter) DirectionTriangle(-1.0, width = b, color = if (art == Art.INSIDER) signal.down else if (art == Art.KONGRESS) KONGRESS_FARBE else ANALYSTEN_FARBE)
        }
        Art.FEIERTAG -> Box(
            Modifier.size(groesse * 2f, groesse * 1.6f).clip(RoundedCornerShape(3.dp))
                .background(grau.copy(alpha = 0.18f)).border(1.dp, grau.copy(alpha = 0.35f), RoundedCornerShape(3.dp)),
        )
    }
}

// Reihe der Markierungen unter der Zahl einer Kachel: hoechstens drei, in der Reihenfolge der Wichtigkeit.
@Composable
internal fun MarkenZeile(arten: List<Art>, tag: TradeCalendar.Tag?, modifier: Modifier = Modifier) {
    if (arten.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        arten.forEach { a ->
            val (hoch, runter) = tag?.richtung(a) ?: (false to false)
            MarkenGlyph(a, hoch, runter, 5.dp)
        }
    }
}

// ------------------------------------------------------------ Legende

internal fun legendenText(art: Art): String = when (art) {
    Art.RUECKBLICK -> S.calLegendReview
    Art.ZAHLEN -> KalenderTexte.t("zahlen")
    Art.INSIDER -> KalenderTexte.t("insider")
    Art.KONGRESS -> KalenderTexte.t("kongress")
    Art.VERFALL -> KalenderTexte.t("verfall")
    Art.EXDIV -> KalenderTexte.t("exdiv")
    Art.DIV_ERHALTEN -> S.calLegendReceived
    Art.DIV_ERWARTET -> S.calLegendExpected
    Art.ANALYSTEN -> KalenderTexte.t("analysten")
    Art.KAUF -> KalenderTexte.t("kauf")
    Art.MAKRO -> KalenderTexte.t("makro.legende")
    Art.FEIERTAG -> KalenderTexte.t("feiertag")
}

// Legende in zwei Spalten gleicher Breite, alle Zeichen genau untereinander. Jeder Eintrag blendet seine Art im Kalender
// aus und ein (ausgeblendet: gedaempft). Ein Eintrag bricht hoechstens an Wortgrenzen um.
@Composable
internal fun KalenderLegende(aus: Set<Art>, onSchalten: (Art, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
        Art.entries.chunked(2).forEach { zeile ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                zeile.forEach { art ->
                    val an = art !in aus
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Switch) { onSchalten(art, !an) }
                            .padding(vertical = 7.dp).alpha(if (an) 1f else 0.4f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) { MarkenGlyph(art, hoch = true, runter = true, groesse = 6.dp) }
                        Text(legendenText(art), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Abschnitte im Tagesblatt

private const val KURZ_BIS = 5

private fun offenerWert(symbol: String): Boolean = symbol.isNotBlank() && !symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)

// Abschnitt mit Titel; ab sechs Zeilen eingeklappt mit „Alle n“.
@Composable
private fun <T> KurzAbschnitt(titel: String, daten: List<T>, schluessel: Any, zeile: @Composable (T) -> Unit) {
    if (daten.isEmpty()) return
    var alle by remember(schluessel) { mutableStateOf(false) }
    SectionHeader(titel)
    (if (alle || daten.size <= KURZ_BIS) daten else daten.take(KURZ_BIS)).forEach { zeile(it) }
    if (daten.size > KURZ_BIS) {
        Text(
            if (alle) S.less else S.bbAlle(daten.size), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(20.dp) { alle = !alle }.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun ZahlenAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("zahlen"), tag.zusatz.zahlen, tag.datum) { z ->
        val erw = z.epsErwartet?.let { Fmt.price(it, "$") }
        val unter = when {
            z.eps != null -> if (erw != null) KalenderTexte.t("zahlen.ergebnis", erw) else KalenderTexte.t("zahlen.ergebnis.ohne")
            z.geschaetzt -> if (erw != null) KalenderTexte.t("zahlen.geschaetzt.erwartung", erw) else KalenderTexte.t("zahlen.geschaetzt")
            else -> if (erw != null) KalenderTexte.t("zahlen.erwartet", erw) else KalenderTexte.t("zahlen.erwartet.ohne")
        }
        val signal = LocalSignal.current
        TradeZeile(
            state, z.symbol, kurzName(z.symbol, z.name), unter, z.eps?.let { Fmt.price(it, "$") }.orEmpty(),
            z.abweichung?.let { Fmt.signedPct(it, 1) }, if (z.abweichung != null) signal.forChange(z.abweichung) else MaterialTheme.colorScheme.onSurface,
        ) { if (offenerWert(z.symbol)) onOpen(z.symbol) }
    }
}

@Composable
internal fun KongressAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    val geld = rememberInsiderGeld(state)
    KurzAbschnitt(KalenderTexte.t("kongress"), tag.zusatz.kongress.sortedByDescending { it.betrag }, tag.datum) { g ->
        InsiderZeile(
            name = kurzName(g.symbol, g.name),
            angaben = listOf(g.person, KalenderTexte.t(if (g.kauf) "insider.kauf" else "insider.verkauf") + " · " + KalenderTexte.t(if (g.haus) "kongress.haus" else "kongress.senat")),
            kauf = g.kauf, verkauf = !g.kauf, betrag = if (g.betrag > 0) geld.text(g.betrag, "USD").first else "–", datum = "",
            onClick = { if (offenerWert(g.symbol)) onOpen(g.symbol) },
        )
    }
}

@Composable
internal fun VerfallAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("verfall"), tag.zusatz.verfaelle, tag.datum) { v ->
        TradeZeile(
            state, v.basis, v.produkt,
            if (v.verkauft) KalenderTexte.t("verfall.verkauft") else KalenderTexte.t("verfall"),
            S.usStueck(Fmt.qty(v.stueck)), null, MaterialTheme.colorScheme.onSurface,
        ) { onOpen(if (v.kennung.isNotBlank()) DERIVAT_ROUTE + v.kennung else v.basis) }
    }
}

@Composable
internal fun ExDivAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("exdiv"), tag.zusatz.exDiv, tag.datum) { e ->
        TradeZeile(
            state, e.symbol, kurzName(e.symbol, e.name), KalenderTexte.t(if (e.erwartet) "exdiv.erwartet" else "exdiv.erhalten"), "", null,
            MaterialTheme.colorScheme.onSurface,
        ) { if (offenerWert(e.symbol)) onOpen(e.symbol) }
    }
}

@Composable
internal fun StufungAbschnitt(tag: TradeCalendar.Tag, state: AppState, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("analysten"), tag.zusatz.stufungen, tag.datum) { s ->
        InsiderZeile(
            name = kurzName(s.symbol, s.name),
            angaben = listOf(KalenderTexte.t(if (s.hoch) "analysten.hoch" else "analysten.runter") + " · " + s.haus),
            kauf = s.hoch, verkauf = !s.hoch, betrag = s.nach.ifBlank { "–" }, datum = "",
            onClick = { if (offenerWert(s.symbol)) onOpen(s.symbol) },
        )
    }
}

@Composable
internal fun KaufAbschnitt(tag: TradeCalendar.Tag, state: AppState, hideBalances: Boolean, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("kauf"), tag.zusatz.kaeufe, tag.datum) { tx ->
        val art = KalenderTexte.t(if (tx.via == "PLAN") "sparplan.stueck" else "kauf.stueck", Fmt.qty(tx.qty), Fmt.price(tx.price))
        TradeZeile(
            state, tx.symbol, tx.title, art, if (hideBalances) "•••• " + Fmt.symbol else Fmt.money(tx.qty * tx.price), null,
            MaterialTheme.colorScheme.onSurface,
        ) { if (offenerWert(tx.symbol)) onOpen(tx.symbol) }
    }
}

@Composable
internal fun MakroAbschnitt(tag: TradeCalendar.Tag, onOpen: (String) -> Unit) {
    KurzAbschnitt(KalenderTexte.t("makro"), tag.zusatz.makro, tag.datum) { m ->
        Box(Modifier.fillMaxWidth().clickable { onOpen(SEITEN_PRAEFIX + "discover/economy") }) { WirtschaftZeile(m.termin) }
    }
}
